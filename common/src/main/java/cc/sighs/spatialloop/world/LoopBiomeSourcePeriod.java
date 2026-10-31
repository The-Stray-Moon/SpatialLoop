package cc.sighs.spatialloop.world;

/**
 * 由 {@code MultiNoiseBiomeSource} 的 mixin 实现：让群系源按 quart 周期折回输入坐标。
 *
 * <p>原版气候采样本身已经是周期的（噪声在采样层周期化），但第三方群系模组（例如 TerraBlender）
 * 会在 {@code getNoiseBiome} 里用传入的 quart 坐标算自己的「区域」哈希。绝对坐标下副本之间哈希不同，
 * 区域边界和区域内地物就会在副本间不一致。折回后第三方看到的是同一份 quart 坐标，副本自然一致。
 *
 * <p>周期只由 {@link SpatialLoopChunkGenerator} 的构造器盖上（值是存档属性），下界/末地没有生成器
 * 盖上周期，群系源保持原版行为。
 */
public interface LoopBiomeSourcePeriod {
    /** @param periodQuarts 折回周期，单位 quart（{@code LoopGeometry.periodQuarts(radius)}）；{@code <= 0} 表示不折。 */
    void spatialloop$setPeriod(int periodQuarts);

    int spatialloop$periodQuarts();
}
