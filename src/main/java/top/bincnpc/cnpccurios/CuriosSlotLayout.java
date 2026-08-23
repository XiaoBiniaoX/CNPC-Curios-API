package top.bincnpc.cnpccurios;

/**
 * 饰品槽在 NPC 物品栏界面里的排布计算。
 *
 * ── 为什么需要（哈基彬指示「改成自动换行/缩排」）──
 * 原本是硬编码「每列 6 格」竖排。启用玩家槽位继承后总槽数明显变多
 * （实测叠加 terra_curio accessory 6 格 + Curios 基础槽 + artifacts feet/hands 等，约 24 格），
 * 固定 6 行会一直向右堆列，撞进 CNPC 自己的掉落率输入框区域甚至溢出面板。
 *
 * ── 排布策略 ──
 * 先竖着填满可用高度，填满再换到下一列（优先纵向 = 列数最少 = 横向侵占最小），
 * 这正是「缩排」要达到的效果。
 *
 * 界面尺寸依据（CNPC1.21.1反编译）：
 *   GuiContainerNPCInterface2.java:46  imageWidth  = 420
 *   GuiNPCInv.java:70                 imageHeight = 200
 * 槽位起点由客户端配置 slotX/slotY 决定（默认 250,50），保持与 1.20.1 一致，不擅自改默认值。
 */
public final class CuriosSlotLayout {

    /** 单个槽位占位（16px 槽 + 2px 间隙），与原版槽距一致 */
    public static final int SLOT_PITCH = 18;

    /** NPC 物品栏界面高度，GuiNPCInv.java:70 */
    private static final int GUI_HEIGHT = 200;
    /** 底部留白，避免最后一行贴着面板边缘 */
    private static final int BOTTOM_MARGIN = 6;
    /** 每列至少 1 行，避免起点被配置调到很低时算出 0 行导致除零 */
    private static final int MIN_ROWS = 1;
    /** 每列最多行数上限：即使起点很高也不排得过密过长 */
    private static final int MAX_ROWS = 10;

    private CuriosSlotLayout() {
    }

    /**
     * 计算每列能放多少行。
     * 依据槽位起点 Y 到面板底部的可用高度自动得出，起点越高能放越多行、列数越少。
     *
     * @param startY 槽位区起点 Y（相对 GUI 左上角）
     */
    public static int rowsPerColumn(int startY) {
        int available = GUI_HEIGHT - startY - BOTTOM_MARGIN;
        int rows = available / SLOT_PITCH;
        if (rows < MIN_ROWS) return MIN_ROWS;
        return Math.min(rows, MAX_ROWS);
    }

    /**
     * 计算第 index 个饰品槽的 X 坐标（相对 GUI 左上角）。
     *
     * @param index  槽位序号，从 0 开始
     * @param startX 槽位区起点 X
     * @param rows   每列行数，来自 {@link #rowsPerColumn(int)}
     */
    public static int slotX(int index, int startX, int rows) {
        return startX + (index / rows) * SLOT_PITCH;
    }

    /**
     * 计算第 index 个饰品槽的 Y 坐标（相对 GUI 左上角）。
     * 先纵向填满一列再换列，故 Y 用取余。
     */
    public static int slotY(int index, int startY, int rows) {
        return startY + (index % rows) * SLOT_PITCH;
    }

    /**
     * 自检：用断言验证排布数学正确（换行时机、坐标递进、行数边界）。
     * 因为这是纯计算逻辑且不依赖 Minecraft 运行时，可直接跑：
     *   java -ea -cp <classes> top.bincnpc.cnpccurios.CuriosSlotLayout
     * 需用 -ea 开启断言，否则 assert 不生效。
     */
    public static void selfCheck() {
        // 起点 50 时：可用高度 200-50-6=144，144/18=8 行
        int rows = rowsPerColumn(50);
        assert rows == 8 : "起点50应得8行，实得" + rows;

        // 第 0..7 个在第一列，X 不变，Y 逐行 +18
        assert slotX(0, 250, rows) == 250 : "第0格X";
        assert slotY(0, 50, rows) == 50 : "第0格Y";
        assert slotX(7, 250, rows) == 250 : "第7格仍在第一列";
        assert slotY(7, 50, rows) == 50 + 7 * 18 : "第7格Y应为最后一行";

        // 第 8 个应换到第二列首行
        assert slotX(8, 250, rows) == 268 : "第8格应换列，X=268";
        assert slotY(8, 50, rows) == 50 : "第8格应回到首行";

        // 边界：起点极低时至少 1 行，不得除零
        assert rowsPerColumn(999) == MIN_ROWS : "起点过低应回落到最少行数";
        // 边界：起点极高时受 MAX_ROWS 限制
        assert rowsPerColumn(-500) == MAX_ROWS : "起点过高应受上限约束";

        System.out.println("CuriosSlotLayout 自检通过：rows=" + rows);
    }

    public static void main(String[] args) {
        selfCheck();
    }
}
