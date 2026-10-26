package cc.sighs.spatialloop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.terrain.LoopTerrain;
import org.junit.jupiter.api.Test;

/**
 * 循环空间的核心不变量：跨过接缝后取到的必须是同一个点。
 *
 * <p>这些测试不启动游戏，只验证周期噪声与地形函数本身——它们是「无缝」的全部数学基础。
 */
class LoopTerrainTest {
    @Test
    void wrapMapsEveryCoordinateIntoOnePeriod() {
        for (int radius : new int[]{2, 3, 8, 16, 64, 128}) {
            int period = LoopGeometry.periodBlocks(radius);
            for (int block = -200_000; block <= 200_000; block += 4159) {
                int wrapped = LoopGeometry.wrapBlock(block, radius);
                assertTrue(wrapped >= -period / 2 && wrapped < period / 2, "out of range: " + block);
                assertEquals(block, wrapped + (long) period * ((block - wrapped) / period), "not congruent: " + block);
            }
        }
    }

    @Test
    void wrappedChunkCoordinatesStayInsideTheRegion() {
        for (int radius : new int[]{2, 5, 32}) {
            for (int chunk = -1000; chunk <= 1000; chunk++) {
                int wrapped = LoopGeometry.wrapChunk(chunk, radius);
                assertTrue(wrapped >= -radius && wrapped < radius, "chunk " + chunk);
            }
        }
    }

    @Test
    void heightsAndBiomesRepeatExactlyAcrossTheSeam() {
        long seed = 20251025L;
        for (int radius : new int[]{3, 8, 32, 128}) {
            LoopTerrain terrain = new LoopTerrain(seed, radius);
            int period = LoopGeometry.periodBlocks(radius);
            int[] xs = {-1, 0, 1, 17, radius * 16 - 1, radius * 16, -radius * 16, -radius * 16 - 1, 12345};
            for (int x : xs) {
                for (int z : xs) {
                    assertEquals(terrain.heightAt(x, z), terrain.heightAt(x + period, z), "height x " + x + " z " + z);
                    assertEquals(terrain.heightAt(x, z), terrain.heightAt(x, z + period), "height z " + x + " z " + z);
                    assertEquals(terrain.biomeAt(x, z), terrain.biomeAt(x + period, z + period), "biome " + x + " " + z);
                    assertEquals(
                            terrain.caveDensity(x, 20, z), terrain.caveDensity(x + period, 20, z + period), 0.0,
                            "cave " + x + " " + z);
                }
            }
        }
    }

    @Test
    void seamNeighbourhoodsAgreeWithinTheRegion() {
        // 东边缘下一点和西边缘第一点必须完全一致：区块 (r, z) 就是 (-r, z) 的副本。
        int radius = 16;
        LoopTerrain terrain = new LoopTerrain(7L, radius);
        int period = LoopGeometry.periodBlocks(radius);
        int east = period / 2;
        for (int dz = -64; dz <= 64; dz++) {
            assertEquals(terrain.heightAt(east - 1, dz), terrain.heightAt(-east - 1, dz));
            assertEquals(terrain.heightAt(east, dz), terrain.heightAt(-east, dz));
            assertEquals(terrain.biomeAt(east, dz), terrain.biomeAt(-east, dz));
        }
        // 顺带确认噪声不是常数，避免测试「因为全是 0 而通过」。
        assertNotEquals(terrain.heightAt(0, 0), terrain.heightAt(300, 300));
    }

    @Test
    void viewDistanceIsAlwaysStrictlyBelowRadius() {
        for (int radius = LoopGeometry.MIN_ACTIVE_RADIUS; radius <= LoopGeometry.MAX_RADIUS; radius++) {
            assertTrue(LoopGeometry.maxViewDistance(radius) < radius, "radius " + radius);
        }
    }
}
