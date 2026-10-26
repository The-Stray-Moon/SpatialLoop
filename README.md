# 空间循环（SpatialLoop）

一个 Minecraft NeoForge 1.21.1 模组：在创建世界时拉一个「空间循环区块半径」，让主世界变成一片
**没有边界、不断重复同一段地形**的循环空间——即「鬼打墙 / 迷踪阵」。

许可证：GPL-3.0。

## 效果

- 半径内的空间东边接西边、南边接北边，地形、生物群系、植被逐块完全一致，没有断崖、没有生态错位。
- 走到区域边缘**不会传送、不会跳坐标**：继续往外走，进入的是同一段地形的下一个周期，看起来永远走不到头。
- **区块存档折回**：磁盘上的 region 文件永远只覆盖折回后的 `2r × 2r` 个区块；在 C 处改动方块、放下箱子，
  走到 C+2r、C+4r 看到的还是同一份数据（方块实体、计划刻的绝对坐标会跟着一起平移）。
- **实体与 POI 也折回**：实体区块、POI 区块的存档同样按周期折回，实体的 `Pos`、POI 记录的位置跟着平移；
  在 C 处驯服的动物、村民认领的工作点，走到副本看到的是同一份数据。
- **原版结构照常生成**：村庄、要塞、前哨站、矿井、试炼密室、废弃传送门等都会出现；放置与生成用折回后的
  canonical 区块坐标，生成后把结构 piece 平移到当前副本，结构起点/引用也随存档折回。`/locate` 和
  末影之眼已适配。
- 视距被强制压到半径以下；模拟距离压到 `2r - 1` 以下，避免同一块空间的两份副本同时处于完整状态。
- 半径是**存档属性**：写进 `level.dat` 的维度生成器设置，换客户端、改配置都不会变。

## 实现要点

| 层 | 位置 | 作用 |
| --- | --- | --- |
| 周期几何 | `common/.../geometry/LoopGeometry.java` | 半径 ↔ 周期（2r 区块 = 32r 方块）的换算与周期坐标 |
| 周期噪声 | `targets/.../world/LoopNoise.java` | 原版 Perlin/NormalNoise 的周期重采样：晶格索引按 `cells = round(Q·f)` 取模，八度振幅/频率与原版一致 |
| 噪声注入 | `.../mixin/{NormalNoise,PerlinNoiseAccessor,RandomState,BlendedNoise,DensityFunctions*,Aquifer,SurfaceSystem}Mixin` | 给每个 `NormalNoise` 盖上维度周期；各密度节点按自己的输入单位压入周期；`BlendedNoise`、含水层与地表缩放调用单独折回 |
| 生成器 | `.../world/SpatialLoopChunkGenerator.java` | `extends NoiseBasedChunkGenerator`；地形/生物群系/洞穴/地表全走原版，本类只折回结构、装饰、雕刻种子 |
| 生物群系 | 原版 `multi_noise` | 气候噪声同样周期化；`BiomeManager` 的模糊查找与 `BIOME_INFO_NOISE` 计数也按周期折回；`MultiNoiseBiomeSourceMixin` 把入口 quart 坐标折回，第三方群系模组（TerraBlender）的区域布局在副本间一致 |
| 创建世界界面 | `.../mixin/{WorldTabMixin,WorldCreationUiStateMixin,CreateWorldScreenMixin}.java` | 滑条、状态、从原版 overworld 生成器取出 `biome_source`/`settings` 装配循环生成器 |
| 视距 / 模拟距离 | `SpatialLoopViewDistance` + `ChunkMapMixin` + `DistanceManagerMixin` + 数据包 mixin | 加载、发送、渲染、实体刻一起收窄 |
| 存档折回 | `ChunkStorageMixin` + `SimpleRegionStorageMixin` + `LoopChunkNbt` | 区块 / 实体 / POI 读写磁盘时按周期折回坐标，并平移方块实体、计划刻、实体位置、POI 记录、结构起点/引用 |
| 结构 | `SpatialLoopChunkGenerator#createStructures` + `applyBiomeDecoration` | canonical 坐标判定与生成、piece 平移；`findNearestMapStructure` 在 canonical 空间搜索后平移到最近副本 |

周期化的核心是「在采样底层换掉噪声」：同一个 `NormalNoise` 会被不同节点用不同缩放调用
（`DensityFunctions.Noise` 传 `blockX·xzScale`、`ShiftNoise` 传 `blockX·0.25`、`SurfaceSystem` 有
`x·1.28` 之类），所以周期以**输入单位**传递（`LoopNoisePeriod` 线程内栈），`NormalNoise#getValue`
按 `Q` 做周期采样。`RandomState` 创建噪声时从当前维度的半径盖上默认方块周期，下界/末地没有周期，
保持原版。`BlendedNoise`（`base_3d_noise`）不走 `NormalNoise`，用同一机制在 `compute` 里折回 x/z；
含水层的格子坐标噪声、矿脉/地表抖动的按坐标随机工厂、`BiomeManager` 的模糊查找、雕刻器种子也都
各自折回到同一个周期。

原版 `ImprovedNoise` 的排列表只有 256 项，晶格数不是 256 的整数倍时接缝会断，所以 `LoopNoise`
用「晶格坐标 + 该层 `xo/yo/zo`」重新做梯度哈希。结果是与原版同风格、同振幅的噪声，但不是原版逐块复刻。

