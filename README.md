# 空间循环（SpatialLoop）

一个 Minecraft NeoForge 1.21.1 模组：在创建世界时拉一个「空间循环区块半径」，让主世界变成一片
**没有边界、不断重复同一段地形**的循环空间——即「鬼打墙 / 迷踪阵」。

许可证：GPL-3.0。

## 效果

- 半径内的空间东边接西边、南边接北边，地形、生物群系、植被逐块完全一致，没有断崖、没有生态错位。
- 走到区域边缘**不会传送、不会跳坐标**：继续往外走，进入的是同一段地形的下一个周期，看起来永远走不到头。
- **区块存档折回**：磁盘上的 region 文件永远只覆盖折回后的 `2r × 2r` 个区块；在 C 处改动方块、放下箱子，
  走到 C+2r、C+4r 看到的还是同一份数据（方块实体、计划刻的绝对坐标会跟着一起平移）。
- 视距被强制压到半径以下；模拟距离压到 `2r - 1` 以下，避免同一块空间的两份副本同时处于完整状态。
- 半径是**存档属性**：写进 `level.dat` 的维度生成器设置，换客户端、改配置都不会变。

## 实现要点

| 层 | 位置 | 作用 |
| --- | --- | --- |
| 周期几何 | `common/.../geometry/LoopGeometry.java` | 半径 ↔ 周期（2r 区块 = 32r 方块）的换算与周期坐标 |
| 周期噪声 | `common/.../noise/PeriodicNoise.java` | 晶格索引按周期取模的梯度噪声，跨周期结果位级相同 |
| 地形模型 | `common/.../terrain/LoopTerrain.java` | 高度、生物群系、洞穴密度，全部只依赖周期坐标 |
| 生成器 | `targets/neoforge-1.21.1/.../world/SpatialLoopChunkGenerator.java` | 摆方块、挖洞穴、周期化装饰入口 |
| 装饰 | `.../world/LoopDecorator.java` | 自有树/草/矿：只依赖折回周期后的坐标，与区块生成顺序无关 |
| 生物群系源 | `.../world/SpatialLoopBiomeSource.java` | 周期温度/湿度 → 原版生物群系 |
| 创建世界界面 | `.../mixin/{WorldTabMixin,WorldCreationUiStateMixin,CreateWorldScreenMixin}.java` | 滑条、状态、装配生成器 |
| 视距 / 模拟距离 | `SpatialLoopViewDistance` + `ChunkMapMixin` + `DistanceManagerMixin` + 数据包 mixin | 加载、发送、渲染、实体刻一起收窄 |
| 区块存档折回 | `ChunkStorageMixin` + `LoopChunkNbt` | 读写磁盘时按周期折回区块坐标，并平移方块实体、计划刻的绝对坐标 |

装饰不使用原版 `applyBiomeDecoration`：原版会读邻居区块、并按异步生成顺序产生不同结果——实测地形逐块一致，
但树冠/泉眼每个副本会差几十块。现在树锚点落在按周期重复的抖动格上，矿脉只写在区块内部，
地表植被按折回坐标哈希，副本因此逐块一致。

## 已知限制

- 不做玩家传送（刻意如此）。因此**玩家坐标仍然连续增长**，F3 会看到很大的数字；折回的是区块存档坐标
  与数据，不是玩家坐标——要让玩家坐标也跳回来，只能传送。
- 实体、POI 的存档没有折回：怪物、掉落物、床/工作站点的 POI 在每个周期各自独立；方块、容器内容、
  计划刻是共享的。
- 同一块空间的两份副本如果被**同时**加载（`/forceload`、或两个相距一个周期的玩家），内存里是两个
  独立对象，保存时后写覆盖先写。正常游玩时视距 `< r`、模拟距离 `< 2r`，不会出现这种情况。
- 只替换主世界生成器；下界/末地保持原版。
- 不生成结构（村庄、要塞等）。结构会横跨接缝，与「完全契合」的目标冲突；因此也没有要塞/末地传送门。
- 植被与矿脉是自有实现，比原版朴素：橡木/白桦/云杉/金合欢、仙人掌、枯木、矮草/花、八种矿石；
  没有原版的海草、甘蔗、南瓜、洞穴植被等细节。
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

- `clean test build` 通过；`LoopTerrainTest` 的 5 个周期不变量测试全部通过。
- 专用服务器（NeoForge 21.1.234）以半径 8 生成世界后，用 `forceload` 取区块
  `(0,0)/(16,0)`、`(-8,0)/(8,0)`（东西接缝）、`(0,-8)/(0,8)`（南北接缝）、`(0,0)/(0,16)`，
  再用 Anvil/NBT 逐块比对：方块、生物群系、4 张高度图完全一致（0 差异）。
- 存档折回（半径 8，周期 16 区块）：在区块 `(100,0)` 放箱子并塞入钻石，保存、卸载；加载同余的
  `(116,0)`，箱子与钻石出现在对应的 `(1861,-60,5)`（方块实体坐标随折回平移）；在 `(116,0)` 的箱子里
  加绿宝石并保存，**重启服务器**后重新加载 `(100,0)`，钻石与绿宝石都在——两份副本用的是同一份存档。
- 同一存档重启服务器正常，生成器 codec 能从 level.dat 还原。
- `forceload` 区域外区块后，`world/region` 仍然只有折回后的 4 个 `.mca`，没有多出来的空 region 文件。
- 客户端启动并自动打开「创建新的世界」界面：滑条 Mixin 注入成功，无报错。
- 未验证：真人拖动滑条、长时间行走的实际观感（需要实际操作客户端）。

## 框架

基于 [Tower-of-Sighs/SighsTemple](https://github.com/Tower-of-Sighs/SighsTemple) 的
`common + targets/<loader>-<mc-version>` 结构。共享代码放在 `common`，加载器/版本专属代码、
Mixin、metadata 放在 target。
