---
verified: 2026-10-30
by: agent
---

# Ground

## What this is

Ixchel UI：面向 Minecraft 的可嵌入 Web 风格 UI 库（HTML + 标准 CSS 子集 + 客户端数据需求声明），以 shade 进宿主 mod 的方式交付，零全局副作用，宿主拥有实际运行的版本；使用方的替代选项是手写原版 GUI，首要目标是自有 mod 的 UI 基础设施。

## Architecture

```
sample-forge-1.20.1    → ixchel-forge-1.20.1    → ixchel-mc-1.20.1 → ixchel-core
testmod-neoforge-1.21.1 → ixchel-neoforge-1.21.1 → ixchel-mc-1.21.1 ↗
```

依赖单向；可见性由编译期 classpath 强制：core 不含 MC（Java 17，两个平台共用），mc 层只见原版（1.20.1 用 legacyforge `mcpVersion` 模式，1.21.1 用 NeoForm 模式），加载器层可见加载器 API。1.20.1 模块用 Java 17，1.21.1 模块用 Java 21。公共构建配置在 `build-logic` 约定插件中。目标分层与渲染设计见 `ref/SD-ixchel-ui-positioning-and-adapter-design-20261025.md`，模块与包划分见 `ref/D-ixchel-module-breakdown-20261026.md`，数据绑定见 `ref/SD-ixchel-viewmodel-and-binding-20261026.md`，容器槽位接入见 `ref/SDG-ixchel-container-slot-integration-20261026.md`；平台分工见 #005。

目录按层分组，项目名保持扁平（项目名即 artifactId），映射写在 `settings.gradle.kts`：`:ixchel-mc-1.20.1` 位于 `mc/1.20.1/`。

## Modules

