package cc.sighs.spatialloop.world;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.terrain.LoopTerrain;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 自有的、纯函数式装饰。
 *
 * <p>为什么不用原版 {@code applyBiomeDecoration}：原版装饰会读邻居区块的方块状态，并往邻居区块写方块
 * （树冠、矿石、泉眼）。相邻区块的装饰顺序由异步区块任务决定，同一块空间的两个副本可能因为生成历史不同
 * 而得到不同的树/矿脉——实测每个副本土块完全一致，但树冠与泉眼会差几十块。
 *
 * <p>这里改为「只依赖周期坐标的确定性函数」：
 * <ul>
 *   <li>树长在一个<b>按周期重复的抖动格</b>上，每个区块只负责放下锚点落在自己范围内的树，树冠允许溢出到相邻区块；
 *       相邻树锚点间距大于树冠直径，写入不会互相覆盖，因此与生成顺序无关。</li>
 *   <li>地表植被逐列按折回周期后的坐标哈希决定，且避开树锚点。</li>
 *   <li>矿脉只在当前区块内部生成，用折回后的区块坐标做随机种子。</li>
 * </ul>
 * 于是区块 {@code (x, z)} 与 {@code (x + 2r, z)} 的装饰结果逐块相同。
 */
public final class LoopDecorator {
    /** 树锚点格的期望边长（方块）。周期不是它的整数倍时自动吸附成整数格。 */
    private static final double TREE_CELL_TARGET = 7.0;
    /** 锚点距离格边的最小留白。留白 2 能保证相邻格的锚点至少相隔 5 格，5×5 的树冠不会互相覆盖。 */
    private static final int TREE_CELL_MARGIN = 2;

    private static final BlockState OAK_LOG = Blocks.OAK_LOG.defaultBlockState();
    private static final BlockState BIRCH_LOG = Blocks.BIRCH_LOG.defaultBlockState();
    private static final BlockState SPRUCE_LOG = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState ACACIA_LOG = Blocks.ACACIA_LOG.defaultBlockState();
    private static final BlockState OAK_LEAVES = persistent(Blocks.OAK_LEAVES);
    private static final BlockState BIRCH_LEAVES = persistent(Blocks.BIRCH_LEAVES);
    private static final BlockState SPRUCE_LEAVES = persistent(Blocks.SPRUCE_LEAVES);
    private static final BlockState ACACIA_LEAVES = persistent(Blocks.ACACIA_LEAVES);

    private static final BlockState SHORT_GRASS = Blocks.SHORT_GRASS.defaultBlockState();
    private static final BlockState FERN = Blocks.FERN.defaultBlockState();
    private static final BlockState DANDELION = Blocks.DANDELION.defaultBlockState();
    private static final BlockState POPPY = Blocks.POPPY.defaultBlockState();
    private static final BlockState CORNFLOWER = Blocks.CORNFLOWER.defaultBlockState();
    private static final BlockState OXEYE_DAISY = Blocks.OXEYE_DAISY.defaultBlockState();
    private static final BlockState DEAD_BUSH = Blocks.DEAD_BUSH.defaultBlockState();
    private static final BlockState CACTUS = Blocks.CACTUS.defaultBlockState();

    private static final List<OreSpec> ORES = List.of(
            new OreSpec(state(Blocks.COAL_ORE), state(Blocks.DEEPSLATE_COAL_ORE), -64, 128, 20, 0.85F, 10),
            new OreSpec(state(Blocks.IRON_ORE), state(Blocks.DEEPSLATE_IRON_ORE), -60, 64, 20, 0.80F, 8),
            new OreSpec(state(Blocks.COPPER_ORE), state(Blocks.DEEPSLATE_COPPER_ORE), -16, 112, 16, 0.70F, 8),
            new OreSpec(state(Blocks.GOLD_ORE), state(Blocks.DEEPSLATE_GOLD_ORE), -64, 32, 4, 0.60F, 6),
            new OreSpec(state(Blocks.REDSTONE_ORE), state(Blocks.DEEPSLATE_REDSTONE_ORE), -64, 16, 8, 0.55F, 7),
            new OreSpec(state(Blocks.DIAMOND_ORE), state(Blocks.DEEPSLATE_DIAMOND_ORE), -64, 16, 2, 0.45F, 5),
            new OreSpec(state(Blocks.LAPIS_ORE), state(Blocks.DEEPSLATE_LAPIS_ORE), -64, 32, 2, 0.40F, 6),
            new OreSpec(state(Blocks.EMERALD_ORE), state(Blocks.DEEPSLATE_EMERALD_ORE), -16, 240, 1, 0.12F, 3)
    );

