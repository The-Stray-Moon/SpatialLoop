# SpatialLoop

多加载器、多 Minecraft 版本的 Minecraft 模组工程。本 README 只描述代码架构、模块边界和构建方式，不介绍玩法内容。

许可证：GPL-3.0。

## 仓库结构

```text
common/                         共享实现 + SPI
targets/<loader>-<mc>/          加载器/版本具体实现
gradle/target-conventions/      各 target 共用的资源与发布约定
```

每个 target 都是独立的 Gradle 根工程。根 `settings.gradle` 只把主 target `neoforge-1.21.1`
作为 composite build 引入；其余 target 直接打开各自目录即可。

## 架构边界

### common

`common` 放所有**已验证没有版本差异**的实现：

- 周期几何与噪声数学（`LoopGeometry`、`LoopNoise`、`LoopCurveMath` 等）；
- 区块所有权协调器与写屏障（`LoopChunkOwnership`、`LoopWriteBarrier`）；
- 视距公式（`LoopLoadingLimits`）、视距钳制（`SpatialLoopViewDistance`）、半径同步时机
  （`LoopRadiusSync`）；
- 客户端弯折计算（`SpatialLoopCurve`）；
- 跨 target 的共享钩子接口；
- `spi/` 下的平台抽象。

`common` 可以引用 Minecraft 类型，但只限所有 target 都存在的稳定 API；能脱离游戏测试的部分保持纯 Java。

### targets

每个 target 只保留加载器或版本差异：

- 加载器入口与生命周期注册；
- 配置实现（NeoForge `ModConfigSpec`、Forge `ForgeConfigSpec`、Fabric properties）；
- 网络实现（NeoForge/Fabric payload、Forge `SimpleChannel`）；
- 版本相关的 `ChunkGenerator`；
- Mixin（原版内部类/方法签名随版本变化）；
- 存档 NBT 平移、gamerule/注册表/结构 API 适配；
- metadata、access widener、shader 资源、客户端界面。

### 源码共享

target 不通过 Gradle 依赖引用 `common`，而是编译同一份源码：

```groovy
tasks.register('prepareCommonSources', Sync) {
    from('../../common/src/main/java') {
        exclude 'cc/sighs/spatialloop/world/LoopPositionalRandomFactory.java'
    }
    into "$buildDir/generated/common-main"
}
```

- `common/src/main/java` 被同步到 `build/generated/common-main` 后参与 target 编译。
- 确认有版本差异的 common 文件，在对应 target 的 `prepareCommonSources` 里 `exclude`，并在 target
  内提供同路径实现。
- `common/src/main/resources` 由 `gradle/target-conventions/publish.gradle` 加入 target 资源。
- 不写运行时版本判断；版本差异由 target 的源码选择承担。

### SPI

SPI 位于 `common/src/main/java/cc/sighs/spatialloop/spi`：

| 接口 | 作用 |
| --- | --- |
| `SpatialLoopPlatform` | 汇总 target 提供的运行时服务 |
| `SpatialLoopServices` | 进程内安装/获取 `SpatialLoopPlatform` |
| `LoopGenerationProfile` | 当前版本的生成依赖半径与 holder halo |
| `LoopNetworking` | 半径同步的发送实现 |
| `LoopServerContext` | 当前服务端/主世界查询 |
| `LoopCurveSettings` | 客户端弯折配置读取 |

target 的加载器入口在构造期调用 `SpatialLoopServices.install(...)`；共同逻辑只依赖这些接口，
不引用加载器 API，也不直接依赖具体 target 类。

## 分层职责

| 层次 | 主要位置 | 说明 |
| --- | --- | --- |
| 几何/数学 | `common/geometry` | 半径、周期、坐标折回、弯折半径换算 |
| 噪声采样 | `common/world` + target Mixin | 周期以输入单位传递；各版本在 `NormalNoise`/`BlendedNoise` 等采样入口注入 |
| 所有权 | `common/ownership` | 纯 Java 状态机，不操作 Minecraft 票据或存储 |
| 存档 | target Mixin + `LoopChunkNbt` | 区块/实体/POI 读写时折回 canonical 槽位并平移绝对坐标 |
| 生成 | target `SpatialLoopChunkGenerator` | 地形/群系沿用原版生成器，只适配结构、装饰、雕刻种子 |
| 视距 | `common/world` + target Mixin | 服务端、客户端、模拟距离共用同一上限公式 |
| 客户端渲染 | `common/client` + target Mixin/资源 | 世界空间 shader 的顶点改写与配置读取 |
| 加载器胶水 | target 入口 | 注册、配置、网络、事件、metadata |

## Targets