- ixchel-core (`core/`): 纯 Java 内核（模板/CSS/样式/布局/事件/绑定/动画），单一模块内按包分层 (#006)，Java 17；M1 原型完成（模板→样式→布局→DisplayList，弹药 HUD 黄金测试通过），binding 已有 source/state/derive、action/task、Scope/subscribe、`<for>`/`<if>` 控制块、模板组件（参数、事件、插槽，见上下文模型文档 §3）、模板表达式与 `on:` 语句、`state:` 局部状态（语言文档 §3.6），style 有 UA、组件、主题、作者四层与 `var()` 自定义属性，库内主题 `@ixchel/vanilla.css`、`@ixchel/dark.css` 由 `runtime.LibrarySheets` 从 classpath 读取（#018），按契约分为模型 API `binding`、表达式语言 `binding.expr`、模板绑定 `binding.compile`，后者经 `ModelAccess` 访问模型内部（#017），event 已有命中测试（按 `transform` 逆变换）与三阶段分派、`mousemove` 与 `on:` 里的 `$event.*`、`<input>` 文本编辑（剪贴板经 `Clipboard` 端口）与修饰键精确过滤（#012）、Tab 焦点、`change`/`close` 事件，binding 另有双向 `bind:value`、`bind:checked`、`bind:open`、`bind:scrolltop`，`<for>` 行里的 `Value` 字段也可双向；表单控件（`dom.FormControl`：text/number/checkbox/range/select，编辑在 `event.FormEditing`）与文档顶层（`dom.TopLayer`：展开的 `<select>` 列表与 `<dialog>`，#014），默认外观在 `runtime/user-agent.css`，样式按 UA、组件、作者三层层叠，CSS 嵌套在解析期展开；选择器有属性选择器与 `:checked`/`:open`；`transform`/`transform-origin` 只作用于绘制与命中（`DrawCommand.PushTransform`）；anim 已有 transition（`@keyframes` 未做，限制见模块拆解 §9 M3 结果），[draft]
- ixchel-mc-1.20.1 (`mc/1.20.1/`): 仅 vanilla 类的适配层（Forge 1.20.1 完整 MVP），按契约分包，见模块拆解 §4：根包为宿主 API（`UiScreen`、`UiContainerScreen`、`UiHud`，均经 `document.HostedDocument` 运行文档，view model 跨重载保留；`UiScreen` 可带父界面、关闭时回到父界面，可作 mod 列表的 `ConfigScreenFactory`）；`element`（`<mc-item>`、`<mc-entity>` 各自带绘制与 tooltip，`title` 文字 tooltip）；`container`（`<mc-slot index>` 写回 `Slot.x/y` 与还原、GUI 区域、hover、可选 `IXCHEL` 逐槽裁剪，#009）；`paint`（`McPainter` 合批回放、按重叠分层定深度，原生元素按 kind 交给 `NativePainter`；`sprite:` 图集精灵；测量器）；`source`（`ClientSources`：mainHand、inventorySlot、inventory、player、polled，`same` 为 ItemStack 感知等值；`WorldProjection`），[draft]
- ixchel-mc-1.21.1 (`mc/1.21.1/`): 仅 vanilla 类的适配层（NeoForge 1.21.1 对齐最小集），包结构与 1.20.1 相同，缺 `container`、`<mc-entity>`、`sprite:`；`UiScreen` 同样可带父界面。顶点 API 分叉只在 `McPainter`（`Tesselator.begin` / `addVertex` / `buildOrThrow`），其余是 API 改名（`ResourceLocation.fromNamespaceAndPath`、`isSameItemSameComponents`、`renderBackground`/`mouseScrolled` 新签名），[draft]
- ixchel-forge-1.20.1 (`loader/forge-1.20.1/`): Forge 加载器胶水，只做注册：`IxchelForge.registerHud(modBus, template, model)` 构造并返回 mc 层的 `UiHud`（`show` 可链式），挂到最上层 HUD（层 id 为模板路径去 `.html`）与资源重载监听；首次调用安装 `WorldProjection` 捕获监听，[draft]
- ixchel-neoforge-1.21.1 (`loader/neoforge-1.21.1/`): NeoForge 加载器胶水；`IxchelNeoForge.registerHud` 契约与 `IxchelForge` 相同。HUD 层用 `RegisterGuiLayersEvent.registerAboveAll` 注册，层 id 为 `<宿主 modid>:<模板路径去 .html>`，[draft]
- sample-forge-1.20.1 (`sample/forge-1.20.1/`): Forge 1.20.1 示例兼开发期宿主 mod（modid `ixchelsample`），库模块源码并入其中运行，不发布。按普通 mod 布局：`registry/`（DeferredRegister）、`network/`（只放基建：单通道 `ixchelsample:main` 与集中注册、`Replication` 最小复制层——服务端状态只经它下发，按 kind + 类型化 key 观察（方块坐标、容器 id），按 kind 的间隔采样，快照型发变化的整份值、集合型只发变化的条目，见 #013；各功能的意图包放在功能包内，使其服务端处理方法保持包内私有，见场景文档阶段 B 结果）、`client/`（`KeyBindings` 表、`ClientSetup` 单一 tick 循环、`index/` H 键索引）、每场景一个包与一个公开入口类（`CharacterScreen`、`ConfigScreen`、`EnvHud`、`LootScreens`、`MachineScreen`、`TerminalScreen`、`WaypointsScreen`）；模板在 `ui/<package>/<stem>.{html,css}`。无人值守检查是 `src/check` 的第二个 mod `ixchelsamplecheck`，仅 `-Pcheck` 时加载，只经业务的公开入口与网络包驱动，`src/main` 不引用它；`check` 包为检查框架（`Checks`、`Script`、`FrameTimer`、`BenchWorld`），`check.scenario` 为各场景检查。现有示例：环境 HUD（S5，含罗盘与路径点）、角色面板（S1，G）、机器面板（S2，动力切石机方块，状态走复制层而非 `ContainerData`）、存储终端（S3，终端物品右键，存储走复制层集合型 kind）、掉落物雷达 HUD 与列表（S4，U）、路径点界面（J）、配置面板（S6，编辑 sample 的真实客户端配置 `SampleConfig`，mod 列表 Config 按钮或索引打开）。场景计划、阶段进度与帧耗时见 `ref/SD-ixchel-sample-scenarios-20261028.md` §3，[draft]
- testmod-neoforge-1.21.1 (`testmod/neoforge-1.21.1/`): NeoForge 1.21.1 开发期宿主 mod，同上。只有弹药 HUD 与 Z 键改装界面（Forge 侧的对应示例已删除，这里保留作为 1.21.1 对齐最小集的验收）。改装数据放在 `CUSTOM_DATA` 组件，网络用 `CustomPacketPayload`，[draft]
- testmod-shade-{a,b}-forge-1.20.1 (`testmod/shade-{a,b}-forge-1.20.1/`): shade 验证用的两个宿主 mod（modid `ixchelshadea`/`ixchelshadeb`），各自 shade 并 relocate 一份 Ixchel 到 `com.azane.shadetest.{a,b}.shadow`，再 reobf。`build` 会编译、打包它们；B 加 `-PshadeIxchel=<版本>` 时改从 mavenLocal 取已发布版本，不发布，[draft]

## In progress

- 双平台构建骨架已就绪（`build` 与 sample、testmod 的 `runClient` 通过）；M1–M5 完成，结果见 `ref/D-ixchel-module-breakdown-20261026.md` §9 与 `ref/SDG-ixchel-container-slot-integration-20261026.md` §8。`@keyframes` 未做。图片图集推迟到圆角路径 C 落地（两者共用一张图集），`<img>` 仍须在 CSS 中写宽高。M1 已知限制见模块拆解 §3。M5 及之后的示例只在 Forge 1.20.1 侧。
- 生产环境实例（portablemc，Forge 1.20.1-47.3.19）已验证两件事：两个不同版本的 shade 副本共存；与 ImmediatelyFast 1.5.5 共存。方法见模块拆解 §9 "M4 shade"。
- 2026-10-28 宿主 API 重做（单路径模板引用、`<link>`、键过滤、`registerHud` 收口、ItemStack 等值）与 sample 普通 mod 布局重构已合入（#47）。refit/手写原版对照基准已删除，性能基线由 S4/S5 检查的 `FrameTimer` 预算承担。
- 样例场景阶段 0、A、B、C 完成（C1：S3 存储终端、文本输入、虚拟网格、按重叠分层；C2：S6 配置面板、表单控件、顶层与对话框、Tab 焦点），结果见 `ref/SD-ixchel-sample-scenarios-20261028.md` §3。
- 后续顺序与状态只在 `ref/D-ixchel-roadmap-20261029.md`：D0、L1、D、L2、L3 完成 → E（S8、S9）/ I（i18n）/ Doc（用户文档）。模板与样式语言的目标形态见 `ref/SD-ixchel-template-and-style-language-20261029.md`，上下文模型与组件设计见 `ref/SD-ixchel-context-model-and-components-20261030.md`（#016）；表达式、`on:` 语句与 `state:`（§3.6）以及组件、控制块、样式分层（L2）、主题与 `var()`（L3）已实现。
- sample 调试参数：`-Pslotcompat=jei|emi` 加载 JEI 或 EMI 加 Mouse Tweaks（槽位 L2，配合 `-Pcheck=machine`）。`-Pcheck=<名>[,…]|all`（无值即 `all`）加载 `ixchelsamplecheck` 并一次启动按顺序跑功能检查（`Checks` 注册，`Script` 按选择器驱动，两步之间必先画一帧；每项检查前统一重置玩家状态），日志输出 `[ixchel-bench] <名> check <项>: PASS|FAIL`，汇总写 `run/checks/report.txt`，截图到 `run/screenshots/`，结束后退出。由 `BenchWorld` 进入 `New World`（不存在则新建创造超平坦）。`--quickPlaySingleplayer` 在存档目录为空时直接失败，不用它。
- CI：`check`（`.github/workflows/check.yml`，PR 与 `master` 上全量 `./gradlew build`）已上线，`master` ruleset 要求它通过，admin 可在 PR 上用 `--admin` 越过；`release` 与 `docs` 流程未实现。见 #010 与 `ref/D-ixchel-ci-workflows-20261026.md`。
- 1.20.1 库模块发布 SRG 产物（#011），消费方配方见 `testmod/shade-b-forge-1.20.1/build.gradle.kts`。
- legacyforge 发布的 `reobfRuntimeElements`/`reobfApiElements` 变体在 `.module` 中不声明任何依赖，`api(core)` 关系丢失。消费方须逐个列出 forge、mc（经 `obfuscation.createRemappingConfiguration` 重映射回 mojmap）与 core（不重映射）。
- testmod 以 sourceSet 并入方式运行库，不经过 shade/relocate；shade 链路由 testmod-shade-a/b 覆盖。CI 只覆盖到构建，游戏内共存靠手动的生产环境验证。

## Not here

- 不是独立安装的 mod：没有 modid、没有自己的入口，初始化由宿主驱动。
- 默认产物不含 mixin、不注册全局 ID、不携带固定 namespace 的 `assets/`、不改主 render target。
- 不内嵌 JS 引擎，不追求浏览器渲染等价。
- 不含复制层或意图 RPC；外部数据来源由宿主接入。
- 不依赖 jsoup；模板由自写严格解析器解析 (#007)。
- 前身 Kumiko 仅为研究过渡项目，不是依赖或对接对象。
