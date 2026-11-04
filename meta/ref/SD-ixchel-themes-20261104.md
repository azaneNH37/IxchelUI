---
tags: [survey, design]
date: 2026-11-04
origin: 拟定库内置的四套主题（原版、常规 dark、全息+电子、赛博）时发现现有主题只能换配色、每页都要链接一串库资源，需要从主题本身与用户用法重新设计主题机制，并盘点样式能力缺口
related: [DECISIONS.md#001, DECISIONS.md#003, DECISIONS.md#015, DECISIONS.md#028, DECISIONS.md#029, DECISIONS.md#032, DECISIONS.md#034, ref/SD-ixchel-template-and-style-language-20261029.md, ref/SD-ixchel-ui-effects-20261102.md, ref/S-ixchel-ui-capability-comparison-20261102.md, ref/D-ixchel-roadmap-20261029.md]
---

# Ixchel UI 主题

## Summary

主题是一个自成一体的外观包：公开的 `--ix-*` 变量、给库词汇（标准元素、`ix-*` 组件、`.ix-*` 表面类）穿外观的规则、自带的 `@keyframes` 与特效，可以 `@import` 一个基底主题。每个页面用一行 `<link rel="theme">` 显式选择；宿主推荐写一份自己的主题文件供页面链接，资源包覆盖这一份即可换皮；mod 级默认主题留给以后的宿主配置文件。库的特效与组件显式引入，多个引入可合成包（#039）。现有能力能做完整的原版与 dark，全息与赛博还缺切角、像素化、字体三项，以及两个内置 layer 特效。§9 各项已于 2026-11-04 按默认推荐定下。

> 文件与引用规则由资源规范（`ref/D-ixchel-resource-spec-20261105.md`，#041）取代：主题是被页面 stylesheet `@link` 的 partial，不再有 `rel="theme"`、主题层、`@import`、`@-ix-effect` 与包；内置特效为 `glint.ixeffect`、`dissolve.ixeffect`，css 中按别名引用。本文其余内容（变量表、表面类、能力缺口、四套主题）不变。

## Detail

### 1. 现状与问题

- 主题按页面链接：sample 中用主题的每个页面一行 `@ixchel/dark.css` 或 `vanilla.css`；guide 目录与四个条目页逐字重复主题、`book`、`page` 三行。
- 库自带的东西也要链接：effects 页 5 行 link 中 2 行是内置特效；U4 之后每用一个 `ix-*` 组件还要再加一行。
- 主题只能换颜色（#034 取代的原 #018：共用规则 + `:root` 配色）：带不了自己的规则、`@keyframes`、特效。特效须由页面 `<link rel="effect">`，主题里写 `paint()` 无从生效。
- 主题挂在通用类名 `.panel`、`.title` 上，与作者类名冲突（E1 结果：组件内 `.title` 被主题层覆盖）。
- 现有两套主题之间布局不一致：vanilla 的 `--ix-frame` 是 `1px solid`，dark 是 `none`，换主题会让面板内容移动 1px。

### 2. 刻画一个主题的维度

| 维度 | 内容 | 现有手段 |
|---|---|---|
| 色彩 | 表面分级（底、面板、内凹、浮层）、文字分级、强调、语义色、选中、焦点 | `--ix-*` 与 `var()` 回退；无 `color-mix()`，状态色逐个写 |
| 边缘与形状 | 直角、像素缺角、圆角、切角；单线、斜面、双线、发光线 | `border` 四边分色、`border-radius`、多层 inset `box-shadow`（不模糊即硬边）；缺缺角与切角 |
| 深度 | 面板、内凹、浮层、对话框的层级表达：斜面、投影、半透明、背景模糊 | `box-shadow`、`opacity`、`backdrop-filter`、`mix-blend-mode` |
| 表面质感 | 纯色、渐变、扫描线、网格、噪点 | 多层背景，`linear-gradient` 配 `background-size`/`repeat` 平铺条纹与网格，`paint()` |
| 文字 | 颜色、阴影、粗细、字号、大小写、字距、字体 | 颜色、粗体、斜体、`text-shadow`（一层不模糊）；字号只有 9px 的整数倍清晰；无 `font-family`、`text-transform`、`letter-spacing` |
| 状态 | hover、active、focus、disabled、selected、checked、open、进出场 | 伪类齐全，进出伪类（#032） |
| 动效 | 状态切换节奏、进出方式、常驻动画（闪烁、扫描） | `transition`、`@keyframes`、`ix_time` |
| 声音 | 点击音 | `-ix-click-sound` |
| 像素策略 | 是否对齐 GUI 像素网格 | 缺（§5.2） |

### 3. 主题机制

#### 3.1 主题的组成

| 组成 | 作用 | 谁可覆盖 |
|---|---|---|
| 变量 `--ix-*`（§4） | 公开接口：表面、框线、阴影、文字、强调、形状、动效 | 页面 CSS、继承它的主题、资源包（项目主题文件） |
| 规则 | 给库词汇穿外观：标准元素（`button`、`input`、`select`、`dialog`、`ix-tooltip` 等）、`ix-*` 组件、`.ix-*` 表面类（§3.4） | 页面 CSS（作者层在主题层之上） |
| 自带资源 | 主题规则引用的 `@keyframes` 与内置特效，页面不必链接 | 不对外 |
| 基底 | 至多一个 `@import`，只改差异 | — |

宿主组件按变量适配主题：组件 CSS 写 `var(--ix-surface)` 等，主题不选中宿主组件的类名。这是宿主组件跟随主题的唯一方式，写进用户文档。

#### 3.2 用户视角的用法

页面显式选择，一行：

```html
<link rel="theme" href="@ixchel/themes/vanilla"/>
<link rel="stylesheet" href="panel.css"/>
<div class="ix-panel">
  <span class="ix-title">…</span>
</div>
```

推荐写法：宿主写一份自己的主题，页面链接它；资源包覆盖这一个文件即可改整个 mod 的外观，不想跟随的页面直接链接内置主题。

```css
/* assets/mymod/ui/themes/main.css */
@import "@ixchel/themes/cyber";
:root { --ix-accent: #FF2A6D; }
```

```html
<link rel="theme" href="../themes/main.css"/>
```

页面局部微调照旧写在页面 CSS 里（`:root { --ix-accent: … }`），作者层高于主题层。

#### 3.3 规则

- 一个页面至多一个 `<link rel="theme">`，写多个报错；组件文件不能链接主题（沿用现有规则），组件经变量跟随使用它的页面。
- href：内置主题 `@ixchel/themes/<名>`（库 href 不带扩展名，#039）；项目主题按真实文件路径（相对模板目录或 `ns:path`，带 `.css`），可被资源包覆盖。内置主题在 classpath（#001），资源包不可覆盖、不随资源重载热更新。
- 主题文件是 CSS，开头（注释之后、第一条规则之前）可写至多一个 `@import "<主题 href>";` 与任意个 `@-ix-effect "<特效 href>";`。被 `rel="theme"` 或 `@import` 引用的文件即主题，不另设标记；导入链可以多级，成环报 `load/import-cycle`。两种 at-rule 只在主题文件中开放，其它样式表或写在规则之后报 `css/misplaced-import`。特效用独立的 `@-ix-effect` 而非 `@import`：库 href 不带扩展名，`@import` 分不出目标种类。
- 层叠：主题文件及其导入链整体进主题层，基底在前；层叠顺序仍为 UA → 组件 → 主题 → 作者（语言文档 §4.1）。主题按文档编译，对该文档的特效注册表，故能写特效参数。
- 页面没有 `rel="theme"` 时只有 UA 样式，lint 不报。以后的宿主配置文件可给出 mod 级默认主题，只作用于没写 `rel="theme"` 的页面，页面上的选择永远优先。
- 换主题不改盒子尺寸：边框宽度由 UA 决定（`button`、`input`、`select`、`dialog`、`ix-tooltip` 1px；表面类无边框），主题只写颜色（含 `border-color`）、背景层、inset `box-shadow`、`backdrop-filter`，不写尺寸、内外边距、`border-width`、`display`、`gap`。改变文字度量的属性（`font-family`、`font-weight`、`text-transform`）只用在 `.ix-title` 这类按内容定宽的位置。
- 引入：库的特效与组件按文件显式引入，或经包（#039）；主题用到的特效由主题文件 `@-ix-effect` 引入，页面不必再写。
- 取代：`<link rel="stylesheet" href="@ixchel/<名>.css">` 删除（clean cutover），旧文件不存在，报 `load/file-not-found`；错误码 `load/library-sheet-not-on-page`、`load/unknown-library-sheet` 停用，组件文件链接主题报 `load/theme-not-on-page`，多个主题报 `load/multiple-themes`。

#### 3.4 表面类

普通容器没有对应的 HTML 元素，作者用类名告诉主题"画成哪种底板"。`ix-` 前缀的类保留给库，lint 报未知的 `.ix-*`。首批：

| 类 | 角色 |
|---|---|
| `.ix-panel` | 主面板，凸起的底板 |
| `.ix-well` | 内凹区：槽位底、列表区、输入区 |
| `.ix-title` | 标题文字 |
| `.ix-muted` | 次要文字 |
| `.ix-divider` | 分隔线 |

浮层不需要表面类：`ix-tooltip`、`dialog` 本身是元素。选中态为 `.ix-selected`，主题不再选中通用的 `.selected`（作者自己的 `.selected` 由作者 CSS 负责）。未选 `<ix-panel>` 元素：一个元素可叠多个类（`class="ix-panel sidebar"`），也不必每种底板新增元素定义。

#### 3.5 不做

- 玩家运行时切换主题：将来按"换主题后重新加载文档"做，本机制不挡路。
- 页面内子树用另一套主题：要求主题规则完全只经变量表达，与 §3.1 的自带规则冲突。
- lint 限制项目主题只能选中库词汇：资源包借项目主题改宿主组件外观是正当用法。

### 4. 变量体系

按角色命名，不按组件命名；tabs、进度条、tree、滚动条、slot 由角色组合，新增组件不必给每套主题加变量。定稿（2026-11-04，草案加 `--ix-overlay-text`：原版浮层是深底白字，与面板相反）：

| 组 | 变量 | 说明 |
|---|---|---|
| 表面 | `--ix-<面>-bg`、`--ix-<面>-image`、`--ix-<面>-shadow`、`--ix-<面>-backdrop`；面 = `panel`、`well`、`overlay`、`control`；另有 `--ix-control-border`、`--ix-overlay-border` | `-shadow` 含框线与斜面（inset）及外投影，`-image` 是背景层列表（含 `paint()`），`-backdrop` 是 `backdrop-filter` 值；`-border` 只给 UA 保留宽度的边框（`button`、`ix-tooltip`）上色，未设为透明（TH1 补） |
| 状态 | `--ix-hover`、`--ix-active`、`--ix-selected`、`--ix-selected-text`、`--ix-disabled-text`、`--ix-focus` | 叠色与焦点环 |
| 文字 | `--ix-text`、`--ix-title`、`--ix-text-muted`、`--ix-text-dim`、`--ix-text-shadow`、`--ix-title-font`、`--ix-overlay-text` | `--ix-title-font` 随 TH3 |
| 强调与语义 | `--ix-accent`、`--ix-on-accent`、`--ix-positive`、`--ix-negative`、`--ix-warning` | |
| 形状 | `--ix-radius`、`--ix-corner` | `--ix-corner` 为 `corner-shape` 值（§5.1），随 TH2 |
| 动效 | `--ix-duration`、`--ix-easing` | |

L3 变量的去向：`--ix-surface` → `--ix-panel-bg`；`--ix-frame` 并入 `--ix-panel-shadow`；`--ix-well` → `--ix-well-bg`；`--ix-button`、`--ix-button-frame` → `--ix-control-bg`、`--ix-control-shadow`；`--ix-button-hover` → `--ix-hover` 叠在 `--ix-control-bg` 上；`--ix-button-text` → `--ix-text`；`--ix-button-disabled-text` → `--ix-disabled-text`；`--ix-tooltip`、`--ix-tooltip-frame`、`--ix-tooltip-text` → `--ix-overlay-bg`、`--ix-overlay-shadow`、`--ix-overlay-text`。

`shape-rendering`（§5.2）、`-ix-click-sound` 是继承属性，主题直接写在 `:root`，不另设变量。

TH1 的基底只给 L3 已有的词汇穿外观（`.ix-panel`、`.ix-title`、`button`、`ix-tooltip`）与新增的表面类；`input`、`select`、`dialog` 的主题规则随 TH4 四套主题一并写，TH1 前后不变。

### 5. 能力缺口与补法

#### 5.1 形状：`corner-shape`

采用 CSS Borders 4 的 `corner-shape`（`round | bevel | notch | scoop | square`），与 `border-radius` 配合：`round` 即现有圆角，`bevel` 是切角，`notch` 配 `border-radius: 1px` 正是原版的 1px 缺角。沿用圆角的 SDF 与遮罩图集（#029），背景、框线、阴影同时生效。未选 `clip-path`：任意多边形要开图层做遮罩或三角化，主题用不到任意形状。

#### 5.2 像素化：`shape-rendering`

现状：圆角与阴影遮罩按每 GUI 像素多个纹素烘焙、线性过滤（`ShapeTessellator.density`），模糊与特效按物理像素计算，GUI 缩放 3–4 时显得平滑、像网页。补法：

- 继承属性 `shape-rendering: auto | crisp-edges`（取 SVG 的属性名）：`crisp-edges` 时遮罩按每 GUI 像素 1 纹素烘焙、最近邻采样，圆角成阶梯，阴影模糊成逐格的像素晕。
- GLSL 原语库加 `ix_snap(p)`，特效可对齐 GUI 像素网格。
- 模糊滤镜像素化（降采样到 GUI 像素后最近邻放大）先不做，看前两项效果再定。

主题在 `:root` 设置：原版、赛博开，dark、全息各自决定。

#### 5.3 文字

- `font-family` 映射到 MC 字体资源 id（`minecraft:default`、`minecraft:uniform`、`minecraft:alt` 附魔台符文、`minecraft:illageralt`，及宿主字体）：`Style.withFont` 已支持，测量与绘制改动小。
- `text-transform: uppercase | lowercase | none`：core 文本变换，便宜。
- `letter-spacing`：需逐字绘制，代价中等，视赛博主题效果再定。

#### 5.4 内置特效

- `ix-chromatic`（layer）：色差 / RGB 分离，全息用。
- `ix-glitch`（layer）：切片错位与闪烁，赛博用。
- `ix-cooldown`（box，参数 `--ix-cooldown-progress`）：图标冷却扫描，由 U4 移入 TH4。
- 现有 `glint`、`dissolve` 改名 `ix-glint`、`ix-dissolve`。
- 扫描线不做特效：1×2px 渐变平铺已能表达。

#### 5.5 不补

`skew`（赛博的倾斜可由切角替代）、`color-mix()`（状态色逐个写成本可接受）、`clip-path`（§5.1）。

### 6. 四套内置主题

● 现有能力可做 ◐ 可近似 ○ 待 §5 补齐

| 主题 | 意图 | 要点 | 支持 |
|---|---|---|---|
| `vanilla` 原版 | 原版容器的浅灰面板 | `#C6C6C6` 底，1px 黑框加 `inset 2px 2px 0 #FFF`、`inset -2px -2px 0 #555` 斜面；内凹反向斜面；按钮 `:active` 翻转斜面；硬阴影文字；无模糊、无常驻动画 | ● 斜面与配色；○ 1px 缺角（`corner-shape: notch`）与 `crisp-edges` |
| `dark` 常规暗色 | 现代暗色浮层 | 半透明深底、`backdrop-filter: blur()`、半透明细亮边、小圆角、投影分层、平滑过渡 | ● 全部 |
| `holo` 全息+电子 | 投影出来的青蓝界面 | `screen`/`plus-lighter` 加色、1×2px 扫描线平铺、`@keyframes` 闪烁、四层渐变背景画四角括号、`:-ix-entering` 配 `scaleY` 投影式进场、`glint` 边缘流光、标题 `drop-shadow` 发光（每个元素一个图层，只用于标题） | ● 大部分；○ 色差（`chromatic`）、装饰字体 |
| `cyber` 赛博 | 高对比霓虹 | 黄、青、品红；切角；彩色 `box-shadow` 霓虹；45° 渐变平铺警示条纹；故障效果；大写标题 | ● 霓虹、条纹；○ 切角、`glitch`、`text-transform`、像素化 |

原版主题不引用原版 GUI 纹理：1.20.1 是 `widgets.png`，1.20.2 起改为 GUI sprite，而内置主题在 core、不分版本 [INFERENCE：未逐版核对纹理路径]。

### 7. 验收方式

- 陈列页：sample 中一份模板列出所有表面类与标准控件的全部状态，四个页面文件各链接一套主题、共用该模板；四张 playtest 截图。
- 换主题不改布局：同一陈列页在四套主题下元素的布局矩形一致，写成 playtest 断言。
- kit：schema 列出内置与项目主题及其变量；lint 报多个 `rel="theme"`、主题文件外或规则之后的 `@import`/`@-ix-effect`、导入成环。不 lint 未知的 `--ix-*` 与 `.ix-*`（#039）。

### 8. 实现顺序

见 `ref/D-ixchel-roadmap-20261029.md` TH1–TH4：主题机制 → 形状与像素化 → 文字 → 四套主题与陈列页，之后 U4。

### 9. 已定（2026-11-04）

1. 内置特效命名：`ix-` 前缀（`paint(ix-glint)`，参数 `--ix-glint-color`），文件名同样带前缀。
2. 项目主题可用项目特效：主题文件以 `@-ix-effect` 引入，内置与项目特效同一写法。
3. 主题自带进出动画：只有 `holo` 带入场动画，内置主题不带离场动画（离场会推迟整页关闭，#032）。
4. 选中态类名改为 `.ix-selected`。
5. 内置特效与组件不免链接，显式引入或经包（#039）。
