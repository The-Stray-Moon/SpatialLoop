package cc.sighs.spatialloop.world;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * 循环世界的地形生成器（1.19.2 版本）。
 *
 * <p>地形、生物群系、洞穴、地表全部交给原版 {@link NoiseBasedChunkGenerator}；本类只做两件事：
 *
 * <ol>
 *   <li><b>坐标折回</b>：结构放置、装饰种子、雕刻器种子都按折回后的 canonical 坐标生成，再整体平移到
 *       当前副本。原版/模组注册的 feature、structure set 全部照常参与。</li>
 *   <li><b>记录半径</b>：{@code radius} 是存档属性（codec 存在 level.dat 的生成器里），噪声的周期化
 *       由 {@code RandomState} 创建 {@code NormalNoise} 时按这个半径盖上周期（见
 *       {@link LoopNoisePeriodAware}）。</li>
 * </ol>
 *
 * <p>与 1.20.1 的差异（1.19.2 还没有 {@code ChunkGeneratorStructureState}）：{@code NoiseBasedChunkGenerator}
 * 构造器还要两个注册表（structure set / noise），结构放置判定用
 * {@code StructurePlacement#isStructureChunk(ChunkGenerator, RandomState, long, int, int)}，
 * 随机状态从 {@code ServerChunkCache#randomState()} 取，同心环位置来自
 * {@code ChunkGenerator#getRingPositionsFor(ConcentricRingsStructurePlacement, RandomState)}。
 */
public final class SpatialLoopChunkGenerator extends NoiseBasedChunkGenerator {
    public static final Codec<SpatialLoopChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
            commonCodec(instance).and(instance.group(
                    RegistryOps.retrieveRegistry(Registry.NOISE_REGISTRY).forGetter(generator -> generator.noises),
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
                    NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(SpatialLoopChunkGenerator::generatorSettings),
                    Codec.INT.fieldOf("radius").forGetter(SpatialLoopChunkGenerator::radius)
            )).apply(instance, SpatialLoopChunkGenerator::new));

    /** codec 需要把噪声注册表原样写回配置；1.19.2 的父类字段是 private，子类自己留一份。 */
    private final Registry<NormalNoise.NoiseParameters> noises;
    private final int radius;
    private final Supplier<List<FeatureSorter.StepFeatureData>> featuresPerStep;
    @Nullable
    private volatile Set<Long> spatialloop$ringChunks;

    public SpatialLoopChunkGenerator(
            Registry<StructureSet> structureSets,
            Registry<NormalNoise.NoiseParameters> noises,
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> settings,
            int radius
    ) {
        super(structureSets, noises, biomeSource, settings);
        this.noises = noises;
        this.radius = LoopGeometry.sanitizeRadius(radius);
        // 群系源入口按 quart 周期折回：第三方群系模组（TerraBlender 等）用入口坐标算区域布局。
        if (this.biomeSource instanceof LoopBiomeSourcePeriod aware) {
            aware.spatialloop$setPeriod(LoopGeometry.periodQuarts(this.radius));
        }
        // 与原版 ChunkGenerator 完全一致：原版/模组注册进生物群系的 placed feature 都会参与排序和放置。
        this.featuresPerStep = Suppliers.memoize(() -> FeatureSorter.buildFeaturesPerStep(
                List.copyOf(this.biomeSource.possibleBiomes()),
                holder -> holder.value().getGenerationSettings().features(),
                true));
    }

    public int radius() {
        return this.radius;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void buildSurface(
            WorldGenRegion region,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess chunk
    ) {
        LoopFeatureNoise.push(LoopGeometry.periodBlocks(this.radius));
        try {
            super.buildSurface(region, structureManager, randomState, chunk);
        } finally {
            LoopFeatureNoise.pop();
        }
    }

    /**
     * 原版雕刻流程原样跑，只是让 {@code setLargeFeatureSeed} 用折回后的相邻区块坐标。
     *
     * <p>洞穴布局相对区块原点，种子折回后副本的洞穴逐块相同；真正的雕刻位置仍是绝对坐标。
     */
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
        LoopCarverSeed.push(this.radius);
        try {
            super.applyCarvers(region, seed, randomState, biomeManager, structureManager, chunk, step);
        } finally {
            LoopCarverSeed.pop();
        }
    }

    /**
     * 结构放置：<b>判定</b>用折回后的 canonical 区块坐标，<b>生成</b>也以 canonical 坐标为原点，
     * 生成完再把所有 piece 平移到当前区块所在的周期。
     *
     * <p>这样放置随机数、结构布局、排他区判定在副本之间完全一致；平移后的 piece 直接落在副本自己的
     * 坐标里，后续 {@code createReferences} 和 FEATURES 阶段都按普通绝对坐标工作。
     */
    @Override
    public void createStructures(
            RegistryAccess registryAccess,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager templateManager,
            long seed
    ) {
        if (!structureManager.shouldGenerateStructures()) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        ChunkPos canonical = new ChunkPos(
                LoopGeometry.wrapChunk(chunkPos.x, this.radius),
                LoopGeometry.wrapChunk(chunkPos.z, this.radius)
        );
        int deltaChunkX = chunkPos.x - canonical.x;
        int deltaChunkZ = chunkPos.z - canonical.z;
        SectionPos sectionPos = SectionPos.bottomOf(chunk);

        this.possibleStructureSets().forEach(holder -> {
            StructurePlacement placement = holder.value().placement();
            List<StructureSet.StructureSelectionEntry> entries = holder.value().structures();

            for (StructureSet.StructureSelectionEntry entry : entries) {
                StructureStart existing = structureManager.getStartForStructure(sectionPos, entry.structure().value(), chunk);
                if (existing != null && existing.isValid()) {
                    return;
                }
            }
            if (!this.spatialloop$isStructureChunk(placement, canonical, randomState, seed)) {
                return;
            }

            if (entries.size() == 1) {
                this.spatialloop$tryGenerateStructure(
                        entries.get(0), structureManager, registryAccess, randomState, templateManager,
                        seed, chunkPos, canonical, sectionPos, chunk, deltaChunkX, deltaChunkZ
                );
            } else {
                List<StructureSet.StructureSelectionEntry> remaining = new ArrayList<>(entries);
                WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
                random.setLargeFeatureSeed(seed, canonical.x, canonical.z);
                int totalWeight = 0;
                for (StructureSet.StructureSelectionEntry entry : remaining) {
                    totalWeight += entry.weight();
                }
                while (!remaining.isEmpty() && totalWeight > 0) {
                    int pick = random.nextInt(totalWeight);
                    int index = 0;
                    for (StructureSet.StructureSelectionEntry entry : remaining) {
                        pick -= entry.weight();
                        if (pick < 0) {
                            break;
                        }
                        index++;
                    }
                    StructureSet.StructureSelectionEntry chosen = remaining.get(Math.min(index, remaining.size() - 1));
                    if (this.spatialloop$tryGenerateStructure(
                            chosen, structureManager, registryAccess, randomState, templateManager,
                            seed, chunkPos, canonical, sectionPos, chunk, deltaChunkX, deltaChunkZ
                    )) {
                        return;
                    }
                    remaining.remove(chosen);
                    totalWeight -= chosen.weight();
                }
            }
        });
    }

    private boolean spatialloop$tryGenerateStructure(
            StructureSet.StructureSelectionEntry entry,
            StructureManager structureManager,
            RegistryAccess registryAccess,
            RandomState randomState,
            StructureTemplateManager templateManager,
            long seed,
            ChunkPos chunkPos,
            ChunkPos canonical,
            SectionPos sectionPos,
            ChunkAccess chunk,
            int deltaChunkX,
            int deltaChunkZ
    ) {
        Structure structure = entry.structure().value();
        StructureStart existing = structureManager.getStartForStructure(sectionPos, structure, chunk);
        int references = existing != null ? existing.getReferences() : 0;
        StructureStart generated = structure.generate(
                registryAccess,
                this,
                this.biomeSource,
                randomState,
                templateManager,
                seed,
                canonical,
                references,
                chunk,
                structure.biomes()::contains
        );
        if (!generated.isValid()) {
            return false;
        }
        StructureStart translated = generated;
        if (deltaChunkX != 0 || deltaChunkZ != 0) {
            for (StructurePiece piece : generated.getPieces()) {
                piece.move(deltaChunkX * 16, 0, deltaChunkZ * 16);
            }
            translated = new StructureStart(
                    generated.getStructure(),
                    chunkPos,
                    generated.getReferences(),
                    new PiecesContainer(generated.getPieces())
            );
        }
        structureManager.setStartForStructure(sectionPos, structure, translated, chunk);
        return true;
    }

    /**
     * 结构判定统一在 canonical 区块坐标上做。
     *
     * <p>同心环（要塞）的环位置是按世界原点算的绝对坐标，直接比较的话小半径世界里永远不会命中；
     * 这里把环位置按周期折回，让要塞以周期副本的形式出现在区域内。
     */
    private boolean spatialloop$isStructureChunk(
            StructurePlacement placement,
            ChunkPos canonical,
            RandomState randomState,
            long seed
    ) {
        if (placement instanceof ConcentricRingsStructurePlacement rings) {
            return this.spatialloop$ringChunks(rings, randomState).contains(canonical.toLong());
        }
        return placement.isStructureChunk(this, randomState, seed, canonical.x, canonical.z);
    }

    private Set<Long> spatialloop$ringChunks(
            ConcentricRingsStructurePlacement rings,
            RandomState randomState
    ) {
        Set<Long> cached = this.spatialloop$ringChunks;
        if (cached != null) {
            return cached;
        }
        Set<Long> built = new HashSet<>();
        List<ChunkPos> positions = this.getRingPositionsFor(rings, randomState);
        // 原版要塞是分散在 3000 万方块世界里的 128 个同心环位置；全部折回区域会变成“每个区块一个要塞”。
        // 有限循环世界里只保留第一个环位置，折回后作为唯一要塞，副本按周期重复。
        if (positions != null && !positions.isEmpty()) {
            ChunkPos pos = positions.get(0);
            built.add(ChunkPos.asLong(
                    LoopGeometry.wrapChunk(pos.x, this.radius),
                    LoopGeometry.wrapChunk(pos.z, this.radius)
            ));
        }
        this.spatialloop$ringChunks = built;
        return built;
    }

    /**
     * {@code /locate}、藏宝图、末影之眼都走这个方法。原版按绝对坐标搜索，在这个模组里会找不到
     * （甚至因为到处生成候选区块把服务器线程卡住）。这里只在 canonical 区域内搜，再把结果平移到
     * 离玩家最近的那个周期副本。
     *
     * <p>1.19.2 没有 {@code ChunkGeneratorStructureState}：种子取 {@code level.getSeed()}，
     * {@code RandomState} 取 {@code ServerChunkCache#randomState()}。
     */
    @Override
    @Nullable
    public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(
            ServerLevel level,
            HolderSet<Structure> targets,
            BlockPos center,
            int radiusChunks,
            boolean skipExistingChunks
    ) {
        RandomState randomState = level.getChunkSource().randomState();
        long seed = level.getSeed();
        int period = LoopGeometry.periodBlocks(this.radius);
        int maxBlockDistance = Math.max(0, radiusChunks) * 16;

        Pair<BlockPos, Holder<Structure>> best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Holder<StructureSet> setHolder : this.possibleStructureSets().toList()) {
            StructurePlacement placement = setHolder.value().placement();
            for (StructureSet.StructureSelectionEntry entry : setHolder.value().structures()) {
                if (!targets.contains(entry.structure())) {
                    continue;
                }
                Structure structure = entry.structure().value();

                if (placement instanceof RandomSpreadStructurePlacement spread) {
                    int spacing = spread.spacing();
                    if (spacing <= 0) {
                        continue;
                    }
                    int minCellX = Math.floorDiv(-this.radius, spacing);
                    int maxCellX = Math.floorDiv(this.radius - 1, spacing);
                    int minCellZ = Math.floorDiv(-this.radius, spacing);
                    int maxCellZ = Math.floorDiv(this.radius - 1, spacing);
                    for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                        for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                            ChunkPos candidate = spread.getPotentialStructureChunk(seed, cellX * spacing, cellZ * spacing);
                            if (candidate.x < -this.radius || candidate.x >= this.radius
                                    || candidate.z < -this.radius || candidate.z >= this.radius) {
                                continue;
                            }
                            if (!placement.isStructureChunk(this, randomState, seed, candidate.x, candidate.z)) {
                                continue;
                            }
                            StructureStart start = this.spatialloop$startAt(level, candidate, structure, skipExistingChunks);
                            if (start == null) {
                                continue;
                            }
                            BlockPos pos = spatialloop$nearestCopy(placement.getLocatePos(candidate), center, period);
                            if (Math.abs(pos.getX() - center.getX()) > maxBlockDistance
                                    || Math.abs(pos.getZ() - center.getZ()) > maxBlockDistance) {
                                continue;
                            }
                            double distance = pos.distSqr(center);
                            if (distance < bestDistance) {
                                bestDistance = distance;
                                best = Pair.of(pos, entry.structure());
                            }
                        }
                    }
                } else if (placement instanceof ConcentricRingsStructurePlacement rings) {
                    for (long packed : this.spatialloop$ringChunks(rings, randomState)) {
                        ChunkPos canonical = new ChunkPos(packed);
                        StructureStart start = this.spatialloop$startAt(level, canonical, structure, skipExistingChunks);
                        if (start == null) {
                            continue;
                        }
                        BlockPos pos = spatialloop$nearestCopy(placement.getLocatePos(canonical), center, period);
                        if (Math.abs(pos.getX() - center.getX()) > maxBlockDistance
                                || Math.abs(pos.getZ() - center.getZ()) > maxBlockDistance) {
                            continue;
                        }
                        double distance = pos.distSqr(center);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = Pair.of(pos, entry.structure());
                        }
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    private StructureStart spatialloop$startAt(
            ServerLevel level,
            ChunkPos pos,
            Structure structure,
            boolean skipExistingChunks
    ) {
        ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS, !skipExistingChunks);
        if (chunk == null) {
            return null;
        }
        StructureStart start = chunk.getStartForStructure(structure);
        return start != null && start.isValid() ? start : null;
    }

    private static BlockPos spatialloop$nearestCopy(BlockPos pos, BlockPos center, int period) {
        int dx = period * Math.round((float) (center.getX() - pos.getX()) / period);
        int dz = period * Math.round((float) (center.getZ() - pos.getZ()) / period);
        return pos.offset(dx, 0, dz);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        super.addDebugScreenInfo(info, randomState, pos);
        info.add("SpatialLoop radius: " + this.radius + " chunks");
    }

    // ------------------------------------------------------------------
    // 装饰：原版 applyBiomeDecoration 的完整复刻，只把装饰种子折回一个周期
    // ------------------------------------------------------------------

    /**
     * 原版流程一字不改：结构 piece 按装饰步放置，然后放置原版/模组注册进生物群系的 placed feature。
     * 唯一的区别是 {@code setDecorationSeed} 使用折回后的区块原点，所以同一块空间的所有副本
     * 拿到同一串随机数；feature 的落点仍然用真实区块原点，副本整体平移一个周期。
     *
     * <p>1.19.2 的 {@code generationSettingsGetter}/{@code getWritableArea} 都是父类私有成员，
     * 这里直接读 {@code Biome#getGenerationSettings()} 并自己算可写包围盒，语义与父类相同。
     */
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        LoopFeatureNoise.push(LoopGeometry.periodBlocks(this.radius));
        try {
            this.spatialloop$applyBiomeDecoration(level, chunk, structureManager);
        } finally {
            LoopFeatureNoise.pop();
        }
    }

    private void spatialloop$applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        ChunkPos chunkPos = chunk.getPos();
        if (SharedConstants.debugVoidTerrain(chunkPos)) {
            return;
        }
        SectionPos sectionPos = SectionPos.of(chunkPos, level.getMinSection());
        BlockPos origin = sectionPos.origin();
        int wrappedX = LoopGeometry.wrapBlock(origin.getX(), this.radius);
        int wrappedZ = LoopGeometry.wrapBlock(origin.getZ(), this.radius);

        List<FeatureSorter.StepFeatureData> steps = this.featuresPerStep.get();
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY);
        Map<Integer, List<Structure>> structuresByStep = structureRegistry.stream()
                .collect(Collectors.groupingBy(structure -> structure.step().ordinal()));

        Set<Holder<Biome>> nearbyBiomes = new ObjectArraySet<>();
        ChunkPos.rangeClosed(sectionPos.chunk(), 1).forEach(pos -> {
            ChunkAccess neighbour = level.getChunk(pos.x, pos.z);
            for (LevelChunkSection section : neighbour.getSections()) {
                section.getBiomes().getAll(nearbyBiomes::add);
            }
        });
        nearbyBiomes.retainAll(this.biomeSource.possibleBiomes());

        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), wrappedX, wrappedZ);
        Registry<PlacedFeature> placedFeatures = level.registryAccess().registryOrThrow(Registry.PLACED_FEATURE_REGISTRY);
        LevelHeightAccessor heightAccessor = chunk.getHeightAccessorForGeneration();
        BoundingBox writable = new BoundingBox(
                chunkPos.getMinBlockX(), heightAccessor.getMinBuildHeight() + 1, chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX(), heightAccessor.getMaxBuildHeight() - 1, chunkPos.getMaxBlockZ()
        );

        try {
            int stepCount = Math.max(GenerationStep.Decoration.values().length, steps.size());
            for (int step = 0; step < stepCount; step++) {
                int structureIndex = 0;
                if (structureManager.shouldGenerateStructures()) {
                    for (Structure structure : structuresByStep.getOrDefault(step, List.of())) {
                        random.setFeatureSeed(decorationSeed, structureIndex, step);
                        structureManager.startsForStructure(sectionPos, structure)
                                .forEach(start -> start.placeInChunk(level, structureManager, this, random, writable, chunkPos));
                        structureIndex++;
                    }
                }

                if (step < steps.size()) {
                    FeatureSorter.StepFeatureData stepData = steps.get(step);
                    IntSet indices = new IntArraySet();
                    for (Holder<Biome> biome : nearbyBiomes) {
                        List<HolderSet<PlacedFeature>> features = biome.value().getGenerationSettings().features();
                        if (step < features.size()) {
                            features.get(step).stream().map(Holder::value)
                                    .forEach(feature -> indices.add(stepData.indexMapping().applyAsInt(feature)));
                        }
                    }
                    int[] sorted = indices.toIntArray();
                    Arrays.sort(sorted);
                    for (int index : sorted) {
                        PlacedFeature feature = stepData.features().get(index);
                        random.setFeatureSeed(decorationSeed, index, step);
                        Supplier<String> description = () -> placedFeatures.getResourceKey(feature)
                                .map(Object::toString)
                                .orElseGet(feature::toString);
                        try {
                            level.setCurrentlyGenerating(description);
                            feature.placeWithBiomeCheck(level, this, random, origin);
                        } catch (Exception exception) {
                            CrashReport report = CrashReport.forThrowable(exception, "Feature placement");
                            report.addCategory("Feature").setDetail("Description", description::get);
                            throw new ReportedException(report);
                        }
                    }
                }
            }
            level.setCurrentlyGenerating(null);
        } catch (Exception exception) {
            CrashReport report = CrashReport.forThrowable(exception, "Biome decoration");
            report.addCategory("Generation")
                    .setDetail("CenterX", chunkPos.x)
                    .setDetail("CenterZ", chunkPos.z)
                    .setDetail("Decoration Seed", decorationSeed);
            throw new ReportedException(report);
        }
    }
}
