package top.bincnpc.cnpccurios.persist;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import top.bincnpc.cnpccurios.client.RenderIsolation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/** 不启动 Minecraft 的最小回归检查；生成内容只留在工作区 Temp。 */
public final class SafetyChecks {
    public static void main(String[] args) throws Exception {
        Path tempRoot = Files.createDirectories(Path.of(args[0]));
        Path root = Files.createTempDirectory(tempRoot, "safety-");
        UUID owner = UUID.randomUUID();
        CompoundTag data = data("minecraft:diamond");
        CompoundTag source = entity(data);
        CompoundTag original = source.copy();
        NpcCuriosStore.State state = new NpcCuriosStore.State(root, owner);

        assert NpcCuriosStore.prepareLoad(state, source).equals(original);
        assert source.equals(original) : "migration mutated source";
        assert NpcCuriosStore.read(state.file()).getCompound("Data").equals(data);
        assert Files.isRegularFile(root.resolve(owner + ".legacy.dat"));

        CompoundTag saved = source.copy();
        NpcCuriosStore.save(state, saved);
        assert saved.hasUUID(NpcCuriosStore.ID);
        assert !saved.getCompound(NpcCuriosStore.ATTACHMENTS).contains(NpcCuriosStore.CAP);
        assert saved.getCompound(NpcCuriosStore.ATTACHMENTS)
                .getString("other:attachment").equals("untouched");

        long modified = Files.getLastModifiedTime(state.file()).toMillis();
        NpcCuriosStore.State reload = new NpcCuriosStore.State(root, owner);
        CompoundTag restored = NpcCuriosStore.prepareLoad(reload, saved);
        assert restored.getCompound(NpcCuriosStore.ATTACHMENTS)
                .getCompound(NpcCuriosStore.CAP).equals(data);
        assert !saved.getCompound(NpcCuriosStore.ATTACHMENTS).contains(NpcCuriosStore.CAP)
                : "restore mutated input";
        NpcCuriosStore.save(reload, entity(data));
        assert Files.getLastModifiedTime(state.file()).toMillis() == modified
                : "unchanged data rewritten";

        UUID cloneOwner = UUID.randomUUID();
        NpcCuriosStore.State clone = new NpcCuriosStore.State(root, cloneOwner);
        CompoundTag cloneData = NpcCuriosStore.prepareLoad(clone, saved);
        assert !clone.id.equals(state.id);
        NpcCuriosStore.save(clone, cloneData);
        assert NpcCuriosStore.read(clone.file()).getUUID("Owner").equals(cloneOwner);
        assert NpcCuriosStore.read(state.file()).getCompound("Data").equals(data);

        CompoundTag edited = entity(data("minecraft:emerald"));
        edited.putUUID(NpcCuriosStore.ID, state.id);
        NpcCuriosStore.State edits = new NpcCuriosStore.State(root, owner);
        NpcCuriosStore.prepareLoad(edits, edited);
        NpcCuriosStore.save(edits, edited);
        assert NpcCuriosStore.read(state.file()).getCompound("Data").equals(data("minecraft:emerald"));
        assert NpcCuriosStore.read(root.resolve(owner + ".legacy.dat"))
                .getCompound("Data").equals(data);
        assert NpcCuriosStore.read(root.resolve(owner + ".bak.dat"))
                .getCompound("Data").equals(data);

        byte[] corrupted = {1, 2, 3};
        Files.write(state.file(), corrupted);
        NpcCuriosStore.State recovered = new NpcCuriosStore.State(root, owner);
        CompoundTag recoveredData = NpcCuriosStore.prepareLoad(recovered, saved);
        assert !recovered.blocked;
        assert !recovered.id.equals(state.id);
        assert recoveredData.getCompound(NpcCuriosStore.ATTACHMENTS)
                .getCompound(NpcCuriosStore.CAP).equals(data);
        assert Arrays.equals(Files.readAllBytes(state.file()), corrupted)
                : "damaged original overwritten";

        NpcCuriosStore.State missing = new NpcCuriosStore.State(root.resolve("missing"), owner);
        CompoundTag reference = new CompoundTag();
        reference.putUUID(NpcCuriosStore.ID, UUID.randomUUID());
        NpcCuriosStore.prepareLoad(missing, reference);
        assert missing.blocked;
        CompoundTag empty = entity(data(""));
        NpcCuriosStore.save(missing, empty);
        assert empty.getUUID(NpcCuriosStore.ID).equals(reference.getUUID(NpcCuriosStore.ID));
        assert !empty.getCompound(NpcCuriosStore.ATTACHMENTS).contains(NpcCuriosStore.CAP)
                : "missing store replaced by empty data";
        CompoundTag newlyEquipped = entity(data("minecraft:iron_ingot"));
        NpcCuriosStore.save(missing, newlyEquipped);
        assert newlyEquipped.getCompound(NpcCuriosStore.ATTACHMENTS)
                .contains(NpcCuriosStore.CAP) : "new equipment lost on read failure";

        Path unwritable = root.resolve("not-a-directory");
        Files.write(unwritable, new byte[]{0});
        NpcCuriosStore.State failing = new NpcCuriosStore.State(unwritable, owner);
        CompoundTag retained = entity(data);
        NpcCuriosStore.prepareLoad(failing, retained);
        NpcCuriosStore.save(failing, retained);
        assert retained.getCompound(NpcCuriosStore.ATTACHMENTS)
                .getCompound(NpcCuriosStore.CAP).equals(data);
        assert !retained.contains(NpcCuriosStore.ID);

        CompoundTag noItems = data("");
        CompoundTag removed = entity(noItems);
        NpcCuriosStore.save(clone, removed);
        assert NpcCuriosStore.read(clone.file()).getCompound("Data").equals(noItems)
                : "intentional unequip ignored";

        PoseStack parent = new PoseStack();
        parent.translate(1, 2, 3);
        var before = new org.joml.Matrix4f(parent.last().pose());
        PoseStack child = RenderIsolation.copyPose(parent);
        child.translate(99, 99, 99);
        child.pushPose();
        assert parent.last().pose().equals(before) : "child corrupted parent matrix";
        assert parent.clear() : "child leaked into parent stack";
        PoseStack overPop = RenderIsolation.copyPose(parent);
        overPop.popPose();
        assert parent.last().pose().equals(before) : "over-pop damaged parent";

        HumanoidModel<LivingEntity> model = new HumanoidModel<>(
                HumanoidModel.createMesh(CubeDeformation.NONE, 0).getRoot().bake(64, 64));
        boolean visible = model.head.visible;
        RenderIsolation.ModelState snapshot = new RenderIsolation.ModelState(model);
        model.head.visible = !visible;
        model.body.skipDraw = true;
        model.leftArm.xScale = 0;
        model.crouching = true;
        model.riding = true;
        snapshot.restore();
        assert model.head.visible == visible && !model.body.skipDraw && model.leftArm.xScale == 1;
        assert !model.crouching && !model.riding;

        System.out.println("SAFETY_CHECKS_PASSED: attachments migration, reload, no-op save, "
                + "clone isolation, inline edits, backups, corrupt/missing/unwritable stores, "
                + "unequip, render matrix/model isolation");
    }

    private static CompoundTag entity(CompoundTag data) {
        CompoundTag entity = new CompoundTag();
        CompoundTag attachments = new CompoundTag();
        attachments.put(NpcCuriosStore.CAP, data.copy());
        attachments.putString("other:attachment", "untouched");
        entity.put(NpcCuriosStore.ATTACHMENTS, attachments);
        return entity;
    }

    private static CompoundTag data(String item) {
        CompoundTag data = new CompoundTag();
        ListTag curios = new ListTag();
        CompoundTag slot = new CompoundTag();
        slot.putString("Identifier", "curio");
        CompoundTag handler = new CompoundTag();
        CompoundTag stacks = new CompoundTag();
        ListTag items = new ListTag();
        if (!item.isEmpty()) {
            CompoundTag stack = new CompoundTag();
            stack.putString("id", item);
            stack.putInt("count", 1);
            items.add(stack);
        }
        stacks.put("Items", items);
        stacks.putInt("Size", 1);
        handler.put("Stacks", stacks);
        slot.put("StacksHandler", handler);
        curios.add(slot);
        data.put("Curios", curios);
        return data;
    }
}