装饰完全走原版 `applyBiomeDecoration`：按装饰步先放结构 piece，再把生物群系 generation settings 里的
placed feature 逐个放下去。唯一的改动是装饰种子用折回后的区块原点（`setDecorationSeed`），
所以同一块空间的所有副本拿到同一串随机数；feature 的落点仍然用真实区块原点，副本整体平移一个周期。
原版和其他模组注册进生物群系的 feature 都会参与生成，不需要任何适配。

原版 feature 会读邻居区块的方块状态、并按异步生成顺序落块，所以同一份区块**第一次生成**时结果可能
依赖生成顺序。正常游玩时这一点由存档折回兜底：第一个生成的副本写入 canonical 槽位，之后所有副本
都从那里读，内容和坐标平移完全一致。

## 已知限制

- 不做玩家传送（刻意如此）。因此**玩家坐标仍然连续增长**，F3 会看到很大的数字；折回的是区块存档坐标
  与数据，不是玩家坐标——要让玩家坐标也跳回来，只能传送。
- 同一块空间的两份副本如果被**同时**加载（`/forceload`、或两个相距一个周期的玩家），内存里是两个
  独立对象，实体也会被读成两份（同一 UUID），保存时后写覆盖先写。正常游玩时视距 `< r`、模拟距离
  `< 2r`，不会出现这种情况。
- 只替换主世界生成器；下界/末地保持原版。
- 要塞：原版 128 个同心环位置全部折回区域内会变成「每个区块一个要塞」，所以只取第一个环位置折回成
  **一个**要塞（副本按周期重复）。`/locate stronghold` 和末影之眼会指向最近的副本。
- 海底神殿：原版读档时会按区块坐标强制重新生成内部 piece（`OceanMonumentStructure.regeneratePiecesAfterLoad`），
  副本的布局可能与 canonical 那份略有差异。其他结构不受影响。
- 只在新建世界时选择半径；已有存档需要重建世界才能启用。超平坦/调试世界不会被替换。
- 世界高度、海平面沿用主世界默认值（-64 / 384 / 63）。
## 在 IDEA 里打开

两种方式都可以，推荐第一种：

1. **直接打开 target（最省事）**：`File → Open` 选 `D:\work\SpatialLoop\targets\neoforge-1.21.1`。
   `common` 会作为 composite build 自动导入成独立模块，同步后 Gradle 面板里能看到
   `runClient` / `runServer`。
2. **打开仓库根目录**：根 `settings.gradle` 已经把 target 作为 composite build 引入，
   所以根项目 `SpatialLoop` 下同样能看到 `SpatialLoop-neoforge-1.21.1` 和 `SpatialLoop-common` 两个模块。

注意 Gradle JVM 必须是 **JDK 21**（`Settings → Build, Execution, Deployment → Build Tools → Gradle →
Gradle JVM`）。本项目已经把它设成 IDE 自带的 `jbrsdk_jcef-21`；换机器后如果提示找不到 JDK，
改成任意 JDK 21 即可。

如果窗口标题还显示旧的 `SighsTemple-...`（IDEA 会把项目名缓存在 `.idea/.name` 里），
同步一次 Gradle，或者 `File → Rename Project` 改成 `SpatialLoop-neoforge-1.21.1`；
也可以直接关掉项目、删除 `targets/neoforge-1.21.1/.idea` 后重新 Open，让 IDEA 按新的
`rootProject.name` 重新生成。

## 构建

需要 JDK 21。每个 target 是独立的 Gradle 根工程：

```powershell
cd targets\neoforge-1.21.1
.\gradlew.bat build
```

产物：`targets/neoforge-1.21.1/build/libs/SpatialLoop-neoforge-1.21.1-<version>.jar`。

`common/` 也可以单独编译（框架约定用 Forge 1.20.1 类路径）：

```powershell
.\gradlew.bat -p common build
```

## 验证记录

- `clean test build` 通过：`LoopGeometryTest`（5 个折回不变量）、`LoopChunkNbtTest`（4 个 NBT 平移）、
  `LoopNoiseTest`（3 个周期噪声不变量：跨周期严格相等、接缝与普通相邻点同量级、值不退化）。
- 临时 world preset（`spatialloop:periodic`，radius=32，seed=20251025）起专用服务器：
  `forceload` 区块 `(100,100)` 与 `(164,100)`（相差一个周期 1024 格）后，
  `execute if blocks` 的 y=-64..39 与 y=40..119 两段均 **Test passed**（26624 / 20480 个方块）。
  另取 `(36,100)`、`(228,100)` 两个副本逐段比对，同样全部通过。
- 接缝：`forceload` 区块 30..33，`save-all flush` 后离线解析 `run/world/region/*.mca`，对 z=1584..1615
  逐列比较 x=511 与折回槽位里的 x=-512（即实际的 x=512）：最大高度差 **3**；32 列中 20 列为 0 或 1。
  同一片区域普通相邻两列的最大高度差是 4（p95=2），所以接缝处没有额外断崖——
  验收里「≤1」的字面阈值在这个种子下落在一处沙丘/陡坡上，未满足；这是地形坡度，不是接缝跳变。
- 装饰/结构仍走原版流程：区块调色板里能看到全部原版矿石、草、树；结构生成与 `/locate` 沿用上一轮验证。
- 存档/实体/POI 折回、视距/模拟距离、重启还原：沿用上一轮验证（本轮未改动这些路径）。
- 未验证：真人拖动滑条、长时间行走的实际观感（需要实际操作客户端）。

## 框架

基于 [Tower-of-Sighs/SighsTemple](https://github.com/Tower-of-Sighs/SighsTemple) 的
`common + targets/<loader>-<mc-version>` 结构。共享代码放在 `common`，加载器/版本专属代码、
Mixin、metadata 放在 target。
