package cc.sighs.spatialloop.world;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.terrain.LoopTerrain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * 周期生物群系源：温度/湿度噪声同样是周期噪声，接缝两侧的生物群系因此完全一致。
 *
 * <p>生物群系在 Minecraft 里按 4 方块（quart）采样；这里用 quart 坐标换算回方块坐标再查表，
 * 保证区块副本之间、接缝两侧取到同一个值。
 */
public final class SpatialLoopBiomeSource extends BiomeSource {
    public static final MapCodec<SpatialLoopBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.CODEC.listOf().fieldOf("biomes").forGetter(source -> source.biomes),
            Codec.LONG.fieldOf("seed").forGetter(source -> source.seed),
            Codec.INT.fieldOf("radius").forGetter(source -> source.radius)
    ).apply(instance, SpatialLoopBiomeSource::new));

    private final List<Holder<Biome>> biomes;
    private final long seed;
    private final int radius;
    private final LoopTerrain terrain;

    public SpatialLoopBiomeSource(List<Holder<Biome>> biomes, long seed, int radius) {
        this.biomes = List.copyOf(biomes);
        this.seed = seed;
        this.radius = LoopGeometry.sanitizeRadius(radius);
        this.terrain = new LoopTerrain(seed, radius);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        if (this.biomes.isEmpty()) {
            throw new IllegalStateException("SpatialLoop biome source has no biomes");
        }
        int index = this.terrain.biomeAt(QuartPos.toBlock(quartX), QuartPos.toBlock(quartZ));
        return this.biomes.get(Mth.clamp(index, 0, this.biomes.size() - 1));
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.biomes.stream();
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }
}
