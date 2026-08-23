package top.bincnpc.cnpccurios.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import noppes.npcs.containers.ContainerNPCInv;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bincnpc.cnpccurios.CNPCcurios;
import top.bincnpc.cnpccurios.CnpcCuriosConfig;
import top.bincnpc.cnpccurios.CuriosSlotLayout;
import top.bincnpc.cnpccurios.NpcSlotInheritance;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * 在 NPC 物品栏容器构造完成后追加 Curios 饰品槽。
 *
 * 本类声明 extends AbstractContainerMenu 的原因：
 * addSlot 是 AbstractContainerMenu 的 protected 方法，只有在子类里才能合法调用。
 * ContainerNPCInv 本身就 extends AbstractContainerMenu（CNPC1.21.1反编译
 * noppes\npcs\containers\ContainerNPCInv.java:29-30），所以这个继承声明与目标类层级一致。
 * 1.21.1 NeoForge 运行时用 mojmap，方法名就是 addSlot，
 * 因此 1.20.1 版里那套 m_38897_ 反射兜底全部删除。
 *
 * 注意：这里只「追加」槽位，不重建。addSlot 会同步扩充 slots / lastSlots / remoteSlots
 * 三个 list（neoforge-21.1.235-sources.jar → AbstractContainerMenu.java:105-109），
 * 三者长度恒等，不会触发 1.20.5+ remoteSlots 长度不一致导致的初始全量包越界崩溃。
 */
@Mixin(value = ContainerNPCInv.class, remap = false)
public abstract class MixinContainerNPCInv extends AbstractContainerMenu {

    /** NPC原始slot数：盔甲4 + 物品3 + 掉落栏9 + 玩家背包(27+9) = 52
     *  依据 CNPC1.21.1反编译 ContainerNPCInv.java:34-53 逐个数出，与 1.20.1 一致 */
    @Unique
    private static final int NPC_BASE_SLOTS = 52;

    /** 仅为满足编译（mixin 类不会被真正实例化），构造体不会进入产物 */
    protected MixinContainerNPCInv() {
        super(null, 0);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cnpccurios$onConstructed(int containerId, Inventory playerInventory, int entityId, CallbackInfo ci) {
        // 容器构造在客户端与服务端都会执行，两侧必须注入完全相同数量的槽位，否则槽位索引错位
        EntityNPCInterface npc = cnpccurios$getNpc(playerInventory, entityId);
        if (npc == null) return;
        cnpccurios$injectSlots(npc);
    }

    /**
     * 按 entityId 取出 NPC 实体。
     * 取法与 CNPC 原版构造里完全一致（ContainerNPCInv.java:33
     * playerInventory.player.level().getEntity(entityId)），mojmap 直接可用。
     * 逐层非空判断：玩家/世界/实体任一为空或类型不符都安全返回 null（约法第7条第③项）。
     */
    @Unique
    private EntityNPCInterface cnpccurios$getNpc(Inventory inv, int entityId) {
        if (inv == null || inv.player == null) return null;
        if (inv.player.level() == null) return null;

        Entity e = inv.player.level().getEntity(entityId);
        return e instanceof EntityNPCInterface npc ? npc : null;
    }

    /**
     * 按 Curios 给该 NPC 分配的槽位逐格追加 SlotItemHandler。
     * 排布沿用 1.20.1：每列 6 格竖排，列间距 18px，起点由客户端配置决定。
     */
    @Unique
    private void cnpccurios$injectSlots(EntityNPCInterface npc) {
        CuriosApi.getCuriosInventory(npc).ifPresent(handler -> {
            // 服务端读 CLIENT 配置会抛异常，这里走安全访问器（详见 CnpcCuriosConfig.slotX 注释）
            final int baseX = CnpcCuriosConfig.slotX();
            final int baseY = CnpcCuriosConfig.slotY();

            // 每列行数按可用高度自动算，槽位多时自动换列（缩排），避免溢出面板
            final int rows = CuriosSlotLayout.rowsPerColumn(baseY);

            int idx = 0;
            for (var entry : handler.getCurios().entrySet()) {
                final String slotId = entry.getKey();
                IDynamicStackHandler stacks = entry.getValue().getStacks();
                if (stacks == null) continue;

                for (int i = 0; i < stacks.getSlots(); i++) {
                    final int slotIndexInType = i;
                    int x = CuriosSlotLayout.slotX(idx, baseX, rows);
                    int y = CuriosSlotLayout.slotY(idx, baseY, rows);

                    this.addSlot(new SlotItemHandler(stacks, slotIndexInType, x, y) {
                        @Override
                        public boolean mayPlace(ItemStack s) {
                            // Curios 槽位类型校验 + 「同一物品不得重复佩戴」
                            // 后者用于规避泰拉饰品 ImmutableMap 重复 key 崩服，
                            // 详见 NpcSlotInheritance.mayPlaceInNpcSlot 注释
                            return NpcSlotInheritance.mayPlaceInNpcSlot(
                                    npc, slotId, slotIndexInType, s);
                        }

                        @Override
                        public void setChanged() {
                            super.setChanged();
                            // 标记下发同步，走 CNPC 自己的同步链
                            npc.updateClient = true;
                        }
                    });

                    // 记录此 slot 的全容器 index 对应哪个 curio 槽位类型，供 GUI 绘制标签用
                    CNPCcurios.SLOT_INDEX_TO_ID.put(NPC_BASE_SLOTS + idx, slotId);
                    idx++;
                }
            }
        });
    }
}
