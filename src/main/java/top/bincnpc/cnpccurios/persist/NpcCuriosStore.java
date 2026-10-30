package top.bincnpc.cnpccurios.persist;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
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

/**
 * NPC 饰品的世界级外置存储。
 * 只有外置文件完成同步写入并回读一致后，才从实体 NBT 摘除 Curios attachment。
 */
public final class NpcCuriosStore {
    static final String ID = "CNPCcuriosDataId";
    static final String ATTACHMENTS = "neoforge:attachments";
    static final String CAP = "curios:inventory";
    private static final Map<EntityNPCInterface, State> ACTIVE = new WeakHashMap<>();

    private NpcCuriosStore() {}

    static final class State {
        final Path directory;
        final UUID owner;
        UUID id;
        CompoundTag data;
        boolean verified;
        boolean blocked;
        Tag originalReference;

        State(Path directory, UUID owner) {
            this.directory = directory;
            this.owner = owner;
            this.id = owner;
        }

        Path file() {
            return directory.resolve(id + ".dat");
        }
    }

    /** Entity.load HEAD：必要时把外置数据贴回副本，绝不修改传入的原始 NBT。 */
    public static synchronized CompoundTag prepareLoad(EntityNPCInterface npc, CompoundTag input) {
        if (npc == null || input == null || !(npc.level() instanceof ServerLevel level)) return input;
        UUID owner = input.hasUUID("UUID") ? input.getUUID("UUID") : npc.getUUID();
        State state = new State(directory(level), owner);
        ACTIVE.put(npc, state);
        return prepareLoad(state, input);
    }

    /** Entity.saveWithoutId RETURN：持久化并在确认安全后从本次保存结果摘除内联数据。 */
    public static synchronized void save(EntityNPCInterface npc, CompoundTag tag) {
        if (npc == null || tag == null || !(npc.level() instanceof ServerLevel level)) return;
        Path directory = directory(level);
        State state = ACTIVE.get(npc);
        if (state == null || !state.owner.equals(npc.getUUID()) || !state.directory.equals(directory)) {
            state = new State(directory, npc.getUUID());
            // 覆盖 CNPC 的克隆/导入等未经过普通 load 的保存路径。
            prepareLoad(state, tag);
            ACTIVE.put(npc, state);
        }
        save(state, tag);
    }

    private static Path directory(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data/cnpccurios/inventory");
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
                    // 损坏的原文件原样保留；从上一版恢复到新的随机 ID，绝不覆盖坏文件。
                    record = read(state.file().resolveSibling(state.id + ".bak.dat"));
                    CNPCcurios.LOGGER.warn("已从备份恢复 NPC {} 的饰品数据；原损坏文件保留",
                            state.owner);
                    state.id = UUID.randomUUID();
                }
                existing = record.getCompound("Data").copy();
                if (!record.getUUID("Owner").equals(state.owner)) state.id = UUID.randomUUID();
            }

            if (inline != null) {
                validateData(inline);
                // 显式内联编辑优先于旧外置引用；迁移时保留不可变 legacy 备份。
                state.data = inline.copy();
                if (state.originalReference == null && hasCuriosEntries(inline)) {
                    Path legacy = state.directory.resolve(state.id + ".legacy.dat");
                    if (Files.exists(legacy)) read(legacy);
                    else write(legacy, record(state, inline), false);
                }
            } else if (existing != null) {
                state.data = existing;
            } else {
                return input;
            }

            if (hasCuriosEntries(state.data) || state.originalReference != null) {
                if (!Files.exists(state.file()) || !state.data.equals(existing)) {
                    write(state.file(), record(state, state.data), true);
                }
                state.verified = true;
            }
            if (inline != null) return input; // 迁移加载不破坏源 NBT；在后续成功保存时再摘除。

            CompoundTag restored = input.copy();
            CompoundTag attachments = restored.getCompound(ATTACHMENTS);
            attachments.put(CAP, state.data.copy());
            restored.put(ATTACHMENTS, attachments);
            return restored;
        } catch (IOException | RuntimeException error) {
            state.blocked = true;
            CNPCcurios.LOGGER.error("NPC {} 饰品迁移/加载失败；原 NBT 与文件均保留 ({})",
                    state.owner, state.file(), error);
            return input;
        }
    }

    static void save(State state, CompoundTag tag) {
        CompoundTag data = inline(tag);
        if (state.blocked) {
            if (state.originalReference != null) {
                tag.put(ID, state.originalReference.copy());
                // 外置源缺失时，不能让一个空 attachment 在下次加载时覆盖未知旧库存。
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
            if (!state.verified && !hasCuriosEntries(data)) return;
            if (!state.verified || !data.equals(state.data) || !Files.isRegularFile(state.file())) {
                write(state.file(), record(state, data), true);
                state.data = data.copy();
                state.verified = true;
            }
            tag.putUUID(ID, state.id);
            strip(tag); // 只删 Curios attachment，且发生在写入与回读验证成功之后。
        } catch (IOException | RuntimeException error) {
            CNPCcurios.LOGGER.error("NPC {} 饰品保存失败；内联 NBT 保留 ({})",
                    state.owner, state.file(), error);
        }
    }

    private static CompoundTag inline(CompoundTag tag) {
        CompoundTag attachments = tag.getCompound(ATTACHMENTS);
        return attachments.contains(CAP, Tag.TAG_COMPOUND) ? attachments.getCompound(CAP) : null;
    }

    private static void strip(CompoundTag tag) {
        CompoundTag attachments = tag.getCompound(ATTACHMENTS);
        attachments.remove(CAP);
        if (attachments.isEmpty()) tag.remove(ATTACHMENTS);
        else tag.put(ATTACHMENTS, attachments);
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

    /** Curios 会保存槽位结构与修饰符；即使当前无物品，只要有槽位条目也不能当作无数据。 */
    private static boolean hasCuriosEntries(CompoundTag data) {
        return !data.getList("Curios", Tag.TAG_COMPOUND).isEmpty();
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
            CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            if (root.getInt("Format") != 1 || !root.hasUUID("Owner")
                    || !root.contains("Data", Tag.TAG_COMPOUND)) {
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
                read(target); // 不以新数据覆盖一个当前不可读的原文件。
                Path previous = target.resolveSibling(
                        target.getFileName().toString().replace(".dat", ".bak.dat"));
                Files.copy(target, previous, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!root.equals(read(target))) throw new IOException("Curios committed write verification failed");
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
