---
tags: [design]
date: 2026-10-29
origin: 模板与样式语言定出目标形态后，需要把它与剩余 sample 场景排成一条顺序；原先顺序分散在场景文档 §3 与语言文档 §7
related: [ref/SD-ixchel-sample-scenarios-20261028.md, ref/SD-ixchel-template-and-style-language-20261029.md, ref/SD-ixchel-context-model-and-components-20261030.md, ref/S-ixchel-ui-capability-comparison-20261102.md, DECISIONS.md#015, DECISIONS.md#029]
---

# Ixchel UI: Roadmap

## Summary

实现顺序与状态的唯一来源；其它 meta 文档只引用本文。已完成 0、A、B、C、D0、L1、D、L2、L3、E1、T1、T2、E2、I、U1–U3、原生声明与元素定义重构 N1–N2，以及 V1–V4（盒子外观、尺寸与排版、动画与进出、整页开关动画）；接下来是 U4（组件库）、FX1–FX3；Doc 先写接入与语言参考，其余随阶段补齐。阶段排序依据见 `ref/S-ixchel-ui-capability-comparison-20261102.md`。sample 迁移只做等价重构，以 `-Pplaytest=all` 为回归网。

## Detail

### 1. 规则

- 本文只记顺序、范围、开工前要定的问题、验收与状态。各阶段的结果写进对应主题文档：场景结果写场景文档的"阶段 X 结果"，语言设计的定案写语言文档对应小节；本文只把状态改为完成并引用结果位置。
- 每个阶段是一批改动，一个分支、一个 PR（项目 AGENTS.md）；阶段末更新 meta、开 PR、合入。
- 迁移现有 sample 只做等价重构：不顺带加功能，check 只改选择器、不改断言。语言文档 §2 示例中的确认框与帮助开关只是演示，不在迁移范围内。
- 影响 mc 层的改动同时改 1.20.1 与 1.21.1，以 `sample-neoforge-1.21.1` 的 `runClient` 冒烟。
- 语言阶段开工前先在语言文档 §6 定下本阶段涉及的待定项。

### 2. 阶段

| 阶段 | 状态 | 场景 | 内容 |
|---|---|---|---|
| 0 | 完成 | — | border、opacity、文本对齐与省略、`:disabled`、自定义元素 tooltip |
| A | 完成 | S5、S4 | 环境 HUD、掉落物雷达 |
| B | 完成（#46） | S1、S2 | `<entity>`、图集精灵与 tint |
| C | 完成 | S3、S6 | 文本输入、虚拟网格、表单控件、顶层与对话框、Tab 焦点 |
| D0 | 完成 | — | 改名与清理 |
| L1 | 完成 | — | 表达式、`on:` 语句、`state:` |
| D | 完成 | S7、S4 箭头 | transform、拖拽 |
| L2 | 完成 | — | 组件、组件层、CSS 嵌套 |
| L3 | 完成 | — | 主题层与主题、`var()` |
| E1 | 完成 | S8 | 文字排版补完：行内流式排版、CJK 断行、`Component` 富文本、行内链接 |
| E2 | 完成 | S9；S1、S6、S7 的 tooltip | 文档内富 tooltip `<ix-tooltip>`，兼容原版物品 tooltip |
| I | 完成 | 全部场景的界面文本 | i18n：`t` 语言键与参数 |
| Doc | 待做 | — | 用户文档站 `docs/` 与语言参考 |
| T1 | 完成 | 2026-10-31 | 静态检查：`@UiTemplate`、成员表编译、原生元素声明数据、错误码、`ixchel-kit` 的 lint 与 schema（`ref/SD-ixchel-tooling-and-editor-20261031.md`） |
| T2 | 完成 | 2026-10-31 | 游戏内检查从 sample 提取进 core / mc / 加载器层（仅 1.20.1） |
| U1 | 完成 | S7、S4 | `text-shadow`、`<for index>`、默认点击音、滚动条拖动与外观 |
| U2 | 完成 | S7、S6 | 拖拽：`draggable`、`dragstart`/`drag`/`dragend`/`drop`、`<ix-drag-preview>` |
| U3 | 完成 | — | 宿主自定义原生元素入口（#030） |
| N1–N2 | 完成 | — | 原生声明随实现类注册；元素定义按消费阶段切面组合（#030、#031），结果见下文 |
| V1 | 完成 | — | `::before`/`::after`（只做装饰）、多层背景、线性渐变、`border-image` 与 `image-rendering: pixelated`、`border-radius`（只做圆角外观）、`box-shadow`、`:not`/`:is`/`:where` |
| V2 | 完成 | — | `calc()` 与 `min()`/`max()`/`clamp()`、em/rem/vw/vh、`line-height`、`align-content`、grid、标准块布局（放宽 `display: block` 含块级子元素的限制） |
| V3 | 完成 | — | `@keyframes` 与 `animation-*`、`on:animationend`、进出伪类 `:-ix-absent`/`:-ix-entering`/`:-ix-exiting`（#032） |
| V4 | 完成 | S1、S6 | 整页开关动画：UiScreen 关闭与 UiHud 显隐复用进出伪类（#032） |
| U4 | 待做 | — | `ix-` 组件库：tabs、tree、进度条等 |
| FX1 | 待做 | — | 私有图层与 shader 注册；`opacity` 整组语义 |
| FX2 | 待做 | — | 标准 `filter`、`backdrop-filter`、`mix-blend-mode` |
| FX3 | 待做 | — | `.effect` 具名特效与首批内置特效（`ref/SD-ixchel-ui-effects-20261102.md`，#028） |

依赖：D0 → L1 → D → L2 → E1 → E2；L3、I、Doc、T1、T2 都依赖 L2，彼此及与 E 互不依赖，可并行或互换；Doc 分两步：接入与语言参考先写，其余随 U1–V3 补齐；U1 → U2（共用指针按住状态）；V1 落地路径 C 图集（#029）；V3 → V4；U1–V3 → U4；FX1 → FX2 → FX3，FX3 依赖 L3 的 `var()` 与 V3 的 `@keyframes`，与 Doc 互不依赖。0–C 的结果见场景文档 §3。T3（对外发布）随 Doc，T4（编辑器）及之后见工具文档 §10。

#### D0 改名与清理

- 引擎：`item` → `mc-item`，`entity` → `mc-entity`，`item-slot slot`/`bind:slot` → `mc-slot index`/`bind:index`（常量 `ItemElement.TAG`、`EntityElement.TAG`、`ContainerLayout.ITEM_SLOT`/`SLOT_ATTRIBUTE`，两个 mc 版本，1.21.1 无 `container`）；保留前缀 `ix-`/`mc-` 的加载期校验（组件尚不存在时只作用于原生元素登记）；`TagRegistry` javadoc 的 "hosts add native elements with `extend()`" 不成立（宿主入口固定 `McElements.TAGS`，`HostedDocument` 固定 `McElements.PAINTERS`，登记新标签会在绘制时抛 "no painter for native element"），改为 mc 层内部用法。
- sample：所有模板改名；machine、storage、waypoints 的 `div.button` 改为 `<button>`；删去 `storage/terminal.css` 中重复 UA 外观的 `.search` 规则；check 选择器同步（`.cell item` → `.cell mc-item`，`.button` → `button`）。testmod 三个模板改名。
- meta：GROUND、CONVENTIONS（容器槽位）、SDG 槽位文档中的元素名。
- 验收：`build`；`-Pcheck=all`，重点 `machine,storage`（容器内可获焦的按钮与原版容器按键 E、数字键是否冲突）；testmod 1.21.1 `runClient`。
- 结果（2026-10-29）：按上述完成，`-Pcheck=all` 122 项通过。原生元素的载荷属性与标签名分开（`ItemElement.PAYLOAD = "item"`，仍写 `bind:item`）。保留前缀的加载期校验未做：目前只有 mc 层登记标签，没有宿主或组件登记入口，校验没有作用对象，移到 L2 随组件 `name` 一起做。按钮改为 `<button>` 后，作者样式补 `color`（machine、storage）与 `border-style: none`（waypoints），使 UA 外观不漏出。testmod 1.21.1 只经 `build` 编译，未进游戏。

#### L1 表达式、语句、局部状态

- 先定：语言文档 §6-2 格式化函数，§6-4 热重载时局部状态是否保留。
- 引擎：表达式解析与加载期类型检查，编译为 `Derived`，路径部分复用 `Bindings` 现有解析；`if` 接受表达式；`on:` 语句（调用、赋值、`;`）；`state:`；`BindAttribute`、`isBindingAttribute` 改为按指令命名。
- sample：迁移只做格式化的 `Derived`（`EnvView`、`MachineView`、`LootListView`、`TerminalView`、`WaypointsView`）与纯视图状态（config 的 `listOpen`、`confirming`，character 的页签）。
- 验收：core 单元测试覆盖类型错误、未知名字、赋值到只读目标，报错带行列；`-Pcheck=all`；记录删去的 `Derived` 数。
- 结果（2026-10-29）：§6-2 定为不提供内置函数，§6-4 定为不保留（语言文档 §6）。core 新增 `binding.Expr`（解析）、`Expressions`（类型检查与编译）、`Types`、`Ref`；`TemplateParser` 按 HTML 规范把后面不是字母或 `#` 的 `&` 当字面字符。sample 删去 22 个 `Derived`（waypoints 3、env 6、machine 1、loot 3、storage 2、character 6、config 1）与 5 个只改状态的动作（`toggleSides`、`filter`、`flipDirection`、`closeList`、`keepEditing`），machine 的 `sidesOpen` 移入 `state:`。留在 Java 的：引用 Java 常量（容量、`CELL`、火焰高度）、读枚举字段（`status().label`）、需要 `Math.round`/`min`、与 `NONE` 比较的。config 的 `listOpen`、`confirming` 由 Java 动作打开，仍是 ViewModel 字段，只把关闭改为模板赋值。`-Pcheck=all` 122 项通过。

#### D S7 任务树、S4 箭头

- 引擎：transform（translate/scale/rotate）与逆变换命中测试；画线命令（`DrawCommand` 加类型，visitor 同步）；`mouseDragged` 转发与检查驱动 `drag(sel, dx, dy)`。
- sample：S7 界面与检查（约 100 节点），按 L1 语法写；S4 方向箭头改用 rotate，去掉 8 方向字形。
- 验收：场景文档 §2 S7 的交互即检查清单；帧耗时进 `FrameTimer` 预算。
- 结果（2026-10-29）：见场景文档"阶段 D 结果"。画线命令与 `mouseDragged` 转发未做：连线用旋转的细 `div`，拖拽用已转发的 `mousemove` 加按键过滤。`-Pcheck=all` 143 项通过；testmod 1.21.1 只经 `build` 编译，未进游戏。

#### L2 组件

- 设计依据：`ref/SD-ixchel-context-model-and-components-20261030.md`（上下文模型、四条不变式、组件端口与使用处规则；原 §6-3 插槽后备内容、§6-5 子 Scope 已在其中定下）。
- 前置整理（L2 分支的前几个提交，行为不变）：`source(...)` 返回只读 `Cell<T>`、`Value<T>` 只表示 state，框架内部持有者（行、事件、动作的 `pending`/`error`）不再用 `Value`；`Compiler` 改为按名字表解析（值 `Ref` 或可调用项），顺带 `private` 字段不可见、`event` 改名 `$event`、`$` 前缀保留、`as` 行名与 `$event` 纳入不遮蔽检查；按契约拆分 `Bindings.java`（编译器、块、各类绑定），元素克隆移到 `dom`；链接图移进 core（core 负责相对路径解析、递归加载、去重、环检测与热重载文件表，mc 层只按 id 读取文本），两个 mc 版本同改；解析器的内容模型检查穿透控制块（`<select>` 内的 `<for>`）。
- 引擎：`<link rel="component">`、`<template name params events>`、外壳元素、参数（含默认值与双向）、事件（含载荷 `$event`）、具名插槽与后备内容；组件名须带连字符且不以 `ix-`/`mc-` 开头（加载期校验，自 D0 移来）；`<for>`/`<if>` 控制块取代元素上的 `each`/`if`（语言文档 §3.5，块实例由单根改为多根）；样式分层先做 UA、组件、作者三层；CSS 嵌套（解析期展开）；组件文件进热重载监听与诊断定位。
- sample：所有模板改用 `<for>`/`<if>`（与组件迁移同批，不留属性形式）；抽出 `ui/common/` 公共组件，只抽至少两处真实重复的结构；迁移 7 个场景与 S7；check 中随树结构变化的选择器与迁移同一提交。
- 验收：`-Pcheck=all`；S4、S5 的 `FrameTimer` 预算不超（外壳元素增加节点）；重复结构与 CSS 的减少量。
- 结果（2026-10-30）：按上述完成。真实重复的结构只有玩家背包网格（character、machine），抽成 `ui/common/player-inventory.html`；槽位外观三份合为 `ui/common/slot.css`；storage 的网格是一个 36 格滚动网格，结构不同，未抽。CSS 净减 37 行（删 58、增 21），HTML 因新组件文件净增 26 行。`-Pcheck=all` 143 项通过，S4、S5 帧耗时 p95 在预算内（env 移动转向 1.1 ms / 6 ms，loot 每 tick 重排 1.6 ms / 14 ms）。缺必填参数或默认值求值失败时不编译组件体，只报一条。CSS 嵌套按父选择器列表逐项展开，各自计算优先级，与 `:is()` 取最高值不同。另将 `binding` 按契约拆为 `binding`、`binding.expr`、`binding.compile`（#017）。testmod 1.21.1 只经 `build` 编译，未进游戏。

#### L3 主题

- 先定：语言文档 §6-1 库内样式表 href 写法。
- 引擎：主题层；core 提供按名字读取库内样式表的入口（classpath，相对 core 类，同 `UiDocument.UserAgent`），两个 mc 层的 href 解析各加一个分支；`var()` 自定义属性（两份主题是同一结构的两套配色，满足语言文档 §4.4 的条件；也是页面状态影响组件外观的通道，上下文模型文档 §3.6）；`vanilla`、`dark` 两份主题。
- sample：7 个场景按两族外观引入主题，删去重复配色。
- 验收：作者 CSS 只剩布局与场景特有外观；`-Pcheck=all` 截图人工核对。
- 结果（2026-10-30）：§6-1 定为 `@ixchel/<名>.css`，只能由页面链接（组件文件链接即报错，未知名字在 link 处报错）。L2 已把链接图移进 core，href 由 `TemplateLoader` 识别、`runtime.LibrarySheets`（接替 `UiDocument.UserAgent`）读取，两个 mc 层只改 javadoc。主题 = 共用规则 `themes/theme.css`（`.panel`、`.title`、`button` 各状态，全部写成 `var(--ix-*)`）加只设 `:root { --ix-* }` 的配色表 `vanilla.css`、`dark.css`（#018）。`var()` 带回退、可继承；自定义属性按普通层叠、总是继承，`bind:style:--x` 可写；`:root` 匹配模板顶层元素。sample 3 个场景用 vanilla、5 个页面（config、index、loot 列表、quests、waypoints）用 dark，env 与 loot HUD 不加主题；CSS 增 69 删 144 行，check 不改。接受的外观变化：按钮获焦时显示悬停底色；machine 选中按钮字色 `#404040`→`#202020`；index 行悬停 `#FFFFFF30`→`#FFFFFF20`、说明字色 `#A0A0A0`→`#AAAAAA`；config 条目悬停 `#FFFFFF10`→`#FFFFFF20`。已知偏差：成环的自定义属性带回退时可能取到回退值（规范要求环上一律无效），尚无场景触及。`-Pcheck=all` 143 项通过；testmod 1.21.1 只经 `build` 编译，未进游戏。

#### E1 文字排版补完（S8 指南书）

范围：M1 只做了单个文本节点、空格处断行的最小集（模块拆解 §3 "M1 已知限制"），本阶段补完多段样式文字的排版。

- 已定（2026-10-31）：
  - `Component` 由 mc 层拆成带样式的文字片段（颜色、粗体、斜体、下划线、删除线），与模板中的 `<span>` 进入 core 的同一套行内排版。取代模块拆解 §3、绑定文档 §3 的"不透明宿主文本对象，由 mc 层测量与换行"：那样 `Component` 与周围文字不能同行续排，且有两套换行。阶段末改写这两处。
  - 链接用标准 `<a>`；库不解释 `href`、不做导航，跳转、目录与返回历史由 ViewModel 的动作完成（`on:click`）。
  - 行内格式化上下文由标准 `display: block` 建立，首版只允许子节点全为行内内容，块级子节点开发期报错；不按"子节点全是行内"隐式切换（`display` 初值为 `flex`，`<div>` 里的每个 `<span>` 现在各是一个 flex 项）。
  - 空白：行内上下文中按 CSS `white-space: normal` 折叠，保留元素间的单个空格；flex 容器中仍丢弃纯空白文本节点（现为 `TemplateParser.text` 一律 trim）。
  - 断行：在空格处之外，CJK 字符之间可断，带基本避头尾（句读与右括号不在行首、左括号不在行尾）；现为只在空格处断，无空格的中文不换行。
  - 行内元素与字体属性：`<span>`、`<a>` 默认 `inline`；`<mc-item>`、`<img>` 为原子行内盒（`inline-block`）；`font-weight` 只收 `normal`/`bold`，`font-style` 只收 `normal`/`italic`，`text-decoration` 只收 `none`/`underline`/`line-through`（MC 字体只有这些开关）；`Component` 自带的样式相当于内层 span 的样式，未设的部分继承 CSS，混淆（`§k`）无 CSS 对应，只能来自 `Component`；超出子集开发期报错（#003）。
  - 行内元素上的盒属性（背景、边框、内边距、外边距；跨行时 CSS 要按片段切开）不支持，`inline` 元素上写即开发期报错；需要带底色或边框的小块（按键提示、标签）用 `inline-block`，它是原子盒、不跨行。跨行高亮出现真实需要时按标准 slice 语义补。
- 引擎：Taffy 无行内格式化上下文，子节点全为行内内容的元素作为带测量函数的叶子，由 core 按样式片段与原子盒排行，绘制与命中测试按行内片段；`TextMeasurer` 按样式测量；绑定的 `Component` 保留样式进入排版（现为两版 `McResources.text` 的 `getString()`）。
- sample：S8 页面类型用组件写；目录由 sample 枚举资源生成，库不提供资源列表；配方轮换用现有时间源（`System::nanoTime`，tick 时间域仍见 §4）。
- 验收：场景文档 §2 S8 的交互逐项写成检查；S8 长页进 `FrameTimer` 预算；`-Pcheck=all`。
- 结果（2026-10-31）：按已定完成，sample 结果见场景文档"阶段 E1 结果"。core 新增 `dom.StyledText`（样式片段），`binding.compile.Bindings.TextConverter` 把绑定值转为片段，`UiDocument.load` 接收宿主的转换器；两版 `McResources` 把 `Component` 拆成片段，`McTextMeasurer` 按样式测量，`DrawCommand.Text` 带粗体、斜体、下划线、删除线、混淆标志。偏差：`<mc-item>`、`<img>` 及所有带载荷的原生元素默认 `display: inline`，按替换元素作原子行内盒（`LibrarySheets.nativeElements` 按标签表生成），而非写成 `inline-block`；flex 容器中的行内子元素按 CSS blockify。sample 中暴露的现有规则（未改）：组件文件只能有一个 `<template>`、不能链接主题，S8 每个条目页各自链接主题与 2–3 个组件；`title` 是全局属性，不能作组件参数名；主题层在组件层之上，组件 CSS 中与主题同名的类（如 `.title`）被主题覆盖，组件内类名应避开主题类名；`<mc-item>` 载荷只能 `bind:item`，正文图标经 ViewModel 的 record 传入。三轮加载错误都要进游戏才暴露，引出工具 T1。`-Pcheck=all` 160 项通过；testmod 1.21.1 只经 `build` 编译，未进游戏。

#### E2 文档内 tooltip

- 已定（2026-10-31）：库元素 `<ix-tooltip>`，不走 Web 标准的 `popover="hint"` + `interestfor` + anchor positioning。标准只能锚定元素、不能跟随光标；`interestfor` 只能写在 `<a>`/`<button>` 上，在 Ixchel 中会使触发者可获焦并进入 Tab 顺序；以 `id` 关联在 `<for>` 行中须拼唯一 id，违反上下文模型不变式 1。语义：
  - 作为任意元素的子元素，平时不参与布局与绘制；悬停目标沿 `Tooltips.render` 的最深者优先规则，取第一个带 `<ix-tooltip>`、`<mc-item>` 或 `title` 的元素。
  - 生效时进入 `TopLayer`（#014 "第二个场景需要时再加"的落地），按内容定尺寸，贴光标，碰屏幕边缘翻转，不参与命中测试；HUD 无指针不显示。
  - 内容在使用方环境编译，绑定、控制块、组件照常可用，组件体内可把 `<slot>` 放进 `<ix-tooltip>`；外观全由 CSS，主题加 `--ix-tooltip-*`。
  - `<mc-item>` 不带 `<ix-tooltip>` 时仍走原版 `renderTooltip(stack)`，其它 mod 的 tooltip 增强照常生效；带时用作者内容。`title` 保留为原版样式的纯文本。
- 已定（2026-10-31）：作者在自定义 tooltip 里沿用原版物品行（`Screen.getTooltipFromItem`，含其它 mod 经 `ItemTooltipEvent` 加的行），用 mc 层原生元素 `<mc-tooltip-lines bind:item="…">`，按 E1 的富文本绘制；放在 `<ix-tooltip>` 内时只在显示时参与布局、取行。物品显式传入而非取祖先 `<mc-item>`：tooltip 外（物品详情）也要能用，且与 `<mc-item bind:item>` 一致。命名：不叫 `mc-tooltip`——与 `<ix-tooltip>` 同后缀会被读成另一种 tooltip 容器，且暗示原版外框；`-lines` 对应 `ItemStack.getTooltipLines`，也表明只有文字行（收纳袋预览、Forge `GatherComponents` 的图像组件不在其中）。未选 ViewModel 字段（S3 的 1300 格网格要为每格预先算好）与 `$` 框架值（要在 `<ix-tooltip>` 内引入新的作用域名字）。
- 已定（2026-11-01）：不做全局替换原版物品 tooltip 的宿主（原 E3，在 `RenderTooltipEvent.Pre` 中接管）。tooltip 只针对 Ixchel 文档内的元素，原版物品 tooltip 经 `<mc-item>` 默认模式与 `<mc-tooltip-lines>` 兼容。
- 前置整理（E2 分支的第一个提交，行为不变）："内容归顶层、不进父元素排版"现以 `tag.equals("dialog")` 散在 `LayoutEngine`（3 处）、`Painter`、`EventDispatcher`（命中测试、Tab 焦点）共 6 处跳过点；收为 dom 中一处判断，`<ix-tooltip>` 接入时只加种类。dialog 居中与遮罩、select 选项列表等按种类的行为仍按种类分派；select 本身在正常排版中，不归入此类。
- 引擎：`TopLayer` 接纳 `<ix-tooltip>` 与按内容定尺寸、定位；两版 mc 层 `Tooltips` 的选取规则。容器界面中文档画在 `renderBg` 内、原版槽位物品与手持物品之前，tooltip 须在原版绘制之后单独绘制（现 `doc.drawTooltip` 处），否则被槽位物品盖住。
- sample：S1 属性说明、S6 配置注释、S7 节点 tooltip 由 `title` 改为 `<ix-tooltip>`，接受的外观变化记入结果；新建 S9 元素 tooltip 示例页。
- 验收：场景文档 §2 S9 的交互逐项写成检查，其中含滚动容器内元素的 tooltip 不被裁剪、贴边翻转、`<mc-item>` 的三种模式；`-Pcheck=all`。
- 结果（2026-11-01）：按已定完成，sample 结果见场景文档"阶段 E2 结果"。`TopLayer` 另存当前 tooltip（至多一个，不进 dialog/select 的打开序列）；显示、移动、隐藏都不重排文档，只对 tooltip 子树单独求尺寸，指针移动只重新定位；tooltip 有自己的显示列表（`UiDocument.tooltipDisplayList`），mc 层在原版绘制之后、`painter.topZ()` 之上单独画，没有 `<ix-tooltip>` 的目标（`title`、默认模式 `<mc-item>`）仍走原版绘制。偏差与补充：
  - 位置：指针右 8、上 16，右侧放不下翻到指针左侧，底部放不下上移，取整到像素。
  - 不设默认 `max-width`（与原版 tooltip 一致），由作者 CSS 给。tooltip 的层节点是 `align-items: flex-start` 的 column：用 row 时被 `max-width` 压窄的内容高度仍按不换行计算（Taffy），文字溢出外框。压窄后宽度取换行后最长行，不是 CSS 的 `max-width` 值。
  - 模板规则：顶层或嵌在另一个 `<ix-tooltip>` 内报 `misplaced-element`；`<input>`、`<img>` 只能含 `<ix-tooltip>`；`<select>` 可直接含一个；带载荷的原生元素子元素不限（S3 的数量角标写在 `<mc-item>` 里）。
  - 原生元素声明新增 `content=lines`（`TagRegistry.hasLineContent`）：不是替换元素，按 flex column 排版，子元素由宿主填为每行一个文本节点，模板中不得写子元素；`<mc-tooltip-lines>` 是唯一用户。
  - 取行：只在显示列表版本变化的帧后全树查找 `<mc-tooltip-lines>`，之后每帧只更新已知且可见的元素（不在任何 tooltip 内，或在正显示的 tooltip 内），逐行比较、有变化才写；隐藏 tooltip 内的元素不调用 `getTooltipFromItem`。
  - 悬停目标沿最深者优先，故 S7 的 tooltip 写在节点的图标 `<mc-item>` 内（否则图标作为更深的元素先命中，显示原版物品 tooltip）；节点图标外 5px 的边框区不再显示 tooltip。
  - `-Pcheck=all` 184 项通过；1.21.1 只经 `build` 编译，未进游戏。

#### I i18n

- 已定（2026-11-02）：见 `ref/SD-ixchel-i18n-20261102.md`（#027）。`t="键"`、`t:<属性>="键"`；译文 `%s`/`%n$s` 插入不带 `mark` 的子元素，`<名>…</名>` 复制 `mark="名"` 的空子元素（0..n 次）；`\n` 强制换行；依赖数据的文本由 Java 返回 `Component`；原文只在 `en_us.json`；名字保留 `t`；不预置 `<b>`/`<i>`。
- 引擎：core 翻译端口（取译文、当前语言）；解析器接受 `t`、`t:<属性>`、`mark`、`translate`，`t` 元素下丢弃空白文本；加载期展开译文（先于绑定编译，组件体与控制块内容同样展开），检查项见设计文档 §5，基准语言 `en_us` 严格；行内排版支持强制换行；选择器 `:lang()`，文档根写当前语言的 `lang`；mc 层以 vanilla `Language` 实现端口，两个 mc 版本；切换语言随资源重载重建。
- 工具：kit 以资源目录与 classpath 中的 `en_us.json` 为基准做译文检查；`lint --enable <码>` 打开默认关闭的码，字面文本警告为第一个；插件 `ixchel { lint { enable(...) } }`。
- sample：1.20.1 与 1.21.1 的所有界面文本（含 S8 正文）改为语言键，Java 侧文本改为 `Component.translatable`；附 `en_us`、`zh_cn`；用 `:lang(zh)` 调整至少一处中文外观（如强调去斜体）。
- 验收：切换游戏语言（`zh_cn`）后界面文本随之变化，写成 playtest；缺键与参数不符在加载期或首次取值时报告，core 单元测试覆盖；sample `ixchelLint` 打开字面文本警告为 0；`-Pplaytest=all`；1.21.1 `runClient` 冒烟。
- 结果（2026-11-02）：按已定完成，引擎、工具与 sample 的结果见 `ref/SD-ixchel-i18n-20261102.md` §7；偏差：S8 的排版样张条目 `typesetting` 不翻译（`translate="no" lang="en"`），其正文就是多文种排版样本。

#### U1 基础体验

- `text-shadow`：标准写法 `<x> <y> 0 <color>`，单层；模糊半径非 0 或多层开发期报错。文字画两遍，不走原版阴影开关。可继承，可过渡。
- `<for index="i">`：`i` 为从 0 起的行下标，只进表达式，随重排更新；不提供 `$index`（嵌套时外层下标取不到）。CSS 不写表达式（标准无此写法），结构伪类继续不排期。
- 默认点击音：core 加端口，mc 层用 `SimpleSoundInstance.forUI` 播 `UI_BUTTON_CLICK`；`<button>`、复选框、`<select>` 打开时触发，`disabled` 不响；作者可关闭。自定义音效走 ViewModel 动作。
- 滚动条：按住滑块拖动、点轨道翻页；宽度与颜色由 CSS 控制。
- 验收：每项 core 单元测试；sample 至少一处用到 `text-shadow`、`index`；playtest 覆盖滚动条拖动；`-Pplaytest=all`；1.21.1 `runClient` 冒烟。
- 结果（2026-11-02）：按上述完成。`text-shadow` 为 `TextShadow`，以 `DrawCommand.Text` 多画一遍实现。点击音端口为 `event.ClickSound`，`-ix-click-sound: none` 关闭，可继承。滚动条几何抽到 `layout.Scrollbar`，由绘制与命中共用；CSS 为 `scrollbar-width`（`auto`/`thin`/`none`）、`scrollbar-color`，不占布局空间。sample：env HUD 用 `text-shadow`，loot 列表用 `index` 做斑马纹，tooltips 列表测滑块拖动。

#### U2 拖拽

- 已定（2026-11-02）：用标准事件名 `draggable`、`dragstart`、`drag`、`dragend`、`drop`，符合 #003；不做 `dataTransfer` 与 `dragover` 允许放下的协议，拖动数据由 `state:` 或 ViewModel 记录，类型检查照常。库负责阈值、开始拖动后不发 `click`、取消与源元素被移除。`drop` 发给光标下带 `on:drop` 的元素，`dragend` 总是最后发给源。高亮用 `:hover`，不做 `dragenter`/`dragleave`；不做 `drop-if`，用 `bind:class` 加 `on:drop` 内判断。`<ix-drag-preview>` 沿用 `<ix-tooltip>` 模式，拖动时进 `TopLayer` 跟随光标、不参与命中。`<mc-slot>` 继续走原版交互。边缘自动滚动不做。
- sample：S7 平移迁移到新模型，删去 `press`、`travel`、`CLICK_SLOP`；S6 列表项排序（新增功能，单独写 playtest）。
- 验收：playtest 加 `dragTo(源, 目标)`；点击与拖动的区分、取消、源被移除有 core 单元测试；`-Pplaytest=all`。
- 结果（2026-11-02）：按已定完成。阈值为 `|dx| + |dy| > 3`，起拖的首个 `drag` 携带自按下起的位移，因此画布严格跟随指针；表单控件内按下不起拖。`TopLayer` 的 tooltip 槽位泛化为 `atPointer`，tooltip 与拖动预览共用布局、绘制与 `atPointerDisplayList`；拖动时不显示任何 tooltip，含原版。S7 删去 `press`、`travel`、`CLICK_SLOP`，`QuestsPlaytest` 断言不变；S6 列表对话框加抓手，新测试项 `config-sort`（落点在行外不改顺序；落到别行时占据其位置）。`PlaytestDriver` 加 `dragTo`、`dragToNth`。

#### U3 原生元素

- 目标：宿主用 Java 写新标签，模板中与内置元素同样使用，绘制由宿主负责。
- 宿主与库经重定位处在同一份类中，宿主代码可以直接实现库接口；#004 约束的是其它 mod。
- 验收：sample 加一个宿主原生元素，kit lint 能识别其标签与载荷类型；两版 mc 同改。
- 结果（2026-11-02）：宿主入口与 kit 类型检查已完成，声明形态由下面的 N1 替换。sample 为 `<sample-gauge bind:fraction>`（tooltips 页）；把载荷绑到 `String` 时 lint 报 `binding/payload-type` 且位置正确，截图确认填充比例与渐变。

#### N1–N2 原生声明与元素定义重构

- N1：原生标签、属性与载荷名由实现类的 `@NativeElement` 声明，载荷类型取 `NativePayload<T>` 泛型；宿主调用 `McElements.register(modid, painter)` 一次注册，kit 不初始化宿主类即可读取同一声明（#030）。两版 mc、容器槽位、sample 与 kit 一起迁移，删除资源声明文件与旧注册入口。
- N2：按已定的切面组合方案完成（#031）；复杂元素的校验、测量、绘制与默认交互归同一元素定义，select/option 为一个单元。阶段不再按闭合元素种类分支；DOM 不依赖流水线实现。宿主自定义 interaction/layout 暂不开放。动机与包边界见模块拆解 §2，声明适配见工具文档 §5。
- 验证（2026-11-02）：全量 `build`、core/kit 测试与两版 sample lint 通过。Forge `-Pplaytest=all`：13 个测试项、201 项断言通过，0 失败；检查了 select 弹层与宿主 gauge 截图。NeoForge 1.21.1 经临时入口实际打开 `UiScreen`，验证文本编辑、checkbox、双向绑定、select 弹层及键盘选择、dialog 模态显示、Escape 关闭回写；截图确认原生物品、下拉层与模态遮罩。首次启动停在无障碍引导页，修正临时入口后完成冒烟；入口和临时资源已移除。未进入世界，未新增 1.21.1 永久 playtest。

#### V1 盒子外观

- 已定（2026-11-02，语言文档 §6-12）：
  - 圆角只做外观（背景、边框、`box-shadow`），经路径 C 落地（#029）；圆角裁剪依赖 FX1，此前 `overflow` 配圆角按矩形裁剪，lint 警告。
  - `::before`/`::after` 只做装饰：`content` 只接受 `""` 与 `none`，其它取值开发期报错（CSS 字符串会绕开 i18n 与 lint 的字面文本检查）。伪元素不进 `querySelector`，事件与 `:hover` 算在宿主元素上。
  - 多层背景：`background-image` 与其余 `background-*` 改为逗号列表，前者在上，各列表按位置对应，短列表按标准循环补齐。层的取值为图片或渐变。
  - 渐变：`linear-gradient`，角度或 `to <方向>`，色标可带位置；不做 `repeating-`、`radial-`、`conic-`。
  - `box-shadow`：多重阴影，offset、blur、spread、color，含 `inset`；可过渡，生成的纹理按整数像素量化缓存。
  - `border-image`：source、slice、width、`fill`、stretch/repeat/round；`image-rendering: pixelated` 同批做（MC 九宫格贴图多为像素风）。
  - `:not`/`:is`/`:where` 的参数为完整选择器列表（可带组合子），优先级按标准。
- 验收：每项有 core 黄金测试；sample 选现有场景页加一处使用并写成 playtest 截图（多层背景、渐变、阴影、圆角、`border-image` 像素风、伪元素）；`-Pplaytest=all`；两个 mc 版本，1.21.1 `runClient` 冒烟。
- 结果（2026-11-03）：按已定完成。伪元素挂在宿主上、不进 `children()`，块容器中按 inline-block 原子排布，绝对定位或块级伪元素在文本块内仍报 `layout/block-in-inline`（V2 匿名块后放开）；不排布子元素的原生元素忽略伪元素。`border-image` 只收 `url()`、不做 outset，图片尺寸未知时报 `paint/border-image-unsized` 并画普通边框。圆角裁剪警告为样式钩子（`paint/rounded-overflow-clip`），lint 与加载期都报。遮罩按 1/8 像素量化缓存，非整数像素。`<img>` 未入图集。`background-*` 改为列表后，绑定的裸数字按一层 px 处理（machine 页回归）。sample：1.20.1 quests 详情面板（截图 `quests-detail`），1.21.1 弹药 HUD。Forge `-Pplaytest=all` 13 项、202 项断言通过；1.21.1 经 quickPlay 进入世界截图确认 HUD。

#### V2 尺寸与排版

- 已定（2026-11-02，语言文档 §6-13）：
  - `display: block` 含块级子元素时用 taffy 标准块布局，含外边距折叠；块级与行内内容混排时生成匿名块，不再报 `BLOCK_IN_INLINE`。
  - `line-height`：`normal`（等于现行行高，现有截图不变）、无单位、px、em、%。
  - 单位：em、rem（根字号，初值 9px）、vw、vh（GUI 视口，即 `UiDocument.frame` 的尺寸），在计算样式时换算为 px。
  - `calc()`：所有长度属性，`+ - * /`，同批 `min()`/`max()`/`clamp()`；含 % 的部分在布局时经 taffy `CalcExpression` 求值。`Length` 改为"百分比 + 像素"，px 与 % 之间的过渡按标准分量插值，不再跳变。
  - grid：接上 taffy 支持的全部写法（模板行列、`fr`、`repeat` 含 auto-fill/auto-fit、`minmax`、命名线、areas、放置），不做 subgrid。
  - 补定（2026-11-03）：含 % 的 `min()`/`max()`/`clamp()` 不是线性式，`Length` 另需表达式形态，过渡按标准 calc-mix 插值。vw/vh 在计算样式时换算，视口变化须重算用到视口单位的样式，不只重新布局。taffy 不生成匿名块，由 core 把连续行内内容包成段落叶子；只含行内内容的块仍是现行段落叶子。其余未写明处按标准：原生元素与表单控件认 `line-height`，`font-size` 收 %（相对父字号），grid 同批接 `justify-items`/`justify-self`、`place-*`、`grid-area`/`grid-row`/`grid-column` 简写与 `grid-auto-*`，`align-content` 对 flex 与 grid 都生效。
- 验收：core 黄金测试覆盖块布局与外边距折叠、匿名块、各单位、calc 与过渡；现有截图不变；sample 选一处网格布局写成 playtest 截图；`-Pplaytest=all`；1.21.1 `runClient` 冒烟。
- 结果（2026-11-03）：按已定完成。相对单位在计算样式时逐元素换算为 px，顶层元素的 rem 取 9px；用到 vw/vh 后视口变化时整体重算样式。`calc()` 只在收 % 的属性上保留到布局，由 taffy 求值。`line-height` 按 CSS half-leading，`normal` 等于测量器行高，表单控件行盒垂直居中。块布局下 flex 与 grid 项是 BFC 根；`layout/block-in-inline` 只在 `display: inline` 内报。简化：流式布局中 inline-block 的基线取下外边距边。grid 属性不可动画；`justify-items` 初值按 stretch；`align-content: normal` 对应 taffy AUTO，flex 行为不变。taffy 限制：行方向 `minmax(定长, auto)` 的最小值被忽略；有 auto 重复时负线号只数非 auto 重复的轨道；auto 重复之内或之后的命名线报 `css/unsupported-value`。sample：1.20.1 guide 配方格改用 grid（截图 `guide-storage`，像素不变）。Forge `-Pplaytest=all` 13 项、202 项断言通过；1.21.1 经 quickPlay 冒烟通过。

#### V3 动画

- 已定（2026-11-02，语言文档 §6-14）：
  - ~~`leave="class"` 块属性~~ 由补定 2 取代。
  - 时间：动画与过渡用墙钟（`System::nanoTime`），游戏暂停不冻结，视效不跟逻辑时间。
  - `@keyframes` 与全部 `animation-*` 长写及简写，含 `animation-play-state` 与 `steps()`；与 `bind:style` 的优先级按标准层叠。
  - 事件：`on:animationend`（播完移除提示、一次性强调后去掉 class）；不做 `animationiteration`（次数用 `animation-iteration-count`）。现无 `transitionend`，不同批补。
  - ~~`@starting-style`~~ 由补定 2 取代。
  - 补定（2026-11-03）：离场等子树内的过渡与有限次动画结束，无限动画不等。离场中恢复复用离场实例，不再重建。`<for>` 离场行原地不动、不参与重排，离场逻辑取最简实现。其余按标准：离散属性在动画进度 50% 处切换，`animationend` 冒泡，伪元素上的动画以宿主为事件目标。
  - 补定 2（2026-11-03，#032）：进出单元的根元素带三个引擎维护的伪类，作者只写 CSS。
    - 单元：`<if>`/`<for>` 副本的顶层元素；文档顶层元素（整页加载时进场）；顶层条目（`<dialog>` 开关、`<select>` 列表、`<ix-tooltip>`、`<ix-drag-preview>`）。UiScreen 关闭与 UiHud 显隐在 V4。
    - `:-ix-absent`：进场第一帧与整个离场；进场第二帧去掉，常态样式按 `transition` 从它过渡过来。`:-ix-entering`：插入起到子树过渡与有限动画结束。`:-ix-exiting`：移除起到结束，之后才真正移除。过渡参数按标准取变化后的样式，故各方向参数分别写在 `:-ix-entering`、`:-ix-exiting` 上。
    - 离场期间 inert（不命中、不聚焦）、停绑定更新；顶层条目离场时立即不再模态、不再算打开。进场期间可交互。
    - 只匹配单元根，后代经组合子选择；外层进出时内层单元不单独播；整页加载时页内块的副本不单独播。中途反悔两阶段互换，过渡从当前值反向。
    - 文档样式表不出现这三个伪类时，进出立即完成，不延后、不多一帧。
    - 删除 `@starting-style` 语法（报 `css/unsupported-at-rule`，hint 指向伪类）与块属性 `leave`（报 `template/unknown-block-attribute`）；StyleEngine 的起始样式机制不保留为语法。`display: none` 显隐不算进出。
- 性能：无限动画使干净帧失效，开工时用 `FrameTimer` 预算核查，核查对象含 S3、S8。
- 验收：core 测试用可替换时钟断言关键帧插值、`steps()`、暂停、`animationend`，以及三个伪类在块、顶层条目、整页加载上的进出、反悔、嵌套、无伪类时立即进出；sample 一处对称的入场与离场动画写成 playtest 截图序列；`-Pplaytest=all`；1.21.1 `runClient` 冒烟。
- 结果（2026-11-03）：按补定 2 完成。`dom.Presence` 管理全部进出单元，经 `Presence.Motion` 端口询问 `Animations.finishing`/`Transitions.running`；`StyleEngine.uses` 判断样式表是否用到三个伪类，未用到时进出同步完成。进场：插入帧以 `:-ix-absent` 计算样式，下一帧去掉，transition 从它开始；整页加载时加载期的样式即首帧，第一帧就开始过渡。单元在一次样式计算之后才检查运动，事件里发起的进出不会被提前判定结束。`<if>`/`<for>` 改用 `Presence`，删除 `Departures`、`Bindings.Motion` 与 `StyleEngine` 的起始样式索引。偏离：顶层条目离场期间保留 `:open`（UA 的 `dialog:open { display: flex }` 等依赖它，去掉就立即隐藏，无从播放），只是 `TopLayer.isOpen`、`modal`、`top` 不再算它，命中跳过它；dialog 离场时 inert，select 列表离场时 select 本身仍可操作。宿主隐藏 tooltip 时就地离场；替换为另一个时旧的立即消失；`<ix-drag-preview>` 同理。sample：1.20.1 quests 详情面板改为 `.detail:-ix-absent { transform: translateX(120px) }` 加 `:-ix-exiting` 改缓动的对称写法，模板不再带属性；截图 `quests-detail-entering`、`quests-detail`、`quests-detail-leaving`。
  - 验证：core 247 项测试通过（`UiDocumentPresenceTest` 6 项：整页进场与内层不单独播、`<if>` 进出与反悔、无伪类立即进出与不等无限动画、`<for>` 原地离场与收回、dialog 离场、旧语法报错）；全量 `build` 与两版 lint 通过；Forge `-Pplaytest=all` 13 项、204 项断言通过，S3、S8 的 `FrameTimer` 预算通过（storage 滚动 p95 9.1ms/14ms，guide 1.9、2.7ms/3、3.5ms）；1.21.1 `runClient` 到标题界面、资源重载无报错，未进世界。
  - 已知：离场期间 `:hover` 保留到指针移动；tooltip 与拖拽预览的进出没有 core 测试（与 dialog 同一套 `TopLayer` 代码）。

#### V4 整页开关动画

- 已定（#032）：UiScreen 关闭、UiHud 显隐复用 V3 的三个伪类，作者写法不变；不需 mixin。
- 引擎：UiScreen 关闭（`onClose` 与外部 `setScreen` 触发的 `removed()` 都算）时立即 `setScreen`，输入归还游戏；文档生命周期从 Screen 拆出，由加载器层绘制钩子画离场残影（关到游戏画在 GUI 层，关回父界面画在 `ScreenEvent.Render.Post`），播完再关闭文档。UiHud `show(false)` 在自己的 HUD 层画到离场结束。两个 mc 版本。
- 已定（2026-11-03）：`UiContainerScreen` 离场时快照可见槽位内容（`container.SlotSnapshot`），跟随各自 `<mc-slot>` 的绘制位置；只跟位置，不缩放、不随 `opacity` 淡出（FX1 前也没有整组淡出）。UiScreen 打开等同文档顶层进场（V3 已实现，无新代码）。
- 验收：一个 Screen 与一个 HUD 的进出写成 playtest 截图序列；关闭动画期间玩家能移动；1.21.1 `runClient` 冒烟。
- 结果（2026-11-03）：完成。core：`UiDocument.exit/exiting/enter`，单元为加载时的文档顶层元素；离场期间绑定停止；离场结束后保持 `:-ix-absent:-ix-exiting`（`Presence.rest`），否则恢复样式会触发回程过渡，下次进场就不再从 absent 起步；`enter` 时离场中的原路返回，已结束的从 absent 进场（`Presence.enterAgain`）。mc：`document.ExitingDocuments` 持有已关屏幕的文档，`removed()` 时 view model 立即关闭、文档继续画；`UiHud.show` 走同一 `exit/enter`，画到离场结束。加载器：`installScreenExits()` 监听 `RenderGuiEvent.Post`（无屏幕时）与 `ScreenEvent.Render.Post`，`registerHud` 已包含；只用 `UiScreen` 的宿主须自行调用一次，否则屏幕立即关闭并警告一次。`UiContainerScreen`（仅 1.20.1）用 `SlotSnapshot`。sample：waypoints 与 env 进出，character 只做离场（屏幕打开时原版按布局位置画槽位物品，不跟随 transform）；截图 `waypoints-entering`/`-leaving`、`env-entering`/`-leaving`、`character-leaving`。
  - 验证：core 249 项测试通过（新增文档离场/反悔/再进场、无规则立即离场）；全量 `build` 与两版 lint 通过；Forge `-Pplaytest=all` 13 项、213 项断言通过，含关闭后屏幕为空、文档仍在离场且玩家已走动；预算通过（storage 滚动 p95 10.1ms/14ms，guide 1.9、2.7ms/3、3.5ms，env 2.3、3.9ms/6ms）。1.21.1 经临时入口在标题界面打开带 `:-ix-absent` 的 `UiScreen` 并关回标题：离场中叠在标题界面上（截图确认），结束后消失，日志无错误；入口与临时资源已移除。未进世界，1.21.1 的关到游戏（GUI 层）与 HUD 显隐未实跑。
  - 已知：屏幕 `renderBackground` 的暗化在关闭时立即消失，不随文档；顶层 `<if>`/`<for>` 副本不在文档单元内，不随整页离场，文档结束时一起消失；at-pointer 内容不画离场；A 打开 B 时 A 的残影叠在 B 上；屏幕实例不可重开（view model 已关，与之前一致）。

#### FX1 私有图层与 shader 注册

- 设计依据：`ref/SD-ixchel-ui-effects-20261102.md` §3、§4（#028）。
- 引擎：mc 层 classpath `ResourceProvider` 与 `ShaderInstance` 构造，程序名 `<宿主modid>_fx_<名>`；加载器胶水层经 `RegisterShadersEvent` 注册、随资源重载重建；私有图层栈（`TextureTarget` 带深度，进出时 flush 并保存、恢复 FBO、viewport、投影、scissor、混合、深度状态，帧内按尺寸复用、用完释放）；`opacity` < 1 的元素走图层，改为整组语义，原生元素随之淡出。两个 mc 版本。
- 验收：重叠子元素半透明与带 `<mc-item>` 的淡出写成 playtest 截图；F3+T 资源重载后绘制正常；其余现有截图不变；`-Pplaytest=all`；1.21.1 `runClient` 冒烟。

#### FX2 标准滤镜与混合

- 先定：`filter` 函数与 `mix-blend-mode` 模式的首批子集（倾向颜色矩阵类函数与 `blur`；`drop-shadow` 是否同批）。
- 引擎：`filter` 走 `layer` 机制，`backdrop-filter` 走背景快照（降采样 + 两遍模糊），`mix-blend-mode` 合成时读父目标；core 解析与开发期报错，不支持的函数报码。两个 mc 版本。
- 验收：每个函数与模式各一张 playtest 截图；带 `backdrop-filter` 的 HUD 进 `FrameTimer` 预算。

#### FX3 具名特效

- 先定：首批内置特效的范围。
- 引擎：`.effect` 解析（`kind`、类型化 `param`）、uniform 与 fsh/vsh/json 生成、GLSL 原语库；`<link rel="effect">`；`background-image: paint(名)`、`filter: -ix-paint(名)`、`backdrop-filter` 中的特效；参数自定义属性按声明类型校验并可 `transition`；错误码 `effect/*`，GLSL 编译错误行号映射回 `.effect`；内置特效经 `@ixchel/effects/…` 引入。
- 工具：kit lint 覆盖 CSS 一侧的检查；schema 列出特效与参数。
- sample：一个场景用一个 `box` 特效与一个 `layer` 特效，其中一个参数由 `bind:style:--x` 驱动。
- 验收：上述用法写成 playtest 截图；错误用法逐项由 lint 报码；改动 `.effect` 后 F3+T 生效；在生产实例装 Oculus + 光影包 + ImmediatelyFast + Embeddium 实测截图并与不装时对比；1.21.1 `runClient` 冒烟。

#### Doc 用户文档

- 先定：站点结构按 Diátaxis 四类（教程、操作指南、参考、原理）；哪些参考页由代码生成（CI 文档 §2：属性注册表、元素注册表）。
- 内容：面向宿主开发者（接入、ViewModel）与资源包作者（模板、CSS、组件覆盖，不写 Java）；语言参考写成规范：数值按 JLS 提升与整除、`==` 按值比较，另列与 Web/JS 的偏离清单（初值、`null` 规则、无 `null` 字面量等）；原理页取自上下文模型文档。
- 工程：VuePress 源码放 `docs/`；生成参考页的 Gradle 任务；`vuepress build` 进 `check`（CI 文档 §7 的触发条件即 `docs/` 建立）。部署 Pages 仍按 CI 文档等仓库转公开与 `release` 落地。
- 验收：`check` 通过；按教程从零写出一个带组件的界面。

#### T1 静态检查

- 设计依据：`ref/SD-ixchel-tooling-and-editor-20261031.md` §3–§7（#021、#023）。
- 宿主 API：ViewModel 加 `@UiTemplate`；`UiScreen`、`UiContainerScreen`、`UiHud` 主路径从注解取模板，显式 id 重载作出口，另有无模型重载；`registerHud(modBus, Class, Supplier)`；开发期警告未声明组合与"声明 `Cell` 实装 `Value`"。sample、testmod（含 shade-a/b）全部迁移，两版同改。
- 引擎：`Compiler` 读成员表（实例反射 / 类反射）；原生元素声明改为 mc 层包路径下的行格式数据文件，由 core `TagRegistry` 解析，带载荷接受类型；`DiagCode` 与各包错误码 enum，`Diagnostic` 加 code 与 hint，迁移全部报错调用点，测试改断言码与位置。
- 工具：`tools/kit`（`:ixchel-kit`，只依赖 core）的 `lint`、`schema`、`--explain`，仅项目内入口；sample 与 testmod 各加一个挂在 `check` 上的 lint JavaExec 任务，照抄对外配方。
- 验收：E1 三类错误（多个 `<template>`、组件文件链接主题、参数名与外壳属性冲突）、未知绑定名、`bind:value` 指向只读 `Cell`、`<for>` 遍历非列表、组件参数不符均由 lint 报出并带位置与错误码；现有模板零错误；`-Pcheck=all` 不变；`build` 通过。
- 结果（2026-10-31）：按已定完成。验收探针逐项报出：`template/component-file-content`、`load/library-sheet-not-on-page`、`template/reserved-member-name`、`binding/unknown-name`、`binding/two-way-target`、`binding/value-type`、`binding/unknown-target`；sample 与 testmod `lintUi` 0 错 0 警（所有页面均被注解认领）；`-Pcheck=all` 160 项通过；core 测试断言全部改为"码@位置"，输出格式只由 `DiagnosticsTest` 固定。偏差：
  - 静态入口是 core 的 `UiDocument.check(template, file, files, tags, shape)`，用占位值编译、不写值、不排版；排版期错误（如 `layout/inline-box-property`）不在 lint 范围。
  - 模板 id 匹配放在 `UiTemplate.Ids`（`*` 不跨 `/` 与 `:`）；挂载点经包内 `mc.DeclaredTemplate` 取唯一具体 id，类上只有通配时须显式传 id（`GuideScreen`）。
  - 没有注解认领的页面不是只做结构检查，而是以空成员表完整检查后去掉 `binding/unknown-name`，另报 `model/unassociated-template`；组件文件按页面链接找出，不单独当页面。孤立（无人链接）的组件文件按页面检查，报 `template/misplaced-element`。
  - `<mc-slot>` 的容器限定现由 `ContainerLayout` 类上的 `@NativeElement(containerOnly = true)` 声明（N1），kit 记录哪些标签只属于容器界面。
  - `bind:<参数>` 编译失败时不再连带报 `binding/missing-parameter`（kit 冒烟时暴露的连锁误报）。
  - kit 不用 JavaExec 的 classpath 加载宿主类：ModDevGradle 打过补丁的 Forge jar 保留原签名，JDK 加载器报 SHA-384 digest error；改为 classpath 只放 kit，宿主运行时 classpath 经 `--classpath` 传入自建的不校验签名的加载器（父优先，core 类型不重复）。
  - schema 列出标签与载荷类型、longhand 与 shorthand、库样式表及其 `--ix-*` 变量（为此公开 `PropertyRegistry.shorthands()`、`LibrarySheets.names()`/`themeVariables()`）、组件、ViewModel 成员表；CSS 取值语法暂无公开描述，未列出。
  - 错误码在 kit 中靠扫描 core 与 `com/azane/ixchel/` 所在 classpath 根的 `DiagCode` enum 得到，不维护名单；kit 自有码为 `model/missing-template`、`model/invalid-template-id`、`model/not-a-view-model`、`model/unchecked-template`、`model/unassociated-template`。

#### T2 游戏内检查

- 设计依据：同上 §8（#022）。
- core 新包放 `Driver`、运行器、`InputPort`/`Pump` 端口与帧统计，`paintedCenter` 公开到 `hitTest` 旁；1.20.1 mc 层实现输入、截图、服务端步骤、槽位、开发世界、失败转储与非 0 退出；forge-1.20.1 提供 `Pump`；`-Dixchel.check.<modid>` 开启。
- sample 的 `Script`、`Checks`、`BenchWorld`、`FrameTimer` 删除，场景改用库内框架，只做等价重构。1.21.1 不适配，记为缺项。
- 验收：`-Pcheck=all` 160 项结果不变；人为制造失败时进程非 0 退出并产出转储；`build` 通过。
- 结果（2026-10-31）：按已定完成。core `check`：`Driver<S>`（自类型泛型，宿主子类的步骤可与库内步骤链式混用；宿主经 `then(Step)` 加步骤）、`Runner`、`InputPort`、`Pump`、`FrameStats`、`Dump`；mc `check`：`Checks`、`Script`、包内 `ScreenInput`；`IxchelForge.installChecks`。场景只改 import、`register(Checks)` 签名与 `FrameTimer.hud`，断言不改；`-Pcheck=all` 160 项通过；把 index 检查的期望行数加一后 `runClient` 以退出值 1 失败（存档先保存完），`run/checks/ixchelsample/dumps/index-1.txt` 含失败项、当时界面与文档树。偏差：
  - 帧计时不再挂 Forge 的屏幕与 HUD 渲染事件，改在 mc 根包 `FrameTimer`，由 `UiScreen`、`UiContainerScreen`、`UiHud` 的 `render` 首尾记录，计时范围仍是整个 `render`；HUD 按 `UiHud` 实例选定（`FrameTimer.hud(hud, n)`），sample 的 `EnvHud.LAYER` 随之删除。
  - 开发世界不再挂标题界面的 init 事件：`Pump` 首次在无玩家且显示标题界面时打开存档，加载器层只提供 `Pump`。
  - 光标（`MouseHandler.xpos/ypos`）与槽位所需的 GUI 原点（`leftPos/topPos`）按 Mojang 名反射：Forge 的 `getGuiLeft` 不在 mc 层 classpath 上；检查只在开发期运行。
  - 非 0 退出靠关机钩子里 `Runtime.halt(1)`：`mc.stop()` 正常退出码恒为 0，钩子在存档保存、窗口关闭之后执行。
  - 报告改为 `run/checks/<modid>/report.txt`，转储在同目录 `dumps/<检查>-<序号>.txt`；PASS/FAIL 日志行格式不变，汇总行加了 modid。
  - 只做失败时自动转储；按键或命令手动转储（工具文档 §8）未做，待有需要时由加载器层加。shade-a 的 `ContainerCheck` 仍是自己的步骤队列，未迁移。

### 3. 风险

| 风险 | 阶段 | 对策 |
|---|---|---|
| 表达式类型检查与路径解析（record 组件、泛型）耦合 | L1 | 只在现有路径解析上加运算符，不另写解析 |
| 组件编译改动绑定核心 | L2 | 模板组件不引入子 Scope，组件体复用 `Compiler` 以端口为环境编译（上下文模型文档 §3.3、§3.5） |
| 行内排版在 Taffy 之外自写 | E1 | 只在子节点全为行内内容的元素上启用，作为带测量函数的叶子，Taffy 树其余部分不变 |
| 外壳元素改变树结构，check 选择器失效 | L2 | 迁移与选择器同提交，断言不改 |
| 1.21.1 落后 | D0、L3 | 两版同改，`sample-neoforge-1.21.1` 冒烟 |
| 错误码迁移触及约 195 个报错调用点与大量断言消息的测试 | T1 | 按包逐个迁移，调用点与测试同提交，测试只改断言对象不改场景 |
| 成员表改造影响运行时绑定 | T1 | 实例反射来源保持现有行为，`-Pcheck=all` 与 core 测试为回归网 |
| 提取检查框架时改变时序（两步之间先画一帧） | T2 | `Pump` 保留"tick 结束且该帧已画完"语义，场景断言不改 |
| 私有图层改动绘制主路径 | FX1 | 只有 `opacity` < 1 的元素走图层，其余绘制不变；现有 playtest 截图为回归网 |
| 光影、ImmediatelyFast 等渲染 mod 共存 | FX1–FX3 | 遵守渲染约定（flush、`GlStateManager`、状态原样归还）；FX3 在生产实例实测 |
| 每帧离屏绘制的开销 | FX2、FX3 | 进 `FrameTimer` 预算；超标时缓存未变脏子树的图层、只重跑合成 |

### 4. 不排期

有真实消费方时再排：

- 带 Java 逻辑的组件（语言文档 §3.4）。
- `@import`（语言文档 §4.4）。
- 性能，触发条件为 `FrameTimer` 预算超标或大列表（S3、S8）卡顿，按序做：Painter 按视口裁剪 > `contain` 与局部重排 > `Derived` 相等截断的代价。现状为首轮评审时的判断，开工前复核。动画时间域已定为墙钟（V3），不再列入。
- 游戏 UI 相对 Web 的缺口：`on:keydown` 指向按键绑定（`KeyMapping`，随玩家改键）；声明式世界锚定（现需 Java 侧 `WorldProjection.toGui` 计算后绑定）。
- 控件与语言：`<textarea>`、IME（中文已可上屏，系统预编辑需平台 API，与零全局副作用冲突）、radio、结构伪类、`+`/`~`、table、横向滚动、`<dialog>`/`<select>` 离场、`drop-if`、`dragenter`/`dragleave`、拖拽边缘自动滚动。
- 工具：游戏内调试桥（做 web editor 时先梳理 user 与 agent 的真实开发路径再设计）；JEI/EMI/REI 接入点（库只暴露查询，宿主写插件）；playtest 机器可读报告与元素截图、VS Code Custom Data、文本滚动。
- 整合包可用的发行形态（另一个宿主 mod，未分析）。
- 附属 mod 扩展主 mod 界面的库原语：由 JDK `Map` 构造的模型、运行时按 id 装入片段的挂载点（工具文档 §11）；1.21.1 游戏内检查适配。
- 场景扩展：场景文档 §5。
