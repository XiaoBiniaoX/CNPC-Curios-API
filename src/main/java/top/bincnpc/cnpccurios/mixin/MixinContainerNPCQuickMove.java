package top.bincnpc.cnpccurios.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.containers.ContainerNPCInv;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NPC 物品栏界面里 shift+左键 的快速搬运逻辑。
 *
 * CNPC 原版 quickMoveStack 直接 return ItemStack.EMPTY（ContainerNPCInv.java:56-58），
 * 即完全不支持 shift 搬运。本 mixin 补上两个方向：
 *   1. shift 点玩家背包里的饰品 → 快速放入第一个空的可放饰品槽（哈基彬要求的主功能）
 *   2. shift 点饰品槽 → 快速取回玩家背包
 *
 * 本类 extends AbstractContainerMenu 的原因（与 1.20.1 原版写法一致）：
 * moveItemStackTo 是 AbstractContainerMenu 的 protected 方法，只有子类能合法调用。
 * ContainerNPCInv 本身就 extends AbstractContainerMenu，所以该继承声明与目标类层级一致。
 *
 * 教训 L15：上一版我为了调 moveItemStackTo 额外造了个 @Invoker 接口 ContainerMenuAccessor，
 * 却忘了把它写进 mixins.cnpccurios.json 的 mixins 列表 —— accessor mixin 未被登记就被业务代码引用，
 * 运行时直接 IllegalClassLoadError 崩游戏（实测崩溃报告 crash-2026-08-23_22.17.47-client.txt）。
 * 现改回 1.20.1 的继承写法：不需要 accessor，少一个文件，也彻底消除这类漏登记风险。
 *
 * 教训 L7：quickMoveStack 绝不能返回 null。
 * 1.21.1 原版 AbstractContainerMenu.doClick 的快速移动分支会直接对返回值调 isEmpty()，
 * 返回 null 会当场 NPE 崩客户端。所以每条接管分支都显式 setReturnValue。
 */
@Mixin(value = ContainerNPCInv.class, remap = false)
public abstract class MixinContainerNPCQuickMove extends AbstractContainerMenu {

    /** 与 MixinContainerNPCInv 保持一致：CNPC 原始 52 槽 */
    @Unique
    private static final int NPC_BASE_SLOTS = 52;
    /** 玩家背包区在容器里的范围 [16, 52)：主背包 27 + 快捷栏 9 */
    @Unique
    private static final int PLAYER_INV_START = 16;
    @Unique
    private static final int PLAYER_INV_END = 52;

    /** 仅为满足编译（mixin 类不会被真正实例化），构造体不会进入产物 */
    protected MixinContainerNPCQuickMove() {
        super(null, 0);
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void cnpccurios$onQuickMoveStack(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        // slots 是 public final NonNullList<Slot>（AbstractContainerMenu.java:47），直接取长度
        int totalSlots = this.slots.size();
        // 没有注入过饰品槽（例如数据包没给该 NPC 配槽位）→ 保持原版行为，不接管
        if (totalSlots <= NPC_BASE_SLOTS) return;
        if (index < 0 || index >= totalSlots) return;

        // 分支一：shift 点击饰品槽 → 取回玩家背包
        if (index >= NPC_BASE_SLOTS) {
            Slot slot = this.getSlot(index);
            if (slot == null || !slot.hasItem()) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            // getItem() 返回的是 handler 内的「活引用」
            // （ItemStackHandler.getStackInSlot 直接 return this.stacks.get(slot)，
            //  neoforge-21.1.235-sources.jar → ItemStackHandler.java:49），
            // 所以 moveItemStackTo 会就地 split/shrink 这个栈，无需回写。
            ItemStack stack = slot.getItem();
            ItemStack original = stack.copy();
            if (this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, false)) {
                // 照原版约定：搬空后显式置 EMPTY，而不是留一个 count=0 的残栈。
                // 对饰品槽尤其重要 —— Curios 靠 setStackInSlot 触发属性移除与 previousStacks 比对，
                // 留残栈会让饰品属性加成卸不掉。
                if (stack.isEmpty()) {
                    slot.set(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
                cir.setReturnValue(original);
            } else {
                // 背包放不下：什么都不动，返回 EMPTY 告知原版「本次没搬走」
                cir.setReturnValue(ItemStack.EMPTY);
            }
            return;
        }

        // 分支二：shift 点击玩家背包里的饰品 → 快速放入第一个空的合法饰品槽
        if (index >= PLAYER_INV_START && index < PLAYER_INV_END) {
            Slot slot = this.getSlot(index);
            if (slot == null || !slot.hasItem()) return; // 空槽交回原版处理

            ItemStack stack = slot.getItem();
            for (int i = NPC_BASE_SLOTS; i < totalSlots; i++) {
                Slot curioSlot = this.getSlot(i);
                if (curioSlot == null || curioSlot.hasItem()) continue;
                // mayPlace 里会走 CuriosApi.isStackValid，非饰品自然进不去
                if (!curioSlot.mayPlace(stack)) continue;

                ItemStack original = stack.copy();
                if (this.moveItemStackTo(stack, i, i + 1, false)) {
                    // 同上：源槽（玩家背包）被搬空后显式置 EMPTY
                    if (stack.isEmpty()) {
                        slot.set(ItemStack.EMPTY);
                    } else {
                        slot.setChanged();
                    }
                    cir.setReturnValue(original);
                    return;
                }
            }
            // 没有可放入的饰品槽 → 不接管，交回原版（原版返回 EMPTY）
        }
    }
}
