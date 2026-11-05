---
tags: [design]
date: 2026-11-05
origin: 资源引用方式在 L5–TH1 迭代中叠出多套规则（rel 种类、@import、主题层、预引入、包），重新从文件出发规范资源的称呼、对外接口与引用关系
related: [DECISIONS.md#034, DECISIONS.md#035, DECISIONS.md#037, DECISIONS.md#039, ref/SD-ixchel-themes-20261104.md, ref/SD-ixchel-template-and-style-language-20261029.md]
---

# 项目资源规范

## Summary

本文是资源文件的目标规范；尚未实现，与现行实现冲突处以本文为准，待实现时修订 #034、#035、#037、#039。已定稿全部各节：引用路径（§1）、CSS（§2）、特效（§3）、前缀（§4）、视图（§5）、登记（§6），§7 列出实现上的改名与删除。CSS 只有 stylesheet 与 partial 两种文件，统一用 `@link` 引用 partial 与特效；被 link 的文件按层排在 link 方之下，不再有主题层，主题只是 partial 的一种用途。特效只由 css 引用，以文件 id 为身份；css 中用 link 处给的 PascalCase 别名（`paint(Glow)`、`--Glow-color`），别名只在本文件有效。html 文件统称视图，以 `<page>` 或 `<component>` 为根声明种类，`<link href>` 不写 `rel`；包删除。元素是 Java 实现的词汇，组件是按文件 link 的资源。core、mc 与宿主用同一 API 登记元素与组件的 Java 部分，库另登记 `@ixchel/` 文件根，根下子树即公开文件；库与宿主文件只在路径与加载方式上不同。

## Detail

### 1 引用路径（href）

- 库文件与宿主文件共用同一套语法、文件种类、引用规则与诊断，只在路径与加载方式上不同；唯一按来源区分的是名字归属（§4）。
- 绝对形式只有两种：ResourceLocation `ns:path`（宿主文件，经资源包读取、可被覆盖）与库路径 `@ixchel/<路径>`（库文件，classpath 读取、不可覆盖）。
- 相对写法是简写：按写 link 的文件所在目录补全为上述两种之一，库文件内的相对路径留在 `@ixchel/` 下，`..` 不越出命名空间根。
- href 一律为文件 id，带扩展名；不再按引用种类补扩展名（取代 #039 中库名省略扩展名的规则）。
- 报错、lint、schema 只显示补全后的绝对 id，资源包作者据此得到要覆盖的路径。
- 库文件能否被资源包覆盖暂不讨论。

### 2 CSS

#### 2.1 文件种类

| 种类 | 声明 | 用途 |
|---|---|---|
| stylesheet | 文件开头 `@stylesheet;` | 某个页面或组件自己的样式，属于一个使用者 |
| partial | 文件开头 `@partial;` | 可复用的样式片段，被 stylesheet 或其它 partial link |

- 种类由文件自身声明，不由引用方式决定；缺少声明或声明与引用关系不符报错。
- 主题不是独立种类：设置 `:root` 令牌（`--ix-*`）并给库词汇写外观的 partial，即通常所说的主题。
- 与 html 对称：页面与 stylesheet 归属单一使用者，组件与 partial 可复用。

#### 2.2 引用

- 唯一的引用写法：`@link "<href>";`，写在所有规则之前，按扩展名区分目标：`.css` 为 partial，`.ixeffect` 为特效，特效可带别名 `@link "<href>" as <别名>;`（§3）。取消 `@import` 与 `@-ix-effect`。

| 引用方 | 可 link | 不可 link |
|---|---|---|
| 页面、组件（html，`<link rel="stylesheet">`） | 至多一个 stylesheet | partial、特效 |
| stylesheet | 任意多个 partial、特效 | stylesheet |
| partial | 任意多个 partial、特效 | stylesheet |

- 页面不能 link partial；取消 `<link rel="theme">`，页面经自己的 stylesheet 选择主题 partial。
- 删除 html 中的 `<style>`：页面与组件的样式只来自它 link 的那一个 stylesheet。`style` 属性与 `bind:style:*` 是元素自身的内联声明，不属于此处。

#### 2.3 展开与层

- stylesheet 展开为一份样式表：深度优先，被 link 的文件先于 link 方；每个文件在一次展开中只出现一次，位于第一次出现处；成环报错。
- 展开顺序（后序）即层序：每个文件一层，被 link 的低于 link 方，并列的按 link 顺序，后者高。层之间按层序决定胜负，不比较特异性；层内按特异性与书写顺序。语义同 CSS Cascade 5 的 `@layer`，`!important` 声明的层序反转。
- partial 按普通 CSS 书写，不需要 `:where()` 降低特异性：link 方的规则总能覆盖它 link 的 partial。
- 文档的层叠，低到高：

| 顺序 | 内容 |
|---|---|
| UA | 引擎常驻 |
| 组件层 | 每个组件的 stylesheet 展开结果，内部按 §2.3 分层 |
| 作者层 | 页面的 stylesheet 展开结果，内部按 §2.3 分层 |
| 内联 | `style` 属性、`bind:style:*` |

- 同一文件可同时出现在组件层与作者层，去重按层分别进行。

示例：

```css
/* techmod:ui/machine/panel.css */
@stylesheet;
@link "../themes/main.css";
@link "../common/slot.css";
.header { flex-direction: row; }
```

```css
/* techmod:ui/themes/main.css —— 宿主主题，资源包覆盖它即整体换皮 */
@partial;
@link "@ixchel/themes/vanilla.css";
:root { --ix-panel-bg: #B8C0C8; }
```

展开后的层序（低到高）：`@ixchel/themes/base.css` < `@ixchel/themes/vanilla.css` < `techmod:ui/themes/main.css` < `techmod:ui/common/slot.css` < `panel.css` 自身规则。

#### 2.4 换皮

- 页面不直接 link 库主题，而是经宿主自己的主题 partial；资源包覆盖该 partial 即可替换基底、令牌与附加规则。
- 替换粒度为整个文件。

#### 2.5 诊断

- `style/component-root-token`（WARNING）：组件层中来自某组件 stylesheet 展开的规则在 `:root` 上声明自定义属性。`:root` 匹配页面顶层，令牌会作用于整个页面；组件应经变量跟随页面，令牌由页面的 stylesheet 设置。

#### 2.6 关键字

- `@stylesheet`、`@partial`、`@link` 不加 `-ix-` 前缀：它们只出现在 Ixchel 的样式文件中，不与标准 CSS 的 at-rule 重名。
- §2 的引用诊断（缺少或不符的种类声明、link 了不允许的种类、成环、`as` 用于 `.css`）待定。

### 3 特效（ixeffect）

#### 3.1 对外接口

- 身份为文件 id（§1）；对外是种类（`box` / `layer`）与参数表（每个参数的 syntax、inherits、initial-value）。css 中使用的名字是 link 处给的别名（§3.3），不是全局名。
- GLSL 函数体与 `ix_*` 原语是内部实现；特效从元素取得的输入（盒子几何、`ix_content`、`ix_time`、`ix_scale`）由引擎提供，不经引用方。

| 使用方 | 使用的内容 |
|---|---|
| css | 别名与种类：`background-image: paint(<box>)`、`filter: -ix-paint(<layer>)`；参数：声明、`transition`、`@keyframes`、`var()` |
| 页面、组件 | 参数：`bind:style:--<别名>-<参数>`，编译期按参数类型转换 |

#### 3.2 引用与可见性

- 特效只由 css 引用（`@link "x.ixeffect" [as <别名>];`），取消 html 的 `<link rel="effect">`。
- 运行时按文档：文档中所有展开结果 link 的特效按文件 id 去重后登记一次；每个参数登记为一个以文件 id 构造的内部属性，进文档的属性表，供样式、transition、keyframes、`bind:style`、布局共用。内部名不可能与作者书写的属性名重合，不同特效之间不存在名字冲突。
- 编译期按文件：
  - 别名只在 link 它的那个文件内有效，不随 partial 展开传递。stylesheet 要写某个 partial 所用特效的参数，自己再 link 该特效（同一文件 id 去重，别名可不同）。
  - partial 单独检查，不依赖 link 它的文件。
  - 页面或组件只可 `bind:style` 它那一个 stylesheet 自己 link 的特效参数，用该 stylesheet 的别名；没有 stylesheet 则不可绑定特效参数。
- 改写：每个文件编译时把本文件内出现的别名（`paint()`、`-ix-paint()`、参数声明、`transition`、`@keyframes`、`var()`，以及任何声明值中的 `paint(…)`）改写为内部名；值在写下它的文件中改写，经 `var()` 传到别处的值不再依赖别名。运行时字符串（如 `bind:style:background-image`）按所在 html 的 stylesheet 的别名表解析。
- 报错与调试工具把内部名还原为"文件 id + 别名"显示。
- 特效 link 只登记，不产生规则，不参与 §2.3 的分层。

#### 3.3 别名与参数写法

- 别名为 PascalCase：`[A-Z][A-Za-z0-9]*`，不含 `-`。省略 `as` 时由文件名转换：`glow-ring.ixeffect` → `GlowRing`。
- 库特效文件名不带 `ix-`（路径已有 `@ixchel/effects/`）：`glint.ixeffect`、`dissolve.ixeffect`，默认别名 `Glint`、`Dissolve`。
- 文件名与参数名为小写 kebab-case（字母开头，字母、数字、`-`）。
- 参数属性写作 `--<别名>-<参数>`，在第一个 `-` 处拆分，无歧义：`--GlowRing-edge-width` 为别名 `GlowRing`、参数 `edge-width`。
- 首字母大写的自定义属性保留给特效参数；普通变量与令牌一律小写开头（如 `--ix-panel-bg`）。CSS 自定义属性区分大小写，引擎保留其大小写（含 `bind:style:--*`）。

```css
@stylesheet;
@link "@ixchel/effects/dissolve.ixeffect";        /* 别名 Dissolve */
@link "techmod:ui/fx/glow.ixeffect";              /* 别名 Glow */
.card       { filter: -ix-paint(Dissolve); transition: --Dissolve-progress 1500ms linear; }
.card.burnt { --Dissolve-progress: 1; }
.badge      { background-image: paint(Glow); --Glow-color: var(--ix-accent); }
```

#### 3.4 诊断

特效文件：

| 代码 | 级别 | 触发 |
|---|---|---|
| `effect/invalid-header`、`effect/missing-kind`、`effect/unsupported-kind`、`effect/missing-descriptor`、`effect/unsupported-syntax`、`effect/compile-failed` | ERROR | 不变 |
| `effect/invalid-name` | ERROR | 文件名或参数名不是小写 kebab-case |
| `effect/duplicate-param` | ERROR | 同一特效内两个参数同名（跨特效的情况已不可能出现） |

引用处（css 与 `bind:style`）：

| 代码 | 级别 | 触发 |
|---|---|---|
| `effect/invalid-alias` | ERROR | `as` 后的别名不是 PascalCase |
| `effect/duplicate-alias` | ERROR | 同一文件中两个不同的特效文件得到同一别名（含省略 `as` 时文件名相同）；报在后一个 link 上，提示用 `as` 改名 |
| `effect/unknown-alias` | ERROR | `paint()`、`-ix-paint()` 中的名字，或首字母大写的自定义属性 `--X-…` 中的 `X`，不是本文件 link 的别名；`paint()` 中写了小写名时提示别名为 PascalCase |
| `effect/unknown-param` | ERROR | 别名有效，但参数不存在，或只写了 `--X` 没有参数部分；提示列出参数表 |
| `effect/wrong-kind` | ERROR | box 特效用于 `filter`，或 layer 特效用于 `background-image`（不变） |
| `effect/unmarked-param` | WARNING | 小写开头的自定义属性按不区分大小写比较，恰好等于某个可见别名的参数（如可见 `Glow` 时写了 `--glow-color`）；提示改为 `--Glow-color` |

- 删除 `effect/duplicate-effect`：同一文件 id 去重，不同文件靠别名区分，文档级重名不再发生。`effect/unknown-effect` 由 `effect/unknown-alias` 取代。
- `bind:style:--X-…` 在 html 编译期使用同一组引用处代码；运行时字符串解析失败时以 `effect/unknown-alias` 报告。
- `as` 写在 `.css` 的 link 上、html link 特效等引用关系错误，归入 CSS 引用的诊断，与 §2 的引用诊断一并定义。

### 4 前缀

一个前缀只表示一种东西：

| 前缀 | 唯一含义 | 实例 |
|---|---|---|
| `-ix-` | 引擎对 CSS 语法的扩展 | 属性 `-ix-click-sound`、函数 `-ix-paint()`、伪类 `:-ix-absent` `:-ix-entering` `:-ix-exiting` |
| `ix-` | Ixchel 提供的公开名字，按形状区分种类，保留给库与引擎 | `.ix-*` 词汇类、`--ix-*` 主题令牌；`<ix-*>` 标签见 html 规范 |
| `ix_` / `Ix` | 特效 GLSL 前导中的原语与结构 | `ix_time`、`ix_content`、`IxBox`、`IxLayer` |
| `@ixchel/` | 库路径（§1） | `@ixchel/themes/vanilla.css` |

- 特效参数不占用 `ix-`：写作首字母大写的 `--<别名>-<参数>`（§3.3）。`--ix-*` 只剩主题令牌一种含义（kit schema 按此收集令牌）。
- 宿主可以给库的 `.ix-*` 写规则、给库的 `--ix-*` 赋值（换皮），不可新造 `ix-` 名字。库的词汇类与令牌表由库文件导出。
- 诊断（WARNING，同时捕获拼写错误）：
  - `style/unknown-library-class`：选择器中的 `.ix-*` 不在库的词汇类表中。
  - `style/unknown-library-token`：声明或 `var()` 中的 `--ix-*` 不在库的令牌表中。
  - 名字归属：文件 id 位于 `@ixchel/` 下的文件可定义 `ix-`、`mc-` 开头的名字（组件名、词汇类、令牌），其他文件不可；这是命名检查按文件 id 放行，不是另一种文件种类。

### 5 视图（html）

#### 5.1 命名

- html 文件统称视图（view），与 ViewModel 对应（MVVM）。"template"一词不再使用。
- 标注 ViewModel 类所对应视图文件的注解为 `@UiView`（取代 `@UiTemplate`）。
- ViewModel 子类命名为 `*Model`（取代 `*View`），如 `QuestsModel`、`PlayerInventoryModel`。
- 实现中的对应改名：`TemplateParser` → `ViewParser`，`Template` → `ParsedView`，`TemplateCode` 与诊断 id `template/*` → `ViewCode` 与 `view/*`，宿主 API 的 `templatePath` → 页面 id；`TemplateLoader` 承担本规范的引用图加载，改为 `link` 包的 `Linker`。

#### 5.2 元素与组件

元素是词汇，组件是资源；使用处同构（#035），作者不必知道标签由哪种方式实现。

| 类别 | 实现 | 例子 | 可见性 | 添加者 |
|---|---|---|---|---|
| HTML 元素 | 引擎 Java | `div` `button` `input` `dialog` `img` | 全局常驻 | 引擎 |
| 引擎扩展元素 | 引擎 Java（顶层、指针定位） | `ix-tooltip` `ix-drag-preview` | 全局常驻 | 引擎 |
| 宿主层元素 | `@UiElement` + `ElementPainter`，宿主层绘制或普通流内容 | `mc-item` `mc-entity` `mc-slot` | 按宿主全局，不经 link | mc 层、宿主（登记，§6） |
| 组件 | 视图文件 + css，可选 ViewModel 与工厂（登记，§6） | `guide-book` `mc-player-inventory` | 按文件 link | 任何文件 |

- 选用：需要标记写不出的能力（自绘、布局或交互、顶层）用元素；其余用组件。布局与交互 facet 仅引擎可用（登记时检查，§6）。
- 组件名是全局身份：进入元素树，被 css 类型选择器与查询观察，因此由组件文件定义、文档内唯一，不做文件局部别名（与特效不同）。
- 前缀只表示提供者（§4），不表示元素或组件。
- 诊断：使用未 link 的组件标签时，`view/unknown-tag` 提示声明该名字的组件文件 id（在库目录与宿主文件中查找）。

#### 5.3 包

- 删除包（`<link rel="package">` 与包文件）：视图直接 link 所用的组件文件。取代 #039 中的包。
- 现有包 `@ixchel/mc/components.html`、`@ixchel/mc/container.html` 删除；sample 四个页面改为直接 link 组件。

#### 5.4 文件种类与引用

```html
<page>
  <link href="panel.css"/>
  <link href="@ixchel/mc/player-inventory.html"/>
  <div class="panel">…</div>
</page>

<component name="guide-page" params="heading=''">
  <link href="page.css"/>
  <div class="page">…</div>
</component>
```

- 视图文件恰有一个根元素 `<page>` 或 `<component>`，由它声明种类；根外只允许注释与空白。根元素是定义，不进元素树。`<component>` 取代 `<template>`，签名属性 `name`、`params`、`events` 不变（#035）。
- `<link>` 写在根元素内、元素树之前，只有 `href`，不写 `rel`；被 link 文件的种类由其自身声明，引用方只核对。
- 视图可 link：至多一个 stylesheet（§2.2）、任意多个 `<component>` 文件。link partial、`.ixeffect`（特效只由 css link，§3）或 `<page>` 均为错误。
- 诊断：
  - `view/missing-root`：无根元素、多个根元素或根元素不是二者之一。
  - `link/wrong-kind`：被 link 文件声明的种类不允许出现在此处。

### 6 登记

#### 6.1 登记什么

core、mc 层与宿主用同一 API 登记元素与组件的 Java 部分；库另登记文件根。css、特效与视图文件不逐个登记，由 link 按 id 读取，工具所需的文件清单由扫描来源得到。

| 登记 | 原因 | 键 |
|---|---|---|
| 库文件根（仅库） | `@ixchel/` 路径需映射到 classpath 位置 | `@ixchel/` 下的路径前缀 |
| 元素 | Java 实现的词汇，没有文件可 link | 标签 |
| 组件的 Java 部分 | 视图文件找不到自己的 ViewModel 与工厂 | 视图文件 id（`@UiView`） |

- 库文件根把一段 `@ixchel/` 路径映射到锚点类旁的一个专用目录；未映射的路径读不到（`link/file-not-found`）。根下整棵子树即库的公开文件，不另设导出清单或内部文件。
- 锚点类：根用库自己的一个类定位，按该类所在包的目录、经该类的类加载器读取（`Class.getResourceAsStream`）。库被宿主重定位打包时文件路径随包名移动，多份库副本并存时各自读到自己的文件。
- 根目录只放视图、css 与特效：现 mc 根锚在 `com/azane/ixchel/mc/`，连 `paint/shaders/*` 与类文件都在可读范围内，需改为专用目录。
- 宿主文件不登记来源：`ns:path` 经加载器的资源系统读取，覆盖所有命名空间与资源包。宿主登记的 modid 只决定元素与组件的可见范围。
- 文件清单只有 kit 需要：kit 直接按条目读取根所在的 jar 或目录（与它扫描类文件同法），不经类加载器枚举，因此不受 #037"枚举 classpath 在 jar 内不可靠"的限制。运行时只按 id 读取，从不枚举。

```java
UiRegistry core = UiRegistry.library()
        .root("", Library.class, "ui/")                         // @ixchel/x → core/library/ui/x
        .elements(HtmlElements.ALL);
UiRegistry mc = UiRegistry.library()
        .root("mc/", McLibrary.class, "ui/")                    // @ixchel/mc/x → mc/ui/x
        .element(new ItemElement()).element(new EntityElement())
        .component(PlayerInventoryModel.class, PlayerInventoryModel::new);
UiRegistry techmod = UiRegistry.host("techmod")                 // 无根：techmod:… 等经资源系统读取
        .element(new GaugeElement())
        .component(EnergyModel.class, EnergyModel::new);

UiHosting hosting = UiHosting.of(core, mc, techmod);            // 一份文档所见
```

API 名与目录名为示意，实现时确定。

#### 6.2 声明

- 元素由 `@UiElement(tag, payload, attributes)` 声明在实现类上（取代 `@NativeElement`），组件的 Java 部分由 `@UiView` 声明；kit 不启动游戏，静态读取这些声明与登记。元素不声明适用界面（#042）。
- 元素与组件是同一 API 的两种声明，"Native"不再使用：所有元素都由 Java 实现，不与 HTML 元素区分。

#### 6.3 按来源保留的差异

| 差异 | 原因 |
|---|---|
| 文件来源：库登记 classpath 根，宿主经资源系统读取所有命名空间 | 加载方式（§1） |
| 名字归属：仅 `@ixchel/` 可定义 `ix-`、`mc-` | §4 |
| 可见范围：core、mc 的登记对所有宿主可见，宿主的只对自己的文档可见；不同宿主可用同一标签 | 宿主互不干扰（现 `McElements.forHost`） |
| 元素能力：布局与交互 facet 仅引擎可用 | 能力限制，不是登记形式的差异 |

### 7 实现对照（改名与删除）

| 位置 | 现在 | 改为 |
|---|---|---|
| core `template` 包 | `TemplateParser`、`Template`、`TemplateCode`、`template/*` | `ViewParser`、`ParsedView`、`ViewCode`、`view/*`；包名随分包确定 |
| | `FileKind.LIBRARY_COMPONENT`、`FileKind.PACKAGE` | 删除；种类由根元素读出（§5.4） |
| | `<template>`；`template/missing-component-template`、`template/component-file-content`、`template/link-after-element`、`template/invalid-link` | `<component>`、`<page>`；`view/missing-root`、`link/wrong-kind`，其余随 §5.4 并入 `view/*` |
| | `RESOURCE_TAGS`（`style`、`link`） | 只剩 `link` |
| | `TemplateLoader`、`Loaded`、`load/*` | `link` 包的 `Linker`、`link/*`；删除 `Theme`、`themes`、`packages` 及 `load/theme-not-on-page`、`load/multiple-themes`、`load/import-cycle`、`load/package-content` |
| | `@NativeElement`、`NativePayload<T>` | `@UiElement`、`ElementPayload<T>` |
| core `html` | `NativeElements.declare` | 并入登记（§6），保留 facet 限制 |
| core `library` 包 | `JavaComponent` | 移出：宿主组件同样使用（`UiHosting:26`），属于组件契约 |
| | `Library` 的组件表、`themes()`、`effects()`、`packages()` 手写目录 | 删除；`Library` 只剩 `@ixchel/` 路径到 classpath 的映射，组件登记见 §6 |
| core、mc 资源 | 库文件与类、着色器同处锚点类目录（mc 根为 `com/azane/ixchel/mc/`） | 移入各自根的专用目录（§6.1） |
| core `runtime` | `UiHosting(Library, Map<String, JavaComponent>)` 与 `forBindings` 中的 `isLibrary` 分支 | 由各登记合成，组件按文件 id 一张表 |
| | `LibrarySheets` | `UserAgentSheets`（只含 UA 样式表与元素的 display） |
| | `UiDocument` 中的 template 命名 | view / page |
| core `css` | `ParsedSheet.Import`（`Kind.THEME`/`EFFECT`）、`imports` | `Link`、`links`，另带文件种类声明 |
| | `css/invalid-import`、`css/misplaced-import` | `css/invalid-link`、`css/misplaced-link` |
| core `style` | `StyleEngine` 的 `THEME` 层 | 删除，按文件分层（§2.3） |
| core `binding` | `@UiTemplate` | `@UiView` |
| mc（两版本） | `DeclaredTemplate`；宿主 API 的 `templatePath`/`template` 参数 | `DeclaredView`；页面 id |
| | `PlayerInventoryView`、`TooltipLinesView` | `PlayerInventoryModel`、`TooltipLinesModel` |
| | `NativePainter<T>` | `ElementPainter<T>` |
| | `McLibrary.LIBRARY`、`UiComponents.register`、`McElements.register`/`PAINTERS`、`TAGS` 逐层 `extend` | 统一登记（§6） |
| kit | `Components.LibraryPackage`；补扩展名逻辑（`Components:75`、`Schema:148`）；读库目录 | 删除；库文件清单改为扫描来源 |
| | `Project.library()` 反射 `LIBRARY` 字段、`ElementDeclarations` 扫描 `@NativeElement` | 读取 §6 的登记与 `@UiElement` |
| sample | ViewModel 子类 `*View`（约 17 个） | `*Model` |
