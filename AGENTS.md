# SpatialLoop 开发规范

本仓库基于 [SighsTemple](https://github.com/Tower-of-Sighs/SighsTemple) 的
`common + targets/<loader>-<minecraft-version>` 结构。主版本 target 是 `neoforge-1.21.1`；
`fabric-1.21.1`、`neoforge-26.1`、`fabric-26.1` 已完成移植；
`forge/fabric 1.20.1`、`forge 1.19.2`、`forge 1.18.2` 已完成核心移植
（周期生成、区块/实体/POI 存档折回、视距、客户端弯折）；区块所有权交接目前只在这些
1.21.1/26.1 target 上完整实现，旧版本待补。

## 架构边界

```text
common/                         共享实现 + SPI（可引用跨版本稳定的 Minecraft 类型）
targets/<loader>-<mc>/          加载器/版本具体实现：入口、配置、网络、Mixin、ChunkGenerator、资源
gradle/target-conventions/      各 target 共用的发布/资源约定
```

- `common` 放所有**已验证没有版本差异**的实现：周期几何与噪声数学（`LoopGeometry`、`LoopNoise`）、
  交接协调器（`LoopChunkOwnership`、`LoopWriteBarrier`）、共享钩子接口、视距公式
  （`LoopLoadingLimits`）、视距钳制（`SpatialLoopViewDistance`）、半径同步时机（`LoopRadiusSync`）。
  这些代码可以引用 Minecraft 类型，但只限各 target 都存在的稳定 API；能脱离游戏测试的部分仍应保持纯 Java。
- SPI 在 `common/src/main/java/cc/sighs/spatialloop/spi`：`SpatialLoopPlatform` 汇总
  `LoopGenerationProfile`（生成依赖/持有 halo 常量）、`LoopNetworking`（载荷发送）、
  `LoopServerContext`（当前服务器）、`LoopCurveSettings`（客户端弯折配置）。target 的加载器入口在构造期调用
  `SpatialLoopServices.install(...)`；共同逻辑只依赖这些接口，不引用加载器 API。
- `targets/neoforge-1.21.1` 是参考实现，放所有确认有版本/加载器差异的部分：NeoForge 入口与
  `DeferredRegister`、`ModConfigSpec` 配置、NeoForge 网络、`SpatialLoopChunkGenerator`、Mixin、
  `LoopChunkNbt`、shader 资源与客户端滑条。地形本身完全来自原版
  `NoiseBasedChunkGenerator`，生物群系用原版 `multi_noise`，不要再写自有地形/生物群系/噪声模型。
- `targets/fabric-1.21.1` 与主版本 MC 相同，只替换加载器层：Fabric 入口、Fabric Networking、
  properties 客户端配置、`BuiltInRegistries.CHUNK_GENERATOR` 注册；因为原版
  `NoiseBasedChunkGenerator` 在 Fabric 下是 final，target 用 access widener 的
  `extendable class`，而 NeoForge/Forge 已由加载器补丁去掉 final。
- 26.1 的 Minecraft 已非混淆，渲染管线换成纯 GLSL `RenderPipeline`：没有 shader JSON，也不支持普通
  标量 uniform。`neoforge-26.1`/`fabric-26.1` 把弯折半径烘成 GLSL 常量，半径变化时清空管线缓存重编译
  （`SpatialLoopShaderPatch.takeRecompileRequest`），不再上传 `CurveRadius`/`ViewRot`。26.1 还移除了
  `ChunkStorage`（`ChunkMap` 直接继承 `SimpleRegionStorage`），chunk 折回并入 `SimpleRegionStorageMixin`。
- 一个 common 文件只有在**所有** target 都能编译/运行时才留在 common；确认有差异时，在对应 target 的
  `prepareCommonSources` 里 `exclude` 该路径，并在 target 内提供实现。不写运行时版本判断来兼容多版本。


## 关键约束

这些不是风格偏好，是玩法本身：

1. **周期必须严格，且只能改采样层**：地形是原版噪声，周期化在噪声采样底层完成，不写自有地形。
   周期以**输入单位** Q 传递：各密度节点 mixin 在调用 `NormalNoise#getValue` 前压入自己的 Q
   （`Q = 方块周期 × 该节点的坐标缩放`），`NormalNoiseMixin` 用 `LoopNoise` 做周期采样
   （八度 `cells = round(Q·f)`，晶格索引按 cells 取模）。没有压栈时用噪声实例上盖着的方块周期，
   该周期由 `RandomStateMixin` 在 `ChunkMapMixin` 压入维度周期时写入；下界/末地没有周期，保持原版。
   任何直接使用绝对坐标的噪声/随机都会在接缝处产生断崖，`LoopNoiseTest` 与 `LoopGeometryTest` 守这条线。
   不是 `NormalNoise` 的几处也要折回：`BlendedNoise`（`base_3d_noise`）、含水层的格子坐标噪声、
   `SurfaceSystem` 的缩放调用、按坐标取种子的随机工厂（矿脉/地表抖动，含水层用格子周期）、
   雕刻器种子（`LoopCarverSeed` + `WorldgenRandomMixin`）、`BiomeManager` 的模糊查找、
   `BIOME_INFO_NOISE` 计数（`LoopFeatureNoise`）。群系源的**入口坐标**也要折（`MultiNoiseBiomeSourceMixin`，
   quart 周期 = 方块周期 / 4 = `LoopGeometry.periodQuarts(r)`），因为 TerraBlender 这类模组在自己的
   `@Inject` 里用入口 quart 坐标算区域哈希（`findValuePositional`）。Mixin 按优先级从高到低应用，
   HEAD 处后应用的代码排在前面，所以本 mixin 用 `priority = 500`（**低于** TerraBlender 的默认
   1000）才能让变量改写先于它的回调执行；用 2000 时实测它的回调反而先跑。新增噪声调用点先想清楚它的输入单位。
2. **装饰必须走原版流程**：`applyBiomeDecoration` 按原版逻辑逐装饰步放结构 piece，再放生物群系
   generation settings 里的 placed feature，只把 `setDecorationSeed` 的坐标折回一个周期。不要改成
   自有装饰——那会把原版和其他模组注册的 feature 全部跳过。原版 feature 会读邻居区块、结果与生成顺序
   有关。存档折回使副本共用 canonical 槽位，但没有所有权交接，不能保证预读或同时生成的副本一致。
3. **视距上限是 `半径 - 14`，不是 `半径 - 1`**：1.21.1 的玩家票据等级是 31（ENTITY_TICKING），
   而 `ChunkLevel.MAX_LEVEL = 33 + FULL 生成依赖半径(11) = 44`；票据等级每向外一格 +1，
   持有圈比票据方形每边多 13 格。同余互斥要求 `2 × (视距 + 13) < 2r`，即 `视距 ≤ r - 14`
   （`LoopLoadingLimits.maxDistance`）。这些数字由 target 的 `LoopGenerationProfile` 提供，
   公式在 common；服务端（`ChunkMap#setServerViewDistance`）、客户端
   （登录包 / 视距变更包，`SpatialLoopViewDistance`）和模拟距离（`DistanceManagerMixin`）
   必须用同一个上限；客户端单独放宽只会画出没有区块的空洞，且会重新引入两份可写副本。
   半径 16 → 视距 2，半径 32 → 18。`LoopGenerationProfile` 里的 11 由
   `ChunkMapOwnershipMixin` 在加载循环维度时用 `LoopGenerationReach.forStatus(FULL)` 现场核对。
4. **半径是存档属性**：由 `SpatialLoopChunkGenerator` 的 codec 保存在 level.dat，不要另建全局配置。
5. **不要传送玩家**：跨过接缝时不做坐标折回/传送，玩家继续往外走即可。循环只由周期生成实现；
   这是用户明确要求的（之前用实体折回做过一版，被否决）。
6. **区块、实体、POI、结构存档都必须折回**：`ChunkStorageMixin` 处理区块本体，`SimpleRegionStorageMixin`
   处理 `entities` / `poi` 两种 `SimpleRegionStorage`；`LoopChunkNbt` 负责平移 `xPos`/`zPos`、方块实体、
   计划刻、实体 `Position`/`Pos`、POI 记录位置、结构起点（`ChunkX`/`ChunkZ`、piece 的 `BB`/`TP*`/`Pos*`）
   与 `References`。新增任何带绝对坐标的 NBT 都要在这里补平移。
7. **结构放置必须走 canonical 坐标**：`createStructures` 用折回后的区块坐标做 placement 判定和 RNG，
   生成完把所有 piece 平移到当前副本坐标；`applyBiomeDecoration` 里按折回后的装饰种子放 piece。
   不要在 `createStructures` 里直接用绝对坐标调用 `placement.isStructureChunk`，否则副本长出的结构会不一样。
   `findNearestMapStructure` 也必须在 canonical 空间搜索后平移到最近副本，不能回退到 `super`。
   要塞只取第一个同心环位置折回（全部折回会过密）。
8. **出生区不常驻**：`MinecraftServerMixin` 在 `prepareLevels` 结束后释放 `START` 票据。原版会让出生区
   永久加载，和它的同余副本同时存在时保存会互相覆盖。
9. **球形弯折只作用于世界空间顶点**：地形用资源覆盖
   `assets/minecraft/shaders/core/rendertype_{solid,cutout,cutout_mipped,translucent,tripwire}.vsh`，
   `Position + ChunkOffset` 是世界轴向的相机相对坐标，直接在 xz 上按弧长弯球面。
   其它世界渲染类型（实体/方块实体/粒子/拴绳/信标/闪电/末地传送门/描边/碰撞箱/文字）由
   `SpatialLoopShaderPatch` 在 `ShaderInstance` 读取 GLSL 时注入同一段弯折：先用
   `rel = transpose(ViewRot) * (ModelViewMat * Position)` 还原相机相对世界坐标，弯折后再乘回
   `ViewRot`。两个 uniform 必须同时出现在 shader JSON 与 vsh 里，并由 `ShaderInstanceCurveMixin`
   在 `apply()` 上传。不要对 GUI/天空/云/物品栏 glint 做弯折：与 GUI 共用的 shader（物品栏物品也用
   `rendertype_entity_*`、文字也用 `rendertype_text`）必须先用 `ProjMat[3][3] == 0`（透视）判断，
   正交投影走原版路径；否则 GUI 坐标会被当作世界坐标下拉并被裁掉。
   弯折参数是纯客户端配置 `SpatialLoopCurveConfig`（surface/strength/adaptive），有效半径由
   `LoopCurveMath.radius` 计算：正=球外表面、负=球内表面、`|R| <= 1` 不弯；着色器用
   `abs(CurveRadius) <= 1` 判定。自适应所需的循环半径由 `SpatialLoopNetwork` 在登录/换维度时通过
   `spatialloop:loop_radius` 推送（客户端没有 ChunkGenerator，不能自己读）。
10. **已知限制**：玩家坐标不折回（不传送的直接后果）；`ForcedChunksSavedData`（`/forceload`）等按绝对坐标存。
   运行时代码现于创建 `ChunkGenerationTask` / 取得 generation 引用前原子准入整个原版生成依赖范围；冲突时
   暂缓任务，旧 holder 走原版 `processUnloads`，实体与 POI 退休后等待区块/实体/POI 的真实写 future。
   r=16 的隔离 NeoForge 服务端已完成 A→B→C→A→B 五轮 `/forceload` 即时切换：remove 后紧接 add，
   无预先等待卸载、无中途 save-all；同相位位置逐轮竖直累加到 10 格、箱子累加到 5 件，正常停服重启后全部核验。
   同步 getChunk 等待期间只提前释放已完成请求留下的 UNKNOWN 保温票据；仍在等待的请求及 PLAYER/FORCED
   等有效票据不动。此回归不等于玩家连续步行验收，也未覆盖实体/POI 内容、多人冲突和 IO 故障。
   多人、强加载冲突和 IO/序列化失败会拒绝或阻塞交接，不得丢弃旧写；不同绝对坐标的同余 holder 在内存中仍非共享实例。
   当前目标版本的 FULL 生成依赖半径为 11，循环世界最低 radius 提升为 16（视距下限 2），
   新建世界默认 32（视距 18）；已有较小半径存档明确拒绝加载，不自动改变存档周期。屏障必须在所有旧生产者停止、最终写入提交后取快照；不得在读 NBT 时等待主线程卸载
   （生成引用/POI join 会死锁）。
   `runGenerationTasks` 会被 `ServerChunkCache.MainThreadExecutor.pollTask` 在 `managedBlock`
   忙等里反复调用，交接 pump 必须只做 O(待退休) 的小工作：不得每次扫全部 owner、不得每次重试全部
   pending（现为 20 ms 节流）。canonical 位置的区块/实体/POI 读写不要深拷贝 NBT（只有折回路径需要
   复制后平移）；`LoopNoise` 每样本不要重算 `Math.pow`，梯度盐必须缓存（`ImprovedNoiseSaltMixin`）。
   旧 holder 卸载后同一绝对坐标可能在同一 tick 被重新 ticket（出生区 START 释放后玩家登录、快速折返）：
   卸载观测必须确认该位置没有新 holder 才能标 `unloaded`；`admit` 遇到 owner 与 holder 不一致时，
   若 absolute 相同且状态是 ACTIVE/RETIRING，要重绑到新 holder 并 `cancelRetirement`，只有 FLUSHING
   才等写盘完成后重新申请。否则 owner 会停在 RETIRING 等一个永远不会到来的 HIDDEN。
   海底神殿读档时原版会按区块坐标重新生成 piece，副本布局可能与 canonical 略有差异。
   周期噪声用「晶格坐标 + `xo/yo/zo`」的自定义梯度哈希（原版 256 项排列表无法严格周期），
   所以地形是原版风格但不是原版逐块复刻：同一 seed 的地形与纯原版不同。
   接缝高度差与普通相邻列同量级（实测最大 3 格，同区域普通相邻列最大 4 格），不是接缝跳变。

## 构建

需要 JDK 21（26.1 target 需要 JDK 25）。common 可以单独构建；target 是独立 Gradle 根工程：

```powershell
cd targets\neoforge-1.21.1
.\gradlew.bat build

cd targets\fabric-1.21.1
.\gradlew.bat build

# 共享源码单独编译/测试（当前以 NeoForge 1.21.1 为 classpath）
.\gradlew.bat -p common build
```

测试（周期不变量与 SPI 公式）：

```powershell
cd targets\neoforge-1.21.1
.\gradlew.bat test
```

Fabric 的 `NoiseBasedChunkGenerator` 是 final，target 用
`src/main/resources/spatialloop.accesswidener` 的 `extendable class` 打开继承；
`ShaderInstance` 的构造器在 Fabric 上是 `(ResourceProvider, String, VertexFormat)`，
NeoForge 另有 `ResourceLocation` 重载，两个 target 的 `ShaderInstanceSourceMixin` 因此不同。

1.20.1 target 用 Java 17。Forge 的 Mixin 0.8.5 不支持在构造器内做 FIELD/HEAD/INVOKE 注入，
`ServerLevel`/`ChunkMap`/`ServerChunkCache`/`RandomState` 的适配改为 `RETURN` 注入，并在构造期间
直接读 `ChunkMap.generator`；Forge 开发运行由 `legacyForge { mixin { config 'spatialloop.mixins.json' } }`
传入 `--mixin.config`，生产 jar 另用 `MANIFEST.MF` 的 `MixinConfigs`（refmap 尚未生成）。

## IDE

- `common` 与各 target 的 `rootProject.name` 分别是 `SpatialLoop-common` 与
  `SpatialLoop-<loader>-<mc>`；仓库根 `settings.gradle` 用 `includeBuild` 引入 target，
  因此打开根目录或直接打开 target 都能让 IDEA 看到两个模块。不要改回模板的名字。
- IDEA 的 Gradle JVM 必须是 JDK 21；26.1 target 用 JDK 25。不要用 JDK 17 导入（moddev 会在同步时报错）。
- `.idea/` 是本地状态，不提交；克隆后由 IDEA 按 `rootProject.name` 重新生成。

## 提交前检查

- 每个 target 用对应 JDK 独立 `build` 通过，`test` 通过。
- 改地形/噪声时确认 `LoopNoiseTest`（跨周期相等 + 接缝连续）与 `LoopGeometryTest` 仍然通过；
  必要时补充断言。用临时 world preset 起一次服务器，核对副本区块 `execute if blocks` 一致。
- 改 Mixin 时确认注入点的方法签名仍与对应加载器/版本一致（主版本 NeoForge 固定 21.1.234）。
- 改 SPI 时同步检查所有 target 的实现与 `SpatialLoopServices.install(...)` 调用顺序。
- 不提交 `build/`、`.gradle/`、`run/`、本地 JDK 或反编译源码目录。