    private final LoopTerrain terrain;
    private final int radius;
    private final int period;
    private final long seed;
    private final int cells;

    public LoopDecorator(long seed, int radius) {
        this.terrain = new LoopTerrain(seed, radius);
        this.radius = LoopGeometry.sanitizeRadius(radius);
        this.period = LoopGeometry.periodBlocks(this.radius);
        this.seed = seed;
        this.cells = Math.max(1, (int) Math.round(this.period / TREE_CELL_TARGET));
    }

    public void decorate(WorldGenLevel level, ChunkPos chunkPos) {
        int originX = chunkPos.getMinBlockX();
        int originZ = chunkPos.getMinBlockZ();

        List<Anchor> own = this.anchors(originX, originZ, 0, 0, 16, 16);
        for (Anchor anchor : own) {
            this.placeTree(level, anchor);
        }

        List<Anchor> nearby = this.anchors(originX, originZ, -2, -2, 18, 18);
        this.placeGroundCover(level, chunkPos, nearby);
        this.placeOres(level, chunkPos);
    }

    // ------------------------------------------------------------------
    // 树
    // ------------------------------------------------------------------

    private record Anchor(int x, int z, int cellX, int cellZ) {
    }

    /**
     * 枚举锚点落在给定局部范围内的树。格子在周期内是固定的，超出周期的格索引按周期取模取哈希，
     * 所以坐标挪一个周期得到的是同一棵树平移一个周期。
     */
    private List<Anchor> anchors(int originX, int originZ, int minDx, int minDz, int maxDx, int maxDz) {
        int localX = Math.floorMod(originX, this.period);
        int localZ = Math.floorMod(originZ, this.period);
        int baseX = originX - localX;
        int baseZ = originZ - localZ;
        double cellSize = (double) this.period / this.cells;

        int cellMinX = (int) Math.floor((localX + minDx) / cellSize);
        int cellMaxX = (int) Math.floor((localX + maxDx - 1) / cellSize);
        int cellMinZ = (int) Math.floor((localZ + minDz) / cellSize);
        int cellMaxZ = (int) Math.floor((localZ + maxDz - 1) / cellSize);

        List<Anchor> result = new ArrayList<>();
        for (int ci = cellMinX; ci <= cellMaxX; ci++) {
            int startX = (int) Math.floor(ci * cellSize);
            int endX = (int) Math.floor((ci + 1) * cellSize);
            int widthX = endX - startX;
            if (widthX < TREE_CELL_MARGIN * 2 + 1) {
                continue;
            }
            int cellX = Math.floorMod(ci, this.cells);
            for (int cj = cellMinZ; cj <= cellMaxZ; cj++) {
                int startZ = (int) Math.floor(cj * cellSize);
                int endZ = (int) Math.floor((cj + 1) * cellSize);
                int widthZ = endZ - startZ;
                if (widthZ < TREE_CELL_MARGIN * 2 + 1) {
                    continue;
                }
                int cellZ = Math.floorMod(cj, this.cells);
                int anchorX = startX + TREE_CELL_MARGIN + (int) (this.hash01(cellX, cellZ, 1) * (widthX - TREE_CELL_MARGIN * 2));
                int anchorZ = startZ + TREE_CELL_MARGIN + (int) (this.hash01(cellX, cellZ, 2) * (widthZ - TREE_CELL_MARGIN * 2));
                int worldX = baseX + anchorX;
                int worldZ = baseZ + anchorZ;
                if (worldX >= originX + minDx && worldX < originX + maxDx
                        && worldZ >= originZ + minDz && worldZ < originZ + maxDz) {
                    result.add(new Anchor(worldX, worldZ, cellX, cellZ));
                }
            }
        }
        return result;
    }

