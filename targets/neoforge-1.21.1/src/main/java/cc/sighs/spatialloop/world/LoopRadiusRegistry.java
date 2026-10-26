package cc.sighs.spatialloop.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 维度 → 循环半径 的运行时查表。
 *
 * <p>区块存档（{@code ChunkStorage}）自带半径字段，但实体与 POI 的 {@code SimpleRegionStorage}
 * 是在别处创建的，拿不到维度生成器；它们只有 {@code RegionStorageInfo}（含维度键）。
 * {@code ServerLevel} 构造时把半径登记到这里，存储层按维度查即可。
 *
 * <p>只保存在当前进程里，不落盘；读档时由 {@code ServerLevel} 的构造器重新登记。
 */
public final class LoopRadiusRegistry {
    private static final Map<ResourceKey<Level>, Integer> RADII = new ConcurrentHashMap<>();

    private LoopRadiusRegistry() {
    }

    public static void put(ResourceKey<Level> dimension, int radius) {
        if (radius <= 0) {
            RADII.remove(dimension);
        } else {
            RADII.put(dimension, radius);
        }
    }

    public static int get(ResourceKey<Level> dimension) {
        Integer radius = RADII.get(dimension);
        return radius == null ? 0 : radius;
    }
}
