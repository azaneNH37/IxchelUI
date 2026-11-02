---
verified: 2026-11-02
by: agent
---

# Ground

## What this is

Ixchel UI：面向 Minecraft 的可嵌入 Web 风格 UI 库（HTML + 标准 CSS 子集 + 客户端数据需求声明），以 shade 进宿主 mod 的方式交付，零全局副作用，宿主拥有实际运行的版本；使用方的替代选项是手写原版 GUI，首要目标是自有 mod 的 UI 基础设施。

## Architecture

```
sample-forge-1.20.1    → ixchel-forge-1.20.1    → ixchel-mc-1.20.1 → ixchel-core
sample-neoforge-1.21.1 → ixchel-neoforge-1.21.1 → ixchel-mc-1.21.1 ↗
ixchel-kit（工具，不进游戏）                                    → ixchel-core
```

依赖单向；可见性由编译期 classpath 强制：core 不含 MC（Java 17，两个平台共用），mc 层只见原版（1.20.1 用 legacyforge `mcpVersion` 模式，1.21.1 用 NeoForm 模式），加载器层可见加载器 API。1.20.1 模块用 Java 17，1.21.1 模块用 Java 21。公共构建配置在 `build-logic` 约定插件中。目标分层与渲染设计见 `ref/SD-ixchel-ui-positioning-and-adapter-design-20261025.md`，模块与包划分见 `ref/D-ixchel-module-breakdown-20261026.md`，数据绑定见 `ref/SD-ixchel-viewmodel-and-binding-20261026.md`，容器槽位接入见 `ref/SDG-ixchel-container-slot-integration-20261026.md`；平台分工见 #005。

元素定义采用消费阶段切面组合（#031）：`html` 装配 core 内置定义，mc 与宿主的类声明转为同一种 `ElementType`；不是 DOM 子类，也不是闭合元素枚举。内部切面跨包可见不表示宿主 API 已开放；宿主 interaction/layout 仍未开放。

目录按层分组，项目名保持扁平（项目名即 artifactId），映射写在 `settings.gradle.kts`：`:ixchel-mc-1.20.1` 位于 `mc/1.20.1/`。

## Modules

