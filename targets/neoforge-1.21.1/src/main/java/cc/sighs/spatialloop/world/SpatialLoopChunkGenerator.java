package cc.sighs.spatialloop.world;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.terrain.LoopTerrain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * 循环世界的地形生成器。
 *
 * <p>和原版 {@code NoiseBasedChunkGenerator} 最大的区别只有一条：所有地形/生物群系/洞穴采样都经过
 * {@link LoopTerrain} 的周期化坐标。因此区块 {@code (x + 2r, z)} 与 {@code (x, z)} 生成的方块
 * <b>逐块完全相同</b>，东接西、南接北不会有断崖或生态错位。
 *
 * <p>装饰（树、草、矿）交给 {@link LoopDecorator}：原版装饰依赖邻居区块的当前状态，异步生成顺序会让
 * 同一块空间的两个副本长出不同的树；自有装饰只依赖折回周期后的坐标，结果与生成顺序无关。
 *
 * <p>结构（村庄、要塞等）会跨越接缝，因此本生成器不放置结构。
 */
public final class SpatialLoopChunkGenerator extends ChunkGenerator {
    public static final MapCodec<SpatialLoopChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.CODEC.listOf().fieldOf("biomes").forGetter(generator -> generator.biomes),
            Codec.INT.fieldOf("radius").forGetter(generator -> generator.radius),
            Codec.LONG.fieldOf("seed").forGetter(generator -> generator.seed),
            Codec.INT.fieldOf("min_y").forGetter(generator -> generator.minY),
            Codec.INT.fieldOf("height").forGetter(generator -> generator.height),
            Codec.INT.fieldOf("sea_level").forGetter(generator -> generator.seaLevel)
    ).apply(instance, SpatialLoopChunkGenerator::new));

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState GRASS_BLOCK = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState SNOW = Blocks.SNOW.defaultBlockState();

    private static final int CAVE_STEP = 4;
    private static final int CAVE_GRID = 5;

    private final List<Holder<Biome>> biomes;
    private final int radius;
    private final long seed;
    private final int minY;
    private final int height;
    private final int seaLevel;
    private final int maxY;
    private final LoopTerrain terrain;
    private final LoopDecorator decorator;

    public SpatialLoopChunkGenerator(List<Holder<Biome>> biomes, int radius, long seed, int minY, int height, int seaLevel) {
        super(new SpatialLoopBiomeSource(biomes, seed, radius));
        this.biomes = List.copyOf(biomes);
        this.radius = LoopGeometry.sanitizeRadius(radius);
        this.seed = seed;
        this.minY = minY;
        this.height = height;
        this.seaLevel = seaLevel;
        this.maxY = minY + height - 1;
        this.terrain = new LoopTerrain(seed, this.radius);
        this.decorator = new LoopDecorator(seed, this.radius);
    }

    public int radius() {
        return this.radius;
    }

    public long seed() {
        return this.seed;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        this.generate(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyCarvers(
            WorldGenRegion region,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            GenerationStep.Carving step
    ) {
        // 洞穴在 fillFromNoise 里一次成形，不需要再跑原版雕刻器。
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
        // 地表在 fillFromNoise 里一次成形。
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    /** 结构会横跨接缝，这里选择不生成结构，保证接缝两侧完全一致。 */
    @Override
    public void createStructures(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager templateManager
    ) {
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        int spawn = this.terrain.heightAt(0, 0) + 1;
        return Math.max(this.minY + 1, Math.min(this.maxY - 1, spawn));
    }

    @Override
    public int getGenDepth() {
        return this.height;
    }

    @Override
    public int getSeaLevel() {
        return this.seaLevel;
    }

    @Override
    public int getMinY() {
        return this.minY;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        int top = this.terrain.heightAt(x, z);
        boolean oceanFloor = type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG;
        int height = oceanFloor ? top + 1 : Math.max(top + 1, this.seaLevel + 1);
        return Math.max(this.minY, Math.min(this.maxY, height));
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        int top = this.terrain.heightAt(x, z);
        int biome = this.terrain.biomeAt(x, z);
        BlockState[] column = new BlockState[level.getHeight()];
        for (int i = 0; i < column.length; i++) {
            int y = level.getMinBuildHeight() + i;
            if (y > top) {
                column[i] = y <= this.seaLevel ? WATER : AIR;
            } else if (y == top) {
                column[i] = this.surfaceState(biome, top, x, z);
            } else if (y >= top - 3) {
                column[i] = top <= this.seaLevel ? this.seabedState(biome, x, z) : DIRT;
            } else {
                column[i] = y < 0 ? DEEPSLATE : STONE;
            }
        }
        return new NoiseColumn(level.getMinBuildHeight(), column);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("SpatialLoop radius: " + this.radius + " chunks");
    }

    // ------------------------------------------------------------------
    // 地形
    // ------------------------------------------------------------------

    private void generate(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();
        LevelChunkSection[] sections = chunk.getSections();
        int minSection = chunk.getMinSection();

        int[] heights = new int[256];
        int[] biomes = new int[256];
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int index = dz * 16 + dx;
                int x = baseX + dx;
                int z = baseZ + dz;
                heights[index] = this.terrain.heightAt(x, z);
                biomes[index] = this.terrain.biomeAt(x, z);
            }
        }

        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int index = dz * 16 + dx;
                this.fillColumn(sections, minSection, baseX + dx, baseZ + dz, heights[index], biomes[index]);
            }
        }

        this.carveCaves(sections, minSection, baseX, baseZ, heights);
    }

    private void fillColumn(LevelChunkSection[] sections, int minSection, int x, int z, int top, int biome) {
        for (int y = this.minY; y <= top; y++) {
            BlockState state;
            if (y == this.minY || (y < this.minY + 3 && this.bedrockRoll(x, y, z))) {
                state = BEDROCK;
            } else if (y == top) {
                state = this.surfaceState(biome, top, x, z);
            } else if (top <= this.seaLevel && y >= top - 3) {
                state = this.seabedState(biome, x, z);
            } else if (biome == LoopTerrain.BIOME_DESERT && top > this.seaLevel && y >= top - 4) {
                state = SAND;
            } else if (biome == LoopTerrain.BIOME_DESERT && top > this.seaLevel && y >= top - 9) {
                state = SANDSTONE;
            } else if (y >= top - 3) {
                state = DIRT;
            } else {
                state = y < 0 ? DEEPSLATE : STONE;
            }
            setBlock(sections, minSection, x, y, z, state);
        }

        for (int y = top + 1; y <= this.seaLevel; y++) {
            setBlock(sections, minSection, x, y, z, WATER);
        }

        if (biome == LoopTerrain.BIOME_SNOWY_PLAINS && top > this.seaLevel && top + 1 <= this.maxY) {
            setBlock(sections, minSection, x, top + 1, z, SNOW);
        }
    }

    private void carveCaves(LevelChunkSection[] sections, int minSection, int baseX, int baseZ, int[] heights) {
        int step = CAVE_STEP;
        int gridY = (this.height + step - 1) / step + 1;
        double[] grid = new double[CAVE_GRID * CAVE_GRID * gridY];
        for (int gx = 0; gx < CAVE_GRID; gx++) {
            for (int gz = 0; gz < CAVE_GRID; gz++) {
                for (int gy = 0; gy < gridY; gy++) {
                    int y = Math.min(this.maxY, this.minY + gy * step);
                    grid[(gx * CAVE_GRID + gz) * gridY + gy] = this.terrain.caveDensity(baseX + gx * step, y, baseZ + gz * step);
                }
            }
        }

        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int top = heights[dz * 16 + dx];
                int limit = top - 6;
                int lowest = this.minY + 2;
                if (limit <= lowest) {
                    continue;
                }
                int gx = dx / step;
                int gz = dz / step;
                double tx = (dx % step) / (double) step;
                double tz = (dz % step) / (double) step;
                for (int y = lowest; y <= limit; y++) {
                    int relative = y - this.minY;
                    int gy = relative / step;
                    double ty = (relative % step) / (double) step;
                    if (sampleCave(grid, gridY, gx, gz, gy, tx, ty, tz) < 0.0) {
                        setBlock(sections, minSection, baseX + dx, y, baseZ + dz, AIR);
                    }
                }
            }
        }
    }

    private static double sampleCave(double[] grid, int gridY, int gx, int gz, int gy, double tx, double ty, double tz) {
        int gx1 = Math.min(gx + 1, CAVE_GRID - 1);
        int gz1 = Math.min(gz + 1, CAVE_GRID - 1);
        int gy1 = Math.min(gy + 1, gridY - 1);
        double c000 = grid[(gx * CAVE_GRID + gz) * gridY + gy];
        double c100 = grid[(gx1 * CAVE_GRID + gz) * gridY + gy];
        double c010 = grid[(gx * CAVE_GRID + gz1) * gridY + gy];
        double c110 = grid[(gx1 * CAVE_GRID + gz1) * gridY + gy];
        double c001 = grid[(gx * CAVE_GRID + gz) * gridY + gy1];
        double c101 = grid[(gx1 * CAVE_GRID + gz) * gridY + gy1];
        double c011 = grid[(gx * CAVE_GRID + gz1) * gridY + gy1];
        double c111 = grid[(gx1 * CAVE_GRID + gz1) * gridY + gy1];
        double c00 = c000 + (c100 - c000) * tx;
        double c10 = c010 + (c110 - c010) * tx;
        double c01 = c001 + (c101 - c001) * tx;
        double c11 = c011 + (c111 - c011) * tx;
        double c0 = c00 + (c10 - c00) * tz;
        double c1 = c01 + (c11 - c01) * tz;
        return c0 + (c1 - c0) * ty;
    }

    private static void setBlock(LevelChunkSection[] sections, int minSection, int x, int y, int z, BlockState state) {
        int sectionIndex = SectionPos.blockToSectionCoord(y) - minSection;
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            return;
        }
        sections[sectionIndex].setBlockState(x & 15, y & 15, z & 15, state, false);
    }

    /**
     * 基岩层的随机高度必须按周期坐标算：直接哈希绝对坐标会让同一块空间的两个副本在 y=-63 附近不一致。
     */
    private boolean bedrockRoll(int x, int y, int z) {
        int wrappedX = LoopGeometry.wrapBlock(x, this.radius);
        int wrappedZ = LoopGeometry.wrapBlock(z, this.radius);
        long hash = this.seed ^ (wrappedX * 0x9E3779B97F4A7C15L) ^ (wrappedZ * 0xC2B2AE3D27D4EB4FL) ^ (y * 0x165667B19E3779F9L);
        hash ^= hash >>> 33;
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= hash >>> 33;
        double roll = (hash >>> 11) * 0x1.0p-53;
        int depth = this.minY + 3 - y;
        return roll < depth * 0.22;
    }

    private BlockState surfaceState(int biome, int top, int x, int z) {
        if (top <= this.seaLevel) {
            return this.seabedState(biome, x, z);
        }
        if (top <= this.seaLevel + 2) {
            return SAND;
        }
        return biome == LoopTerrain.BIOME_DESERT ? SAND : GRASS_BLOCK;
    }

    private BlockState seabedState(int biome, int x, int z) {
        if (biome == LoopTerrain.BIOME_DESERT) {
            return SAND;
        }
        int wrappedX = LoopGeometry.wrapBlock(x, this.radius);
        int wrappedZ = LoopGeometry.wrapBlock(z, this.radius);
        long hash = this.seed ^ (wrappedX * 0x9E3779B97F4A7C15L) ^ (wrappedZ * 0xC2B2AE3D27D4EB4FL);
        hash ^= hash >>> 33;
        return ((hash >>> 40) & 7) < 5 ? GRAVEL : SAND;
    }

    // ------------------------------------------------------------------
    // 装饰：自有、纯周期函数的植被与矿脉（见 LoopDecorator）
    // ------------------------------------------------------------------

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        ChunkPos chunkPos = chunk.getPos();
        if (SharedConstants.debugVoidTerrain(chunkPos)) {
            return;
        }
        // 不用原版装饰：原版会读邻居区块、并被邻居的装饰顺序影响，副本之间会出现几十块差异。
        this.decorator.decorate(level, chunkPos);
    }
}
