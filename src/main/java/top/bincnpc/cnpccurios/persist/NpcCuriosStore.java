package top.bincnpc.cnpccurios.persist;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import noppes.npcs.entity.EntityNPCInterface;
import top.bincnpc.cnpccurios.CNPCcurios;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** World-scoped files; never strip inline data before a verified, durable write. */
public final class NpcCuriosStore {
    static final String ID = "CNPCcuriosDataId";
    static final String CAP = "curios:inventory";
    private static final Map<EntityNPCInterface, State> ACTIVE = new WeakHashMap<>();

    private NpcCuriosStore() {}

    static final class State {
        final Path directory;
        final UUID owner;
        UUID id;
        CompoundTag data;
        boolean verified, blocked;
        Tag originalReference;

        State(Path directory, UUID owner) {
            this.directory = directory;
            this.owner = owner;
            this.id = owner;
        }

        Path file() { return directory.resolve(id + ".dat"); }
    }

    public static synchronized CompoundTag prepareLoad(EntityNPCInterface npc, CompoundTag input) {
        if (!(npc.level() instanceof ServerLevel level)) return input;
        UUID owner = input.hasUUID("UUID") ? input.getUUID("UUID") : npc.getUUID();
        State state = new State(directory(level), owner);
        ACTIVE.put(npc, state);
        return prepareLoad(state, input);
    }