- ixchel-core (`core/`): 纯 Java 17 内核，单一模块按包单向分层（#006）。模板/CSS/样式/布局/事件/绑定/动画共用 DOM；元素定义由 `html` 组合消费阶段的切面（#031），表单状态按节点存放、定义跨节点共享。表达式、组件与作用域边界见 #016、#017，主题见 #018，行内排版见 #019，静态与游戏内检查见 #021–#026，i18n 见 #027；能力进度只记在路线图，[draft]
- ixchel-mc-1.20.1 (`mc/1.20.1/`): 仅 vanilla 类的适配层（Forge 1.20.1 完整 MVP），按契约分包，见模块拆解 §4：根包为宿主 API（`UiScreen`、`UiContainerScreen`、`UiHud`，主路径从 ViewModel 的 `@UiTemplate` 取模板，显式 id 重载作出口，均经 `document.HostedDocument` 运行文档，view model 跨重载保留；`UiScreen` 可带父界面、关闭时回到父界面，可作 mod 列表的 `ConfigScreenFactory`）；`element`（`<mc-item>`、`<mc-entity>` 各自带绘制与 tooltip，`title` 文字 tooltip；标签与载荷类型由实现类的 `@NativeElement` 与泛型声明，kit 不初始化类即可读取，#030）；`container`（`<mc-slot index>` 声明在 `ContainerLayout` 类上，写回 `Slot.x/y` 与还原、GUI 区域、hover、可选 `IXCHEL` 逐槽裁剪，#009）；`paint`（`McPainter` 合批回放、按重叠分层定深度，原生元素按 kind 交给 `NativePainter`；`sprite:` 图集精灵；测量器）；`source`（`ClientSources`：mainHand、inventorySlot、inventory、player、polled，`same` 为 ItemStack 感知等值；`WorldProjection`）；`playtest`（游戏内测试，术语见 #026：`Playtests` 按 `-Dixchel.playtest.<modid>` 运行、进开发世界、可选玩家重置、写 `playtests/<modid>/report.txt`、有断言失败时退出码 1；`Playtest` 经原版 `Screen` 输入并加服务端步骤、截图（`playtests/<modid>/screenshots/`）、槽位，断言失败时把打开界面的文档写到 `playtests/<modid>/dumps/`；仅开发期，光标与 GUI 原点按 Mojang 名反射）；根包 `FrameTimer` 在 `UiScreen`/`UiContainerScreen`/`UiHud` 的 `render` 内计时，[draft]
- ixchel-mc-1.21.1 (`mc/1.21.1/`): 仅 vanilla 类的适配层（NeoForge 1.21.1 对齐最小集），包结构与 1.20.1 相同，缺 `container`、`playtest`、`FrameTimer`、`<mc-entity>`、`sprite:`（core 解析放行，绘制分支未实现）；`UiScreen` 同样可带父界面。顶点 API 分叉只在 `McPainter`（`Tesselator.begin` / `addVertex` / `buildOrThrow`），其余是 API 改名（`ResourceLocation.fromNamespaceAndPath`、`isSameItemSameComponents`、`renderBackground`/`mouseScrolled` 新签名），[draft]
- ixchel-forge-1.20.1 (`loader/forge-1.20.1/`): Forge 加载器胶水，只做注册：`IxchelForge.registerHud(modBus, ViewModel 类, Supplier)`（另有显式模板 id 重载）构造并返回 mc 层的 `UiHud`（`show` 可链式），挂到最上层 HUD（层 id 为模板路径去 `.html`）与资源重载监听；首次调用安装 `WorldProjection` 捕获监听；`IxchelForge.installPlaytests(modid, playtests -> …)` 以"tick 结束且其前已画一帧"为 `Pump` 安装游戏内测试，[draft]
- ixchel-neoforge-1.21.1 (`loader/neoforge-1.21.1/`): NeoForge 加载器胶水；`IxchelNeoForge.registerHud` 契约与 `IxchelForge` 相同。HUD 层用 `RegisterGuiLayersEvent.registerAboveAll` 注册，层 id 为 `<宿主 modid>:<模板路径去 .html>`，[draft]
- sample-forge-1.20.1 (`sample/forge-1.20.1/`): Forge 1.20.1 示例兼开发期宿主 mod（modid `ixchel_sample`），不发布。按使用方形态构建（#024）：库以 jar 依赖接入，构建只靠 MDG 与 `com.azane.ixchel` 插件（#025），产物经 shadow + relocate（到 `com.azane.ixchel.sample.shadow`）+ reobf。按普通 mod 布局：`registry/`（DeferredRegister）、`network/`（只放基建：单通道 `ixchel_sample:main` 与集中注册、`Replication` 最小复制层——服务端状态只经它下发，按 kind + 类型化 key 观察（方块坐标、容器 id），按 kind 的间隔采样，快照型发变化的整份值、集合型只发变化的条目，见 #013；各功能的意图包放在功能包内，使其服务端处理方法保持包内私有，见场景文档阶段 B 结果）、`client/`（`KeyBindings` 表、`ClientSetup` 单一 tick 循环、`index/` H 键索引）、每场景一个包与一个公开入口类（`CharacterScreen`、`ConfigScreen`、`EnvHud`、`Guide`、`LootScreens`、`MachineScreen`、`QuestsScreen`、`TerminalScreen`、`WaypointsScreen`）；模板在 `ui/<package>/<stem>.{html,css}`。游戏内测试是 `src/playtest` 的第二个 mod `ixchel_sample_playtest`（不打包），仅 `-Pplaytest` 时加载，只经业务的公开入口与网络包驱动，`src/main` 不引用它；`playtest` 包含经 `IxchelForge.installPlaytests` 安装的入口与每场景一个 `*Playtest` 类。现有示例：环境 HUD（S5，含罗盘与路径点）、角色面板（S1，G）、机器面板（S2，动力切石机方块，状态走复制层而非 `ContainerData`）、存储终端（S3，终端物品右键，存储走复制层集合型 kind）、掉落物雷达 HUD 与列表（S4，U）、路径点界面（J）、配置面板（S6，编辑 sample 的真实客户端配置 `SampleConfig`，mod 列表 Config 按钮或索引打开）、任务树（S7，索引打开）、指南书（S8，索引打开，条目是 `ui/guide/entries/` 下的模板）。场景计划、阶段进度与帧耗时见 `ref/SD-ixchel-sample-scenarios-20261028.md` §3，[draft]
- sample-neoforge-1.21.1 (`sample/neoforge-1.21.1/`): NeoForge 1.21.1 示例（modid `ixchel_sample`），同样按使用方形态、经插件构建（shade + relocate，不 reobf），无游戏内测试。只有弹药 HUD 与 Z 键改装界面，作为 1.21.1 对齐最小集的验收（#005）。改装数据放在 `CUSTOM_DATA` 组件，网络用 `CustomPacketPayload`，[draft]
- ixchel-kit (`tools/kit/`): 游戏外静态检查工具，只依赖 core，Java 17，随库发布；CLI `lint`（结构 + 类型，`file:line:col` + 错误码 + hint，`--json`，有错误退出 1）、`schema`、`explain <码>`，仅项目内入口（宿主的 JavaExec 由插件注册为 `ixchelLint`，classpath 只放 kit，参数只有源码资源目录 `--resources` 与宿主运行时 classpath `--classpath`；classpath 中的目录视为宿主自己的编译输出，在其中扫描 `@UiTemplate`，jar 为依赖）。包：`project`（资源根、自建类加载器、`@UiTemplate` 扫描、原生元素声明、组件文件）、`lint`、`schema`。宿主类经自建的不校验签名的类加载器读取：ModDevGradle 打过补丁的 Forge jar 保留原签名，JDK 加载器会拒绝（SHA-384 digest error）。没有注解认领的页面按无模型检查并去掉 `binding/unknown-name`，另报 `model/unassociated-template`。两个 sample 的 `ixchelLint` 挂在 `check` 上，[draft]
- ixchel-gradle-plugin (`tools/gradle-plugin/`): Gradle 插件 `com.azane.ixchel`（#025），included build，Java，只支持 MDG legacyforge（Forge 1.20.1）与 MDG neoforge（NeoForge 1.21.1）；`IxchelExtension` 承担全部接线，Forge 1.20.1 专有的重映射与 reobf 在 `LegacyForge`，只在该分支加载。库版本与 taffy 坐标由构建生成的 `build.properties` 内嵌。根构建的 `build`、`publishToMavenLocal` 带上它。

