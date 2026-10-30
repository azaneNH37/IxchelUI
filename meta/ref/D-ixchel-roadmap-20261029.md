---
tags: [design]
date: 2026-10-29
origin: 模板与样式语言定出目标形态后，需要把它与剩余 sample 场景排成一条顺序；原先顺序分散在场景文档 §3 与语言文档 §7
related: [ref/SD-ixchel-sample-scenarios-20261028.md, ref/SD-ixchel-template-and-style-language-20261029.md, ref/SD-ixchel-context-model-and-components-20261030.md, DECISIONS.md#015]
---

# Ixchel UI: Roadmap

## Summary

实现顺序与状态的唯一来源；其它 meta 文档只引用本文。已完成 0、A、B、C、D0、L1、D、L2；接下来是 L3（主题）、E（S8、S9）、I（i18n）、Doc（用户文档）。sample 迁移只做等价重构，以 `-Pcheck=all` 为回归网。

## Detail

### 1. 规则

- 本文只记顺序、范围、开工前要定的问题、验收与状态。各阶段的结果写进对应主题文档：场景结果写场景文档的"阶段 X 结果"，语言设计的定案写语言文档对应小节；本文只把状态改为完成并引用结果位置。
- 每个阶段是一批改动，一个分支、一个 PR（项目 AGENTS.md）；阶段末更新 meta、开 PR、合入。
- 迁移现有 sample 只做等价重构：不顺带加功能，check 只改选择器、不改断言。语言文档 §2 示例中的确认框与帮助开关只是演示，不在迁移范围内。
- 影响 mc 层的改动同时改 1.20.1 与 1.21.1，以 testmod 的 `runClient` 冒烟。
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
| L3 | 待做 | — | 主题层与主题、`var()` |
| E | 待做 | S8、S9 | 行内流式排版、tooltip 宿主、`Component` 富文本 |
| I | 待做 | — | i18n：`t` 语言键与参数 |
| Doc | 待做 | — | 用户文档站 `docs/` 与语言参考 |

依赖：D0 → L1 → D → L2 → E；L3、I、Doc 都依赖 L2，彼此及与 E 互不依赖，可并行或互换；Doc 写到的已实现部分随各阶段补齐。0–C 的结果见场景文档 §3。

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

#### E S8 指南书、S9 增强 tooltip

- 引擎：跨 `span` 的行内流式排版与行内链接命中；tooltip 宿主（`RenderTooltipEvent.Pre`，接管原版由宿主 API 开关开启，#009）；`Component` 富文本（S9 的 tooltip 行与物品名带颜色与格式，现被 `getString()` 拍平；建模按 GROUND 的"不透明宿主文本对象"方向）。
- sample：S8 页面类型用组件写；S9。
- 验收：场景文档 §2 S8、S9 的交互即检查清单。

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

### 3. 风险

| 风险 | 阶段 | 对策 |
|---|---|---|
| 表达式类型检查与路径解析（record 组件、泛型）耦合 | L1 | 只在现有路径解析上加运算符，不另写解析 |
| 组件编译改动绑定核心 | L2 | 模板组件不引入子 Scope，组件体复用 `Compiler` 以端口为环境编译（上下文模型文档 §3.3、§3.5） |
| 外壳元素改变树结构，check 选择器失效 | L2 | 迁移与选择器同提交，断言不改 |
| 1.21.1 落后 | D0、L3 | 两版同改，testmod 冒烟 |

### 4. 不排期

有真实消费方时再排：

- `ix-` 组件库内容；带 Java 逻辑的组件（语言文档 §3.4）；宿主原生元素登记入口（§6-6）。
- `@import`（语言文档 §4.4）。
- 性能，触发条件为 `FrameTimer` 预算超标或大列表（S3、S8）卡顿，按序做：Painter 按视口裁剪 > `contain` 与局部重排 > `Derived` 相等截断的代价 > tick 与帧两个时间域（动画用游戏时间，现为 `System::nanoTime`，暂停不冻结）。现状为首轮评审时的判断，开工前复核。
- 游戏 UI 相对 Web 的缺口：`on:keydown` 指向按键绑定（`KeyMapping`，随玩家改键）；交互音效（控件点击音）；`border-image` 九宫格与 `image-rendering: pixelated`；声明式世界锚定（现需 Java 侧 `WorldProjection.toGui` 计算后绑定）。
- 整合包可用的发行形态（另一个宿主 mod，未分析）。
- 场景扩展：场景文档 §5。
