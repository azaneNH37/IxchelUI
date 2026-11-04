---
tags: [survey, design]
date: 2026-10-31
origin: E1 的 S8 模板三轮加载错误都要进游戏（每轮约 50 s）才暴露；agent 与人写 UI 时需要运行前校验与游戏内验证，先定工具的分层、模板与模型的关联方式、位置与分发
related: [ref/D-ixchel-roadmap-20261029.md, ref/SD-ixchel-template-and-style-language-20261029.md, ref/SD-ixchel-context-model-and-components-20261030.md, ref/D-ixchel-ci-workflows-20261026.md, DECISIONS.md#004, DECISIONS.md#010, DECISIONS.md#021, DECISIONS.md#022, DECISIONS.md#023]
---

# Ixchel UI: Tooling, In-game Playtests and Editor

## Summary

游戏外只做静态检查，运行时验证一律在游戏内，不做无头运行（#022）。静态检查由只依赖 core 的 `ixchel-kit`（`tools/kit`）提供：lint（结构 + 类型）与 schema，类型检查的输入是 ViewModel 类上的 `@UiTemplate` 与类反射得到的成员表（#021），编译器改为读成员表；诊断带稳定的符号错误码（#023）；原生元素声明改为 mc 层的数据文件。游戏内测试（playtest）从 sample 提取进库：core 驱动、mc 层输入与转储、加载器层两个钩子，开发期属性开启。命名：游戏外一律称 lint，游戏内一律称 playtest（#026）。可视化编辑器排在最后，Node 从那一步才引入。

## Detail

### 1. 起因

E1 写 S8 时三轮失败都是结构错误：组件文件含多个 `<template>`、组件文件链接 `@ixchel/dark.css`、参数名 `title` 与外壳元素属性冲突（及连带的"未知标签"）。它们不需要游戏，却只有进游戏加载才报出。agent 手写模板最常见的错误是写出子集外的标签、属性与 CSS，其次是绑定名与类型；交互、原版路径、服务端与像素问题只能在游戏内验证。

### 2. 分层

按依赖划分工具能做的事：

| 层 | 依赖 | 能做 |
|---|---|---|
| α | core + 只读资源 | 结构 lint、schema、布局与绘制回放（编辑器） |
| β | + 宿主编译产物（ViewModel 类） | 绑定名与类型检查 |
| δ | 运行中的游戏 | 交互、原版路径、服务端、槽位、像素、帧预算 |

不做无头运行（在游戏外实例化 ViewModel、跑 binding 与事件）：它要求宿主把 ViewModel 与 MC 类解耦，代价落在作者的架构上，能覆盖的范围又被 β 与 δ 夹住（#022）。

| | 游戏外静态检查（lint） | 游戏内测试（playtest） |
|---|---|---|
| 提供者 | `ixchel-kit` | 库（core + mc + 加载器层） |
| 宿主接入 | 一个 JavaExec 任务挂在 `check` 上 | 一个开发期 sourceSet（自己的 `@Mod`，注册场景）+ run 配置属性 |
| 耗时 | 秒级 | 一次启动约 1 分钟，跑全部场景 |

不考虑云端 agent，只面向本地 agent（如 Claude Code）与人。

### 3. 模板与 ViewModel 的关联（#021）

现状：关联只在运行时、按实例、按名字建立（`new UiScreen(template, model)` 等，`Compiler.pageNames` 反射实例字段，`Compiler.java:958`）。静态检查需要事先知道"哪个模板配哪个类"。

```java
@UiTemplate("examplemod:ui/waypoints/list.html")              // String[] value()，完整资源 id
final class WaypointsView extends ViewModel { ... }

@UiTemplate("examplemod:ui/guide/entries/*.html")             // 通配只作用于路径部分
final class GuideView extends ViewModel { ... }

new UiScreen(new WaypointsView());   // 主路径：注解须恰好一个具体 id
new UiScreen(id, view);              // 显式 id：通配、多模板，也是出口
new UiScreen(id);                    // 无 ViewModel
IxchelForge.registerHud(modBus, EnvView.class, EnvView::new);  // HUD：类显式给出
```

- 注解值用完整 id（`MODID + ":ui/..."` 是合法的编译期常量）；不支持命名空间通配 `*:`。其它命名空间的 id 语法上允许、运行时可用，lint 只查本项目资源根，其余标为未检查。
- 出口：显式 id 不在注解范围内、或类无注解时照常加载（运行时绑定检查不变），开发期警告"该组合未经静态检查"。正确性不靠注解，注解只决定静态覆盖面。真实需要出口的情形：模板 id 来自数据（整合包作者在自己的命名空间写页面）；通用 ViewModel 不愿为每个界面派生空子类。
- 资源包覆盖同一 id 不受影响。跨 mod 复用 ViewModel 不支持也无需求：各 mod 的副本 relocate 后类型不通（#004）。
- `models.json`（各 ViewModel 的成员表）不再手写，改为构建时由注解与类生成、随 jar 导出，供资源包作者的 lint 使用（T3）。

### 4. 编译器读成员表

`Compiler` 不再直接反射实例，改读成员表：名字 → 种类（Cell / Action）、声明类型（含泛型参数）、可写性。

| 成员表来源 | 用途 |
|---|---|
| 实例反射 | 运行时，行为不变 |
| 类反射 | lint（`Class.forName(n, false, loader)`，不触发初始化） |
| JDK `Map` | 预留：附属 mod 扩展点的"表构造模型"（§10），现在不做 |

静态与运行时只差两处，都留给运行时：

- 字段声明为 `Cell` 而实际装的是 `Value`：静态只见声明类型，规则是"字段声明写精确类型"，开发期运行时对不一致给警告。
- 字段为 `null`（`Name.Unset`）：运行时状态，照旧在运行时报。

record 成员本来就按声明的组件类型解析（`Compiler.java:912-943`），无需改动。

### 5. 原生元素声明（#030）

声明留在元素的 Java 实现类上，kit 用 `Class.forName(name, false, loader)` 读取注解与泛型，不创建元素实现、不运行游戏初始化。运行时与 kit 调用同一个声明适配器，构造同一种切面组合定义（#031）。

- 自绘元素：实现类声明 `@NativeElement(tag = "mc-item", payload = "item")`，实现 `NativePainter<ItemStack>`；绘制与 tooltip 同属该类。
- 原版槽位：`ContainerLayout` 声明 `@NativeElement(tag = "mc-slot", attributes = "index", containerOnly = true)`，不伪造绘制载荷。
- 文本行：不是原生元素。`<mc-tooltip-lines>` 为 mc 层库 Java 组件（#038），kit 经库的预引入表认识它。

载荷类型只写在泛型参数里；绑定接受 `T` 或 `Supplier<T>`。`containerOnly` 供 kit 限制适用界面；宿主注册仍按 modid 分开。声明格式不再要求作者维护资源文件，也不维护 kit 专用副本。内部布局与交互切面暂不向宿主开放。

### 6. 诊断错误码（#023）

现状：`Diagnostic(severity, message, pos)`，严重级别由调用点决定；core 约 195 个报错调用点（css 76、binding 57、template 54、style 5，粗略 grep）；测试多处断言消息原文。

```java
public interface DiagCode {
    String id();                     // "binding/unknown-name"
    Diagnostic.Severity severity();
    String summary();                // 英文一句话，供 --explain 与文档生成
}
public record Diagnostic(DiagCode code, String message, SourcePos pos, String hint) { }  // hint 可空

public enum BindingCode implements DiagCode {   // 放在报告它的包里
    UNKNOWN_NAME("binding/unknown-name", ERROR, "the name is not a member of the view model"), ...
}
diag.error(BindingCode.UNKNOWN_NAME, pos, msg);
```

| 方面 | 规则 |
|---|---|
| 形式 | 符号名 `领域/名称`，全英文（码、消息、summary、hint） |
| 领域 | 按加载阶段：`load`、`template`、`i18n`（`t` 展开与译文检查）、`css`、`style`、`binding`、`element`（mc 层定义）、`model`（注解与成员表） |
| 定义处 | 每领域一个实现 `DiagCode` 的 enum，放在报告它的包里；mc 层自定义，core 不需知道 |
| 严重级别 | 由码决定，不由调用点决定；暂不可配置 |
| 粒度 | 一种作者可改正的错误一个码，估计 60–90 个；库内部不变式被破坏是 bug，抛异常，不作诊断 |
| 输出 | 文本 `file:line:col: error[binding/unknown-name]: message`，下一行 `  hint: …`；JSON `{code, severity, file, line, col, message, hint}` |
| 稳定性 | 码是公开契约：不改名、不复用，废弃保留占位；消息原文不是契约 |
| 校验 | 一个测试扫描所有 enum 保证 id 唯一 |
| 文档 | 由注册表生成每个码的页面（#010 参考页由代码生成）；kit 提供 `--explain <code>` |
| 测试迁移 | 断言消息原文的测试改为断言码与位置；`DiagnosticsTest` 测输出格式本身，保留 |

`Diagnostics.within(note)` 不变，组件上下文仍追加在消息后。

### 7. ixchel-kit

| | 内容 |
|---|---|
| 制品 | `:ixchel-kit`（`tools/kit/`），只依赖 core，与库同版本；T1 普通 jar，T3 再出 fat jar |
| 项目内（主入口） | 宿主写 JavaExec：classpath 只放 kit，宿主运行时 classpath 经 `--classpath` 传入（其中的目录即宿主编译输出，在其中找 ViewModel），源码资源目录经 `--resources` 传入；读 `@UiTemplate` 构建成员表；原生元素声明从 mc 制品读，无需配置目标版本 |
| 项目外（T3） | `java -jar`：结构检查与 schema，`--target` 选内置声明；资源包作者的类型检查用导出的 `models.json` |
| `lint` | 结构 + 类型，`file:line:col` + 错误码 + hint，`--json`；有错误退出码非 0。注解列出的模板不存在报错；没有注解认领的模板只做结构检查并提示"未关联模型" |
| `schema` | 当前目标的标签、属性、CSS 属性与取值；主题变量 `--ix-*`；本项目组件（参数、事件、插槽）；各 ViewModel 成员表 |
| `explain <码>` | 错误码说明 |

包按契约：`project`（资源根、宿主 classpath、注解扫描、声明读取、成员表构建）、`lint`、`schema`，入口 `Main`。

lint 任务由 Gradle 插件 `com.azane.ixchel` 注册为 `ixchelLint` 并挂在 `check` 上（#025、#026）；两个 sample 应用同一个插件，不收进 `build-logic` 约定插件：自家用的就是外部作者的写法，坏了第一时间暴露。

### 8. 游戏内测试（playtest）

现状：sample 的 `src/check`（`Script` 约 440 行、`Checks`、`BenchWorld`、`FrameTimer` 与 10 个场景）只服务 sample；`Checks.finish()` 调 `mc.stop()`，退出码不反映失败；testmod-neoforge-1.21.1 没有检查框架。

| 层 | 内容 |
|---|---|
| core（`core.playtest`） | `PlaytestDriver`：步骤队列、选择器操作、读取、断言记录（`record`）；`PlaytestRun`：按名选择、汇总文本（`summary`）；端口 `InputPort`、`Pump`；帧预算断言 `FrameBudget`（median/p95）。`paintedCenter` 公开、放在 `EventDispatcher.hitTest` 旁，保证"算出的坐标"与"命中的元素"一致 |
| mc 层 | 经 vanilla `Screen` 实现 `InputPort`；`hoverAt`、截图、服务端步骤、槽位、开发世界引导、可选玩家重置；失败时转储文档树与布局（也可按键或命令手动转储）；`UiScreen` 与 HUD 内计时；有失败时非 0 退出 |
| 加载器层 | `Pump`：tick 结束且该帧已画完 |
| 宿主 | 开发期 sourceSet `playtest`：自己的 `@Mod` 调 `IxchelForge.installPlaytests(modid, …)` 注册测试项；属性 `ixchel.playtest.<modid>=名字[,…]|all`。sourceSet 与 `-Pplaytest` 接线由插件的 `ixchel { playtests(…) }` 提供（#025） |

- 随现有 mc / 加载器制品发布，不另出按版本的制品，也无第二份 SRG 配方；代价是 `PlaytestDriver` 等成为 core 的公开 API。
- 等待单位为帧；转储与 `-Dixchel.devResources.<modid>` 同类，属性开启、仅开发期。
- sample 迁移只做等价重构，全部测试项结果不变为验收。
- 1.21.1 暂不做适配，记为缺项（#005）。

### 9. 编辑器（T4）

- 架构：编辑器后端 `ixchel serve`（kit 内），前端 `tools/editor`（TS + Vite + React，代码区 CodeMirror 6），JSON over HTTP/WS；预览用 Canvas 2D 回放 `DisplayList` JSON；编辑器写回 html/css 源文件，经开发期热重载进游戏。游戏内热重载是最高保真的预览，编辑器不追求像素一致。
- 事实来源只有 core：用 TS 重写解析或让浏览器直接渲染这份 HTML/CSS 会形成两套语义，不选。core 编译期只依赖纯 Java 的 `dev.vfyjxf:taffy`，链接图在 core，可离线加载。
- 前置：源码位置贯通到元素、属性与 CSS 声明（按源码区间写回、最小 diff）；工具版测量器（从用户本机 MC client jar 读字形宽度，不打包 Mojang 资源，读不到退近似宽度并在预览标出；物品图标读 `textures/item/<id>.png`，没有画占位框）；布局转储与 `render`（`DisplayList` 转 PNG）。
- 构建：`npm ci && vite build` 的 `dist/` 由 kit 的 `processResources` 拷进 jar 的 `/editor/`；Node 由 `com.github.node-gradle.node`（`download = true`）下到 Gradle 缓存，入口仍只有 `./gradlew build`。只改 Java 的人不碰 Node。
- 分发：随 kit jar 走，挂 GitHub Releases，随 `release` 工作流（#010，未实现）。不选：托管静态站点连本地 CLI（分别发版协议错配，https 页面访问 localhost 受私有网络访问限制）；npm / `npx`（多一个 Node 前提）；Electron / Tauri（分平台打包签名更新）。
- 浏览器内运行 core（可选）：TeaVM（Apache-2.0，0.16.0，反射成员须按 policy 声明）为候选，CheerpJ（许可与体积）、GraalVM Web Image（实验阶段）不选。core 中会触到限制的只有 `Compiler` 的反射与 `MethodHandles`、`LibrarySheets` 的资源读取；适配度为推断，以 spike 为准。

### 10. 分阶段

| 阶段 | 内容 | 验收 |
|---|---|---|
| T1 静态检查 | §3–§7：`@UiTemplate` 与挂载 API（sample、testmod 迁移）、成员表编译、原生元素声明数据、错误码、kit 的 `lint`/`schema`/`explain`（项目内）；sample 与 testmod 各一个挂在 `check` 上的 lint 任务 | E1 三类错误与未知绑定、`bind:value` 指向只读 `Cell`、`<for>` 遍历非列表、组件参数不符均由 lint 报出并带位置与码；现有模板零错误；`-Pcheck=all` 不变 |
| T2 游戏内检查 | §8（仅 1.20.1） | `-Pcheck=all` 160 项结果不变；人为制造失败时非 0 退出并产出转储 |
| T3 对外 | 项目外 CLI（fat jar）；导出 `models.json`；容器槽位检查（模板与 menu 的槽位数） | 随 Doc 阶段发布 |
| T4 编辑器 | §9：转储与 `render`、`serve`、前端 | — |
| T5（可选） | 拖拽与属性面板；TeaVM spike；LSP（lsp4j） | — |

T1 与 T2 互不依赖，可并行。

### 11. 缺项与不做

- 1.21.1 游戏内检查适配：暂缺（#005）。
- 附属 mod 扩展主 mod 界面：同类条目（按钮、标签页、信息行）用宿主 record + action 即可，库无需支持；附属 mod 自带布局与逻辑的面板需要两个库原语——由 JDK `Map` 构造的模型（§4 预留）与运行时按 id 装入片段的挂载点——等出现三个真实场景再做。扩展点由宿主声明，契约只用 JDK 类型（#004）。
- 不做：无头 testkit；在游戏外加载 MC 代码的 spike；手写 `model.json`；Gradle DSL 清单、开发期运行时记录关联、扫描挂载调用作为关联来源（分别是重复声明、新写的 UI 当场无法检查、实现脆弱）；云端 agent 与 xvfb。
