package cc.sighs.spatialloop.terrain;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.noise.PeriodicNoise;

/**
 * 循环世界的纯数学地形模型。
 *
 * <p>所有输入都是世界绝对方块坐标；内部先用 {@link LoopGeometry#unit(int, int)} 折到一个周期内，
 * 再采样周期噪声。因此 {@code heightAt(x, z) == heightAt(x + 32r, z)}（位级相同），
 * 区块 (r, 0) 作为 (-r, 0) 的副本时，两边的高度、生物群系、洞穴在接缝处严丝合缝。
 *
 * <p>这里不依赖 Minecraft：生物群系只以整数编号返回，方块由 target 层负责摆放。
 */
public final class LoopTerrain {
    public static final int SEA_LEVEL = 63;
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;

    public static final int BIOME_PLAINS = 0;
    public static final int BIOME_FOREST = 1;
    public static final int BIOME_TAIGA = 2;
    public static final int BIOME_DESERT = 3;
    public static final int BIOME_SAVANNA = 4;
    public static final int BIOME_SNOWY_PLAINS = 5;
    public static final int BIOME_COUNT = 6;

    private static final int MAX_Y = MIN_Y + HEIGHT - 1;

    private final int radius;
    private final int period;
    private final PeriodicNoise noise;

    public LoopTerrain(long seed, int radius) {
        this.radius = LoopGeometry.sanitizeRadius(radius);
        this.period = LoopGeometry.periodBlocks(this.radius);
        this.noise = new PeriodicNoise(seed);
    }

    public int radius() {
        return this.radius;
    }

    public int period() {
        return this.period;
    }

    /** 该列最高一块实心方块的 y 坐标（这一层之上是空气或水）。 */
    public int heightAt(int blockX, int blockZ) {
        double u = LoopGeometry.unit(blockX, this.period);
        double v = LoopGeometry.unit(blockZ, this.period);

        double continent = fbm2(u, v, 900.0, 3);
        double hills = fbm2(u, v, 220.0, 3);
        double detail = fbm2(u, v, 55.0, 2);

        double height = 66.0 + continent * 30.0 + hills * 8.0 + detail * 2.5;
        double mountain = smoothstep(0.2, 0.7, continent) * (28.0 + 42.0 * Math.max(0.0, hills));
        height += mountain;

        int top = (int) Math.floor(height);
        return Math.max(MIN_Y + 8, Math.min(MAX_Y - 48, top));
    }

    /** 该列的生物群系编号。采样步长是 4 方块，与 Minecraft 的 quart 对齐。 */
    public int biomeAt(int blockX, int blockZ) {
        double u = LoopGeometry.unit(blockX, this.period);
        double v = LoopGeometry.unit(blockZ, this.period);
        double temperature = fbm2(u, v, 1500.0, 2);
        double humidity = fbm2(u, v, 900.0, 2);

        if (temperature > 0.42) {
            return humidity > 0.0 ? BIOME_SAVANNA : BIOME_DESERT;
        }
        if (temperature > 0.08) {
            return humidity > 0.05 ? BIOME_FOREST : BIOME_PLAINS;
        }
        if (temperature > -0.32) {
            return humidity > 0.2 ? BIOME_FOREST : BIOME_TAIGA;
        }
        return BIOME_SNOWY_PLAINS;
    }

    public static boolean isCold(int biome) {
        return biome == BIOME_TAIGA || biome == BIOME_SNOWY_PLAINS;
    }

    public static boolean isSandy(int biome) {
        return biome == BIOME_DESERT;
    }

    /**
     * 洞穴密度。返回负数表示该点应当被挖空。
     *
     * <p>两套系统叠加：低频「奶酪洞」负责大空腔，两组中频噪声的等值线负责隧道。
     * 调用方按 4 方块步长采样后做三线性插值即可，不必逐方块求值。
     */
    public double caveDensity(int blockX, int y, int blockZ) {
        double u = LoopGeometry.unit(blockX, this.period);
        double v = LoopGeometry.unit(blockZ, this.period);

        double cheese = this.noise.fbm3(u, v, y, cells(300.0), cells(300.0), 56.0, 2, 0.5);
        double tunnelA = this.noise.fbm3(u, v, y + 97.0, cells(120.0), cells(120.0), 40.0, 2, 0.5);
        double tunnelB = this.noise.fbm3(u, v, y - 41.0, cells(120.0), cells(120.0), 40.0, 2, 0.5);

        double cheeseSolid = Math.abs(cheese) - 0.105;
        double tunnelSolid = tunnelA * tunnelA + tunnelB * tunnelB - 0.022;
        return Math.min(cheeseSolid, tunnelSolid);
    }

    private double fbm2(double u, double v, double featureSize, int octaves) {
        int cells = cells(featureSize);
        return this.noise.fbm2(u, v, cells, cells, octaves, 0.5);
    }

    private int cells(double featureSize) {
        return Math.max(1, (int) Math.round(this.period / featureSize));
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = (value - edge0) / (edge1 - edge0);
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }
}