    public static synchronized void save(EntityNPCInterface npc, CompoundTag tag) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        Path directory = directory(level);
        State state = ACTIVE.get(npc);
        if (state == null || !state.owner.equals(npc.getUUID()) || !state.directory.equals(directory)) {
            state = new State(directory, npc.getUUID());
            // Also handles entities created through NPC-only clone/import paths.
            prepareLoad(state, tag);
            ACTIVE.put(npc, state);
        }
        save(state, tag);
    }

    private static Path directory(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/cnpccurios/inventory");
    }

    static CompoundTag prepareLoad(State state, CompoundTag input) {
        state.originalReference = input.get(ID) == null ? null : input.get(ID).copy();
        CompoundTag inline = inline(input);
        try {
            if (state.originalReference != null) {
                if (!input.hasUUID(ID)) throw new IOException("invalid external data UUID");
                state.id = input.getUUID(ID);
            }
            CompoundTag existing = null;
            if (Files.exists(state.file()) || state.originalReference != null) {
                CompoundTag record;
                try {
                    record = read(state.file());
                } catch (IOException unreadable) {
                    // Recover to a NEW file. Never overwrite a damaged original with a stale backup.
                    record = read(state.file().resolveSibling(state.id + ".bak.dat"));
                    CNPCcurios.LOGGER.warn("Recovered NPC {} Curios from backup; original file retained", state.owner);
                    state.id = UUID.randomUUID();
                }
                existing = record.getCompound("Data").copy();
                if (!record.getUUID("Owner").equals(state.owner)) state.id = UUID.randomUUID();
            }

            if (inline != null) {
                validateData(inline);
                state.data = inline.copy(); // Explicit inline edits win over an older reference.
                if (state.originalReference == null && !inline.getList("Curios", Tag.TAG_COMPOUND).isEmpty()) {
                    Path legacy = state.directory.resolve(state.id + ".legacy.dat");
                    if (Files.exists(legacy)) read(legacy);
                    else write(legacy, record(state, inline), false);
                }
            } else if (existing != null) {
                state.data = existing;
            } else {
                return input;
            }

            if (!state.data.getList("Curios", Tag.TAG_COMPOUND).isEmpty() || state.originalReference != null) {
                if (!Files.exists(state.file()) || !state.data.equals(existing)) {
                    write(state.file(), record(state, state.data), true);
                }
                state.verified = true;
            }
            if (inline != null) return input; // Migration does not mutate/destructively clean the source NBT.
            CompoundTag restored = input.copy();
            CompoundTag caps = restored.getCompound("ForgeCaps");
            caps.put(CAP, state.data.copy());
            restored.put("ForgeCaps", caps);
            return restored;
        } catch (IOException | RuntimeException error) {
            state.blocked = true;
            CNPCcurios.LOGGER.error("NPC {} Curios migration/load failed; original NBT/files retained ({})",
                    state.owner, state.file(), error);
            return input;
        }
    }

    static void save(State state, CompoundTag tag) {
        CompoundTag data = inline(tag);
        if (state.blocked) {
            if (state.originalReference != null) {
                tag.put(ID, state.originalReference.copy());
                // Do not turn a missing store into an authoritative empty inventory on next load.
                if (data != null && !hasItems(data)) strip(tag);
            }
            return;
        }
        if (data == null) {
            if (state.verified) tag.putUUID(ID, state.id);
            return;
        }
        try {
            validateData(data);
            if (!state.verified && data.getList("Curios", Tag.TAG_COMPOUND).isEmpty()) return;
            if (!state.verified || !data.equals(state.data) || !Files.isRegularFile(state.file())) {
                write(state.file(), record(state, data), true);
                state.data = data.copy();
                state.verified = true;
            }
            tag.putUUID(ID, state.id);
            strip(tag); // Only this capability is removed, after successful verification.
        } catch (IOException | RuntimeException error) {
            CNPCcurios.LOGGER.error("NPC {} Curios save failed; inline NBT retained ({})",
                    state.owner, state.file(), error);
        }
    }

    private static CompoundTag inline(CompoundTag tag) {
        CompoundTag caps = tag.getCompound("ForgeCaps");
        return caps.contains(CAP, Tag.TAG_COMPOUND) ? caps.getCompound(CAP) : null;
    }

    private static void strip(CompoundTag tag) {
        CompoundTag caps = tag.getCompound("ForgeCaps");
        caps.remove(CAP);
        if (caps.isEmpty()) tag.remove("ForgeCaps");
    }

    private static boolean hasItems(CompoundTag data) {
        for (Tag slot : data.getList("Curios", Tag.TAG_COMPOUND)) {
            CompoundTag stacks = ((CompoundTag) slot).getCompound("StacksHandler");
            for (String type : new String[]{"Stacks", "Cosmetics"}) {
                if (!stacks.getCompound(type).getList("Items", Tag.TAG_COMPOUND).isEmpty()) return true;
            }
        }
        return false;
    }

    private static void validateData(CompoundTag data) throws IOException {
        if (!data.contains("Curios", Tag.TAG_LIST)) throw new IOException("missing Curios list");
    }

    private static CompoundTag record(State state, CompoundTag data) {
        CompoundTag record = new CompoundTag();
        record.putInt("Format", 1);
        record.putUUID("Owner", state.owner);
        record.put("Data", data.copy());
        return record;
    }

    static CompoundTag read(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            CompoundTag root = NbtIo.readCompressed(input);
            if (root.getInt("Format") != 1 || !root.hasUUID("Owner") || !root.contains("Data", Tag.TAG_COMPOUND)) {
                throw new IOException("invalid Curios store: " + file);
            }
            validateData(root.getCompound("Data"));
            return root;
        } catch (RuntimeException error) {
            throw new IOException("invalid Curios store: " + file, error);
        }
    }

    private static void write(Path target, CompoundTag root, boolean backup) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "curios-", ".tmp");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, bytes);
            try (FileOutputStream output = new FileOutputStream(temp.toFile())) {
                output.write(bytes.toByteArray());
                output.getFD().sync();
            }
            if (!root.equals(read(temp))) throw new IOException("Curios staged write verification failed");
            if (backup && Files.exists(target)) {
                read(target); // Do not overwrite an unreadable original.
                Path previous = target.resolveSibling(target.getFileName().toString().replace(".dat", ".bak.dat"));
                Files.copy(target, previous, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!root.equals(read(target))) throw new IOException("Curios committed write verification failed");
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
