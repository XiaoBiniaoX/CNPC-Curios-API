package top.bincnpc.cnpccurios.client;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.SlotItemHandler;
import noppes.npcs.entity.EntityNPCInterface;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * NPC饰品栏容器。只含Curios槽位，无玩家背包。
 */
public class ContainerNPCCurios extends AbstractContainerMenu {

    private final EntityNPCInterface npc;

    public ContainerNPCCurios(EntityNPCInterface npc) {
        super(null, 0);
        this.npc = npc;
        if (npc == null) return;

        CuriosApi.getCuriosInventory(npc).ifPresent(handler -> {
            int idx = 0;
            var curios = handler.getCurios();
            for (var e : curios.entrySet()) {
                String sid = e.getKey();
                IDynamicStackHandler sh = e.getValue().getStacks();
                for (int i = 0; i < sh.getSlots(); i++) {
                    final int si = i;
                    final String id = sid;
                    int x = 8 + (idx % 8) * 18;
                    int y = 18 + (idx / 8) * 18;
                    this.addSlot(new SlotItemHandler(sh, si, x, y) {
                        @Override public void setChanged() {
                            super.setChanged();
                            try { npc.updateClient = true; } catch (Exception ignored) {}
                        }
                        @Override public boolean mayPlace(ItemStack s) {
                            if (s.isEmpty()) return true;
                            return CuriosApi.isStackValid(
                                    new SlotContext(id, npc, si, false, true), s);
                        }
                    });
                    idx++;
                }
            }
        });
    }

    public EntityNPCInterface getNpc() { return npc; }
    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p) { return npc != null; }
}
