---
tags: [design]
date: 2026-10-29
origin: 模板与样式语言定出目标形态后，需要把它与剩余 sample 场景排成一条顺序；原先顺序分散在场景文档 §3 与语言文档 §7
related: [ref/SD-ixchel-sample-scenarios-20261028.md, ref/SD-ixchel-template-and-style-language-20261029.md, ref/SD-ixchel-context-model-and-components-20261030.md, DECISIONS.md#015]
---

# Ixchel UI: Roadmap

## Summary

实现顺序与状态的唯一来源；其它 meta 文档只引用本文。已完成 0、A、B、C、D0、L1、D、L2、L3、E1、T1、T2、E2；接下来是 I（i18n）、Doc（用户文档）。sample 迁移只做等价重构，以 `-Pcheck=all` 为回归网。

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
| I | 待做 | — | i18n：`t` 语言键与参数 |
| Doc | 待做 | — | 用户文档站 `docs/` 与语言参考 |
| T1 | 完成 | 2026-10-31 | 静态检查：`@UiTemplate`、成员表编译、原生元素声明数据、错误码、`ixchel-kit` 的 lint 与 schema（`ref/SD-ixchel-tooling-and-editor-20261031.md`） |
| T2 | 完成 | 2026-10-31 | 游戏内检查从 sample 提取进 core / mc / 加载器层（仅 1.20.1） |

依赖：D0 → L1 → D → L2 → E1 → E2；L3、I、Doc、T1、T2 都依赖 L2，彼此及与 E 互不依赖，可并行或互换；Doc 写到的已实现部分随各阶段补齐。0–C 的结果见场景文档 §3。T3（对外发布）随 Doc，T4（编辑器）及之后见工具文档 §10。

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

- 先定：参数写法（按 MC 语言文件的位置参数 `%s`/`%1$s`，模板中如何给出参数表达式）；参数是否经 `$` 命名空间暴露（#016）；字面文本警告的范围。
- 引擎：`t="语言键"`（语言文档 §3.5 保留项）与参数；core 只定义翻译端口，mc 层以 vanilla 语言表实现，两个 mc 版本；语言切换后重新取值；组件体内的 `t` 同样可用；字面文本开发期警告（绑定文档 §3 i18n 行）。
- sample：所有界面文本改为语言键，附 `en_us` 语言文件。
- 验收：切换游戏语言后界面文本随之变化；缺键与参数个数不符在加载期或首次取值时报告；`-Pcheck=all`。

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
  - `<mc-slot>` 声明在 `container/elements.txt` 单独成文件，不用 `host=` 字段；kit 记录哪些标签只属于容器界面。
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

### 4. 不排期

有真实消费方时再排：

- `ix-` 组件库内容；带 Java 逻辑的组件（语言文档 §3.4）；宿主原生元素登记入口（§6-6）。
- `@import`（语言文档 §4.4）。
- 性能，触发条件为 `FrameTimer` 预算超标或大列表（S3、S8）卡顿，按序做：Painter 按视口裁剪 > `contain` 与局部重排 > `Derived` 相等截断的代价 > tick 与帧两个时间域（动画用游戏时间，现为 `System::nanoTime`，暂停不冻结）。现状为首轮评审时的判断，开工前复核。
- 游戏 UI 相对 Web 的缺口：`on:keydown` 指向按键绑定（`KeyMapping`，随玩家改键）；交互音效（控件点击音）；`border-image` 九宫格与 `image-rendering: pixelated`；声明式世界锚定（现需 Java 侧 `WorldProjection.toGui` 计算后绑定）。
- 整合包可用的发行形态（另一个宿主 mod，未分析）。
- 附属 mod 扩展主 mod 界面的库原语：由 JDK `Map` 构造的模型、运行时按 id 装入片段的挂载点（工具文档 §11）；1.21.1 游戏内检查适配。
- 场景扩展：场景文档 §5。