    private void placeTree(WorldGenLevel level, Anchor anchor) {
        int biome = this.terrain.biomeAt(anchor.x, anchor.z);
        int ground = this.terrain.heightAt(anchor.x, anchor.z);
        if (ground <= LoopTerrain.SEA_LEVEL + 2) {
            return;
        }
        double density = switch (biome) {
            case LoopTerrain.BIOME_FOREST -> 0.85;
            case LoopTerrain.BIOME_TAIGA -> 0.70;
            case LoopTerrain.BIOME_SAVANNA -> 0.35;
            case LoopTerrain.BIOME_PLAINS -> 0.20;
            case LoopTerrain.BIOME_SNOWY_PLAINS -> 0.16;
            case LoopTerrain.BIOME_DESERT -> 0.30;
            default -> 0.15;
        };
        if (this.hash01(anchor.cellX, anchor.cellZ, 3) > density) {
            return;
        }
        double typeRoll = this.hash01(anchor.cellX, anchor.cellZ, 4);

        switch (biome) {
            case LoopTerrain.BIOME_DESERT -> {
                if (typeRoll < 0.55) {
                    this.placeCactus(level, anchor, ground, typeRoll);
                } else {
                    this.set(level, anchor.x, ground + 1, anchor.z, DEAD_BUSH);
                }
            }
            case LoopTerrain.BIOME_TAIGA, LoopTerrain.BIOME_SNOWY_PLAINS -> this.placeSpruce(level, anchor, ground, typeRoll);
            case LoopTerrain.BIOME_SAVANNA -> this.placeAcacia(level, anchor, ground, typeRoll);
            case LoopTerrain.BIOME_FOREST -> {
                if (typeRoll < 0.65) {
                    this.placeRoundTree(level, anchor, ground, typeRoll, OAK_LOG, OAK_LEAVES);
                } else {
                    this.placeRoundTree(level, anchor, ground, typeRoll, BIRCH_LOG, BIRCH_LEAVES);
                }
            }
            default -> this.placeRoundTree(level, anchor, ground, typeRoll, OAK_LOG, OAK_LEAVES);
        }
    }

    private void placeRoundTree(WorldGenLevel level, Anchor anchor, int ground, double roll, BlockState log, BlockState leaves) {
        int height = 4 + (int) (roll * 3.0);
        for (int dy = 1; dy <= height; dy++) {
            this.set(level, anchor.x, ground + dy, anchor.z, log);
        }
        int top = ground + height;
        for (int dy = -2; dy <= 1; dy++) {
            int radius = dy <= -1 ? 2 : 1;
            this.leafDisc(level, anchor.x, top + dy, anchor.z, radius, leaves);
        }
        this.set(level, anchor.x, top + 2, anchor.z, leaves);
    }

