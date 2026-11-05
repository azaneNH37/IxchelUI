---
tags: [survey, design]
date: 2026-11-04
origin: 规划 U4（组件库）时，对照 AUI 与 LDLib2 的现成控件，并按科技、魔法、冒险、实用类 mod 的常见界面整理需要内置的元素与组件；2026-11-05 按 TH4 落地结果逐项定稿
related: [DECISIONS.md#003, DECISIONS.md#016, DECISIONS.md#030, DECISIONS.md#034, DECISIONS.md#035, DECISIONS.md#040, DECISIONS.md#041, DECISIONS.md#043, DECISIONS.md#044, ref/D-ixchel-roadmap-20261029.md, ref/S-ixchel-ui-capability-comparison-20261102.md, ref/SD-ixchel-themes-20261104.md, ref/SD-ixchel-context-model-and-components-20261030.md, ref/SD-ixchel-template-and-style-language-20261029.md]
---

# Ixchel UI U4 Built-in Elements and Components

## Summary

已定稿（2026-11-05）。U4 做四件事：引擎补齐（可写的 `provide:`/`uses`、表达式管道、`<else>`/`<empty>`、`disabled` 子树状态、`<details>`、radio）、库模板组件（`ix-tabs` 一组、`ix-bar`、`ix-stepper`、`ix-cycle`、`ix-pager`、`ix-tree-row`、`ix-search`）与表面类、`<mc-item>` 修饰与轮播。新引擎元素（视口、右键菜单、径向菜单、按键捕获、取色器）、`<mc-fluid>`、`<mc-key>` 另开阶段。执行顺序与验收在路线图 U4。

## Detail

### 1. 现成控件对照

源码：AUI `common/.../element` 与 `targets/forge-1.20.1/.../element`；LDLib2 `gui/ui/elements`（均为本地克隆，未构建运行）。

| 来源 | 控件 |
|---|---|
| AUI | 标准 HTML 为主：`input`（text、number、checkbox、radio、range、color、file、date 等）、`textarea`、`select`、`pre`、`canvas`、`svg`、`iframe`、`audio`；MC：`item`、`slot`、`container`、`recipe`（按配方 id 生成槽位）、`ingredient`（多候选轮播，悬停暂停，间隔可由 CSS 或属性设）、`sprite`（帧动画）、`texture`、`theme-switcher`。无 tabs、tree、进度条 |
| LDLib2 | basic：`button`、`label`、`toggle`（含 group）、`switch`、`text-field`、`text-area`、`selector`、`search-component`、`progress-bar`（填充方向、插值）、`slider`、`tag-field`；container：`scroller-view`、`virtual-scroller`、`split-view`、`tab-view`、`tree-list`、`menu`、`graph-view`（节点图平移缩放）；inventory：`item-slot`、`fluid-slot`（容量）、`inventory-slots`；misc：`scene`（3D）、`color-selector`、`code-editor`、`structured-tag-editor`（NBT）、`localized-number-text`、`inspector` |
| Ixchel | `div`、`span`、`a`、`img`、`button`、`input`（text、number、checkbox、range）、`select`/`option`、`dialog`、`ix-tooltip`、`ix-drag-preview`；`mc-item`、`mc-entity`、`mc-slot`、`mc-tooltip-lines`、`mc-player-inventory` |

sample 中已重复手写的控件：tabs（character、effects）、竖向能量条与水箱（machine）、血条（env HUD）、toast（loot HUD）、选中行列表（index、waypoints）。

### 2. 场景

| 类型 | 典型界面 | 共性需求 |
|---|---|---|
| 科技（Mekanism、Thermal、AE2、RS、GT、IE） | 机器、终端、合成计划 | 竖向能量条、流体罐（流体贴图平铺 + 刻度）、进度箭头与燃烧火焰（贴图按比例裁切）、红石模式等循环按钮、六面配置、过滤用幽灵槽、大数字（1.2k、12.5 kFE）、数量输入（±，Shift ×10）、侧边抽屉标签 |
| 魔法（Botania、Ars、Thaumcraft、Blood Magic、Iron's Spells） | 法典、研究树、法术选择 | 翻页书、可平移缩放的节点树与连线、径向菜单、法力条、图标冷却扫描、带物品链接的富文本 |
| 冒险/RPG（Curios、Apotheosis、FTB Quests、Easy NPC、Waystones） | 角色面板、任务、对话、HUD | 属性行带条、血条掉血拖尾、Boss 条、对话框（逐字显示与选项）、toast、罗盘、稀有度边框、装饰槽占位图标、按键提示 `[R]` |
| 实用（Cloth/YACL 配置、Jade、小地图、整理） | 配置、信息 HUD、列表 | 开关、带数值滑块、枚举循环、取色器、按键捕获、列表编辑、搜索与过滤标签、分页、右键菜单、可折叠分组、可排序表格 |
| 原版对照 | 统计、进度、创造物品栏、切石机、村民交易、配方书 | 统计是带标签的可排序表格；进度是标签加可平移的树；配方书与交易是分页或滚动网格加禁用态 |

"外层持有共享值、内层若干项读它并在点击时写它"在上表中反复出现：tabs 与面板、分段选择、菜单与菜单项、可选列表与行。这是 §3.1 做可写 `provide:`/`uses` 的依据。

### 3. 引擎补齐

#### 3.1 共享上下文 `provide:`/`uses`（#043）

```html
<component name="ix-tabs" params="value">
  <div provide:tabs="value"><slot></slot></div>
</component>

<component name="ix-tab" params="value label" uses="tabs">
  <button class="ix-tab-button" part="button" bind:class:ix-selected="tabs == value"
          on:click="tabs = value" bind:text="label"></button>
</component>
```

- `provide:<名>="<表达式>"` 写在任意元素上，对其子树中声明 `uses="<名>"` 的组件实例可见；页面与组件体都可写。
- `uses` 是组件签名的一部分，与 `params`、`events` 并列；所用名字进入组件体的名字表，受不遮蔽规则约束。
- 解析：加载期，按实例外壳在展开后元素树中的位置找最近的提供者。插槽内容写在使用方文本里，提供者在组件体里，因此按展开后的树而不是文本位置查找；展开在加载期一次完成，结果静态（不变式 4）。
- 可写性：表达式是提供者手上的可写路径（局部状态、`Value` 字段、组件参数中使用方传入的可写路径）时，使用者可以写入，写入落到该路径；否则只读，写入在加载期报错。即提供者只能转交自己被授予的写权限（不变式 2）。
- 没有提供者：加载期报 `component/missing-provider`，不设默认值。
- 不做：组件事件冒泡到提供者（改事件模型，影响面更大）；Java 给的文档根作提供者（没有消费者）。

#### 3.2 表达式管道（#044）

```html
<span bind:text="energy | compact"></span>                 <!-- 12500 → 12.5k -->
<span bind:text="(energy | compact(2)) + ' FE'"></span>    <!-- 后面要拼接时加括号 -->
<span bind:text="value * 1.0 / max | percent"></span>      <!-- 0.456 → 46% -->
```

- 语法：`pipe := expr ('|' 名字 ('(' expr (',' expr)* ')')?)*`，优先级最低（低于 `?:`，同 Angular）；`primary` 的括号内是 `pipe`；属性值顶层是 `pipe`。单个 `|` 原本无用，`||` 按最长匹配不受影响。
- 参数用括号，不用冒号（`:` 已属 `?:`）。
- 管道名只出现在 `|` 右侧，与 ViewModel 成员、局部状态、参数互不冲突，不加 `$`。
- 首版只有内置管道，宿主不能注册；都是纯函数，`Locale.ROOT`，不随游戏语言变化。单位不做管道：括号拼接，或经 i18n `%s` 放进语言文件。

| 管道 | 输入 | 输出 |
|---|---|---|
| `compact(digits = 1)` | 数值 | 千进位 k/M/G/T，最多 `digits` 位小数、去尾零：`12500` → `12.5k`，`999` → `999` |
| `duration` | tick 数（整数） | `m:ss`，满一小时 `h:mm:ss`：`1250` → `1:02` |
| `percent` | 0–1 的数值 | 四舍五入到整数加 `%`：`0.456` → `46%` |

- 类型检查在加载期：未知管道 `binding/unknown-pipe`，输入或参数类型不符 `binding/pipe-type`。
- 带管道的表达式不是裸路径，不能双向绑定。

#### 3.3 `<else>`、`<empty>`

```html
<for each="rows" as="r" key="r.id">…</for><empty><span t="…none"></span></empty>
<if test="a">…</if><else>…</else>
```

- 作为紧跟其后的兄弟块，中间只允许空白；不紧邻报加载错误。
- 不做 `<else-if>`：config 的 6 个按类型分支的 `<if>` 照旧写。
- i18n 文档 §7 "模板中没有 `<else>`"随之作废。

#### 3.4 `disabled` 子树状态

- `DISABLED` 状态改为"自身或任一祖先带 `disabled`"，同 `<fieldset>`；`:disabled` 匹配、获焦判断（`EventDispatcher` 第 797 行）与事件判断（`inDisabled`）统一用它。设置或移除 `disabled` 时整棵子树标样式脏。
- 现状：事件已沿祖先链禁用，选择器与获焦只看元素自身。这是 #040 已定、尚未实现的部分。

#### 3.5 `<details>`/`<summary>`

```html
<details bind:open="expanded"><summary t="…"></summary>…</details>
```

- `bind:open` 双向，复用 `:open`；`<summary>` 须为第一个子元素，否则 lint 报错；点击 `<summary>` 切换。
- 展开标记由 UA 的 `summary::before` 按 `:open` 画。
- 收起时其余内容 `display: none`；不是进出单元，首版无开合动画。

#### 3.6 radio

```html
<for each="modes" as="m" key="m.id">
  <input type="radio" bind:group="mode" bind:value="m.id"/>
</for>
```

- 按绑定路径分组，不用 `name`（同 Svelte `bind:group`）：`name` 在 `<for>` 行里要拼唯一名字，违反不变式 1。
- 选中即 `mode == value`，点击时把 value 写回 `mode`；`bind:group` 只接受可写的裸路径。
- `:checked` 与键盘行为同 checkbox；不做方向键在组内移动。

#### 3.7 不做

- `<textarea>`：仍不排期。
- `<progress>`：不做，用 `ix-bar`（#040）。

### 4. 库组件与表面类

#### 4.1 规则

- 只有带模板逻辑（计算、内部状态、多个元素协作）的才做组件；只是外观的做表面类，交给主题。
- 组件是模板组件，不带 ViewModel（#035）；文件 `@ixchel/components/<名>.html`，文件名不带 `ix-`（同库特效），一个文件一个组件，使用方逐个 link（#039、#041）。
- 部件（#040）：`class="ix-<组件>-<部件>" part="<部件>"`。类给主题（全局 partial，类名带组件名避免撞名），`part` 给页面（`ix-bar.energy::part(fill)`，只在该外壳下查找，用短名）。外壳本身按标签名选中，不加类。部件名列入 schema，改名即破坏。
- `base` 把每个部件映射到已有角色变量，四个主题文件不改即可显示（TH4 已定的验收）；部件与表面类先补进主题文档 §6.1 的角色×状态矩阵。

#### 4.2 组件

| 组件 | 签名 | 部件 | 内容 |
|---|---|---|---|
| `ix-tabs` | `params="value"` | — | 外层，`provide:tabs="value"`；使用方传可写路径 |
| `ix-tab` | `params="value label" uses="tabs"` | `button` | 标签头，`t:label` 翻译；选中加 `.ix-selected` |
| `ix-tab-panel` | `params="value" uses="tabs"` | — | `<if test="tabs == value"><slot>`：切换时面板内局部状态重建；要保留状态的页面照旧用 `bind:class:shown` |
| `ix-bar` | `params="value max dir='right'"` | `fill`、`trail` | 比例在组件内 `value * 1.0 / max`（防整除）；`dir` 取 `up\|down\|left\|right`；贴图填充由页面写 `::part(fill)` 背景，不设参数；`trail` 为掉血拖尾，用更慢的 `transition`，默认不显示，主题或页面打开。填充缩进 `--ix-bar-inset`（默认 `1px`，避开主题画在外壳上的框；无框的条设 `0px`），并继承外壳的 `border-radius`、`corner-shape`：引擎只有矩形裁剪，所以满格时与外壳同形，未满时末端也按同样的角切 |
| `ix-stepper` | `params="value min max step=1"` | `dec`、`input`、`inc` | −/+ 按钮加数字输入，`on:click.shift` ×10，`?:` 夹取 |
| `ix-cycle` | `params="value options"` | `button` | 原版 CycleButton：左键下一项、右键上一项；当前项用 `<for index>` 嵌 `<if test="i == value">` |
| `ix-pager` | `params="page count"` | `prev`、`label`、`next` | 上一页 / 页码 / 下一页，首末页禁用 |
| `ix-tree-row` | `params="depth expanded leaf" events="toggle"` | `arrow`、`label` | 树的一行（缩进、展开箭头，内容进默认插槽）；数据由 ViewModel 给展平列表（组件不能自引用，`load/component-cycle`） |
| `ix-search` | `params="value"` | `input`、`clear` | 输入框加清除按钮 |

部件名与参数名为开工默认值，实现中如需调整，须在合入前改本表（合入后即公开契约）。

#### 4.3 表面类

| 类 | 用法 | 状态 |
|---|---|---|
| `.ix-switch` | 加在 checkbox 上 | `:checked`、`:disabled` |
| `.ix-row` | 列表行 | 悬停、`.ix-selected`、`:disabled` |
| `.ix-badge` | 徽标、计数 | — |
| `.ix-key` | 按键提示 `[R]`，文字由使用方写；显示真实绑定键的是 `<mc-key>`（另开阶段） | — |

### 5. `<mc-item>` 修饰与 `<mc-item-cycle>` 轮播

```html
<mc-item bind:item="stack"></mc-item>                          <!-- 默认画原版修饰 -->
<mc-item bind:item="stack" bind:count="amount | compact"></mc-item>
<mc-item bind:item="icon" decorations="none"></mc-item>
<mc-item-cycle bind:items="candidates" interval="1000"></mc-item-cycle>   <!-- 轮播 -->
```

- 修饰：默认画数量、耐久条与冷却（原版 `renderItemDecorations`）；`count` 覆盖数量文字；`decorations="none"` 关闭。index、loot、quests 等页面因此出现耐久条，playtest 截图随之更新。
- 轮播（2026-11-05 定）：单独的绘制元素 `<mc-item-cycle>`，载荷 `items`（`List<ItemStack>`），与 `<mc-item>` 同在 mc `element` 包、共用修饰绘制、`count`/`decorations` 与原版 tooltip（tooltip 显示当前项）。下标 `(guiTicks / 间隔) % 数量`，与 `Feed.tickClock` 同源，同一界面的轮播同相、随游戏暂停而停；`interval` 为毫秒，按 50ms 一 tick 换算，默认 1000；悬停时该格冻结在当前项（按元素的悬停状态，不需事件）。
- 不做：一个元素多个载荷（`ElementPayload<T>` 只有一个 `T`，要换声明机制，只有这一处消费者）；Java 组件包 `<mc-item>`（读不到外壳悬停，每实例轮询）。#030 不变。
- 两个 mc 版本都做。

### 6. 另开阶段

| 项 | 原因 |
|---|---|
| `ix-viewport` 平移缩放视口 | 滚轮缩放、限位、连线；S7 现为手写 |
| `ix-menu` 右键菜单 | 指针处进 `TopLayer`、点外部关闭；可复用 §3.1 的菜单项结构 |
| `ix-radial` 径向菜单 | 按角度命中扇区、按住键弹出 |
| 按键捕获、取色器 | 按键名与改键走 mc 层 |
| `<mc-fluid>` | `FluidStack` 与流体贴图查询是加载器 API，mc 层只依赖原版类；维持宿主实现，sample 示范一份 |
| `<mc-key>` | 读 `KeyMapping`，做成 Java 组件（#035） |
| 方块与多方块预览 | 3D 场景 |

冷却扫描已由 TH4 做成内置特效 `cooldown`（box），不做元素。
