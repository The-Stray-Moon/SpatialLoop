package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopBiomeSourcePeriod;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 群系源的入口坐标按 quart 周期折回，让第三方群系模组看到与副本一致的区域布局。
 *
 * <p>原版气候噪声已经是周期的，所以 {@code getNoiseBiome} 拿到绝对坐标也不影响原版群系；但
 * TerraBlender 之类的模组会在自己的 {@code @Inject} 里用这个 quart 坐标算区域哈希
 * （{@code IExtendedParameterList.findValuePositional(TargetPoint, x, y, z)}），绝对坐标下副本的
 * 区域边界就不一样。这里在方法入口把 x/z 换成折回后的值，模组和原版看到的是同一份坐标。
 *
 * <p>{@code priority = 500}：Mixin 按优先级从高到低应用，HEAD 处的代码是「后应用者排在前面」，
 * 所以优先级必须<b>低于</b> TerraBlender 的默认 1000，我们的变量改写才会排在它读参数的回调之前
 * （实测 priority 2000 时 TerraBlender 的回调反而先执行，拿到的是未折回坐标）。
 *
 * <p>周期由 {@code SpatialLoopChunkGenerator} 的构造器写入（{@link LoopBiomeSourcePeriod}），
 * 没有写入时（下界/末地、非循环存档）保持原版行为。
 */
@Mixin(value = MultiNoiseBiomeSource.class, priority = 500)
public abstract class MultiNoiseBiomeSourceMixin implements LoopBiomeSourcePeriod {
    @Unique
    private int spatialloop$periodQuarts;

    /** {@code getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler)} 的 x。 */
    @ModifyVariable(
            method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1
    )
    private int spatialloop$wrapQuartX(int quartX) {
        return this.spatialloop$wrapQuart(quartX);
    }

    /** 同一个方法的 z；{@code this} 占 index 0，y 是 index 2（不折，竖直方向没有周期）。 */
    @ModifyVariable(
            method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
            at = @At("HEAD"),
            argsOnly = true,
            index = 3
    )
    private int spatialloop$wrapQuartZ(int quartZ) {
        return this.spatialloop$wrapQuart(quartZ);
    }

    @Unique
    private int spatialloop$wrapQuart(int quart) {
        int period = this.spatialloop$periodQuarts;
        // 周期是 quart 格数，和气候噪声在方块坐标上的周期严格对应，所以折回不改变原版群系结果。
        return period <= 0 ? quart : Math.floorMod(quart, period);
    }

    @Override
    public void spatialloop$setPeriod(int periodQuarts) {
        this.spatialloop$periodQuarts = periodQuarts;
    }

    @Override
    public int spatialloop$periodQuarts() {
        return this.spatialloop$periodQuarts;
    }
}