    private void placeSpruce(WorldGenLevel level, Anchor anchor, int ground, double roll) {
        int height = 6 + (int) (roll * 4.0);
        for (int dy = 1; dy <= height; dy++) {
            this.set(level, anchor.x, ground + dy, anchor.z, SPRUCE_LOG);
        }
        for (int dy = 2; dy <= height; dy++) {
            // 半径上限 2：树冠占地不超过 5×5，相邻树锚点至少隔 5 格，写入不会互相覆盖。
            int radius = Math.min(2, (height - dy + 1) / 2);
            int y = ground + dy;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= radius) {
                        this.setIfAir(level, anchor.x + dx, y, anchor.z + dz, SPRUCE_LEAVES);
                    }
                }
            }
        }
        this.setIfAir(level, anchor.x, ground + height + 1, anchor.z, SPRUCE_LEAVES);
    }

    private void placeAcacia(WorldGenLevel level, Anchor anchor, int ground, double roll) {
        int height = 4 + (int) (roll * 2.0);
        for (int dy = 1; dy <= height; dy++) {
            this.set(level, anchor.x, ground + dy, anchor.z, ACACIA_LOG);
        }
        int top = ground + height;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 3) {
                    this.setIfAir(level, anchor.x + dx, top, anchor.z + dz, ACACIA_LEAVES);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                this.setIfAir(level, anchor.x + dx, top + 1, anchor.z + dz, ACACIA_LEAVES);
            }
        }
    }

    private void placeCactus(WorldGenLevel level, Anchor anchor, int ground, double roll) {
        int height = 1 + (int) (roll * 3.0);
        for (int dy = 1; dy <= height; dy++) {
            this.set(level, anchor.x, ground + dy, anchor.z, CACTUS);
        }
    }

    private void leafDisc(WorldGenLevel level, int x, int y, int z, int radius, BlockState leaves) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius + 1) {
                    this.setIfAir(level, x + dx, y, z + dz, leaves);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 地表植被
    // ------------------------------------------------------------------

    private void placeGroundCover(WorldGenLevel level, ChunkPos chunkPos, List<Anchor> nearbyAnchors) {
        int originX = chunkPos.getMinBlockX();
        int originZ = chunkPos.getMinBlockZ();
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = originX + dx;
                int z = originZ + dz;
                int top = this.terrain.heightAt(x, z);
                if (top <= LoopTerrain.SEA_LEVEL + 2) {
                    continue;
                }
                if (this.nearAnchor(nearbyAnchors, x, z)) {
                    continue;
                }
                int biome = this.terrain.biomeAt(x, z);
                double roll = this.hash01(Math.floorMod(x, this.period), Math.floorMod(z, this.period), 5);
                if (biome == LoopTerrain.BIOME_DESERT) {
                    if (roll < 0.020) {
                        this.set(level, x, top + 1, z, DEAD_BUSH);
                    }
                } else if (biome == LoopTerrain.BIOME_SNOWY_PLAINS) {
                    continue;
                } else if (biome == LoopTerrain.BIOME_FOREST) {
                    if (roll < 0.22) {
                        this.set(level, x, top + 1, z, SHORT_GRASS);
                    } else if (roll < 0.25) {
                        this.set(level, x, top + 1, z, this.flower(x, z));
                    }
                } else if (biome == LoopTerrain.BIOME_TAIGA) {
                    if (roll < 0.16) {
                        this.set(level, x, top + 1, z, FERN);
                    }
                } else if (biome == LoopTerrain.BIOME_SAVANNA) {
                    if (roll < 0.20) {
                        this.set(level, x, top + 1, z, SHORT_GRASS);
                    }
                } else {
                    if (roll < 0.20) {
                        this.set(level, x, top + 1, z, SHORT_GRASS);
                    } else if (roll < 0.23) {
                        this.set(level, x, top + 1, z, this.flower(x, z));
                    }
                }
            }
        }
    }

    private BlockState flower(int x, int z) {
        double roll = this.hash01(Math.floorMod(x, this.period), Math.floorMod(z, this.period), 6);
        if (roll < 0.40) {
            return DANDELION;
        }
        if (roll < 0.60) {
            return POPPY;
        }
        if (roll < 0.80) {
            return CORNFLOWER;
        }
        return OXEYE_DAISY;
    }

    private boolean nearAnchor(List<Anchor> anchors, int x, int z) {
        for (Anchor anchor : anchors) {
            if (Math.abs(anchor.x - x) <= 1 && Math.abs(anchor.z - z) <= 1) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 矿脉
    // ------------------------------------------------------------------

    private void placeOres(WorldGenLevel level, ChunkPos chunkPos) {
        int wrappedChunkX = Math.floorMod(chunkPos.x, 2 * this.radius);
        int wrappedChunkZ = Math.floorMod(chunkPos.z, 2 * this.radius);
        Random random = new Random(this.hash64(wrappedChunkX, wrappedChunkZ, 7));
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        for (OreSpec spec : ORES) {
            for (int vein = 0; vein < spec.veins(); vein++) {
                if (random.nextFloat() > spec.chance()) {
                    continue;
                }
                int x = minX + 2 + random.nextInt(12);
                int z = minZ + 2 + random.nextInt(12);
                int y = spec.minY() + random.nextInt(Math.max(1, spec.maxY() - spec.minY()));
                this.placeVein(level, chunkPos, random, x, y, z, spec);
            }
        }
    }

    private void placeVein(WorldGenLevel level, ChunkPos chunkPos, Random random, int x, int y, int z, OreSpec spec) {
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int size = 2 + random.nextInt(spec.maxSize());
        BlockState ore = y < 0 ? spec.deepslate() : spec.stone();
        for (int i = 0; i < size; i++) {
            int bx = x + random.nextInt(3) - 1;
            int by = y + random.nextInt(3) - 1;
            int bz = z + random.nextInt(3) - 1;
            if (bx < minX || bx > minX + 15 || bz < minZ || bz > minZ + 15) {
                continue;
            }
            if (level.isOutsideBuildHeight(by)) {
                continue;
            }
            BlockState current = level.getBlockState(new BlockPos(bx, by, bz));
            if (current.is(Blocks.STONE) || current.is(Blocks.DEEPSLATE) || current.is(Blocks.DIRT)
                    || current.is(Blocks.ANDESITE) || current.is(Blocks.DIORITE) || current.is(Blocks.GRANITE)
                    || current.is(Blocks.TUFF)) {
                level.setBlock(new BlockPos(bx, by, bz), ore, 3);
            }
        }
    }

    private record OreSpec(BlockState stone, BlockState deepslate, int minY, int maxY, int veins, float chance, int maxSize) {
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
        if (level.isOutsideBuildHeight(y)) {
            return;
        }
        level.setBlock(new BlockPos(x, y, z), state, 3);
    }

    private void setIfAir(WorldGenLevel level, int x, int y, int z, BlockState state) {
        if (level.isOutsideBuildHeight(y)) {
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState current = level.getBlockState(pos);
        if (current.isAir() || current.is(Blocks.SNOW)) {
            level.setBlock(pos, state, 3);
        }
    }

    private static BlockState persistent(net.minecraft.world.level.block.Block block) {
        return state(block).setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
    }

    private static BlockState state(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState();
    }

    private double hash01(int a, int b, int salt) {
        long h = this.seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0xD6E8FEB86659FD93L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    private long hash64(int a, int b, int salt) {
        long h = this.seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0xD6E8FEB86659FD93L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ (h >>> 33);
    }
}