| Target | Minecraft | 加载器 | Java | 说明 |
| --- | --- | --- | --- | --- |
| `neoforge-1.21.1` | 1.21.1 | NeoForge | 21 | 主参考实现，所有权交接完整 |
| `fabric-1.21.1` | 1.21.1 | Fabric | 21 | 与主版本同 MC，仅加载器层不同 |
| `neoforge-26.1` | 26.1 | NeoForge | 25 | 非混淆 API + 新渲染管线 |
| `fabric-26.1` | 26.1 | Fabric | 25 | 与 NeoForge 26.1 同 MC |
| `forge-1.20.1` | 1.20.1 | Forge | 17 | 旧存储/票据 API |
| `fabric-1.20.1` | 1.20.1 | Fabric | 17 | 与 Forge 1.20.1 同 MC |
| `forge-1.19.2` | 1.19.2 | Forge | 17 | 旧注册表/结构 API |
| `forge-1.18.2` | 1.18.2 | Forge | 17 | 无 `RandomState`，API 差异最大 |

当前状态：

- 所有 target 都已迁移核心实现，可独立构建、测试并启动专用服务端。
- 区块所有权交接（准入、写屏障、实体/POI 退休）目前只在 1.21.1/26.1 target 上完整实现；
  1.20.1、1.19.2、1.18.2 是核心移植，缺少这层交接。
- Forge 旧版本生产 jar 的 Mixin refmap 尚未生成；开发运行通过 `--mixin.config`，生产 jar 通过
  `MANIFEST.MF` 的 `MixinConfigs` 加载配置。

## 版本差异的处理方式

差异必须来自对应版本的源码核对，不能靠猜测：

- **1.21.1**：`ChunkStatus` 在 `chunk.status` 包；有 `ChunkPyramid`、`SimpleRegionStorage`、
  `GenerationChunkHolder`。
- **1.20.1**：`ChunkStatus` 回到 `chunk` 包；`ChunkMap extends ChunkStorage`；没有
  `SimpleRegionStorage`/`GenerationChunkHolder`；Forge Mixin 0.8.5 不支持构造器内的
  FIELD/HEAD/INVOKE 注入。
- **1.19.2/1.18.2**：注册表静态字段、结构/装饰 API、`PositionalRandomFactory`、客户端 UI 均不同；
  1.18.2 还没有 `RandomState`。
- **26.1**：Minecraft 不再混淆；`ResourceLocation` → `Identifier`；`ChunkPos` 变成 record；
  `CompoundTag` getter 返回 `Optional`；`ChunkStorage` 移除；渲染管线改为纯 GLSL `RenderPipeline`。

加载器层差异：

- Fabric 的原版 `NoiseBasedChunkGenerator` 是 final，用 access widener 的
  `extendable class` 打开继承；Forge/NeoForge 由加载器补丁去掉 final。
- Fabric 的 `ShaderInstance` 构造器是 `(ResourceProvider, String, VertexFormat)`；
  NeoForge 另有 `ResourceLocation` 重载。
- Forge 的 Mixin 配置通过 `legacyForge { mixin { config ... } }` 和 jar manifest 加载。

## 构建

每个 target 独立构建：

```powershell
cd targets\neoforge-1.21.1
.\gradlew.bat build
```

其他 target 同理，替换目录名即可。JDK 要求：

| Target | JDK |
| --- | --- |
| 1.21.1 | Gradle JVM 21 |
| 1.20.1 / 1.19.2 / 1.18.2 | Gradle JVM 21，toolchain 17 |
| 26.1 | Gradle JVM / toolchain 25 |

`common` 可以单独编译/测试（当前以 NeoForge 1.21.1 为 classpath）：

```powershell
.\gradlew.bat -p common build
```

测试：

```powershell
cd targets\neoforge-1.21.1
.\gradlew.bat test
```

发布：

```powershell
cd targets\<loader>-<mc>
.\gradlew.bat publishMods
```

发布约定在 `gradle/target-conventions/publish.gradle`：按 target 选择 `remapJar`/`reobfJar`/`jar`
作为产物；CurseForge 项目 ID 在根 `gradle.properties`，Modrinth 仅在提供项目 ID 与 token 时启用。

## 新增 target

1. 复制现有 target 的 `build.gradle`、`settings.gradle`、`gradle.properties`、`ci.properties`。
2. 在 `prepareCommonSources` 中登记该版本的 common 排除项，并在 target 内实现同路径类。
3. 实现加载器入口和 `SpatialLoopPlatform`，在构造期安装 SPI。
4. 按对应版本源码移植 Mixin、`ChunkGenerator`、存档 NBT 与客户端实现。
5. 补齐 metadata、mixin 配置、access widener 和资源。
6. 运行 `build`、`test`、`runServer`，确认 `Done` 且无 Mixin apply 错误。

## IDE

- 推荐直接打开 `targets/<loader>-<mc>`；`common` 会作为 composite build 导入。
- 也可以打开仓库根目录；当前根 `settings.gradle` 只引入主 target。
- Gradle JVM：1.21.1/1.20.1/1.19.2/1.18.2 用 JDK 21；26.1 用 JDK 25。

## 架构限制

- `common` 是源码共享，不是二进制库；每个 target 仍要单独编译和测试。
- Mixin 必须按版本维护，不能共享注入点字符串。
- 所有权交接尚未覆盖旧版本 target。
- Forge 生产 jar 的 Mixin refmap 尚未接入。