## In progress

- 双平台构建骨架已就绪（`build` 与两个 sample 的 `runClient` 通过）；M1–M5 完成，结果见 `ref/D-ixchel-module-breakdown-20261026.md` §9 与 `ref/SDG-ixchel-container-slot-integration-20261026.md` §8。`@keyframes` 未做。圆角路径 C 已落地（V1）：每个宿主 modid 一张遮罩图集（`<modid>:ixchel/mask_atlas`）；`<img>` 未入图集，仍须在 CSS 中写宽高。M1 已知限制见模块拆解 §3。M5 及之后的示例只在 Forge 1.20.1 侧。
- 生产环境实例（portablemc，Forge 1.20.1-47.3.19）已验证两件事：两个不同版本的 shade 副本共存；与 ImmediatelyFast 1.5.5 共存。方法见模块拆解 §9 "M4 shade"。
- 2026-10-28 宿主 API 重做（单路径模板引用、`<link>`、键过滤、`registerHud` 收口、ItemStack 等值）与 sample 普通 mod 布局重构已合入（#47）。refit/手写原版对照基准已删除，性能基线由 S4/S5 游戏内测试的 `FrameTimer` 预算承担。
- 样例场景阶段 0、A、B、C 完成（C1：S3 存储终端、文本输入、虚拟网格、按重叠分层；C2：S6 配置面板、表单控件、顶层与对话框、Tab 焦点），结果见 `ref/SD-ixchel-sample-scenarios-20261028.md` §3。
- 后续顺序与状态只在 `ref/D-ixchel-roadmap-20261029.md`：D0、L1、D、L2、L3、E1、T1（静态检查：`@UiTemplate`、错误码、`ixchel-kit`）、T2（游戏内测试提取进库，仅 1.20.1）、E2（`<ix-tooltip>`、`<mc-tooltip-lines>`，S9）、I（i18n：`t`、`t:<属性>`、`mark`，见 `ref/SD-ixchel-i18n-20261102.md`）、U1–U3（`text-shadow`、`<for index>`、点击音、滚动条；拖拽与 `<ix-drag-preview>`；宿主原生元素，#030）完成 → V1–V3、U4、FX，Doc 分步写，工具方案见 `ref/SD-ixchel-tooling-and-editor-20261031.md`（#021–#023）。能力覆盖与排序依据见 `ref/S-ixchel-ui-capability-comparison-20261102.md`。模板与样式语言的目标形态见 `ref/SD-ixchel-template-and-style-language-20261029.md`，上下文模型与组件设计见 `ref/SD-ixchel-context-model-and-components-20261030.md`（#016）；表达式、`on:` 语句与 `state:`（§3.6）、组件、控制块、样式分层（L2）、主题与 `var()`（L3）、行内排版（E1）、文档内 tooltip（E2）以及模板 i18n（I）已实现。
- sample 调试参数：`-Pslotcompat=jei|emi` 加载 JEI 或 EMI 加 Mouse Tweaks（槽位 L2，配合 `-Pplaytest=machine`）。`-Pplaytest=<名>[,…]|all`（无值即 `all`）加载 `ixchel_sample_playtest` 并设 `-Dixchel.playtest.ixchel_sample`，一次启动按顺序跑所选测试项（库内 `Playtests`/`Playtest`，两步之间必先画一帧；每个测试项前统一重置玩家状态），日志输出 `[ixchel-playtest] <测试项> <断言>: PASS|FAIL`，结尾 `[ixchel-playtest] ixchel_sample done: <n> playtests, <p> passed, <f> failed`（计数为断言），汇总写 `run/playtests/ixchel_sample/report.txt`，失败转储写同目录 `dumps/`，截图写同目录 `screenshots/`，结束后退出，有断言失败时进程退出码 1、`runClient` 失败。运行先关闭新手教程（只改内存中的 `tutorialStep`），再从标题界面进入 `New World`（不存在则新建创造超平坦）。`--quickPlaySingleplayer` 在存档目录为空时直接失败，不用它。
- CI：`check`（`.github/workflows/check.yml`，PR 与 `master` 上全量 `./gradlew build`）已上线，`master` ruleset 要求它通过，admin 可在 PR 上用 `--admin` 越过；`release` 与 `docs` 流程未实现。见 #010 与 `ref/D-ixchel-ci-workflows-20261026.md`。
- 1.20.1 库模块发布 SRG 产物（#011），消费方的重映射与 reobf 由插件完成。legacyforge 发布的 `reobfRuntimeElements`/`reobfApiElements` 变体在 `.module` 中不声明依赖；MDG 重映射本就不传递，插件逐个列出 forge、mc 与 core，故未修。
- 发布：根项目 `publishToMavenLocal` 同时发布库模块与插件（included build `gradle-plugin`），还没有远程仓库。
- sample 开发期以 jar 依赖运行库，其产物即 shade 链路；仓库内不再保留第二个 shade 宿主，两份副本共存只有上面那次生产验证。新的 sample jar 尚未放进生产实例运行。

## Not here

- 不是独立安装的 mod：没有 modid、没有自己的入口，初始化由宿主驱动。
- 默认产物不含 mixin、不注册全局 ID、不携带固定 namespace 的 `assets/`、不改主 render target。
- 不内嵌 JS 引擎，不追求浏览器渲染等价。
- 不含复制层或意图 RPC；外部数据来源由宿主接入。
- 不依赖 jsoup；模板由自写严格解析器解析 (#007)。
- 前身 Kumiko 仅为研究过渡项目，不是依赖或对接对象。
