---
tags: [design]
date: 2026-10-26
origin: 梳理 Web 风格 UI 所需模块，确定内核包划分、包间契约、mc 与加载器层分块、里程碑、性能约束与编辑器分级；读 MC 源码确认 GuiGraphics 不合批；迁入 Kumiko 对 LDLib2 内核的可参考结论（vanilla Slot 相关结论已移到槽位接入文档）。同日补充实现顺序，并把 binding 最小集前移到 M2。
related: [DECISIONS.md#003, DECISIONS.md#004, DECISIONS.md#006, DECISIONS.md#007, ref/SD-ixchel-ui-positioning-and-adapter-design-20261025.md, ref/SD-ixchel-viewmodel-and-binding-20261026.md, ref/SDG-ixchel-container-slot-integration-20261026.md]
---

# Ixchel UI: Module Breakdown

## Summary

三层不变：`ixchel-core`（纯 Java）→ `ixchel-mc-<时代>`（仅 vanilla）→ `ixchel-<加载器>-<版本>`（胶水）。core 是单一 Gradle 模块，内部按包单向分层（#006）；模板解析器自写（#007）。包间只通过四个契约交互：`Element` 树、`PropertyDef` 注册表、`ComputedStyle` 数组、sealed `DrawCommand`。

性能上关键不在内核（典型界面一帧约 0.1 ms 量级）而在提交：`GuiGraphics` 的矩形、文本、贴图调用逐条刷新或逐条 draw call，Painter 必须自写顶点并按状态合批。

## Detail

### 1. 每帧流水线

```mermaid
flowchart LR
  I[收件箱/轮询来源] --> B[刷新脏绑定] --> D[DOM 变更]
  D --> S[样式: 匹配+层叠+计算值] --> L[布局: taffy + 文本测量]
  L --> P[生成 DisplayList] --> M[McPainter 提交]
  E[输入事件] --> D
```

每一级只在上游标脏时工作：无 DOM 变化跳过样式；计算值中与布局相关的属性未变则不 `setStyle`；taffy 树不脏则不 `computeLayout`；DisplayList 仍每帧提交（立即模式宿主）。

### 2. core 包与依赖

| 包 | 内容 | 依赖 |
|---|---|---|
| `diag` | 源位置、诊断收集、开发期叠加层数据 | — |
| `css.syntax` | CSS Syntax L3 分词、组件值 | diag |
| `css.selector` | 选择器解析、优先级、匹配 | css.syntax, dom |
| `css.property` | 属性注册表：名称、语法、初值、是否继承、是否影响布局、是否可过渡 | css.syntax |
| `dom` / `template` | `Element` 树、类名与状态位；严格模板解析器 | diag, css.property（计算值）, css.syntax（`style` 属性的声明） |
| `style` | 规则索引、匹配、层叠、`var()`、计算值 | dom, css.* |
| `anim` | transition、`@keyframes` | style |
| `text` | 文本测量接口与结果缓存、换行 | — |
| `layout` | `Element` ↔ taffy 节点同步、布局结果查询（mc 层据此写回 `Slot.x/y`） | dom, css.property, text |
| `paint` | DisplayList 生成、裁剪栈 | dom, css.property, layout, text |
| `event` | 命中测试、三阶段派发、焦点（`tabindex`、Tab 切换）、hover/active/focus 状态位、`Keys` 键名表；`UiEvent` 与监听器存放在 `dom`（`Element` 持有监听器） | dom, layout, css.property |
| `binding` | Value / Derived / Action / Scope，模板绑定；`on:keydown.<key>` 过滤查 `event.Keys` | dom, template, event |
| `runtime` | `UiDocument`：组装上述阶段、帧驱动、热重载入口 | 全部 |

`css.property` 是**唯一的支持清单**：解析、校验、开发期报错、特性文档与 VS Code `css.customData` 都由它生成，不另维护列表。

规模粗估（推断）：core 合计约 11.7k 行，与 SD §6 的 8~12k 一致。对照：LDLib2 `gui/ui/style` 7.6k 行（含 `PropertyRegistry` 与 transition）、event 0.8k、layout 1.7k（Taffy 一侧）。

### 3. 包间契约

契约代码在 `com.azane.ixchel.core` 下：`dom.Node`/`Element`/`TextNode`/`LayoutBox`、`css.property.PropertyDef`/`PropertyRegistry`/`ComputedStyle`、`paint.DrawCommand`/`DisplayList`。读代码看不出的约定：

- `ComputedStyle` 放在 `css.property` 而不是 `style`：`Element` 持有它，放在 `style` 会形成 dom ↔ style 环。`LayoutBox` 同理放在 `dom`。
- 树节点分 `Element` 与 `TextNode`：模板允许混排文本（如 `<span>a</span>/<span>b</span>`）。选择器只匹配 `Element`，`TextNode` 的样式取父元素。
- 样式脏标记的粒度是子树：元素脏表示它与整棵子树都重算；祖先只记"有脏后代"，供样式阶段跳过干净子树。没有结构伪类与属性选择器，所以增删子节点、改普通属性不标父元素脏；例外：改 `id` 标该元素脏（参与 `#id` 匹配），已算过样式的元素被挂到（新）父节点下时标该元素脏（继承值与组合选择器随祖先变化）。`class`/`style` 不走 `setAttribute`，分别用 `addClass`/`removeClass` 与 `setInlineStyle`。
- 坐标是文档 GUI 像素，原点在视口左上；`LayoutBox` 存绝对坐标的 border box。模板顶层元素的父节点是尺寸等于视口的隐式根。
- `font-size` 以 GUI 像素计，`9px` = vanilla 字体 1 倍（行高 9）；`DrawCommand.Text` 的 `(x, y)` 是行框左上角，基线对齐由 `McPainter` 处理。
- `DrawCommand` 的子类型随实现加入，当前为 `Rect`/`Text`/`Image`/`Custom`/`PushClip`/`PopClip`；计划中的 `RoundRect`、`Border`、`NineSlice` 在对应功能落地时再定字段。物品不单设 `Item`，走 `Custom(kind, payload, x, y, w, h)`：原生元素由 `TagRegistry.Builder.nativeTag(tag, payload)` 注册，payload 只能经 `bind:<payload>` 写入（按引用判断变化），`Painter` 对有 payload 的原生元素输出 `Custom`，内核不解释 `payload`。mc 层 `<item bind:item>` 用 vanilla `renderItem` 按元素尺寸缩放绘制；物品写深度至 z+158，`McPainter` 把其后的命令整体抬高 160，连续物品共用一层。
- `Syntax.parse` 报错后返回 `null`，该声明整条丢弃；简写在解析期展开，`ComputedStyle` 只存长写属性。选择器列表中任一项非法则整条规则丢弃（同 CSS）。
- 布局只在计算值中 `affectsLayout` 属性变化、文本、字号或子节点列表变化，或视口变化时才跑 taffy；只改颜色、背景等的重算不调用 taffy `setStyle`。
- 干净帧：上一帧以来无重算、无文本与子节点变化、视口不变、未调用 `invalidateText()` 时，`UiDocument.frame` 跳过布局同步与绘制，沿用上一帧的 `DisplayList`，core 不分配。`DisplayList` 带版本号，只在重绘时递增，`McPainter` 据此复用顶点缓冲。
- 模板属性分三类：普通属性（按 `TagRegistry` 校验）、`bind:*`（连同名字与值的源位置原样保存在 `Element` 上，交给 binding 层校验）、尚未支持的 `on:*`/`each`/`as`/`key`/`if`/`t`（开发期报错）。`TagRegistry` 是实例：core 提供 HTML 子集，mc 层追加原生元素后传入加载。
- 文本在 M2 是纯字符串：`Component` 由 mc 层 `getString()` 拍平，颜色、字重只由 CSS 给出。富文本以后以不透明宿主文本对象加入，测量与换行交给 mc 层（vanilla `StringSplitter`），core 不建模样式。字体或资源重载后宿主调用 `UiDocument.invalidateText()`。
- 加载的 `Diagnostics` 留在 `UiDocument` 上：成功加载的警告与加载后才出现的错误（如延迟编译的内联样式）宿主都能读到，供开发期叠加层显示。

M1 已知限制（均为开发期可见行为，后续按场景放开）：

- 单位只有 `px`、`%`；所有 at-rule（含 `@media`、`@keyframes`）报错；`var()` 与自定义属性报错。
- 文本两端空白被裁掉，`<span>a</span> / <span>b</span>` 中间得到 `"/"`。换行只在空格处断开，无空格的 CJK 文本不会换行。
- `<img>` 没有固有尺寸，必须在样式里给出 `width`/`height`，否则为 0；固有尺寸要等 mc 层能查询纹理大小。

黄金测试夹具在 `core/src/test/resources/golden/<场景>/`：`hud.html`、`hud.css`、`expected.txt`（DisplayList 的逐行文本形式，首行注明视口尺寸与测试用测量器）。期望坐标已用 taffy 1.1.5 按同样式手工搭树核对。

### 4. mc 层与加载器层

`ixchel-mc-<时代>` 按对外承担的契约分包（2026-10-28 重组，原则见项目 `AGENTS.md`「分包」）。宿主只 import 根包与 `source`；加载器层用根包，外加 `WorldProjection.capture` 喂相机：

| 包 | 契约 | 主要类 |
|---|---|---|
| `mc`（根） | 宿主 API：把原版的屏幕、容器屏幕、HUD 帧与输入交给文档 | `UiScreen`、`UiContainerScreen`、`UiHud` |
| `mc.document` | 文档生命周期，所有宿主共用：从宿主资源加载（`<link>` 解析）、开发期热重载与保留上一版、错误叠加层、逐帧绘制与 tooltip；view model 跨重载保留 | `HostedDocument`；`McResources`、`DevResources`、`DevOverlay` 包内私有 |
| `mc.element` | 原版内容进入文档：每个原生元素一个单元，自带标签、绘制（`NativePainter`）与 tooltip 解析 | `McElements`（`TAGS`、`PAINTERS`）、`Tooltips`；`ItemElement`、`EntityElement` 包内私有 |
| `mc.container` | 容器槽位接入：GUI 区域、`Slot.x/y` 写回与还原、隐藏规则、hover 判定、`IXCHEL` 逐槽裁剪绘制 | `ContainerLayout`（含 `<item-slot>` 的 `TAGS`）、`ClippedSlotItems`；`SlotPositions` 包内私有 |
| `mc.paint` | core 端口实现：DisplayList 回放（自写顶点合批、scissor、深度抬升）、文本与图片测量、`sprite:` 图集精灵 | `McPainter`、`NativePainter`、`McTextMeasurer`、`McImageMeasurer`；`Sprites` 包内私有 |
| `mc.source` | 游戏状态作为数据源 | `ClientSources`、`WorldProjection` |

依赖单向：根 → document/container → element → paint；source 独立。`McPainter` 不认识任何原生元素，按 `DrawCommand.Custom.kind` 查 `NativePainter`，绘制返回写入的深度，其后命令整体抬高。1.21.1 为最小集：无 `container`、`<entity>`、`sprite:`，其余包与 1.20.1 同构。

`ixchel-<加载器>-<版本>`：只做注册——`registerHud` 构造 `UiHud`，把它挂到 HUD 层与资源重载监听，安装 `WorldProjection` 的相机捕获；不碰文档加载与开发期设施。

### 5. 模板解析器（#007）

- 规则类 XML：标签必须闭合或自闭合、属性必须加引号、不做隐式闭合；未知标签与属性开发期报错，带文件:行:列。
- 仅识别注册过的元素（HTML 子集 + 原生元素）；`<style>` 与 `style` 属性交给 css 包。
- 估计约 0.5k 行（推断）。

jsoup 的问题在 HTML5 容错建树（隐式闭合、adoption agency、foster parenting）会静默改写结构，而非 DOM 模型本身。

### 6. 圆角/阴影/渐变路径

| 路径 | 做法 | 取舍 |
|---|---|---|
| A | CPU 三角化 + `POSITION_COLOR` | 无 shader；无抗锯齿；阴影只能近似 |
| B | SDF shader | 效果最好；MVP 即需 shader 注册与自定义 `ResourceProvider`，Painter 随顶点 API 时代分叉 |
| **C（推荐，未定）** | CPU 生成覆盖率/SDF 纹理放入单张图集，自写 `POSITION_TEX_COLOR` 顶点，九宫格拉伸；纹理以宿主 modid 注册 `DynamicTexture` | 无 shader、有抗锯齿；与图片共用合批路径；纹理按"半径×边框×模糊"键缓存 |

### 7. 性能

一次性基准（本地脚本，未入库；JIT 预热后取中位数）：

| 项 | 规模 | 耗时 |
|---|---|---|
| 绑定，无变化 | 50 来源 / 200 绑定 | 0.5 µs |
| 绑定，一个来源变化 | 同上 | 2.9 µs |
| 样式匹配，朴素 | 300 节点 × 150 规则 | 242.4 µs |
| 样式匹配，按最右侧类名建索引 | 同上 | 26.1 µs |
| taffy 布局，无变化 | 301 节点 | 18.6 µs |
| taffy 布局，单叶子变脏 | 同上 | 52.1 µs |
| taffy 布局，全部变脏 | 同上 | 432.4 µs |

未测：层叠与计算值、文本测量。

MC 源码事实（1.20.1 与 1.21.1 vanilla）：`GuiGraphics.fill` / `fillGradient` / `drawString`（1.21.1 还有 `fillRenderType`）每次调用末尾 `flushIfUnmanaged()` → `bufferSource.endBatch()`；`innerBlit` 每次调用 `BufferUploader.drawWithShader`。即逐元素调用 = 逐元素一次提交。MC 一侧提交耗时约 0.5~2 ms（推断，未实测）。1.21.6+ 延迟渲染下原生是否合批未核实。

由此的约束：

- 样式规则按最右侧复合选择器（id/类/标签）建索引。
- 只在影响布局的计算值变化时调用 taffy `setStyle`；树不脏时跳过 `computeLayout`。
- 文本测量结果按（字符串、字号、宽度约束）缓存。
- DisplayList 复用数组，稳态零分配。
- Painter 自写顶点，只在渲染状态或纹理切换时提交；覆盖率纹理与图片同一图集。
- M2 起在游戏内测量帧耗时，对照同界面手写原版实现。

### 8. 编辑器支持分级

| 级别 | 内容 | 时机 |
|---|---|---|
| L1（已完成，Forge 1.20.1） | 开发环境下文件监听热重载，错误显示在叠加层。宿主以 `-Dixchel.devResources.<modid>=<资源根目录>` 开启：模板与样式直接读源码目录，保存即重载；加载失败保留上一版并在叠加层列出错误，运行期错误也列出；图片仍走打包资源 | MVP |
| L1+ | 由属性注册表与元素注册表生成 VS Code `html.customData` / `css.customData` | MVP 后低成本 |
| L2 | 浏览器预览：预览用重置样式表模拟 MC 字体与像素对齐；ViewModel 结构描述 + mock 数据填充绑定 | 有外部使用者后 |
| L3 | 游戏内开发服务器（`com.sun.net.httpserver`，只绑 127.0.0.1，默认关闭）+ GrapesJS 类可视化编辑器 | 不在规划内 |

L3 前需核实启动器运行时是否包含 `jdk.httpserver` 模块。

### 9. 里程碑（计划）

| 里程碑 | 内容 | 退出条件 |
|---|---|---|
| M1（已完成） | core 原型：模板 → 样式 → 布局 → DisplayList，单元测试覆盖弹药 HUD | 不碰 MC 产出正确绘制命令 |
| M2（已完成，ImmediatelyFast 共存移到 M4） | mc 1.20.1：McPainter、文本、HUD 宿主；binding 最小集（来源、派生、文本/属性绑定） | 游戏内显示随实际弹量变化的弹药 HUD；帧耗时对比；与 Iris、ImmediatelyFast 共存 |
| M3（已完成） | 事件、binding 其余部分（`action`、`each`/`if`、`Scope`）、`UiScreen`：Z 键改装 | 与手写原版对比（见绑定文档 §7） |
| M4（已完成） | shade 配方与 NeoForge 1.21.1 对齐最小集 | 两个不同版本副本共存；1.21.1 跑同一验收模板；生产环境与 ImmediatelyFast 共存 |
| M5（已完成） | `UiContainerScreen` 与 Slot 通道：背包式改装 | vanilla 物品事务行为不变；`ref/SDG-ixchel-container-slot-integration-20261026.md` §8 的 L1–L3 通过 |

#### 实现顺序

原则：契约先行；每个里程碑先打通最薄的一条端到端路径，再按验收场景加宽；会推翻设计的风险点用独立探针提前验证。

M1 之前：

1. 冻结 §3 四个契约的骨架与 `diag`。
2. 先写验收夹具：弹药 HUD 模板、样式表与期望的 DisplayList（黄金测试）。M1 以它通过为退出条件。

M1（core，按依赖顺序；`dom`/`template` 与 2~3 可并行）：

| 序 | 包 | 本阶段范围 |
|---|---|---|
| 1 | `css.syntax` | 完整分词 |
| 2 | `css.property` | 注册表机制完整；条目只覆盖弹药 HUD 用到的属性，后续按场景追加 |
| 3 | `dom` / `template` | 严格解析器完整（#007） |
| 4 | `css.selector` | 标签/类/id/后代/子代；`:hover`/`:active`/`:focus` 的解析与匹配已在 M1 完成，状态位由 M3 的 `event` 写入 |
| 5 | `style` | 按最右侧选择器建索引、层叠、计算值；`var()` 可延后 |
| 6 | `text` | 测量接口 + 等宽假实现（供单测） |
| 7 | `layout` | taffy 同步与脏标记 |
| 8 | `paint` | DisplayList 生成、裁剪栈 |
| 9 | `runtime` | `UiDocument` 组装帧流水线，黄金测试通过 |

M2（mc 1.20.1 + forge 1.20.1）：`McResources` → `McTextMeasurer`（换掉假实现，复核换行）→ `McPainter`（矩形、文本，自写顶点合批）→ `UiHud` 与加载器 HUD 注册 → `binding` 最小集 + `ClientSources`（mainHand、inventory）→ 图片与物品绘制 → 开发期热重载（L1）→ 帧耗时与 Iris/ImmediatelyFast 共存。圆角路径 C 在弹药 HUD 实际用到时才进入。

M2 结果（2026-10-26，Forge 1.20.1 开发环境，AMD 核显，`gradlew :testmod-forge-1.20.1:runClient -Pcompat -Pbench` 无人值守）：`HudBench` 交替绘制 Ixchel 弹药 HUD 与图元相同的手写 `GuiGraphics` 版本，计 overlay Pre→Post 的 CPU 时间（含提交，不含 GPU），每窗 600 帧、丢弃预热窗。

| 条件 | Ixchel 中位数 / p95 | 手写版中位数 / p95 |
|---|---|---|
| 无光影（两窗） | 215 / 336 µs，166 / 268 µs | 300 / 454 µs，231 / 346 µs |
| Oculus 1.8.0 + Complementary Reimagined r5.9.3（两窗） | 233 / 372 µs，207 / 318 µs | 333 / 520 µs，297 / 448 µs |

Ixchel 两种条件下都比手写版快约 25–30%：手写版每帧重算备弹数并逐条提交，Ixchel 在数据不变时跳过取值与布局，矩形与图片合批。Iris 在 Forge 1.20.1 上对应 Oculus（依赖 Embeddium），开光影后 HUD 位置、颜色与物品图标正常（截图核对）。ImmediatelyFast 的 common mixin 不带 refmap、注解里直接写 SRG 名，开发环境重映射不改注解，注入失败、客户端启动即崩，只能在生产环境验证；M4 已在生产环境验证共存（见下方"M4 shade"）。

M3：`event`（命中、三阶段派发、焦点、状态位）与 `css.selector` 状态伪类 → `McInput` → `UiScreen` → `binding` 其余部分 → `anim`（transition）放在最后，不阻塞退出条件。

M3 结果（2026-10-27，同 M2 环境，`gradlew :testmod-forge-1.20.1:runClient -Pbench=refit` 无人值守）：`RefitBench` 交替打开 Ixchel 改装界面（Z 键）与功能、失败处理一致的手写原版版本 `VanillaRefitScreen`（V 键），计 Screen 渲染 Pre→Post 的 CPU 时间（结束前 flush，含提交）与客户端线程分配；"变化"窗每 tick 改一次界面上显示的配件数量。两轮结果：

| 窗 | Ixchel 中位数 / p95 µs | 原版中位数 / p95 µs | Ixchel / 原版每帧分配 |
|---|---|---|---|
| 无变化 | 145.7 / 237.5，144.6 / 237.6 | 211.0 / 327.2，191.2 / 299.2 | 约 83 KB / 约 86 KB |
| 频繁变化 | 150.5 / 1058.4，161.0 / 660.7 | 211.2 / 345.1，189.2 / 290.7 | 约 190 KB / 约 89 KB |

- 无变化帧 Ixchel 快约 25–30%；每次变化 Ixchel 多约 0.6–0.9 ms、约 0.6 MB 分配，原版只多约 3 KB。M4 已 profile 并部分优化，见下方"M4 变化帧"。
- 代码量：Ixchel 243 行（ViewModel 117、模板 45、样式 81，另约 10 行订阅），原版 331 行。
- 目标失效：服务端把枪移出锁定格再放回，两版界面都保持打开，`valid` 先变为失效再恢复，4 项检查通过。
- 行为差异：原版保留按钮点击音效与 Tab 焦点切换，Ixchel 尚无；原版打开时弹 "Inspecting Scope" 提示（`selectType` 调用），Ixchel 因订阅晚于初值设置而不弹。
- transition 限制：只在同单位长度、数字、预乘颜色之间插值，其余属性过渡瞬切；`steps()` 开发期报错；时间取整到微秒；首次样式与 `display: none` 前后不产生过渡；反向按 CSS Transitions 缩短时长。游戏内以 120ms 背景色过渡截图核对（20 ms 中途、303 ms 终态）。

M4：先优化变化帧，再 shade 配方（Forge 1.20.1，两个不同版本副本），再 `ixchel-mc-1.21.1`（顶点 API 时代分叉只落在 `McPainter`）与 neoforge 1.21.1 加载器胶水，跑同一套验收模板。

M4 变化帧（2026-10-27，JFR 分配采样 + core 内探针）：

- 变化帧的 core 开销几乎全在 taffy `computeLayout`。refit "频繁变化"窗改的是 "x5↔x6"：一个文本节点变了，就被 `markDirty`，整条祖先链跟着重排。
- 已做：`LayoutEngine` 记录 taffy 对每个文本节点发出的测量请求（已知宽高、可用宽度）及其结果。文本变化时用新文本重新回答这些请求，全部相同就不标脏，只重新排版该文本。这对任意 `TextMeasurer` 都精确，不依赖字宽可加。另外，`Painter` 改为按下标遍历子节点（只读视图的 for-each 每次分配迭代器）。
- 结果（同一命令，两轮）：

  | 窗 | Ixchel p95 µs | Ixchel 每帧分配均值 | 原版每帧分配均值 |
  |---|---|---|---|
  | 频繁变化 | 437.2，319.0 | 约 85 KB | 约 89 KB |

  均值 227.8 / 191.8 µs，原版 234.7 / 214.6 µs。
- 未解决：文本宽度真的变化时（临时把基准改为 5↔15 实测），仍回到基线：p95 999 / 635 µs，每帧分配均值约 190–199 KB。
  - core 探针显示，单次重排约 95 KB 加上同级每行约 10 KB，与总行数成线性。叶子测量缓存是命中的，只有变化节点被重测。
  - JFR 中 89% 是 taffy-java 内部分配（`FloatSize` 占 63%，另有 `LayoutInput`、`FlexItem`）。缓存命中路径本身也在分配（`LayoutOutput.fromOuterSize`），Ixchel 侧已无可削减。
- 未来方向（未排期）：
  - CSS `contain: size layout` 布局边界：作者显式标注、须有确定宽高；外层 taffy 树里当固定尺寸叶子，子树用独立 taffy 树计算。
  - 或者 fork/补丁 taffy-java，削减缓存命中与 flex item 的对象分配；代价是长期维护 fork，且 shade 时重定位的是 fork。

M4 shade（2026-10-27，Forge 1.20.1）：

- 配方：`testmod/shade-{a,b}-forge-1.20.1`，shadow 9.6.1。`shade` 配置带 runtime/library/jar/external 属性，排除 fastutil；shadowJar relocate `com.azane.ixchel` 与 `dev.vfyjxf.taffy`；`obfuscation.reobfuscate(tasks.shadowJar, …)`，`jar` 改 classifier `slim`。发布产物经 `-PshadeIxchel` 重映射的方式见 #011。
- 产物核对：A 的 jar 有 125 个 ixchel 类和 98 个 taffy 类，全部重定位；fastutil 引用保持原包名；`McPainter` 中的 MC 调用全部为 SRG `m_` 名，没有残留的 mojmap 名。
- 生产环境：portablemc 启动 `forge:1.20.1-47.3.19`，mods 为 ImmediatelyFast-Forge 1.5.5+1.20.4、shade-a（当前源码）和 shade-b（从 mavenLocal 的 `0.1.0-m3` 构建；B 的 jar 中没有 M4 新增的 `measuresAlike`）。两个 HUD 同时正常显示；日志里两份 `IxchelForge` 各自从自己的 jar 加载，没有类冲突，也没有 mixin 报错。

M4 NeoForge 1.21.1（2026-10-27，`gradlew :testmod-neoforge-1.21.1:runClient`，用临时脚本无人值守运行，脚本已删除）：

- 弹药 HUD 和 Z 键改装界面直接用 Forge 侧的模板、样式与贴图，未作改动，渲染结果一致（截图核对）。
- 改装流程：安装玻璃板后 `pending` 先为 true；服务端约 6 tick 后回包，`scope = glass`，背包里的玻璃板 5→4，望远镜退回背包。
- 1.21.1 的 `Screen.renderBackground` 自带模糊，所以改装界面背后是模糊的世界，这与 1.20.1 不同，由原版行为决定。

M5：按槽位文档推进：反射写坐标与 `isHovering` 覆写 → `UiContainerScreen`（`VANILLA` 绘制）→ Slot 通道（轮询比较）→ 第三方 mod 兼容（L2）→ `IXCHEL` 绘制开关（#009）→ 生产环境 SRG 验证（L3）。

M5 结果（2026-10-27）：`UiContainerScreen` + `<item-slot>` + 背包式改装（#32）；L2 在 `-Pslotcompat=jei|emi` 下由 `-Pbench=backpack` 跑，含真实光标悬停检查（#33）；`IXCHEL` 绘制逐槽裁剪、槽位随滚动移动（#34）；关闭时还原槽位坐标，L3 由 shade-a 的 `ContainerCheck`（`-Dixchelshadea.check=container`）在生产实例跑（#35）。详见槽位文档 §8。

提前探针（只用 mc 层 + testmod，不依赖 core，可在 M2 期间做）：槽位文档 §8 的 L1，验证 ModLauncher 下 `Slot` 包对反射开放、`isHovering` 覆写生效、1.20.1 零面积裁剪。失败会推翻 #008 或 #009，不应拖到 M5。

### 10. 迁入的参考结论（Kumiko 调研）

- LDLib2 中零 MC 依赖、可参考思路的类：`StyleEngine`、`HierarchicalStyleMatcher`、`StyleSelector` / `StyleRule` / `Stylesheet`、`PropertyRegistry`、`UIEventDispatcher`、`UIEvent`。LDLib2 为 LGPL，只参考思路，不复制代码。
- LDLib2 的 `UIElement` 同时承载树、布局、样式、事件；Ixchel 把这四者分到 dom / layout / style / event 包，`Element` 只是数据。
- vanilla Slot 的事务硬约束、坐标写回、命中、绘制与第三方 mod 兼容：见 `ref/SDG-ixchel-container-slot-integration-20261026.md`。
- 不继承 Kumiko 的复制层与意图 RPC：引擎中 UI 是状态的投影，MC 中 UI 是状态的通道；LDLib2 的同步会话绑定在 `player.containerMenu` 上，在 HUD 与无 Menu 的 Screen 上不可用。
