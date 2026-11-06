---
tags: [design]
date: 2026-10-29
origin: 模板与样式语言定出目标形态后，需要把它与剩余 sample 场景排成一条顺序；原先顺序分散在场景文档 §3 与语言文档 §7
related: [ref/SD-ixchel-sample-scenarios-20261028.md, ref/SD-ixchel-template-and-style-language-20261029.md, ref/SD-ixchel-context-model-and-components-20261030.md, ref/S-ixchel-ui-capability-comparison-20261102.md, DECISIONS.md#015, DECISIONS.md#029]
---

# Ixchel UI: Roadmap

## Summary

实现顺序与状态的唯一来源；其它 meta 文档只引用本文。已完成 0、A、B、C、D0、L1、D、L2、L3、E1、T1、T2、E2、I、U1–U3、原生声明与元素定义重构 N1–N2、V1–V4（盒子外观、尺寸与排版、动画与进出、整页开关动画）、FX1–FX3（私有图层与整组 `opacity`、标准滤镜与混合、`.ixeffect` 具名特效），L4–L8（Java 组件、共享来源 Feed、库组件加载，#035、#036；库资源统一，#037；顶层外暂停轮询与 `<mc-tooltip-lines>` 组件化，#038；删除框架值，#035 修订；lint 复用 core 的组件文件判定与行内内容规则），TH1（主题机制与库资源显式引入，#034、#039）、RS（按资源规范重做文件种类、引用与登记，#041）、TH2–TH4（形状与像素化、文字、四套内置主题，`ref/SD-ixchel-themes-20261104.md`）、U4（组件库）、REC（确定性录制）、CSS1、CSS2（参考前端视效补齐 CSS，依据 `ref/S-ixchel-frontend-visual-gap-20261106.md`）、TH5（`holo` 重做、`cyber` 换成 `amber`）；下一步 FT（内置字体）；Doc 分批写，第一批（站点基建、生成的参考页与 Java API 页）已合入（#87）。阶段排序依据见 `ref/S-ixchel-ui-capability-comparison-20261102.md`。sample 迁移只做等价重构，以 `-Pplaytest=all` 为回归网。

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
| L4 | 完成 | character、machine、storage 的背包 | Java 组件（每实例子 ViewModel、`Param`/`TwoWay`/`Event` 端口）、共享来源 `Feed`、数值拓宽、库组件文件免链接（#035、#036） |
| L5 | 完成 | character、machine、storage 的背包 | 库资源统一：`@ixchel/` 与 `ns:path` 同一套解析与读取，按层的库（文件根、预引入表、库 Java 组件），Java 组件单表（#037） |
| L6 | 完成 | S9 的物品行 tooltip | 顶层外的组件实例暂停轮询；`<mc-tooltip-lines>` 改为库 Java 组件，删除 `content=lines`（#038） |
| L7 | 完成 | character、machine、storage 的背包 | 删除框架值，数据入口只有 ViewModel；`<mc-player-inventory>` 读 `player.containerMenu`（#035 修订） |
| L8 | 完成 | 全部页面的 lint | lint 复用 core：组件文件判定由 core 公开，行内内容规则拆为 `layout.Formatting`，load 与 lint 同用；kit 不再自建探针页或判定 |
| E1 | 完成 | S8 | 文字排版补完：行内流式排版、CJK 断行、`Component` 富文本、行内链接 |
| E2 | 完成 | S9；S1、S6、S7 的 tooltip | 文档内富 tooltip `<ix-tooltip>`，兼容原版物品 tooltip |
| I | 完成 | 全部场景的界面文本 | i18n：`t` 语言键与参数 |
| Doc | 进行中 | — | 用户文档站 `docs/` 与语言参考（#045、#046）：第一批（plume 基建、kit 生成参考页、doclet 生成 `/java-api/` 页、名字与死链检查）已合入（#87），手写内容分批写 |
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
| TH1 | 完成 | 全部页面的主题与库引入 | 主题机制：`<link rel="theme">`、主题文件的 `@import` 与 `@-ix-effect`、`.ix-*` 表面类、按角色的 `--ix-*` 变量；库资源显式引入与包、删除预引入表（#034、#039），结果见下文；文件与引用机制由 RS 重做 |
| RS | 完成 | 全部页面 | 资源规范（`ref/D-ixchel-resource-spec-20261105.md`，#041）：视图根元素与 `<link href>`、css 的 `@stylesheet`/`@partial`/`@link`、特效别名、删除主题层与包、`ix-` 名字检查、统一登记与库根专用目录、改名（view、`@UiView`、`*Model`、`@UiElement`）；结果见下文 |
| CS | 完成 | character、machine、storage | 容器槽位倒置依赖（#042）：`<mc-slot bind:menuslot>` 与环境无关，`UiContainerScreen` 按身份匹配；删除 `containerOnly`；`<mc-player-inventory bind:menu>`；结果见下文 |
| TH2 | 完成 | effects 页 Shapes 标签 | 形状与像素化：`corner-shape`、`shape-rendering: crisp-edges`、GLSL `ix_snap`；结果见下文 |
| TH3 | 完成 | effects 页 Shapes 标签 | 文字：`font-family`（MC 字体 id）、`text-transform`；不做 `letter-spacing`；结果见下文 |
| TH4 | 完成 | themes | 四套内置主题 `vanilla`、`dark`、`holo`、`cyber`，内置特效 `chromatic`、`glitch`、`cooldown`、`stripes`，主题陈列页；结果见下文 |
| TX | 完成 | effects 页 Shapes, text 标签 | 文字清晰字号：`font-size` 向下取整到每字形像素占整数个物理像素（主题文档 §5.3）；`McTextMeasurer.crispSize`，测量与绘制同一字号，起点对齐物理像素；单测 `CrispSizeTest`，playtest 断言 6px 文字宽度 |
| U4 | 完成 | character、effects、machine、env、index、waypoints、config、themes | 组件库与内置元素补齐：可写 `provide:`/`uses`、表达式管道、`<else>`/`<empty>`、`disabled` 子树状态、`<details>`、radio、`ix-*` 组件与表面类、`<mc-item>` 修饰与轮播（`ref/SD-ixchel-u4-components-20261104.md`，#040、#043、#044），结果见下文 |
| FX1 | 完成 | — | 私有图层；`opacity` 整组语义（shader 构造并入 FX2） |
| FX2 | 完成 | — | 标准 `filter`、`backdrop-filter`、`mix-blend-mode` |
| FX3 | 完成 | — | `.ixeffect` 具名特效与首批内置特效（`ref/SD-ixchel-ui-effects-20261102.md`，#028），结果见下文 |
| REC | 完成 | effects、themes、quests | 确定性录制：可注入的文档时钟与游戏时间步进、逐帧钩子、ffmpeg 编码、光标与点击叠加、按元素取景与超采样，录制指令复用 playtest 脚本；结果见下文 |
| CSS1 | 完成 | effects 页 CSS 标签 | 低难度 CSS 补齐：`outline`、`letter-spacing`、`inset`、`radial-gradient`/`conic-gradient`/`repeating-*-gradient`、`background-position` 可动画、`skew`、`aspect-ratio`；结果见下文 |
| CSS2 | 完成 | effects 页遮罩标签 | 图层遮罩类：`clip-path`、`mask-image`、`background-clip: text`；结果见下文 |
| TH5 | 完成 | themes | 主题重做：`holo` 按 CodePen 参考重做，`cyber` 换成 `amber`（飞船 HUD）；结果见下文 |
| FT | 完成 | effects 页、themes | 内置字体：库自带 OFL 字体与 `@font-face`，缺字回退，兼容 ModernUI；结果见下文 |

依赖：D0 → L1 → D → L2 → E1 → E2；L3、I、Doc、T1、T2 都依赖 L2，彼此及与 E 互不依赖，可并行或互换；Doc 分两步：接入与语言参考先写，其余随 U1–V3 补齐；U1 → U2（共用指针按住状态）；V1 落地路径 C 图集（#029）；V3 → V4；U1–V3 → U4；FX1 → FX2 → FX3，FX3 依赖 L3 的 `var()` 与 V3 的 `@keyframes`，与 Doc 互不依赖；FX3 → TH1 → RS → TH2、TH3（彼此独立），TH2–TH3 → TH4 → U4（U4 组件的主题规则写进四套主题）；L2 → L4 → L5 → L6，L5 → TH1（`@ixchel/themes/` 走统一解析），L5 → U4（`ix-*` 组件进 core 的库）；REC 只依赖 T2，与 CSS1 互不依赖，可并行；CSS1 → CSS2（图层遮罩复用 CSS1 的渐变绘制）。0–C 的结果见场景文档 §3。T3（对外发布）随 Doc，T4（编辑器）及之后见工具文档 §10。

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
- 结果（2026-10-30）：§6-1 定为 `@ixchel/<名>.css`，只能由页面链接（组件文件链接即报错，未知名字在 link 处报错）。L2 已把链接图移进 core，href 由 `TemplateLoader` 识别、`runtime.LibrarySheets`（接替 `UiDocument.UserAgent`）读取，两个 mc 层只改 javadoc。主题 = 共用规则 `themes/theme.css`（`.panel`、`.title`、`button` 各状态，全部写成 `var(--ix-*)`）加只设 `:root { --ix-* }` 的配色表 `vanilla.css`、`dark.css`（原 #018，现由 #034 取代，TH1 改造）。`var()` 带回退、可继承；自定义属性按普通层叠、总是继承，`bind:style:--x` 可写；`:root` 匹配模板顶层元素。sample 3 个场景用 vanilla、5 个页面（config、index、loot 列表、quests、waypoints）用 dark，env 与 loot HUD 不加主题；CSS 增 69 删 144 行，check 不改。接受的外观变化：按钮获焦时显示悬停底色；machine 选中按钮字色 `#404040`→`#202020`；index 行悬停 `#FFFFFF30`→`#FFFFFF20`、说明字色 `#A0A0A0`→`#AAAAAA`；config 条目悬停 `#FFFFFF10`→`#FFFFFF20`。已知偏差：成环的自定义属性带回退时可能取到回退值（规范要求环上一律无效），尚无场景触及。`-Pcheck=all` 143 项通过；testmod 1.21.1 只经 `build` 编译，未进游戏。

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
- 已定（2026-11-03）：
  - core 往 `DisplayList` 发 `PushLayer`/`PopLayer`，mc 只回放；图层范围在子树画完后取这段命令的包围盒并集回填（阴影、模糊等外扩已在命令内），与当前裁剪求交。
  - 图层在 transform 外：按屏幕空间开图层，子树带着 transform 画进去，合成时贴回原位；`paintElement` 的命令顺序不变。图层按物理像素分配（GUI 坐标 × GUI 缩放）。
  - 合成：进图层时设 `blendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA)`，图层内为预乘结果，合成用 `(ONE, ONE_MINUS_SRC_ALPHA)`；以"图层内半透明文字与物品"截图验证，原生绘制若把 alpha 写坏，再改为自写合成 shader，shader 注册随之留在 FX1，否则移到 FX2。
  - 容器槽位只在离场时随整组淡出：`SlotSnapshot` 画进文档所在图层；屏幕打开时原版槽位不接管。
  - 圆角裁剪不在 FX1，有需求时另开阶段；`paint/rounded-overflow-clip` 照旧警告。
- 引擎：mc 层 classpath `ResourceProvider` 与 `ShaderInstance` 构造，程序名 `<宿主modid>_fx_<名>`；加载器胶水层经 `RegisterShadersEvent` 注册、随资源重载重建；私有图层栈（`TextureTarget` 带深度，进出时 flush 并保存、恢复 FBO、viewport、投影、scissor、混合、深度状态，帧内按尺寸复用、用完释放）；`opacity` < 1 的元素走图层，改为整组语义，原生元素随之淡出。两个 mc 版本。
- 验收：重叠子元素半透明与带 `<mc-item>` 的淡出写成 playtest 截图；F3+T 资源重载后绘制正常；其余现有截图不变；env 与 waypoints 整页进出场期间进 `FrameTimer` 预算；`-Pplaytest=all`；1.21.1 `runClient` 冒烟。
- 结果（2026-11-03）：完成，与 FX2 同一分支。core 发 `PushLayer`/`PopLayer`，范围回填为子树命令包围盒并与裁剪求交；mc `OffscreenLayers` 按物理像素开 `TextureTarget`（帧内按尺寸复用，空闲后释放），图层内按上述预乘混合，合成在 FX2 改为自写 composite shader（见 FX2 结果）。`opacity` < 1 改为整组语义，`<mc-item>` 与 `SlotSnapshot` 随组淡出。与"引擎"一条的出入：shader 不走 `RegisterShadersEvent`，见 FX2 结果。
  - 验证：effects 页"透明度"标签：整组重叠处只见上层方块、逐个淡出可见两层、组内物品随之淡出、卡片淡出（截图 `effects-opacity`、`effects-faded`）；F3+T 由 effects playtest 调 `reloadResourcePacks` 后重开页面复查整组透明度、`multiply` 与背景模糊；env HUD 面板与 waypoints 面板的进出场加了 `opacity` 淡入淡出，env 离场与再进场、waypoints 再次打开进 `FrameTimer`（env 离场/进场 p95 1.0ms/6ms，waypoints 进场 p95 0.6ms/3ms）。屏幕离场由 `ExitingDocuments` 画，不在 `FrameTimer` 计时范围内，未计时；同一绘制路径已由 HUD 离场覆盖。1.21.1 冒烟见 FX2。

#### FX2 标准滤镜与混合

- 已定（2026-11-03）：
  - `filter` 首批：`grayscale`、`sepia`、`saturate`、`hue-rotate`、`invert`、`brightness`、`contrast`、`opacity`、`blur`、`drop-shadow`。
  - `mix-blend-mode` 首批只做固定管线能表达的：`normal`、`multiply`、`screen`、`plus-lighter`、`darken`、`lighten`（后两者用 `MIN`/`MAX` 混合方程），不读父目标；父目标快照只给 `backdrop-filter`。
  - 原版屏幕背景（1.21.1 模糊与两版的暗化）默认保留，作者可关闭，改用 `backdrop-filter` 或自己画会随文档淡出的暗化。
  - `filter` 与 `backdrop-filter` 可过渡：函数列表对得上逐个插值，一边是另一边前缀时以默认值补齐，其余跳变，同 `transform()`。
- 引擎：`filter` 走 `layer` 机制，`backdrop-filter` 走背景快照（降采样 + 两遍模糊），按元素圆角形状画回；core 解析与开发期报错，不支持的函数报码。两个 mc 版本。
- 验收：每个函数与模式各一张 playtest 截图；带 `backdrop-filter` 的 HUD 进 `FrameTimer` 预算。
- 结果（2026-11-03）：完成。core：三个属性的解析、插值与报码，`Filter`、`DrawCommand.Backdrop`、`PushLayer` 带滤镜与混合模式；图层范围按滤镜外扩放大。mc（两版）：`FilterShaders` 在首次使用时从 classpath 构造，程序名 `<modid>_ixchel_<名>`，不走 `RegisterShadersEvent`（不是资源包内容，F3+T 后实测已链接程序照常工作）；shader JSON 不带 blend 节点，`ShaderInstance.apply()` 会按默认不透明 `BlendMode` 关掉 blending，所以统一为"`FilterShaders.apply` → 设混合状态 → 建网格 → `FilterShaders.draw`"；`FilterPasses` 跑滤镜与背景快照（降采样 + 两遍模糊），`OffscreenLayers` 按混合模式合成，`McPainter.visitBackdrop` 按元素圆角形状画回。sample：effects 页分透明度、滤镜、混合三个标签页；env HUD 三块面板加 `backdrop-filter: blur(3px)`。
  - 验证：core 251 项测试通过；全量 `build` 与两版 lint 通过；Forge `-Pplaytest=all` 14 项、252 项断言通过，含 10 个滤镜函数、6 个混合模式、背景模糊与灰度的像素断言，以及资源重载后的复查；env 带背景模糊后预算通过（静止 p95 0.7ms、移动 1.4ms/6ms）。1.21.1 经临时入口在标题界面打开 effects 页、逐个标签截图：整组透明度、全部滤镜、混合模式与背景模糊/灰度与 1.20.1 一致，日志无错误；入口与临时资源已移除。
  - 与验收的出入：截图按标签页各一张（`effects-filters`、`effects-blend`），每个函数与模式是带标签的一格，没有逐项单独截图。
  - 已知：背景模糊 sigma 与条纹周期接近时条纹仍可见，属 CSS 预期（样例改用 `blur(8px)` 演示）；背景快照用 `GL_LINEAR` blit 最多 8 倍降采样，细纹理可能混叠，未观察到问题；1.21.1 未进世界，HUD 的背景模糊未实跑。

#### FX3 具名特效

- 已定（2026-11-03，2026-11-04 补充）：
  - 文件后缀为 `.ixeffect`。
  - shader 构造：库自带的固定 shader 照 #033 从 classpath 构造，不走 `RegisterShadersEvent`；`.ixeffect` 在文档首次使用时构造，按资源路径缓存，库自己的重载监听清空缓存；编译错误报进对应文档的诊断并映射行号，不中断资源重载。`reloadShaders` 不关闭库构造的程序，清缓存时须先 `close()` 旧实例：`Program` 按名字留在全局表里，不关则同名重建会复用旧编译结果，改动不生效。
  - 程序名为整段资源路径转义后的 `<宿主modid>_fx_<路径>`；参数前缀仍取文件名，同一文档引入两个同名特效时开发期报错，不做别名。
  - 首批只做 `kind box` 与 `kind layer`；`kind backdrop` 报 `effect/*` 码，有场景再做。
  - 参数声明照搬 `@property` 的三个描述符：`param 名 { syntax: <类型>; inherits: true|false; initial-value: 值; }`，`inherits` 必填。`transition` 与 `@keyframes` 按声明类型插值；其余自定义属性照旧无类型，不开放通用 `@property`。
  - 单位：`b.pos`、`b.size`、`ix_mouse` 与 `<length>` 参数均为物理像素，另给 `ix_scale`（GUI 缩放）。
  - 分层：头部解析与内置特效文件在 core（内置经 `@ixchel/effects/…` 引入，与官方 CSS 同在 classpath，资源包不可覆盖）；正文包装、`ix_*` 原语库与编译在各版本 mc。宿主自己的 `.ixeffect` 在 assets，可被资源包覆盖。
  - `paint()` 铺满背景区、按 `border-radius` 裁剪，不受 `background-size`/`position`/`repeat` 影响，可与 `url()`、渐变按层叠用。GLSL 编译失败报进文档诊断，box 层跳过、layer 按无特效合成。无指针（HUD）时 `ix_mouse` 取远处值，另给 `ix_mouse_inside`。
  - 首批内置特效只有 `glint`（box）与 `dissolve`（layer）。
  - `ix_time` 取文档时钟（与 transition、动画同为墙钟），取模防精度损失；`ix_mouse` 为元素内像素坐标，与 `b.pos` 同系。
  - `.ixeffect` 热重载与模板读同一套开发期源目录。
- 引擎：`.ixeffect` 解析（`kind`、类型化 `param`）、uniform 与 fsh/vsh/json 生成、GLSL 原语库；`<link rel="effect">`；`background-image: paint(名)`、`filter: -ix-paint(名)`；参数自定义属性按声明类型校验并可 `transition`；错误码 `effect/*`，GLSL 编译错误行号映射回 `.ixeffect`；内置特效经 `@ixchel/effects/…` 引入。layer 特效的元素本地坐标经 transform 逆矩阵 uniform 给出（FX1 图层在 transform 外）。
- 工具：kit lint 覆盖 CSS 一侧的检查；schema 列出特效与参数。
- sample：一个场景用一个 `box` 特效与一个 `layer` 特效，其中一个参数由 `bind:style:--x` 驱动。
- 验收：上述用法写成 playtest 截图；错误用法逐项由 lint 报码；改动 `.ixeffect` 后 F3+T 生效；在生产实例装 Oculus + BSL 光影 + ImmediatelyFast + Embeddium 实测截图并与不装时对比；1.21.1 `runClient` 冒烟。
- 结果（2026-11-04）：完成。core：`effect` 包（`EffectParser` 解析头部，`Effects` 管一个文档引入的特效与参数，`LibraryEffects` 读 `@ixchel/effects/glint`、`dissolve`，13 个 `effect/*` 码）；`<link rel="effect">` 由 `TemplateLoader` 解析；参数按声明类型注册为属性，可 `transition`、`@keyframes`、`bind:style:--x`；新增 `DrawCommand.Paint`（box 特效），`PushLayer` 的滤镜链可含 `LayerPaint`（`-ix-paint()`）。mc（两版）：`paint.EffectPrograms` 把正文夹在 `shaders/effect.glsl` 原语库与按 kind 生成的 `main` 之间编译，参数为 uniform；`FilterPasses` 跑 layer 特效，`McPainter.visitPaint` 画 box 特效。kit：lint 逐个检查项目内 `.ixeffect` 头部（未被引入的也查），schema 列出库与项目的特效及参数（属性名、类型、继承、初值）。sample（1.20.1）：effects 页"着色器"标签，`glint` 按钮、项目自带的 `spotlight`（跟随指针的 box 特效）、`dissolve` 卡片一张由 `transition` 驱动、一张由 `bind:style:--dissolve-progress` 逐步驱动；两张卡片放在不透明底上，溶解后的像素断言不受面板后的世界影响。
  - 与"已定"的出入：
    - 不在首次绘制时构造、也没有库自己的重载监听：`HostedDocument.load()` 编译文档引入的全部特效，开发期保存与 F3+T 都会重载文档，覆盖同样的场景；编译失败报 `effect/compile-failed` 并抛出加载错误（开发期叠加层显示），不中断资源重载。
    - 程序名不是 `<modid>_fx_<路径>`，而是每次构造取新名 `<modid>_ixchel_fx<n>`，按特效 id 缓存、文本不变则复用；新程序编译成功后才关闭旧实例，失败时旧程序照画（开发期改坏后文档仍可用）。
    - 行号映射：GLSL 1.50 中 `#line n` 使下一行为 n+1，正文前写 `#line (bodyLine - 1)`；实测改坏的 `spotlight.ixeffect` 报在 `:8:1`。
  - 验证：core 260 项测试、kit 测试通过；全量 `build` 与两版 lint 通过；Forge `-Pplaytest=all` 14 项全部通过，effects 新增 10 项断言（指针在外暗、在内亮，流光移动，溶解 0、1、0.5 三档，资源重载后仍画）；开发期热重载临时实测：改坏 `.ixeffect` 显示 `effect/compile-failed` 且旧程序照画，改回后生效。1.21.1 的 sample 没有 effects 页，经临时入口在标题界面打开带三种特效的页面截图确认，入口已移除。
  - 未做：生产实例（Oculus + BSL + ImmediatelyFast + Embeddium）实测，待有该环境时补；`FrameTimer` 预算未单列着色器标签。
  - 已知：参数在 uniform 不存在时（正文没用到）原版打 "could not find uniform" 警告；行内 `style` 不经 `Effects.check`；类型化参数不能经 `var()` 读出；`kind backdrop` 报 `effect/unsupported-kind`；关闭特效的 `ShaderInstance` 会一并关掉共享的顶点程序对象，已链接的程序不受影响。

#### L4 Java 组件与共享来源

- 设计依据：上下文模型文档 §3.7、绑定文档 §10（#035、#036）。
- 已定（2026-11-04）：共享表每份库副本一张；去重按帧与按 tick 两种，由 Feed 定义选择；数值拓宽 `Integer` → `Long`/`Float`/`Double`、`Double` → `Float`，字面量属性仍为 `String`；`disabled` 约定其后由 #040 定下（外壳上的普通 `disabled`）。
- core：`Param`/`TwoWay`/`Event` 与 `param()`/`twoWay()`/`event()`；`ModelShape.Kind` 加 `EVENT`、`Member.port`；`Feed`（`polled`/`opened`、`perTick`）、共享表、时钟端口、`source(feed, key|keyCell)`；`ModelAccess` 扩充；`Compiler.instantiate` 构造与接线子模型、签名核对（`component/signature-mismatch`）、使用处按声明类型检查、`TwoWay` 要求可写路径；`Block.Instance` 释放钩子；`Bindings` poll 与关闭子模型；框架值 `Map<String, Ref>` 入参；数值拓宽；库组件文件可用 `ix-`/`mc-` 前缀、classpath 来源、免链接。
- mc（两版）：`UiComponents.register(modid, 类, 工厂)`；`$player`，容器界面另有 `$menu`；时钟端口（渲染帧计数、客户端游戏时间）。1.20.1：库组件 `mc-player-inventory`（`params="menu=$menu"`，按 `Slot.container` 为 `Inventory` 与 `getContainerSlot()` 分出背包区与快捷栏）；1.21.1 无 `container`，不带该组件。
- kit：成员表读端口种类；`@UiTemplate` 指向组件文件的类按 Java 组件检查（签名核对、使用处类型）。
- sample：character、machine、storage 三处背包改用 `<mc-player-inventory/>`，删去三个 ViewModel 的下标列表与 `ui/common/player-inventory.html`；machine 的 `MachineState` 改由 `Feed.opened` 包住 `Replication.watch` 取得。
- 验收：core 测试覆盖端口接线与签名错误、`TwoWay` 写回与未传入时的局部状态、子模型随 `<if>`/`<for>` 挂载与离场后关闭、Feed 同周期去重、引用计数释放与按 key 切换、数值拓宽；`-Pplaytest=all` 通过（背包槽位位置与迁移前一致）；两版 lint 零错误；`build`；1.21.1 冒烟。
- 结果（2026-11-04）：完成。core：`binding` 新增 `Param`、`TwoWay`、`Event`、`Feed`，`ViewModel` 加 `param()`/`twoWay()`/`event()`/`source(feed, key|keyCell)`；`runtime.UiHosting` 携带 Java 组件（按组件名的工厂）、库组件文件与框架值，`UiDocument` 关闭时关闭子模型；新码 `component/signature-mismatch`、`component/param-type`、`component/read-only-two-way`、`component/unbound-port`；`TemplateLoader.Loaded` 多一张组件名到文件的表。mc（两版）：`UiComponents`、`$player`，`HostedDocument` 接 `UiHosting`、加载时装 `Feed.tickClock`、重载与关闭时关闭旧文档；1.20.1 另有 `$menu` 与 `container.PlayerInventoryView`（`@UiTemplate("@ixchel/player-inventory.html")`，模板在类旁）。kit：从 mc 层的 `@UiTemplate("@ixchel/...")` 类读库组件，`$player`/`$menu` 按类型名声明，组件文件不再按页面检查，schema 列出库组件与端口标志。sample：三处背包改为 `<mc-player-inventory/>`，删去 `common/player-inventory.{html,css}` 与三个 ViewModel 的下标列表；`MachineView` 的状态改为静态 `Feed.opened` 包住 `Replication.watch`；playtest 的槽位选择器改为 `mc-slot`。验收：core 288 个测试通过；`build`（含两版 lint 零错误）；`-Pplaytest=all` 14 项 262 断言全过，背包截图与迁移前一致；1.21.1 客户端启动与资源重载无错误。
  - 与"已定"的出入：
    - 共享表不是一张全局表，而是挂在每个 `Feed` 上（`perTick()` 返回带独立实例表的副本）；作为静态常量时共享效果相同。
    - 按帧去重的周期是一次 `Bindings.update()`（core 内计数），mc 只提供 tick 时钟（`Gui#getGuiTicks`），没有渲染帧计数端口。
    - `binding.compile` 不能依赖 `runtime`（#006），`UiDocument` 把 `UiHosting` 拆成 `Bindings.Hosted` 与 `TemplateLoader.LibraryFile` 传下。
    - `TwoWay` 的默认值与声明的 `Event<T>` 载荷按可赋值加转换检查（`TwoWay<Float>` 可写整数默认值，`Event<Float>` 接受 `Integer`），传入的路径仍要求类型精确一致。
    - `Param`/`TwoWay` 只接线一次，直接委托给接入的单元格，不经 `Flat`；`Flat` 只用于按 key 切换的 Feed 引用。
  - 已知：参数不能命名为 `key`（`<for>` 的属性，报 `template/moved-attribute`）或 `text`（`template/reserved-member-name`）；`disabled` 约定其后由 #040 定下。

#### L5 库资源统一

- 设计依据：#037；语言文档 §3.3、§4.3。
- core：新包 `library`（只依赖 `binding`）的 `Library`：文件根（锚点类，按其相对路径读 classpath，读结果缓存）、预引入表（标签 → `@ixchel/` 文件 id）、库 Java 组件（文件 id → 类与工厂）、按层 `extend`；core 的库 `Library.CORE` 持主题与特效文件（移到 `library/` 资源目录）。`TemplateLoader.resolve` 把 `@ixchel/` 当命名空间根，读取按前缀分派，库文件不进 `files`；预引入表取代 `LibraryFile` 与 `Linker.library` 的独立来源，`ix-`/`mc-` 名按文件 id 前缀放行；`LibraryEffects`、`LibrarySheets` 的读取改经 `Library`（主题的组合表保留到 TH1）。`UiHosting` 改为（`Library`、按文件 id 的 Java 组件表、框架值），删除 `LibraryComponent`；库的 Java 组件并入同一张表。
- mc（两版）：`McLibrary`（`Library.CORE.extend()`，1.20.1 声明 `mc-player-inventory`，L6 起两版声明 `mc-tooltip-lines`）；所有宿主（`UiScreen`、`UiContainerScreen`、`UiHud`）传同一个库；`$menu` 仅容器界面，非容器界面使用 `<mc-player-inventory>` 在使用处报错。`player-inventory.html` link 旁边的 `player-inventory.css`，组件自设槽位尺寸，内部类名加组件名前缀，`<mc-slot>` 带 `.ix-well`。
- kit：从 `McLibrary` 读预引入表与库 Java 组件，不再扫描 `com.azane.ixchel.mc.*` 的注解；schema 的库组件、主题、特效出自同一个 `Library`。
- sample：`slot.css` 只保留斜面外观（选中 `.ix-well` 或 `mc-player-inventory mc-slot`），`terminal.css` 的覆盖改用新类名。
- 验收：core 测试覆盖 `@ixchel/` 相对解析与 `..` 不越出、宿主文件 link 库文件、库组件 link 自身样式表、预引入与显式 link 同一文件只加载一次、项目组件用 `ix-`/`mc-` 名报错；`-Pplaytest=all` 通过且背包截图不变；两版 lint 零错误；`build`；1.21.1 冒烟。
- 结果（2026-11-04）：完成。core：`library.Library`（`CORE` 持 `library/themes/`、`library/effects/`）与 `JavaComponent`；`TemplateLoader` 以 `Library` 取代 `LibraryFile` 表，`@ixchel/` 文件经库读取，`effect/unknown-library-effect` 删除（库特效缺失报 `load/file-not-found`）；`UiHosting(Library, 组件表, 框架值)`。mc（两版）：根包 `McLibrary`，所有宿主经 `UiComponents.hosting` 共用；`player-inventory.{html,css}` 在 `mc/container/` 资源下。kit：`Project.library()` 取 mc 层唯一的 `public static final Library LIBRARY`，`Components` 按预引入表声明库组件。验收：core 与 kit 测试、两版 lint 零错误、`build`、`-Pplaytest=all` 14 项 262 断言全过，背包截图不变；1.21.1 冒烟（随 L6 一并）。
  - 与"已定"的出入：`McLibrary` 放在 mc 根包而非 `element`——它要声明 `container.PlayerInventoryView`，`element` 引用 `container` 会反转包依赖；`PlayerInventoryView` 因此改为 `public`。kit 不按类名找 `McLibrary`，而是扫描 mc 层中唯一的 `LIBRARY` 字段，测试替身无需与真实类同名。
  - `effect/unknown-library-effect` 的删除经确认保留：库特效与其它库文件同走 `load/file-not-found`，原设计为库特效单设错误码是多余的。

#### L6 顶层外暂停轮询与 `<mc-tooltip-lines>` 组件化

- 设计依据：#038。
- core：组件子模型记住外壳元素；`Bindings` poll 前跳过外壳位于"带 `LayerFacet` 且不在 `TopLayer` 中"的元素之内的子模型，首次 poll 同样推迟；`UiDocument.setTooltip` 先于 `frame` 的 `bindings.update()`，故 tooltip 显示的那一帧即取值。删除 `NativeElement.lines`、`TagRegistry.hasLineContent` 及 UA 的行列规则。
- mc（两版）：`mc-tooltip-lines` 为库 Java 组件（`params="item"`，`Screen.getTooltipFromItem` 轮询，行列表按 `Component` 结构比较，空行保留行高），删除 `TooltipLines` 与 `HostedDocument` 的找元素、填行循环。
- kit：随 `lines` 内容种类删除相关检查；组件不带默认 `<slot>`，写子元素按已有规则报错。
- sample：S9 页面写法不变；`TooltipsPlaytest` 的取行改为读组件内的行元素，"隐藏 tooltip 不取行"断言保留。
- 验收：core 测试覆盖未显示 tooltip 与未打开 dialog 内子模型不 poll、显示当帧取到新值；`-Pplaytest=all` 通过（S9 物品行与截图不变）；两版 lint 零错误；`build`；1.21.1 冒烟。
- 结果（2026-11-04）：完成。core：`ChildModel` 持有外壳、`TopLayer` 与组件体的绑定组，休眠时既不 poll 也不更新组件体；休眠中跳过过的实例醒来时 `poll()` 返回"已变化"，驱动当次 `root.update()`；`bind:open` 在同一次更新中打开 dialog 时，组件体更新前补一次 poll。`TopLayer.shows(e)`（条目或指针元素，含退场中）。删除 `NativeElement.lines`、`HtmlContent.LINES`，`NativeElements.type` 去掉 `lines` 参数（`TagRegistry.hasLineContent` 已不存在）。mc（两版）：`element.TooltipLinesView`（`Source.polled(getTooltipFromItem)`，行为 `record Line(index, text)`，以下标为 key，空行替换为不换行空格）、`tooltip-lines.{html,css}`；删除 `TooltipLines`、`HostedDocument` 的找元素与填行循环。kit：无改动（没有 `lines` 相关检查）。sample：`TooltipsPlaytest` 读组件内行元素的 `textContent`。验收：core 两项新测试（未显示 tooltip、未打开 dialog），去掉休眠判断时两项均失败；`build` 与两版 lint 零错误；`-Pplaytest=all` 中 env 帧时一次 p95 6021 us 超 6000 预算，单独重跑 env 与 tooltips 全过（p95 3559–5530 us），物品行截图正确；1.21.1 客户端启动、资源重载与 HUD 加载无错误，未进界面。

#### L7 删除框架值

- 设计依据：#035（修订）。
- core：删除 `UiHosting.FrameworkValue` 与 `UiHosting.framework`、`Bindings.Hosted.Framework` 与每帧刷新的框架单元格、`Compiler` 的框架名表；参数默认值只见前面的参数。`$` 仍保留给框架（`$event`）。
- mc（两版）：`UiComponents.hosting` 不再带 `$player`，`UiContainerScreen` 不再带 `$menu`。1.20.1：`<mc-player-inventory>` 无参，`PlayerInventoryView` 以 `ClientSources.player(p -> p.containerMenu, null)` 取菜单。
- kit：删除框架值声明。
- sample：页面写法不变。
- 验收：core 与 kit 测试；两版 lint 零错误；`build`；`-Pplaytest=all` 通过且背包截图不变。
- 结果（2026-11-04）：完成，按上列实施。`PlayerInventoryView` 的菜单字段为 `private`，模板只见 `main`/`hotbar`；删除 core 中两项框架值测试。验收：core 与 kit 测试、`build`、两版 lint 零错误；`-Pplaytest=all` 14 项 262 断言全过，存储终端截图中背包槽位位置不变。

#### L8 lint 复用 core

- 动机：kit 自行拼探针页判定组件文件、lint 不检查行内内容规则（`layout/block-in-inline`、`absolute-in-inline`、`inline-box-property` 只在布局时报出），两处与运行时分叉。
- core：`TemplateLoader.declared(id, read, library, tags)` 返回文件顶层 `<template>` 声明的组件或 `null`，kit 的 `Components` 改用它。新类 `layout.Formatting` 按计算样式与树结构判定每个盒子的角色（`Mode`：叶子、弹性/网格项、块级流、段落；`FlowChild`：自成盒、行内；`InlineChild`：跳过、文本、行内盒、原子盒）并报行内内容错误；`LayoutEngine` 的 sync/collect 按它分支，`UiDocument.lint` 在 restyle 后调用 `Formatting.check`。lint 不跑布局、不编造视口或字体度量。
- 不覆盖：`<if>`/`<for>` 块内容、未打开的顶层元素（与绑定检查同一边界）；依赖尺寸的检查（容器槽位过小等）仍只在运行时。
- 已定（2026-11-04，原待定）：文档种类由 Java 侧推出，页面上不写声明。已作废（2026-11-05，#042）：文档没有种类，`<mc-slot>` 与环境无关。
- 验收：core 与 kit 测试；`build` 两版 lint 零错误；`-Pplaytest=all` 通过。
- 结果（2026-11-04）：完成，按上列实施；sample 页面无新报错。`UiDocumentTest` 原先固定"lint 不报行内规则"的用例改为断言报出。验收：core 与 kit 测试、`build`、两版 lint 零错误；`-Pplaytest=all` 260/262，失败两项为 env 帧时间（未改动时同样超标）与 effects 的 glint 移动检查（单跑与重跑均通过，检查对相位敏感，暂不修，归 REC 一并处理）。

#### TH1 主题机制

- 设计依据：`ref/SD-ixchel-themes-20261104.md` §3、§4、§9（#034、#039）。
- 已定（2026-11-04）：主题文档 §9 与 §4 变量表；边框宽度归 UA；库资源显式引入与包（#039）。
- 引擎：`<link rel="theme">`（每页至多一个，报 `load/multiple-themes`；组件文件链接报 `load/theme-not-on-page`）；主题文件开头的 `@import "<主题>"`（至多一个）与 `@-ix-effect "<特效>"`，别处报 `css/misplaced-import`，导入成环报 `load/import-cycle`；导入链整体进主题层，主题按文档对特效注册表编译；内置主题为 `@ixchel/themes/<名>`，`base` 共用规则、`vanilla`/`dark` 各自 `@import` 它；删除 `LibrarySheets` 的组合表与 `load/library-sheet-not-on-page`、`load/unknown-library-sheet`。显式引入：删除 `Library` 预引入表，新增 `<link rel="package">`（只含 link 的文件，可含组件、特效、包）；库 href 不带扩展名、按引用种类补，项目 href 带扩展名（特效也带）；`Library` 增加主题、特效、包的目录，取代 `LibraryEffects` 名单；mc 层库文件移到库根下的公开路径（`@ixchel/mc/tooltip-lines`、`@ixchel/mc/player-inventory`），每版一个 `@ixchel/mc/components` 包；内置特效改名 `ix-glint`、`ix-dissolve`。
- 工具：lint 报上列错误码；schema 列出主题（内置与项目）及其变量、库特效、包及其内容。不 lint 未知的 `--ix-*`/`.ix-*`。
- sample：所有页面改用 `rel="theme"` 与 `.ix-*`、`.ix-selected`（主题负责的选中态）；容器界面与其余界面各写一份宿主主题（基于 `vanilla`、`dark`）供页面链接；用到 `<mc-*>` 组件的页面引入 `@ixchel/mc/components`；effects 页改用 `ix-` 特效名，项目特效 href 带扩展名。原先靠 1px `border` 的 vanilla 面板补 1px `padding`。
- 验收：`-Pplaytest=all` 通过，背包槽位位置不变，截图差异（vanilla 框线改画法、dark 按钮恢复 UA 的 1px 边框）人工核对；两版 lint 零错误；`build`；1.21.1 冒烟。
- 结果（2026-11-04）：完成。core：`ParsedSheet.imports` 收集主题文件开头的 `@import`/`@-ix-effect`（新码 `css/invalid-import`、`css/misplaced-import`）；`TemplateLoader` 解析 `rel="theme"`、`rel="package"`，`Loaded.themes` 为 `Theme(id, 解析结果)` 列表（基底在前），另公开 `theme(href, read, library)` 与 `packaged(id, …)` 供工具读取；新码 `load/multiple-themes`、`load/theme-not-on-page`、`load/import-cycle`、`load/package-content`；`UiDocument` 按文档对特效注册表编译主题；`Library` 删除预引入表，`Builder.component(类, 工厂)` 由类的 `@UiTemplate` 取文件，`theme`/`effect`/`pack` 登记目录；`LibraryEffects` 删除，`LibrarySheets` 只剩 UA 与原生元素样式；`themes/theme.css` 换成 `base.css`。mc（两版）：库文件移到 `@ixchel/mc/tooltip-lines`、`@ixchel/mc/player-inventory`。kit：schema 的 `librarySheets` 换成 `themes`（内置与页面链接到的项目主题，变量含基底的）与 `packages`；`Components` 按库的包与 Java 组件声明库组件。sample（1.20.1）：`ui/themes/container.css`（基于 `vanilla`）与 `overlay.css`（基于 `dark`），三个容器界面的 `.ix-panel` 内边距 7px→8px、machine 侧边栏 `border` 改为 `box-shadow` 加 1px 内边距并右下移 1px；playtest 只改选择器。1.21.1 sample 无主题，只改 `.ix-selected`。
  - 与"已定"的出入：
    - mc 包按界面种类分两个：`@ixchel/mc/components`（两版，`<mc-tooltip-lines>`）与 `@ixchel/mc/container`（仅 1.20.1，引入前者并加 `<mc-player-inventory>`）。包在引入时加载其中全部组件，`<mc-player-inventory>` 含 `<mc-slot>`，非容器界面引入即报未知标签；原"使用处报错"随预引入表一起消失。
    - 变量表多两个：`--ix-control-border`、`--ix-overlay-border`，给 UA 保留宽度的 `button` 与 `ix-tooltip` 边框上色（未设为透明）；主题文档 §4 已补。
    - sample 中原先不吃主题选中底色的元素（loot 筛选、quests 节点）在 `.ix-selected` 上自设 `background-color: transparent`，waypoints 选中行自设字色。
  - 验证：core 与 kit 测试；`build`；两版 lint 零错误（临时页面确认 lint 报出新码）；`-Pplaytest=all` 14 项 262 断言全过（首轮 tooltips 页因引入含 `<mc-slot>` 的包整项失败，即上条出入；glint 移动检查偶发失败，见 REC）；machine、config 截图人工核对外观与布局不变；1.21.1 冒烟由用户确认。
  - 已知：dark 下按钮恢复 UA 的 1px 透明边框，按钮比 L3 大 2px（playtest 未断言到）；dark 悬停为 `--ix-hover` 叠在 `--ix-control-bg` 上，选中的按钮悬停时也叠色。

#### RS 资源规范

- 设计依据：`ref/D-ixchel-resource-spec-20261105.md`（#041；修订 #030、#034、#035、#037、#039）。
- 分批提交，顺序：改名（view、`@UiView`、`*Model`、`@UiElement`、诊断前缀）→ 视图根元素与 `<link href>`、删除包 → css 文件种类与 `@link`、删除主题层与 `@import`/`@-ix-effect`/`<style>` → 特效别名与 `--<Alias>-<param>` → `ix-` 名字检查 → 统一登记与库根专用目录 → kit、sample、文档。
- 验收：core 与 kit 测试；`build`；两版 lint 零错误；全部完成后一次 `-Pplaytest=all`，截图与 TH1 一致；1.21.1 冒烟。
- 结果（2026-11-05）：完成，五个提交（改名；视图与样式表；特效别名；统一登记；库名字检查）。core：视图根元素 `<page>`/`<component>` 与 `<link href>`，`link.Linker` 取代 `TemplateLoader`；css 文件声明 `@stylesheet;`/`@partial;`，经 `@link` 引入部分样式与特效，部分样式按后序分层、`!important` 反序；删除包、主题层、`@import`/`@-ix-effect` 与 `<style>`；特效在每个 css 文件内以 PascalCase 别名引用，参数为 `--<Alias>-<param>`，按文件改写为由特效文件 id 生成的内部名（规则、行内样式、`bind:style` 同一套）；新码 `effect/invalid-alias`、`duplicate-alias`、`unknown-alias`、`unmarked-param`，删 `duplicate-effect`、`unknown-effect`。登记：`runtime.UiRegistry`（`CORE`、`library()`、`host(modid)`）登记元素、Java 组件与库文件根，`UiHosting.of` 合成并拒绝重名，宿主不得登记 `ix-`/`mc-` 标签或 `@ixchel/` 文件；`Library` 只剩按前缀的根（库文件在锚点类旁的 `ui/` 目录），运行时只按 id 读取；`JavaComponent` 移到 `binding`，`NativeElements` 改为 `DeclaredElements`，术语"原生元素"改为"绘制元素"。mc（两版）：`UiRegistries`（`MC`、`register`、`hosting`）取代 `McLibrary`、`UiComponents`、`McElements.register` 与 `ContainerLayout.TAGS`，`<mc-slot>` 以 `element(ContainerLayout.class, SlotSnapshot.PAINTER)` 登记，`HostedDocument` 不再收额外绘制器。kit：经 `UiRegistries.MC` 反射读取 mc 层的库，`LibraryFiles` 扫描库根（目录与 jar）列出库文件，schema 的库主题为全部库 `@partial` css（因此含 `base.css`）；lint 按全部库 css 的 `.ix-*` 类与 `--ix-*` 变量检查宿主 css 与行内样式（`style/unknown-library-class`、`style/unknown-library-token`，警告），组件样式表在 `:root` 设自定义属性报 `style/component-root-token`（加载与 lint 都查）。sample 两版迁移，lint 零错误零警告；`-Pplaytest=all` 14 项 262 断言全过。
  - 与"已定"的出入：`ix-` 名字表只由工具构建（运行时不枚举文件，故不查）；`<mc-slot>` 由登记携带绘制器，不再是 `HostedDocument` 的参数。
  - 推迟：`core.view` 由原 `template` 包整体改名，解析器与宿主可见的元素声明（`UiElement`、`ElementPayload`、`TagRegistry`）仍同包，按契约拆包留待后续。

#### CS 容器槽位倒置依赖

- 设计依据：#042（作废 L8 的"文档种类由 Java 侧推出"）。
- 结果（2026-11-05）：完成。core：删除 `UiElement.containerOnly`、`UiRegistry.Element.containerOnly`、`UiHosting` 的第二张标签表（`tags()` 无参）。mc 1.20.1：`container.SlotElement` 声明 `<mc-slot>`，载荷 `Slot`，只画离场快照；`ContainerLayout` 按 `Slot` 身份匹配占位，`MISSING_INDEX`/`MALFORMED_INDEX`/`INDEX_OUT_OF_RANGE` 改为 `container/missing-slot`（容器界面中未绑定槽位）与 `container/foreign-slot`（槽位不属于本菜单），`UNPLACED_SLOT`、`DUPLICATE_SLOT` 保留；`ContainerElements.into` 登记本包；`PlayerInventoryModel` 以 `Param<AbstractContainerMenu> menu` 取菜单，不再读 `player.containerMenu`。两版 `HostedDocument` 去掉 `container` 参数。kit：`ElementDeclarations` 只剩一张表，schema 去掉 `containerOnly`。sample：三页面改 `bind:menuslot`，模型持有菜单。验收：`build`（含 core、kit 测试与两版 lint）零错误；`-Pplaytest=all` 262/262，截图与改前一致。

#### TH2 形状与像素化

- 设计依据：主题文档 §5.1、§5.2。
- 已定（2026-11-04）：`crisp-edges` 用每宿主第二张最近邻过滤的遮罩图集，遮罩键带密度；`corner-shape` 同时作用于背景、框线、阴影、box 特效的 SDF、私有图层裁剪与 `backdrop-filter`（GLSL `IxBox` 加形状参数）；取值每角一个关键字，不做 `superellipse()`，不可过渡；不改盒子尺寸，主题可随意设置。
- 主题接入（承 TH1）：`base` 给 `.ix-panel`、`.ix-well`、`button`、`ix-tooltip:open` 写 `corner-shape: var(--ix-corner, round)` 与 `border-radius: var(--ix-radius, 0)`；`shape-rendering` 由主题写在 `:root`（`vanilla` 开 `crisp-edges`，`dark` 不开）；`--ix-corner`、`--ix-radius` 进主题文档 §4 表，schema 随之列出。
- 引擎：`corner-shape`（`round | bevel | notch | scoop | square`，1–4 值），沿用圆角 SDF 与遮罩图集；继承属性 `shape-rendering: auto | crisp-edges`，`crisp-edges` 时遮罩每 GUI 像素 1 纹素、最近邻采样；GLSL 原语 `ix_snap(p)`。两个 mc 版本。
- 验收：effects 页加一个标签：五种 `corner-shape`、`crisp-edges` 开关前后的圆角与阴影各一格，像素断言阶梯与切角；1.21.1 冒烟。
- 结果（2026-11-05）：完成。core：`corner-shape` 每角一个属性加简写，形状随 `RoundRect.Radii.corners` 传递，`ShapeTessellator` 烘焙五种形状的覆盖与模糊；`shape-rendering` 继承，经 `Fill`/`Border`/`Shadow` 的 `crisp` 传到 mc。mc（两版）：每宿主第二张图集 `<modid>:ixchel/mask_atlas_crisp`，最近邻上传；`IxCorner` uniform、`IxBox`/`IxLayer.corner`、`ix_sd_shape_rect`/`ix_sd_box`、`ix_snap`；`backdrop.fsh` 跟随角形状；glint 改用 `ix_sd_box`，`ix_sd_round_rect` 保留。主题：`base` 在 `:root` 设 `--ix-radius: 0`、`--ix-corner: round`，四种表面写 `border-radius`/`corner-shape`；`vanilla` 开 `crisp-edges`。验收：`build`；`-Pplaytest=all` 通过，Shapes 标签逐格断言切角、阶梯、逐像素阴影与 auto 的抗锯齿；1.21.1 只验到编译与 lint。
  - 与"已定"的出入：
    - crisp 遮罩不是"同一图集、键带密度"，而是每宿主两张图集（线性、最近邻各一），密度由图集决定。
    - crisp 除每纹素一 GUI 像素外，还把边缘、半径与阴影外沿取整到 GUI 像素，纹素中心采样一次，不做抗锯齿；盒子本身已是整像素（taffy rounding），阶梯与屏幕网格对齐。
    - `--ix-radius`/`--ix-corner` 在 `base` 的 `:root` 给默认值，而非写在 `var()` 回退里，schema 因此能列出。
    - crisp 不改特效的覆盖计算；特效要像素化自己调 `ix_snap`。
  - 推迟：bevel/scoop 的内框线按半径内缩近似，斜边上框线略细（bevel 约 0.7 倍）。

#### TH3 文字

- 设计依据：主题文档 §5.3。
- 已定（2026-11-04）：core 的 `TextMeasurer`/`TextShaper` 加字体参数；`Component` 片段自带的字体优先于 CSS `font-family`；`text-transform` 作用于全部文本（含 `t=` 译文与 `Component`），测量前变换；未知字体 id 只在运行时开发期报错，lint 不查（字体是资源包内容）；不做 `letter-spacing`。
- 主题接入（承 TH1）：`base` 给 `.ix-title` 写 `font-family: var(--ix-title-font, inherit)`，`text-transform` 由各主题直接写。
- 已定（2026-11-05，原"先定"）：主题只经 `--ix-title-font` 改标题字体，现有内置主题都不设；`text-transform` 由各主题自写，内置主题目前不写；TH4 的布局矩形断言只覆盖定宽元素。
- 引擎：`font-family` 取 MC 字体资源 id 列表，测量与绘制按 `Style.withFont`；`text-transform: none | uppercase | lowercase`。两个 mc 版本。
- 验收：`minecraft:uniform`、`minecraft:alt` 与大写各一行截图；行内排版测量与绘制宽度一致（core 测试覆盖 `text-transform`）。
- 结果（2026-11-05）：完成。core：`font-family`（逗号分隔，字符串或裸 `ns:path`/`path`，缺省命名空间为 `minecraft`，拒绝 `sprite:`）取宿主有的第一个，都没有用默认字体，缺失的报 `layout/unknown-font`（WARNING，每文档每字体一次）；`text-transform` 按 `Locale.ROOT`、测量前变换；`TextMeasurer.width` 加 `font`、新增 `hasFont`，`TextShaper.Run`、`TextFragment`、`DrawCommand.Text`、`StyledText.Span` 带字体，`Component` 自带字体优先。mc（两版）：`Style.withFont` 测量与绘制，`hasFont` 查 `assets/<ns>/font/<path>.json`。主题：`base` 的 `.ix-title` 写 `font-family: var(--ix-title-font, inherit)`。验收：core 测试（`straße` → `STRASSE` 的宽度、字体回退、`Component` 字体优先、未知字体报码）；`-Pplaytest=all` 通过，三行文字宽度不同于默认行、`minecraft:uniform`/`alt` 不报未知。
  - 与"已定"的出入：
    - `text-transform` 不作用于表单控件自绘的文字（`input` 的值与占位、`select` 的标签）与原版 `title` tooltip，避免光标下标错位。
    - 未知字体报运行时 WARNING（`layout/unknown-font`），不是 ERROR。
  - 同分支随后修 ModernUI 兼容：测量改用 `font.getSplitter().stringWidth` 的小数宽度（`Font.width` 按段向上取整会累积误差）；测量与绘制都用 `StringDecomposer.iterate` 构造字符序列（`§` 作普通字符；原版下 `font.width(String)` 也会吃掉 `§` 码而绘制照画，effects 页加一行断言）；GUI 缩放变化时 `invalidateText`。MUI 下其余差异记在主题文档 §5.3。
  - 推迟：`Component` 显式写 `minecraft:default` 时与"未设字体"不可分，跟随 CSS；文档里"怎么加字体"一节随 Doc。

#### TH4 四套内置主题

- 设计依据：主题文档 §6、§7、§8（主题的分析框架与角色×状态矩阵）。
- 已定（2026-11-04）：只有 `holo` 带入场动画，内置主题不带离场动画；`ix-cooldown` 由 U4 并入（box，参数 `--ix-cooldown-progress`）；陈列页单独定 `FrameTimer` 预算，不沿用 env HUD（其 p95 在未改动时已超 6000 us）。
- 承 TH1：主题自带特效经主题 partial 的 `@link` 引入（RS 已删 `@-ix-effect`）；glint 移动检查的相位问题仍归 REC。
- 承 TH2/TH3：`--ix-corner`/`--ix-radius` 已由 `base` 接到四种表面，`holo`/`cyber` 只设值；内置主题不能自带字体（#001 不带固定命名空间的 `assets/`），只从原版字体中选，宿主主题可覆盖 `--ix-title-font`。
- 已定（2026-11-05）：
  - 样式作用域（取主流框架的做法，Vue scoped / Shadow DOM）：partial 一律全局，排在最低的作者层；页面与组件的 stylesheet 只匹配本视图写的元素（插槽内容归写它的视图）与本视图中组件的外壳，只检查选择器的主体，祖先不限；组件 stylesheet 也匹配自己实例的外壳。层序 UA < partial < 组件 stylesheet < 页面 stylesheet < 内联，外壳上页面赢。页面定制组件内部只经 `part` 属性与 `::part(名)`（可跟状态伪类）、变量与继承属性；插槽内容的样式放进使用方 link 的 partial，不加 `::slotted()`。组件 link 的 partial 同样全局。
  - 变量契约：接上只有名字的 `--ix-active`、`--ix-focus`、`--ix-duration`、`--ix-easing`、`--ix-text-shadow` 与四个 `-backdrop`；新增 `--ix-well-border`（`input`/`select` 归内凹面）；`base` 的 `:root` 给全部契约变量默认值，schema 因此列出完整契约。
  - `base` 规则：`input`、`select`、下拉选项、`dialog`、复选框与滑条（`accent-color`）、滚动条、`button:active`；焦点用边框色（引擎无 `outline`，阴影列表拼 `var()` 遇 `none` 失效）；过渡用 `--ix-duration`/`--ix-easing`；状态类 `.ix-invalid`、`.ix-busy`、`.ix-done`（错误、等待、成功由 ViewModel 判断，用类不用伪类）。
  - 主题自由：变量值、`shape-rendering`、`text-transform`，以及结构不同的效果（斜面翻转、反色、闪烁、故障、进场）由各主题自写规则。
  - U4 部件同时带类与 `part`：类给主题，`part` 给页面；类名带组件名（`class="ix-bar-fill" part="fill"`，#040 修订，原示例 `class="ix-tab" part="tab"` 作废）；U4 往 `base` 追加部件到角色的映射，验收为不改四个主题文件即可在四套主题下显示。
  - 选中叠悬停保留；dark 系按钮的 1px 透明边框保留为布局基准；`cyber` 标题大写，布局断言只覆盖定宽元素，陈列页给标题定宽。
  - 常驻动画先保证效果，不设幅度限制；减弱动效开关以后单独加。
  - 不做：疏密（主题不改尺寸、边距）、声音、`cursor`。
- 顺序：0 样式作用域（层序、作用域、`::part()`，迁移 terminal 与 guide，修订资源规范 §2.3）→ 角色×状态矩阵与四套主题设计稿（确认后再写主题文件）→ 变量契约与 `base` 规则，`vanilla`、`dark` → 内置特效 `ix-chromatic`、`ix-glitch`、`ix-cooldown`，`holo`、`cyber`。
- sample：主题陈列页，按矩阵列出全部部件与状态，四个页面各链接一套主题。
- 验收：四张陈列页截图；同一陈列页在四套主题下的布局矩形一致（playtest 断言）；全息与赛博常驻动画在陈列页预算内；1.21.1 冒烟。
- 结果（2026-11-05）：完成。
  - 块 0 样式作用域：见资源规范 §2.3（`::part()`、`part` 全局属性、`css/invalid-part`）；sample 迁移 terminal（`::part(hotbar)`）与 guide（段落排版移入 `guide.css`）。
  - `base`：`:root` 给全部契约变量默认值（取 UA 样式的颜色），并把 `text-shadow`、`scrollbar-color` 接到变量；接上 `input`、`select`、下拉选项、`dialog`、复选框与滑条（`accent-color`）、`button:active`/`:focus`、`.ix-invalid`/`.ix-busy`/`.ix-done`；过渡用 `--ix-duration`/`--ix-easing`。kit schema 中各内置主题的变量表因此一致。
  - 主题：`vanilla`（斜面、`notch`、`crisp-edges`、0ms，按下翻转斜面）、`dark`（`backdrop: blur(4px)`、3px 圆角）、`holo`（扫描线与四角括号背景层、`Glint` 流光、`@keyframes` 闪烁、标题 `drop-shadow` 加 `Chromatic`、`minecraft:uniform` 标题、`scaleY` 进场）、`cyber`（`bevel` 只切左上与右下，控件 3px、面板 8px，`crisp-edges`、按下转强调色、标题大写加 `Glitch`、`.ix-busy` 走 `Stripes`）。
  - 内置特效：`chromatic`、`glitch`（layer），`cooldown`、`stripes`（box）；按资源规范 §3 文件名不带 `ix-`，路线图原名 `ix-chromatic` 等作废。`stripes` 是计划外新增：cyber 的移动警示条纹用渐变平铺做不了平移（`background-position` 不可动画）。
  - sample：主题陈列页 `ui/showcase/`（组件 `theme-showcase` 加四个各 link 一套主题的页面，标签页切换即打开另一页），索引新增"主题"条目。
  - 测试：core `BuiltInThemesTest`（四套主题无诊断、布局矩形一致）；playtest `themes`：四张截图、布局一致（排除跟随指针的 tooltip）、holo/cyber 帧时间 p95 预算 12000 us（holo 中位约 5000 us、p95 6600-8600 us，最重；cyber 约 2500 us）。
  - 与"已定"的出入：陈列页标题定宽，cyber 大写与 holo 字体只改标题宽度内的文字；`glitch` 每 1/15 秒以 8% 概率触发，未加减弱开关（按已定，以后单独加）。

#### U4 组件库与内置元素补齐

- 设计依据：`ref/SD-ixchel-u4-components-20261104.md`（#040、#043、#044）。
- 已定（2026-11-05）：范围与各项写法见设计文档 §3–§5；`<mc-fluid>`、`<mc-key>`、新引擎元素另开阶段（§6）。
- 已定（2026-11-05）：轮播为单独的绘制元素 `<mc-item-cycle bind:items>`（设计文档 §5），#030 不变。
- 顺序：1 引擎（`disabled` 子树状态 → `<else>`/`<empty>` → 管道 → `provide:`/`uses` → `<details>` → radio，各带 lint 与 kit schema）→ 2 角色×状态矩阵补部件与表面类（确认后再写 `base`）→ 3 库组件与 `base` 规则 → 4 `<mc-item>` 修饰与轮播（两版）→ 5 sample 迁移与陈列页。
- core：可写 `provide:` 的转交与加载期解析（`component/missing-provider`）；管道解析、类型检查与三个内置管道（`binding/unknown-pipe`、`binding/pipe-type`）；`Element` 的 `DISABLED` 按祖先计算，获焦与选择器同用；库组件文件 `@ixchel/components/<名>.html`。
- mc（两版）：`<mc-item>` 原版修饰、`count`、`decorations="none"`；`<mc-item-cycle bind:items>` 轮播。
- kit：schema 列出组件签名（含 `uses`）、部件名、表面类与管道。
- sample：character、effects 的 tabs 改 `ix-tabs`/`ix-tab`（删页面的 `.tab` 规则）；machine 能量条与水箱、env 血条改 `ix-bar`（删 Java 中按比例算的高度与宽度）；index、waypoints 的选中行改 `.ix-row`；5 处 `if="rows.size == 0"` 改 `<empty>`；只做格式化的数字 `Derived` 改管道；主题陈列页加全部组件与表面类。迁移只做等价重构，截图因 `<mc-item>` 修饰变化的除外。
- 验收：core 测试覆盖 `provide:` 可写转交、只读写入报错、无提供者报错、插槽内容按展开后树解析；管道优先级、参数与类型错误、三个管道的边界（`999`/`1000`、满一小时、0 与 1）；`<empty>`/`<else>` 不紧邻报错；禁用容器内按钮匹配 `:disabled` 且不可获焦。`BuiltInThemesTest` 与 playtest `themes`：扩展后的陈列页在四套主题下无诊断、布局矩形一致，四张截图；`-Pplaytest=all` 通过；两版 lint 零错误；`build`；1.21.1 冒烟（`<mc-item>` 修饰）。
- 结果（2026-11-05）：完成。
  - core：库组件 `tabs`、`tab`、`tab-panel`、`bar`、`stepper`、`cycle`、`pager`、`tree-row`、`search`（`@ixchel/components/<名>.html`）；表面类尺寸在 UA 样式表、外观在 `base`，四个主题文件未改；`<slot provide:x>` 让组件把值提供给使用方写进插槽的内容。
  - 计划外：number、range 输入的 `bind:value` 可绑数值路径（只写回能按路径类型解析的文本），`ix-stepper` 的 `input` 部件靠它；绘制元素的载荷按类型参数检查（`List<ItemStack>` 不收 `List<String>`），`ElementType.payloadType()` 改为 `Type`，另有 `payloadClass()`。
  - sample：tabs、bars、rows、`<empty>`/`<else>` 迁移；`MachineModel` 删 `energyHeight`/`waterHeight`，`EnvModel` 删 `entityHealthWidth`；陈列页加全部组件与表面类，`ThemesPlaytest` 选择器改 `.ix-tab-button.ix-selected` 并断言切页。
  - 与"已定"的出入：没有一处 `Derived` 等价于管道（`TerminalModel.abbreviate` 截断不进位、效果时长用原版格式、熔炉火焰与箭头的像素取整不同于比例），均保留；index 行只加 `.ix-row` 不加 `.ix-selected`（`on` 表示 HUD 开启而非选中）。
  - 截图复查修正：`input`、`select` 去掉 `well-shadow`（与边框叠成 2px，TH4 起就有）；sample `common/slot.css` 不再给 `.ix-well` 加边框（与主题的内阴影叠成 2px）；`ix-bar` 填充内缩并继承外壳的角（原来是盖住外框和切角的纯矩形）。
  - `mc-slot` 默认由主题管理（`base` 规则，core 只出现选择器文本）：新变量 `--ix-slot-shadow`，默认用 `well-border` 画内侧上左加外侧下右各 1px，相邻槽位共用一条 1px 线；`vanilla` 覆盖为原版斜面。四套主题都是直角（cyber 试过 2px 切角，成片时线条交叉杂乱，放弃）。库 `mc-player-inventory` 与 sample 的 `.slot` 不再自带外观，陈列页加 2×2 槽位。外侧 1px 画在布局盒之外，滚动容器边缘可能被裁。
  - 已知不稳定：env "moving and turning" 帧时间 p95 预算 6000 us 在最后两次全量运行中超出（6149、6205 us），同代码单独跑 5545 us；TH4 已记录它在未改动时也会超。U4 把 env 的两根条换成了 `ix-bar`，未做改动前后的对比，不能排除有小幅影响，预算未动。glint 移动检查也有一次全量运行失败（相位问题，归 REC，见 TH1）。
  - 验收：core、kit 测试通过（新增 `UiLibraryComponentsTest` 8 项、载荷类型参数一项）；`build` 与 sample lint 零错误；最终代码 `-Pplaytest=all` 15 个 playtest 291 过 1 败（上一条的帧时间），`-Pplaytest=env` 单独 20 项全过（须 `lang:en_us`，`run/options.txt` 为 `zh_cn` 时文字断言失败）；1.21.1 客户端启动到资源加载无异常，改装界面的 `<mc-item>` 修饰未在游戏中打开查看。

#### REC 确定性录制

- 动机：单张截图看不出 V3、V4 这类动画的全程；文档的动图、演示视频与逐帧检查需要可重复生成的录像，界面一改即可重录。做法参照 LDLib2 的 `UIRecording`（`ref/S-ixchel-frontend-visual-gap-20261106.md` §5）。
- 已定（2026-11-06）：
  - 录制复用 playtest 脚本：新增的录制指令（take、停顿、拟人光标移动）在普通 playtest 中为空操作或直接瞬移，断言照常；断言失败时录制同样以非 0 退出。开启参数 `-Precord=<名>[,…]`，由插件转为 `-Dixchel.record.<modid>`，与 `-Pplaytest` 互斥；默认不录，不进 `build`，CI 不依赖 ffmpeg。
  - 时钟：文档时钟改为宿主可注入（mc 层注入游戏的时间源）；录制期间 mc 层把游戏时间源换成步进时钟，每画一帧推进 1/fps 秒，过渡、动画、`ix_time`、客户端 tick 与集成服务器一起受控，录制快于或慢于实时都不影响成片；take 外恢复墙钟并保持连续。取代原"只固定文档时钟、世界仍实时"的方案。
  - 逐帧：加载器层提供每帧回调（画完、交换缓冲之前），录制时每帧抓一帧；普通 playtest 仍按 tick 推进。
  - 编码：ffmpeg 子进程，原始帧经 stdin 输入，输出 MP4（可选再出 GIF）；读回先用同步 `glReadPixels`，帧耗时实测成问题再改异步。ffmpeg 路径可由属性指定，默认取 PATH。
  - 叠加层只画在复制出的帧上：光标（输入框上为 I 形）与点击波纹；字幕、按键条、聚光灯、镜头缩放不做（后期剪辑处理）。
  - 取景：整窗或按选择器取元素矩形加 padding；窗口尺寸与 GUI 缩放可由参数固定，大窗口高缩放输出小分辨率即超采样。
  - 拟人光标：录制时移动按 Fitts 定律定时长、沿曲线逐帧移动，沿途元素真实悬停（hover、tooltip、`ix_mouse` 特效可见）；节奏随机数以 playtest 名为种子，两次录制结果相同。
  - 输出：`run/playtests/<modid>/recordings/<take>.mp4`。
  - 范围：仅 1.20.1（1.21.1 无 playtest）。
- 先定（开工时）：1.20.1 `Util.timeSource` 是否可写及其覆盖面；每帧回调在 Forge 1.20.1 的挂点是否早于交换缓冲；集成服务器追赶视频时间的方式（1.20.1 无 `tickRateManager`）。
- 顺带修复：effects playtest 的 glint 移动检查对相位敏感（L8 结果）。检查只取按钮上沿一行、相隔 10 tick 采样两次，glint 脉冲占周长 15% 且按墙钟 `ix_time` 移动，两次都落在上沿外即误判为未动。改为在步进时钟下按已知 `ix_time` 采样（或沿整圈边缘取样）。
- sample：themes 陈列页依次切换四套主题、effects 页逐个标签各录一段。
- 验收：一段进出动画录成 MP4，帧数与设定一致、逐帧位置符合缓动曲线；同一脚本录两次逐帧相同；不开参数时 playtest 行为与耗时不变；`-Pplaytest=all` 通过。
- 结果（2026-11-07）：完成。
  - core：`PlaytestDriver.take(name[, TakeOptions], body)`、`hold(ms)`；录制时 take 内的点击、悬停、滚动、拖拽、按键、输入改为逐帧步骤：光标沿 `CursorPath` 移动，时长与停顿由 `Pacing` 按 Fitts 定律给出、以 playtest 名为种子；take 开始时光标置于 `(0, 0)`，成片长度与上一段留下的光标位置无关。不录制时 take、hold 不加步骤，各输入步骤与之前逐一相同。新端口 `Recording`、`Pump.frames`、`InputPort.pointer`；`UiDocument.clock` 改为宿主可设。
  - mc 1.20.1：`Recorder`（窗口 1280×720、GUI 缩放 2、60 fps，只改内存中的选项）、`GameClock`（首个 take 起替换 `Util.timeSource`，每画一帧推进 1/fps 秒，take 之间接续）、`FfmpegEncoder`（同步 `glReadPixels`，原始帧经 stdin 交给 ffmpeg 出 H.264）、`RecordingOverlay`（光标与点击波纹只画在复制出的帧上）、`Framing`（按元素取景加 padding）、`Take`（可选再出 GIF）；录制期间正午、晴天、关昼夜与天气循环，结束时恢复两条规则；进入世界后先等 40 tick。两版 `HostedDocument` 的文档时钟取 `Util::getNanos`。加载器：`IxchelForge` 的 `Pump` 加逐帧回调。插件：`-Precord` 与 `-Precord.fps/.ffmpeg/.window/.guiScale/.scale/.crf`，与 `-Pplaytest` 互斥。
  - 先定项：1.20.1 的 `Util.timeSource` 是可写静态字段，客户端 tick、过渡、动画、`ix_time` 都经它取时间；逐帧挂点用 `RenderTickEvent` END，此时世界、HUD、屏幕与 toast 已画完，尚未 blit 与交换缓冲；1.20.1 没有 `tickRateManager`，每个 take 开始时用反射把集成服务器的下一 tick 时间对齐到视频时间。
  - sample：effects 每个标签一段（7 段）；themes 一段（依次切换四套主题，悬停出 tooltip）；quests 详情一段。glint 移动检查改为沿整圈边缘取样，与相位无关。血条两次点击后才检查的断言改为每次点击后各查一次：录制时移到第二根血条的耗时超过伤害条 0.5 s 的延迟。
  - playtest 运行在内存中固定 `en_us`，不写 `options.txt`：此前 `run/options.txt` 为 `zh_cn` 时，英文文字断言成片失败。只有 `language` playtest 自己切到 `zh_cn` 再切回。
  - 验证：core 386 项测试通过，其中 `PlaytestDriverRecordingTest` 覆盖：不录制时无额外步骤；逐帧移动无跳跃、hold 恰为其帧数；起始光标不同的两次录制逐帧相同（去掉 take 开头的光标归位后此项失败）。`CursorPathTest`、`FramingTest` 通过；`build` 通过。`-Precord=effects` 连续两次，7 段帧数逐段相同（244/187/184/598/151/184/517），76 项断言全过；`-Precord=themes,quests` 两段（1474、298 帧），46 项全过。看过 effects-css 与 themes 成片（60 fps、1280×720，光标叠加、悬停、点击与主题切换可见）。`-Pplaytest=all` 15 项 314 过 3 败，3 项都是帧时间预算（env 移动转向与淡出、storage 滚动）；本机 16 GB 内存且同时运行远程桌面，未计入。
  - 未逐帧对照成片检查位置是否符合缓动曲线；曲线本身由 `CursorPathTest` 覆盖。

#### CSS1 低难度 CSS 补齐

- 动机：参考前端组件时，下列特性单独补上即可解锁最多（`ref/S-ixchel-frontend-visual-gap-20261106.md` §2）；全部属于已有绘制路径（纹理入图集、2D 矩阵、行内排版）的延伸。
- 范围：`outline`（含 `outline-offset`，跟随 `border-radius` 与 `corner-shape`，不参与布局）；`letter-spacing`（TH3 原定不做，此处改定；测量与绘制同一间距，与清晰字号取整、CJK 断行共存）；`inset` 简写；`radial-gradient`、`conic-gradient`、`repeating-linear-gradient`、`repeating-radial-gradient`；`background-position` 可动画；`skew()`/`skewX()`/`skewY()`（命中测试按逆矩阵）；`aspect-ratio`。
- 先定（开工时）：radial 与 conic 的绘制路径（图集纹理还是解析 shader：两者不能像线性渐变那样九宫格拉伸）；radial 的形状与尺寸关键字子集；`aspect-ratio` 是否直接用 taffy 的支持；超出子集的写法照 #003 开发期报错。
- sample：effects 页加一个标签逐项演示；可移植的 pen（§4）择其可用者做进演示。
- 验收：core 测试覆盖解析、插值与报错码；effects playtest 每项一格截图与像素断言；`-Pplaytest=all`；1.21.1 `runClient` 冒烟。
- 结果（2026-11-07）：完成。
  - core：`outline-width/style/color/offset` 与 `outline` 简写（style 只支持 `none`、`solid`）；`letter-spacing` 可继承、可动画，em 按自身字号换算，百分比报错，测量、断行、省略号与绘制用同一间距，宿主把间距取整到屏幕像素；`inset` 简写；`aspect-ratio` 交给 taffy，对数插值，比值一边为 0 时按 `auto`；`background-position-x/y` 按可重复列表逐层插值；四种渐变，`repeating-conic-gradient`、颜色空间、颜色提示报 `css/unsupported-value`，repeating 铺开超过 1024 个色标报 `paint/gradient-too-many-stops`；`skew()`/`skewX()`/`skewY()`，命中沿用逆矩阵。
  - 先定项：radial 与 conic 不入图集，由 `ShapeTessellator` 切成楔形凸片、用顶点色（弦误差不超过 0.1 GUI 像素）；`aspect-ratio` 直接用 taffy。
  - mc（两版）：`McPainter` 在间距不为 0 时逐字形绘制；所有批次关闭面剔除，否则镜像变换会把矩形与图片剔掉。
  - 修复：顶点落在硬色标上时取所在色段的颜色。之前 repeating 条纹糊成渐变，线性渐变也受影响。
  - sample：effects 页"CSS"标签每项一格，并移植 pen `RockStarwind/pozBLqd` 的血条（skew、镜像、延迟收缩的伤害条、radial 星空），pen 地址写在模板注释里。
  - 验证：core 测试覆盖解析、插值与报错码；effects playtest 用像素断言逐项检查 outline、Latin 与 CJK 的间距、inset、radial、conic、repeating、背景位置动画、aspect-ratio、skew 命中、血条；1.21.1 经临时入口截图确认 CSS 页与镜像血条，入口已删除。
  - 已知：下划线与删除线在字间断开；表单控件自绘的文字不应用 `letter-spacing`；ModernUI 下未验证；`border-image-source` 仍不接受渐变。

#### CSS2 图层遮罩

- 范围：`clip-path`（`inset()`、`circle()`、`ellipse()`、`polygon()`）、`mask-image`（渐变与图片）、`background-clip: text`；走 FX1 私有图层，合成时乘遮罩。
- 先定（开工时）：遮罩的生成方式（CPU 覆盖率纹理还是合成 shader 内解析）；`clip-path` 是否参与命中测试（标准为参与）；是否可过渡。
- 验收：同 CSS1。
- 结果（2026-11-07）：完成。
  - 先定项：遮罩画进私有图层的第二张纹理，复用 `paintImage` 的路径（各种渐变与 `url()` 都能用），`composite.fsh` 合成时乘它的 alpha。`clip-path` 在合成 shader 里逐像素算有向距离，边缘抗锯齿，支持 `inset()` 圆角、`circle()`、`ellipse()`、`polygon()`（最多 64 个顶点，nonzero 与 evenodd），形状坐标经变换的逆矩阵换算。`clip-path` 参与命中，形状外的点不命中自身及后代；`mask-image` 不参与命中。两端是同一种函数且参数个数相同时 `clip-path` 可过渡（polygon 还要求顶点数与填充规则相同），否则报 `css/not-animatable`；`mask-image`、`background-clip` 不可动画。
  - core：`ClipPath`、`ShapeSyntaxes`、`ClipShape`、`DrawCommand.Mask`，`PushLayer` 带 clip 与 masked。`background-clip` 支持 border-box、padding-box、content-box、text；text 把背景单独放进图层，用元素及后代的文字（不透明白色、不带阴影）作遮罩，文字本身仍按 `color` 再画一遍。`path()`、`url()`、`shape()`、几何盒、`mask-mode`/`mask-composite` 的非默认值、超过 64 个顶点都报 `css/unsupported-value`；`mask-size/position/repeat/clip` 固定为默认值。运行期新增 `paint/mask-image-unsized`、`paint/mask-too-many-tiles`。
  - mc（两版）：`OffscreenLayers` 加遮罩目标、裁剪参数与 polygon 顶点；`McPainter.visitMask`；composite shader 加 `Sampler1` 与裁剪 uniform。
  - sample：effects 页"遮罩"标签：四种 clip 形状与一格菱形到方形的变形动画、列表线性淡出、径向光斑、下界之星纹理遮罩、渐变文字、扫光文字。
  - 验证：core `UiDocumentClipMaskTest`；effects playtest 6 项断言（每种形状内外像素、被裁掉的角不命中、线性与径向遮罩的半透明过渡、纹理遮罩逐格、渐变文字只填字形）；1.21.1 经临时入口截图，与 1.20.1 一致，入口已删除。
  - 已知：polygon 每像素遍历全部边，大面积时的开销未测；只有第一层背景能选 `text`；文字阴影不进遮罩。
- 另评估、不在本阶段：`z-index`（用量最高，要改绘制与命中顺序）、3D 变换（透视合成与命中）、伪元素文字与 `attr()`、`@font-face`。

#### TH5 主题重做

- 动机：TH4 的 `holo`、`cyber` 只换了配色，展示效果不足；按 `ref/S-ixchel-frontend-visual-gap-20261106.md` §4 的 CodePen 参考重做。
- 已定（2026-11-07，用户）：`holo` 取 Ahmod-Musa/YPGGZgg（角括号、扫描线、标题色差）与 nin10doking/bNpRMEM（青色光晕面板），角括号明显、完整边框若隐若现；`cyber` 先按 gwannon/LYjvOLK（2077 黄底）做出后被否，改为 DESKTOP-the-decoder/ZEZJvPL（琥珀飞船 HUD），并改名 `amber`；`dark`、`vanilla` 不动；字体延后到 FT。
- 结果（2026-11-07）：完成。设计稿见主题文档 §6、§6.2。
  - `holo`：面板直角，四角 10px 亮括号；框线与外晕只在颜色上呼吸（外晕半径固定，阴影遮罩只烘焙一次）；第一层背景是 1px 扫描线，`background-position-y` 动画下扫；标题大写加 1px 字间距，`--Chromatic-offset` 由 `@keyframes` 偶尔跳开；按钮悬停与焦点发光；徽章改为描边；不再用 `Glint`。
  - `amber`：近黑底、琥珀框线、直角、`crisp-edges`；内凹、输入框与条带共用 100° 暗色斜纹（`--ix-well-image`）；条的填充半透明，`flex-basis` 过渡 `steps(8)`；按下填满琥珀色；`.ix-busy` 走 `Stripes`。内置特效 `glitch` 不再被内置主题使用，仍作为库特效保留。
  - 改名涉及：core `amber.css`（删 `cyber.css`）、`BuiltInThemesTest`、kit `SchemaTest`（主题按名排序，`base` 的下标改为 1）、sample 陈列页 `themes/amber.{html,css}`、标签页、`ThemesPlaytest`、索引说明。
  - 验证：core `BuiltInThemesTest`、kit `SchemaTest` 通过；`-Pplaytest=themes` 20 项通过、截图逐张查看；holo 帧时间 p95 超预算 12000 us（中位约 10-11 ms），同一次会话把 master 的旧 holo 换回实测为中位 10.2 ms、p95 13.0 ms，说明是本机负载而非新主题退化，预算未动；amber 中位约 4.0 ms、p95 5.2 ms。
  - 已知：主题文件 `@link` 的 `base.css` 排在主题规则之前，层序上主题规则一律胜出、与选择器优先级无关；主题写宽泛选择器（如 `button { color }`）会盖掉 `base` 的状态规则，须把状态重写一遍（2077 草稿中踩到，`amber` 未写这类规则）。低 alpha 的白色重复渐变条纹（`#FFFFFF14`）在游戏内画得比底色更暗，core 输出的命令正确、`#FFFFFF33` 的同类条纹正常，原因未查，`amber` 改用不透明 `#262626` 条纹绕开。

#### FT 内置字体

- 动机：主题与展示需要非 MC 字体；内置主题不能带固定命名空间的 `assets/`（#001）。
- 先定（开工时）：字体文件放 classpath，由 mc 层自建字形来源与 `FontSet`，不经资源包；CSS 侧 `@font-face`；缺字回退到 MC 默认字体，测量与绘制同一套回退；装 ModernUI 时的行为；只收 SIL OFL 字体，原样内置不做子集，授权文本随 jar 带上。
- 验收：两版各显示一种内置字体；中英混排缺字回退正确；装与不装 MUI 排版一致或差异写明；`-Pplaytest=all`；1.21.1 冒烟。
- 结果（2026-11-07）：完成；与先定的出入是不建 `FontSet`。
  - core：`@font-face { font-family; src: url() }` 只收这两个描述符，`src` 为 `.ttf`/`.otf`，名字不含 `:`；不合规报 `css/invalid-font-face` 并跳过。`src` 与 `@link` 同样解析（库 `@ixchel/fonts/...` 或相对本 css 文件）。`font-family` 中不带命名空间的名字先找本文档的 `@font-face`，再当 `minecraft:` 字体 id；`layout/unknown-font` 按解析后的 id 判断。`Library` 新增按 id 读字节。
  - 库字体：`@ixchel/fonts/orbitron.ttf`、`share-tech-mono.ttf`，原样内置，OFL 文本放在同目录（`*-OFL.txt`），`NOTICE` 写明。
  - mc 层（两版同一份 `Face`）：用 LWJGL STB 自己栅格化，字形按"每字形像素占整数个物理像素"的字号烘焙进每宿主一张的遮罩图集（`MaskAtlas.region` 对宿主开放），绘制为整物理像素对齐的四边形；粗体错开一个字形像素再画一遍，斜体错切，下划线与删除线位置同原版。字体缺的字成段交给 `Font` 测量与绘制，两边走同一条路径。不建 `FontSet` 的原因：构造与 `reload` 两版不同（1.20.1 STB、1.21.1 FreeType），且 `Font` 正是 MUI 的 mixin 对象；自绘绕开两者，并复用现有图集。
  - 主题：`holo` 标题用 Orbitron，`amber` 标题用 Share Tech Mono（`--ix-title-font`）。sample：effects 页"形状与文字"页签加两种字体的中英混排样例；1.21.1 弹药 HUD 的数字改用 Share Tech Mono。
  - ModernUI：未在装 MUI 的实例实测。[推断] 内置字体的字形不经 `Font`，MUI 不影响；缺字段经 `Font`，装 MUI 时画成 MUI 的默认字体，测量同样经 MUI，排版仍一致。
  - 验证：core、kit 测试与 `build`（含两版 lint）通过；`-Pplaytest=effects` 78 项、`themes` 21 项通过，截图逐张查看；1.21.1 经临时入口打开含两种字体的页面截图，中英回退正确，入口已删。`-Pplaytest=all` 15 项 317 断言通过、2 项失败，均为 env、storage 的帧时间预算；同一会话中不含 FT 的 `de8238e` 跑 env、storage 同样超预算、数值相当（storage 中位 13.0 ms 对 13.8 ms），判定为本机负载，预算未动。
  - 同批：playtest 运行时在原版造 narrator 之前把 `narrator` 选项设为 OFF，并在进世界前把 `GameNarrator.narrator` 换成 `Narrator.EMPTY`；无人值守运行时系统语音库初始化失败会弹阻塞对话框卡住测试。

#### Doc 用户文档

- 先定：站点结构按 Diátaxis 四类（教程、操作指南、参考、原理）；哪些参考页由代码生成（CI 文档 §2：属性注册表、元素注册表）。
- 内容：面向宿主开发者（接入、ViewModel）与资源包作者（模板、CSS、组件覆盖，不写 Java）；语言参考写成规范：数值按 JLS 提升与整除、`==` 按值比较，另列与 Web/JS 的偏离清单（初值、`null` 规则、无 `null` 字面量等）；原理页取自上下文模型文档；ModernUI 下的字体差异取自主题文档 §5.3。
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
  - 没有注解认领的页面不是只做结构检查，而是以空成员表完整检查后去掉 `binding/unknown-name`，另报 `model/unassociated-template`；组件文件按页面链接找出，不单独当页面。（L4 起组件文件一律不按页面检查，只经使用处检查。）
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
- 主题文件之外的 `@import`（语言文档 §4.4；主题文件内由 TH1 开放）。
- 性能，触发条件为 `FrameTimer` 预算超标或大列表（S3、S8）卡顿，按序做：Painter 按视口裁剪 > `contain` 与局部重排 > `Derived` 相等截断的代价。现状为首轮评审时的判断，开工前复核。动画时间域已定为墙钟（V3），不再列入。
- 游戏 UI 相对 Web 的缺口：`on:keydown` 指向按键绑定（`KeyMapping`，随玩家改键）；声明式世界锚定（现需 Java 侧 `WorldProjection.toGui` 计算后绑定）。
- 控件与语言：`<textarea>`、IME（中文已可上屏，系统预编辑需平台 API，与零全局副作用冲突）、结构伪类、`+`/`~`、table、横向滚动、`<dialog>`/`<select>` 离场、`drop-if`、`dragenter`/`dragleave`、拖拽边缘自动滚动（radio 已进 U4）。
- 工具：游戏内调试桥（做 web editor 时先梳理 user 与 agent 的真实开发路径再设计）；JEI/EMI/REI 接入点（库只暴露查询，宿主写插件）；playtest 机器可读报告与元素截图、VS Code Custom Data、文本滚动。
- 整合包可用的发行形态（另一个宿主 mod，未分析）。
- 附属 mod 扩展主 mod 界面的库原语：由 JDK `Map` 构造的模型、运行时按 id 装入片段的挂载点（工具文档 §11）；1.21.1 游戏内检查适配。
- 场景扩展：场景文档 §5。
