package cc.sighs.spatialloop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import org.junit.jupiter.api.Test;

/**
 * 循环空间的核心不变量：跨过接缝后取到的必须是同一个点。
 *
 * <p>地形本身由原版噪声生成，周期化由 {@code NormalNoiseMixin} 完成（需要启动游戏才能测）；
 * 这里守住所有折回运算的数学基础。
 */
class LoopGeometryTest {
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
    void chunkAndBlockWrappingAgree() {
        for (int radius : new int[]{3, 12, 32, 128}) {
            for (int chunk = -300; chunk <= 300; chunk += 7) {
                int wrappedChunk = LoopGeometry.wrapChunk(chunk, radius);
                for (int offset = 0; offset < 16; offset++) {
                    assertEquals(
                            LoopGeometry.wrapBlock(chunk * 16 + offset, radius),
                            wrappedChunk * 16 + offset,
                            "radius " + radius + " chunk " + chunk + " offset " + offset
                    );
                }
            }
        }
    }

    @Test
    void neighbouringPointsAtTheSeamStayNeighbours() {
        int radius = 16;
        int period = LoopGeometry.periodBlocks(radius);
        int lastBlockOfRegion = period / 2 - 1;
        assertEquals(period / 2, lastBlockOfRegion + 1);
        // 区域最东一格的下一格折回后就是区域最西一格。
        assertEquals(-period / 2, LoopGeometry.wrapBlock(period / 2, radius));
        assertEquals(lastBlockOfRegion, LoopGeometry.wrapBlock(-period / 2 - 1, radius));
    }

    @Test
    void quartPeriodMatchesTheBlockPeriod() {
        for (int radius : new int[]{3, 12, 32, 128}) {
            int quartPeriod = LoopGeometry.periodQuarts(radius);
            assertEquals(LoopGeometry.periodBlocks(radius), quartPeriod * 4, "radius " + radius);
            for (int quart = -50_000; quart <= 50_000; quart += 733) {
                int wrappedQuart = Math.floorMod(quart, quartPeriod);
                // 群系源入口折的是 quart 坐标，气候采样器再乘 4 变回方块坐标：
                // 折回后的方块坐标必须与原坐标同余（mod 方块周期），否则群系会与地形错位。
                assertEquals(
                        0,
                        Math.floorMod(LoopGeometry.wrapBlock(quart * 4, radius) - wrappedQuart * 4,
                                LoopGeometry.periodBlocks(radius)),
                        "radius " + radius + " quart " + quart
                );
            }
        }
    }

    @Test
    void viewDistanceIsAlwaysStrictlyBelowRadius() {
        for (int radius = LoopGeometry.MIN_ACTIVE_RADIUS; radius <= LoopGeometry.MAX_RADIUS; radius++) {
            assertTrue(LoopGeometry.maxViewDistance(radius) < radius, "radius " + radius);
        }
    }
}
