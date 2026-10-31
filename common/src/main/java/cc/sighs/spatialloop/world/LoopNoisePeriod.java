package cc.sighs.spatialloop.world;

/**
 * 「本次 {@code NormalNoise#getValue} 调用的输入周期」的线程内栈。
 *
 * <p>同一个 {@code NormalNoise} 在不同节点里收到的输入单位不同（方块、方块 ×xzScale、方块 ×0.25、
 * 方块 / d0……），所以周期不能写死在噪声实例上。各节点 mixin 在调用前压栈，返回后弹栈；没有压栈时
 * 用噪声实例自身盖上的方块周期（{@code SurfaceSystem} 之类直接传方块坐标的调用）。
 *
 * <p>用可变栈而不是 lambda 包装，是因为地形生成每秒要调用几百万次，不能每次分配对象。
 */
public final class LoopNoisePeriod {
    private static final ThreadLocal<Stack> STACK = ThreadLocal.withInitial(Stack::new);
    private static final ThreadLocal<double[]> RARITY = ThreadLocal.withInitial(() -> new double[]{Double.NaN});

    private LoopNoisePeriod() {
    }

    /** 压入本次调用的输入周期与 y 频率权重。 */
    public static void push(double period, double yWeight) {
        Stack stack = STACK.get();
        if (stack.size == stack.periods.length) {
            int size = stack.size * 2;
            double[] periods = new double[size];
            double[] weights = new double[size];
            System.arraycopy(stack.periods, 0, periods, 0, stack.size);
            System.arraycopy(stack.yWeights, 0, weights, 0, stack.size);
            stack.periods = periods;
            stack.yWeights = weights;
        }
        stack.periods[stack.size] = period;
        stack.yWeights[stack.size] = yWeight;
        stack.size++;
    }

    public static void pop() {
        Stack stack = STACK.get();
        if (stack.size > 0) {
            stack.size--;
        }
    }

    /** 栈顶周期；没有压栈时返回 {@code fallback}（噪声实例上的方块周期）。 */
    public static double currentPeriod(double fallback) {
        Stack stack = STACK.get();
        return stack.size == 0 ? fallback : stack.periods[stack.size - 1];
    }

    public static double currentYWeight() {
        Stack stack = STACK.get();
        return stack.size == 0 ? 1.0 : stack.yWeights[stack.size - 1];
    }

    /** {@code WeirdScaledSampler} 在 {@code mapper.get(input)} 之后紧接着调用噪声，这里暂存缩放因子。 */
    public static void setRarity(double rarity) {
        RARITY.get()[0] = rarity;
    }

    public static double takeRarity() {
        double[] holder = RARITY.get();
        double rarity = holder[0];
        holder[0] = Double.NaN;
        return rarity;
    }

    private static final class Stack {
        private double[] periods = new double[8];
        private double[] yWeights = new double[8];
        private int size;
    }
}
