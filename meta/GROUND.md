---
verified: 2026-10-28
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

- ixchel-core (`core/`): 纯 Java 内核（模板/CSS/样式/布局/事件/绑定/动画），单一模块内按包分层 (#006)，Java 17；M1 原型完成（模板→样式→布局→DisplayList，弹药 HUD 黄金测试通过），binding 已有 source/state/derive、action/task、Scope/subscribe、each/if（见绑定文档 §5），event 已有命中测试与三阶段分派，anim 已有 transition（`@keyframes` 未做，限制见模块拆解 §9 M3 结果），[draft]
- ixchel-mc-1.20.1 (`mc/1.20.1/`): 仅 vanilla 类的适配层（Forge 1.20.1 完整 MVP）；已有 `McPainter`（矩形、图片、文本、`<item>`）、`McTextMeasurer`、`McResources`、`McElements`、`UiHud`、`UiScreen`（整屏 UI，输入先交文档，关闭即关 view model）、`UiContainerScreen`（`<item-slot>` 布局写回 `Slot.x/y`，关闭时还原；槽位物品绘制可选 `IXCHEL` 逐槽裁剪，#009）、`ItemTooltips`、`WorldProjection`（世界坐标→GUI）、`ClientSources`（mainHand、inventorySlot、inventory、player），[draft]
- ixchel-mc-1.21.1 (`mc/1.21.1/`): 仅 vanilla 类的适配层（NeoForge 1.21.1 对齐最小集）；由 mc/1.20.1 复制后改写，类与契约相同。顶点 API 分叉只在 `McPainter`（`Tesselator.begin` / `addVertex` / `buildOrThrow`），其余是 API 改名（`ResourceLocation.fromNamespaceAndPath`、`isSameItemSameComponents`、`renderBackground`/`mouseScrolled` 新签名），[draft]
- ixchel-forge-1.20.1 (`loader/forge-1.20.1/`): Forge 加载器胶水；已有 `IxchelForge.registerHud`（资源重载时重新加载文档，HUD 层置于最上；开发期热重载与错误叠加层，见模块拆解 §8 L1），[draft]
- ixchel-neoforge-1.21.1 (`loader/neoforge-1.21.1/`): NeoForge 加载器胶水；已有 `IxchelNeoForge.registerHud`，契约与 `IxchelForge` 相同。HUD 层用 `RegisterGuiLayersEvent.registerAboveAll` 注册，层 id 为 `<宿主 modid>:<layerId>`，[draft]
- sample-forge-1.20.1 (`sample/forge-1.20.1/`): Forge 1.20.1 示例兼开发期宿主 mod（modid `ixchelsample`），库模块源码并入其中运行，不发布。H 键打开示例索引（`Samples.all()` 登记）；现有示例：改装（Z）、背包改装（容器）、状态 HUD、拾取提示 HUD、路径点界面；另有 `probe/` 槽位探针与 `perf/` 基准。后续场景与迁移计划见 `ref/SD-ixchel-sample-scenarios-20261028.md`，[draft]
- testmod-neoforge-1.21.1 (`testmod/neoforge-1.21.1/`): NeoForge 1.21.1 开发期宿主 mod，同上。只有弹药 HUD 与 Z 键改装界面，模板、样式和贴图与 Forge 侧相同。改装数据放在 `CUSTOM_DATA` 组件，网络用 `CustomPacketPayload`；没有 V 键原版对照、`-Pbench`、`-Pcompat`，[draft]
- testmod-shade-{a,b}-forge-1.20.1 (`testmod/shade-{a,b}-forge-1.20.1/`): shade 验证用的两个宿主 mod（modid `ixchelshadea`/`ixchelshadeb`），各自 shade 并 relocate 一份 Ixchel 到 `com.azane.shadetest.{a,b}.shadow`，再 reobf。`build` 会编译、打包它们；B 加 `-PshadeIxchel=<版本>` 时改从 mavenLocal 取已发布版本，不发布，[draft]

## In progress

- 双平台构建骨架已就绪（`build` 与 sample、testmod 的 `runClient` 通过）；M1–M5 完成，结果见 `ref/D-ixchel-module-breakdown-20261026.md` §9 与 `ref/SDG-ixchel-container-slot-integration-20261026.md` §8。`@keyframes` 未做。图片图集推迟到圆角路径 C 落地（两者共用一张图集），`<img>` 仍须在 CSS 中写宽高。M1 已知限制见模块拆解 §3。M5 及之后的示例只在 Forge 1.20.1 侧。
- 生产环境实例（portablemc，Forge 1.20.1-47.3.19）已验证两件事：两个不同版本的 shade 副本共存；与 ImmediatelyFast 1.5.5 共存。方法见模块拆解 §9 "M4 shade"。
- sample 调试参数：`-Pcompat` 加载 Oculus + Embeddium；`-Pslotcompat=jei|emi` 加载 JEI 或 EMI 加 Mouse Tweaks（槽位 L2）。`-Pbench`（无值即 `hud`）只用于计时基准：`hud`（`HudBench` 交替计时截图）与 `refit`（`RefitBench` 对比 Ixchel 与手写原版改装界面）。`-Pcheck=<名>[,…]|all`（无值即 `all`）一次启动按顺序跑功能检查（`check/Checks` 注册，`check/Script` 按选择器驱动，两步之间必先画一帧；每项检查前统一重置玩家状态），日志输出 `[ixchel-bench] <名> check <项>: PASS|FAIL`，汇总写 `run/checks/report.txt`，截图到 `run/screenshots/`，结束后退出。两者都由 `BenchWorld` 进入 `New World`（不存在则新建创造超平坦）。`--quickPlaySingleplayer` 在存档目录为空时直接失败，不用它。
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
