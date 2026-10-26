# SpatialLoop 开发规范

本仓库基于 [SighsTemple](https://github.com/Tower-of-Sighs/SighsTemple) 的
`common + targets/<loader>-<minecraft-version>` 结构。当前只有一个 target：`neoforge-1.21.1`。

## 架构边界

```text
common/                          不依赖具体加载器/版本差异的共享源码与资源
targets/neoforge-1.21.1/         入口、注册、Mixin、客户端界面、metadata
gradle/target-conventions/       各 target 共用的发布/资源约定
```

- `common` 目前只放纯 Java 的周期几何、周期噪声和地形模型，以及共享语言文件。这些代码不引用
  Minecraft 类型，可以脱离游戏单独测试。
- `targets/neoforge-1.21.1` 放所有 Minecraft / NeoForge 相关实现：`ChunkGenerator`、`BiomeSource`、
  Mixin、注册与资源。
- 不要把 Minecraft 类型引入 `common` 的现有类；若将来移植到别的版本/加载器，按框架规则在 target 的
  `prepareCommonSources` 里 `exclude` 有差异的 common 路径，并在 target 内提供实现。
- 不写运行时版本判断来兼容多个版本；差异由各 target 的适配层承担。

## 关键约束

这些不是风格偏好，是玩法本身：

1. **周期必须严格**：所有地形采样都走 `LoopGeometry.unit(...)` 折回 `[0, 1)`，噪声晶格索引按周期取模。
   任何直接使用绝对坐标的改动都会在接缝处产生断崖。`common/src/test/java` 里的
   `LoopTerrainTest` 就是守这条线的。
2. **装饰必须与生成顺序无关**：不使用原版 `applyBiomeDecoration`。原版会读邻居区块的方块状态，
   异步区块任务的先后顺序会让副本长出不同的树。`LoopDecorator` 的所有随机数只来自折回周期后的坐标：
   树锚点按周期重复的抖动格排布（相邻锚点至少隔 5 格，树冠不互相覆盖），矿脉只写当前区块内部，
   地表植被逐列哈希。改动这部分时必须保持「不读邻居装饰结果、写入不重叠」。不要换回原版装饰。
3. **视距严格小于半径**：`LoopGeometry.maxViewDistance(radius) = radius - 1`，服务端
   （`ChunkMap#setServerViewDistance`）与客户端（登录包 / 视距变更包）两侧都要压；模拟距离压到
   `2r - 1` 以下（`DistanceManagerMixin`），避免同余区块同时处于完整状态。
4. **半径是存档属性**：由 `SpatialLoopChunkGenerator` 的 codec 保存在 level.dat，不要另建全局配置。
5. **不要传送玩家**：跨过接缝时不做坐标折回/传送，玩家继续往外走即可。循环只由周期生成实现；
   这是用户明确要求的（之前用实体折回做过一版，被否决）。
6. **区块存档必须折回**：`ChunkStorageMixin` 在读写磁盘时把区块坐标按周期取模，`LoopChunkNbt` 同步平移
   `xPos`/`zPos`、方块实体和计划刻的绝对坐标。将来若给生成器加结构，结构起点/引用的区块坐标也必须平移。
   这样磁盘只覆盖 `2r × 2r` 区块，副本读到的是同一份数据。
7. **出生区不常驻**：`MinecraftServerMixin` 在 `prepareLevels` 结束后释放 `START` 票据。原版会让出生区
   永久加载，和它的同余副本同时存在时保存会互相覆盖。
8. **已知未折回**：实体与 POI 存档仍按绝对坐标存；两份同余区块被同时加载（`/forceload`、相距一个周期的
   两个玩家）时内存里是独立对象，后保存的覆盖先保存的。正常游玩时视距/模拟距离都小于周期，不会发生。

## 构建

需要 JDK 21。target 是独立 Gradle 根工程：

```powershell
cd targets\neoforge-1.21.1
.\gradlew.bat build
```

测试（周期不变量）：

```powershell
cd targets\neoforge-1.21.1
.\gradlew.bat test
```

## IDE

- `common` 与 `targets/neoforge-1.21.1` 的 `rootProject.name` 分别是 `SpatialLoop-common` 与
  `SpatialLoop-neoforge-1.21.1`；仓库根 `settings.gradle` 用 `includeBuild` 引入 target，
  因此打开根目录或直接打开 target 都能让 IDEA 看到两个模块。不要改回模板的名字。
- IDEA 的 Gradle JVM 必须是 JDK 21；不要用 JDK 17 导入（moddev 会在同步时报错）。
- `.idea/` 是本地状态，不提交；克隆后由 IDEA 按 `rootProject.name` 重新生成。

## 提交前检查

- target 用 JDK 21 独立 `build` 通过，`test` 通过。
- 改地形/噪声时确认 `LoopTerrainTest` 仍然通过；必要时补充断言。
- 改 Mixin 时确认注入点的方法签名仍与当前 NeoForge 版本一致（本项目固定 21.1.234）。
- 不提交 `build/`、`.gradle/`、`run/`、本地 JDK 或反编译源码目录。
