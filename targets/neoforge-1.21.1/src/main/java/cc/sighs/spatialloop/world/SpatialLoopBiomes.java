package cc.sighs.spatialloop.world;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * 循环世界使用的生物群系表。顺序必须与 {@code LoopTerrain.BIOME_*} 常量一一对应，
 * 生成器把这张表写进 level.dat，读档时原样恢复。
 */
public final class SpatialLoopBiomes {
    public static final List<net.minecraft.resources.ResourceKey<Biome>> KEYS = List.of(
            Biomes.PLAINS,
            Biomes.FOREST,
            Biomes.TAIGA,
            Biomes.DESERT,
            Biomes.SAVANNA,
            Biomes.SNOWY_PLAINS
    );

    private SpatialLoopBiomes() {
    }

    public static List<Holder<Biome>> resolve(RegistryAccess registryAccess) {
        Registry<Biome> registry = registryAccess.registryOrThrow(Registries.BIOME);
        return KEYS.stream().map(key -> (Holder<Biome>) registry.getHolderOrThrow(key)).toList();
    }
}
