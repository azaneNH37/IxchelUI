---
tags: [survey, design]
date: 2026-10-29
origin: 讨论 user-agent.css 时引出主题、控件与组件化，经与前端框架、游戏引擎对照，收敛为模板与样式语言的目标形态
related: [DECISIONS.md#001, DECISIONS.md#003, DECISIONS.md#004, DECISIONS.md#007, DECISIONS.md#014, ref/SD-ixchel-viewmodel-and-binding-20261026.md, ref/SD-ixchel-sample-scenarios-20261028.md]
---

# 模板与样式语言的目标形态

## Summary

以"最终的 HTML 与 CSS 长什么样"定义模板与样式语言的下一步：组件是由模板定义的自定义元素，按资源路径引入，保留外壳元素，以参数、事件、插槽与外部交互；模板获得表达式、事件语句与局部状态，只做格式化的 `Derived` 与纯视图状态不再进 ViewModel；样式按 UA、组件、主题、作者四层排优先级，层间不比优先级（specificity）。逻辑仍在 Java，不引入 JS。本文为设计草案，除注明"现有"外均未实现。

## Detail

### 1. 术语

本文与后续代码、文档统一使用以下术语。"宿主"只指嵌入 Ixchel 的 mod。

| 术语 | 含义 | 不要混用 |
|---|---|---|
| 元素（element） | 元素树中的节点，如一个 `div` | — |
| 标签（tag） | 源码中的 `<div>`（开始标签）与 `</div>`（结束标签） | 不用"标签"指元素 |
| 标签名（tag name） | `div`；`TagRegistry` 登记的就是标签名 | — |
| HTML 元素 | core 实现的 HTML 子集（`TagRegistry.HTML`），按标准语义 | — |
| 原生元素（native element） | 由 Java 绘制的元素，如 `mc-item`；`TagRegistry.nativeTag` 登记，绘制经 `NativePainter` | Web 语境的"原生"指内置 HTML 元素，本项目不这么用 |
| 组件（component） | 由模板定义的自定义元素；定义在组件文件中 | — |
| 自定义元素（custom element） | 原生元素与组件的统称：非 HTML、带连字符的元素 | — |
| 组件实例 | 组件在模板中的一次使用 | — |
| 外壳元素（host element） | 组件实例在元素树中的根元素，标签名即组件名 | 不译作"宿主元素"，避免与宿主 mod 混淆 |
| HTML 属性（attribute） | 标签上的 `name="value"` | 与 CSS 属性区分，必要时写全 |
| CSS 属性（property） | `color`、`width` 等 | — |
| 组件参数（param） | 组件声明的输入，经外壳元素上的 HTML 属性或指令传入 | 不称"属性" |
| 事件参数（event） | 组件声明的输出，使用方用 `on:<名>` 传入动作 | — |
| 插槽（slot） | 组件模板中由使用方填入内容的位置，`<slot>` | 不与 `mc-slot`（容器槽位）混淆 |
| 指令（directive） | 带前缀的 HTML 属性：`<指令>:<参数>[.<修饰符>]*="<值>"`，如 `on:keydown.up.shift` | 代码中现称 binding attribute（`BindAttribute`） |
| 指令参数 / 修饰符 | `on:keydown.up` 中的 `keydown` / `up` | 与组件参数区分 |
| 结构指令 | 改变元素树的无前缀指令：`if`、`each`、`as`、`key` | — |
| 绑定（binding） | `bind:` 指令建立的数据到元素的关联 | — |
| 动作（action） | ViewModel 上的 `Action` 字段 | — |
| 局部状态 | 模板中以 `state:` 声明、不进 ViewModel 的状态 | — |
| 表达式 / 语句 | 指令值中的求值式 / `on:` 值中的执行式 | — |
| 层叠来源（origin） | CSS 规范的 user-agent、author 等来源；现有 `StyleEngine` 的 origin 0/1 | — |
| 层叠层（cascade layer） | 作者来源内部的分层，语义同 CSS `@layer` | — |
| UA 样式表 | core 的 `runtime/user-agent.css` | — |
| 主题 | 库随带的、只含视觉的可选样式表 | — |
| 继承（inheritance） | CSS 属性值沿元素树由父传子（`color`、`font-size` 等） | CSS 没有样式表或规则之间的继承 |

### 2. 目标形态示例

以 S2 路径点列表（`ui/waypoints/list.html`）为例，加入删除确认与帮助开关。

#### 2.1 文件

```
assets/ixchelsample/ui/
  common/panel.html     common/panel.css      组件 ui-panel
  common/confirm.html   common/confirm.css    组件 ui-confirm
  waypoints/row.html    waypoints/row.css     组件 waypoint-row（只用一次，仅为示意）
  waypoints/list.html   waypoints/list.css    页面模板
```

#### 2.2 组件文件

```html
<!-- common/panel.html -->
<link rel="stylesheet" href="panel.css"/>
<template name="ui-panel" params="heading">
  <div class="header">
    <span class="title" bind:text="heading"></span>
    <slot name="info"></slot>
  </div>
  <div class="body">
    <slot></slot>
  </div>
</template>
```

```html
<!-- common/confirm.html -->
<link rel="stylesheet" href="confirm.css"/>
<template name="ui-confirm" params="open text label" events="confirm">
  <dialog bind:open="open">
    <span class="text" bind:text="text"></span>
    <div class="actions">
      <button on:click="open = false">Cancel</button>
      <button class="danger" on:click="confirm; open = false" bind:text="label"></button>
    </div>
  </dialog>
</template>
```

```html
<!-- waypoints/row.html -->
<link rel="stylesheet" href="row.css"/>
<template name="waypoint-row" params="row">
  <span class="name" bind:text="row.name"></span>
  <span class="coords" bind:text="row.x + ', ' + row.y + ', ' + row.z"></span>
  <span class="distance" bind:class:far="row.distance > 1000" bind:text="row.distance + ' m'"></span>
</template>
```

#### 2.3 页面模板

```html
<!-- waypoints/list.html -->
<link rel="stylesheet" href="@ixchel/dark.css"/>
<link rel="stylesheet" href="list.css"/>
<link rel="component" href="../common/panel.html"/>
<link rel="component" href="../common/confirm.html"/>
<link rel="component" href="row.html"/>
<div class="screen" state:help="false" state:confirming="false"
     on:keydown.up="selectPrevious" on:keydown.down="selectNext"
     on:keydown.delete="confirming = selectedId >= 0">
  <ui-panel class="waypoints" heading="Waypoints">
    <span slot="info" class="count" bind:text="count + (count == 1 ? ' waypoint' : ' waypoints')"></span>
    <button slot="info" class="help" on:click="help = !help">?</button>

    <span class="hint" if="help">Click a row or use Up/Down; Delete removes. Distances update as you move.</span>
    <div class="list">
      <span class="empty" if="count == 0">No waypoints yet. Add one where you stand.</span>
      <waypoint-row each="rows" as="r" key="r.id" bind:row="r"
                    tabindex="0" bind:class:selected="r.id == selectedId" on:click="select(r)"></waypoint-row>
    </div>
    <div class="buttons">
      <button title="Saves the block you stand on, in this dimension" on:click="add">Add here</button>
      <button bind:disabled="selectedId < 0" on:click="confirming = true">Remove</button>
    </div>
  </ui-panel>

  <ui-confirm bind:open="confirming" text="Remove this waypoint?" label="Remove" on:confirm="remove"></ui-confirm>
</div>
```

#### 2.4 样式表

```css
/* common/panel.css：组件层 */
ui-panel {
  padding: 6px;
  background-color: #101418E0;
  > .header {
    flex-direction: row; justify-content: space-between; align-items: center; margin-bottom: 2px;
    .title { color: #FFFF55; }
  }
}

/* common/confirm.css：组件层 */
ui-confirm .actions { flex-direction: row; justify-content: flex-end; column-gap: 4px; margin-top: 4px; }

/* waypoints/row.css：组件层；.selected 由使用方添加，是该组件公开的 class */
waypoint-row {
  flex-direction: row; align-items: center; padding: 3px;
  transition: background-color 120ms;
  &:hover { background-color: #FFFFFF20; }
  &:focus { background-color: #FFFFFF30; }
  &.selected { background-color: #4060FF60; }
  .distance {
    flex-grow: 1; color: #55FF55;
    &.far { color: #FFAA00; }
  }
}

/* waypoints/list.css：作者层，只剩布局与本页特有的外观 */
.screen { width: 100%; height: 100%; align-items: center; justify-content: center; }
ui-panel.waypoints { width: 240px; }
.list { height: 90px; overflow: auto; background-color: #00000060; }
waypoint-row {
  .name { width: 80px; }
  .coords { width: 100px; }
}
.buttons { flex-direction: row; column-gap: 4px; margin-top: 4px; }
.title { color: #FFFFFF; }   /* 优先级 0,1,0 仍覆盖 panel.css 的 .title：作者层高于组件层 */
```

#### 2.5 加载后的元素树

调试、`Script` 选择器与诊断看到的是这棵树：

```
div.screen
├─ ui-panel.waypoints              外壳元素
│  ├─ div.header
│  │  ├─ span.title                "Waypoints"
│  │  ├─ span.count                来自 slot="info"
│  │  └─ button.help               来自 slot="info"
│  └─ div.body                     默认插槽内容
│     ├─ span.hint                 help 为 true 时
│     ├─ div.list
│     │  └─ waypoint-row.selected ×N
│     │     └─ span.name / span.coords / span.distance.far
│     └─ div.buttons
└─ ui-confirm
   └─ dialog                       打开时进入顶层（#014）
```

#### 2.6 ViewModel

```java
final class WaypointsView extends ViewModel {
    record Row(int id, String name, int x, int y, int z, long distance) {}

    final Value<List<Waypoint>> waypoints = source(Source.polled(Waypoints::all));
    final Value<Integer> selectedId = state(-1);
    final Value<BlockPos> playerPos = source(ClientSources.player(LocalPlayer::blockPosition, BlockPos.ZERO));
    final Derived<List<Row>> rows = derive(waypoints, playerPos, WaypointsView::rows);
    final Derived<Integer> count = derive(waypoints, List::size);
    // add、select、remove、selectPrevious、selectNext 不变
}
```

| 现有 `WaypointsView` 中的 | 目标形态 |
|---|---|
| `Row.selected`；`rows` 依赖 `selectedId` | `bind:class:selected="r.id == selectedId"`，选中变化不重建行列表 |
| `Row.coords`、`Row.distance` 拼好的字符串 | 模板表达式 |
| `empty`、`hasSelection` | `count == 0`、`selectedId < 0` |
| `count` 拼成 "N waypoints" | 模板表达式 |
| 帮助、确认框开关 | `state:help`、`state:confirming` |

#### 2.7 原生元素

改名后的 `mc-` 元素（现为 `item`、`entity`、`item-slot slot`）：

```html
<mc-slot class="slot" index="5"></mc-slot>
<mc-slot class="slot" each="inventory" as="i" key="i" bind:index="i"></mc-slot>
<mc-entity class="doll" bind:entity="doll"></mc-entity>
<mc-item class="icon" bind:item="r.icon"></mc-item>
```

### 3. 模板语言

#### 3.1 文件种类

| 种类 | 顶层内容 | 引用方式 |
|---|---|---|
| 页面模板 | `<link>` 声明，然后是元素树 | 宿主 Java 侧传 `ResourceLocation`（现有） |
| 组件文件 | `<link>` 声明，然后恰好一个 `<template name>` | `<link rel="component" href>` |
| 样式表 | CSS | `<link rel="stylesheet" href>`（现有） |

#### 3.2 标签名与前缀

| 前缀 | 含义 | 提供方 |
|---|---|---|
| 无（不含连字符） | HTML 元素，按标准语义；未支持的报未知标签（现有） | core |
| `ix-` | 库提供、与 MC 无关的自定义元素 | core |
| `mc-` | 库提供、表示 MC 概念的自定义元素（`mc-item`、`mc-entity`、`mc-slot`，以后 `mc-fluid`） | mc 层或加载器层 |
| 其它带连字符的名字 | 宿主的组件与原生元素，建议以 modid 开头 | 宿主 |

- 前缀按领域划分，不按实现划分：组件改为原生元素或反之，标签名不变。`mc-fluid` 即使由加载器层实现也用 `mc-`。
- 新增元素前先查 HTML 是否有标准元素（进度条用 `progress`，折叠用 `details`/`summary`），依次再考虑 `ix-`、`mc-`。
- 组件 `name` 与宿主登记的原生元素不得以 `ix-`、`mc-` 开头，加载期报错。
- 撞名只可能发生在一个宿主内部：库按宿主 shade（#001），文档可用的标签只来自 HTML、库与该宿主。同一文档中两个组件同名，加载期报错。

#### 3.3 顶层声明

- `<link rel="stylesheet" href>`（现有）：相对模板目录或 `ns:path`；库内样式表用保留写法 `@ixchel/<名>.css`（暂定，前缀须使用 namespace 不允许的字符，避免与 `ns:path` 冲突）。
- `<link rel="component" href>`：路径规则同上；被引入的组件文件可再引入组件。组件文件随模板与样式表加入热重载监听列表（`McResources.Loaded.files`）。

#### 3.4 组件

- 定义：`<template name="<组件名>" params="<参数…>" events="<事件…>">`，空格分隔。`<template>` 沿用标准含义（不渲染、按实例克隆的内容）。
- 外壳元素：组件实例在树中保留外壳元素，模板内容成为它的子元素。使用方的 CSS 给外壳元素定尺寸与位置，组件自己的 CSS 管内部。
- 外壳元素上的 HTML 属性与指令：
  - 全局属性（`class`、`id`、`style`、`tabindex`、`disabled`、`title`）作用于外壳元素；
  - 普通属性 `p="文本"` 传字符串常量给参数 `p`；
  - `bind:p="表达式"` 传绑定值；若表达式是可写的状态（`Value` 或局部状态），组件内对 `p` 的赋值写回，即双向参数；
  - `on:e="动作或语句"` 传事件参数 `e`；
  - 结构指令（`if`、`each`、`as`、`key`）与 `bind:class:*`、`bind:style:*` 作用于外壳元素；
  - 参数名或事件名与全局属性重名（如 `title`）在加载期报错。
- 作用域：组件模板内只能引用自己的参数、事件、局部状态，不能直接引用外层 ViewModel 或外层局部状态。
- 插槽：`<slot>` 为默认插槽，`<slot name="n">` 为具名插槽；使用方以子元素填入，`slot="n"` 指定具名插槽。插槽内容的绑定在使用方的作用域中解析。
- 实例：每个组件实例有自己的局部状态与订阅，随 `if`/`each` 的挂载与卸载创建、销毁；`each` 中按 `key` 保持实例身份。
- 加载期解析并校验每个组件文件，诊断定位到组件文件的行列。
- 带 Java 逻辑的组件（后续）：宿主在 Java 侧按组件路径登记工厂，每个实例得到一个子 ViewModel。模板中不写 Java 类名，relocate 后类名会变（#001）。

  ```java
  new UiScreen(template, view, parent,
          Components.of("ixchelsample:ui/common/color-picker.html", ColorPickerView::new));
  ```

#### 3.5 指令

语法：`<指令>:<指令参数>[.<修饰符>]*="<值>"`。

| 指令 | 指令参数 | 值 | 现状 |
|---|---|---|---|
| `bind:` | 目标：HTML 属性名、`text`、`class:<名>`、`style:<CSS 属性>`、双向目标 `value`/`checked`/`open`/`scrolltop`、组件参数 | 表达式 | 现有（值仅路径与 `!`） |
| `on:` | 事件名或组件事件名；修饰符为键名与 `shift`/`ctrl`/`alt`、鼠标键（#012） | 语句 | 现有（值仅动作与一个路径参数） |
| `state:` | 状态名 | 初值表达式（只求值一次） | 新增 |

结构指令：`if="表达式"`、`each="表达式" as="名" key="路径"`（现有，`if` 改为接受表达式）；`t="语言键"`（现有）。

`state:` 可写在任何元素上，作用于该元素的子树；在组件模板中则属于组件实例。

#### 3.6 表达式与语句

- 表达式：字面量（数字、单引号字符串、`true`/`false`）、路径、`!`、`+ - * / %`、比较 `== != < <= > >=`、`&& ||`、`? :`；`+` 有一侧为字符串时为拼接。加载期按 `Bindings` 现有的反射与泛型类型解析检查类型，编译为 `Derived` 节点。
- 语句（只在 `on:` 值中）：调用动作 `act` 或 `act(表达式)`、赋值 `状态 = 表达式`，以 `;` 分隔。赋值目标只能是局部状态、可写的 ViewModel `Value` 或双向组件参数。
- 不提供：函数定义、循环、任意方法调用。格式化（补零、小数位）是否以内置函数提供待定（§6）。

### 4. 样式语言

#### 4.1 层叠顺序

| 顺序（低→高） | 来源与层 | 包含 |
|---|---|---|
| 1 | user-agent 来源 | `user-agent.css`（现有） |
| 2 | 作者来源·组件层 | 组件文件中 link 的样式表 |
| 3 | 作者来源·主题层 | `@ixchel/…` 样式表 |
| 4 | 作者来源·不分层 | 页面模板中 link 的样式表、`<style>`、`style` 属性与 `bind:style:*`（现有顺序） |

- 层间按层的顺序决定，不比较优先级；同层内按优先级与书写顺序（现有 `StyleEngine.CASCADE_ORDER`）。
- 语义同 CSS Cascade 5 的 `@layer`：未分层的作者样式高于任何层；`!important` 声明的层顺序反转。库提供的样式表（UA、主题）不使用 `!important`。
- 层由样式表被 link 的位置决定，不开放 `@layer` 语法。

#### 4.2 主题

- 只含视觉：颜色、边框、文字色，以及 `:hover`、`:focus`、`:disabled` 等状态；不含 `width`、`height`、`margin`。
- 首批 `vanilla`（原版容器灰：`#C6C6C6` 底、`#555555` 边、`#404040` 字）与 `dark`（暗色浮层：`#101418E0` 底、`#FFFF55` 标题），来源为 sample 现有两族外观。
- 按 #001 放 classpath，以相对 core 类的路径读取（同 `UiDocument.UserAgent`），relocate 后可用；不随资源重载热更新。
- UA 样式表保持为不引入主题时的兜底外观。

#### 4.3 组件样式

- 组件的样式表与组件文件放在一起，由组件文件 link，进入组件层。
- 不做样式隔离：作者与主题可以选中组件内部元素。组件样式的选择器以组件名开头；组件约定由使用方添加的 class（如 `waypoint-row.selected`）属于组件的公开接口。

#### 4.4 复用手段

| 手段 | 现状 | 计划 |
|---|---|---|
| 一个元素带多个 class、选择器列表 | 支持 | — |
| 多个样式表 | 支持，经模板中的多个 `<link>` | — |
| 嵌套（CSS Nesting） | 报错（`CssParser`："At-rules and nested rules are reported as errors"） | 随组件实现；解析期展开，不改样式引擎 |
| `var()` 自定义属性 | 报错（`Declarations`："custom properties are not supported yet"） | 主题出现同一结构的多个配色变体时 |
| `@import` | 报错（不支持任何 at-rule） | 暂缓：文件组合已由模板侧 `<link>` 完成 |
| `@layer` 语法 | 不支持 | 不开放，层由 link 位置决定 |
| `@extend`、`@mixin` | — | 不做：不是 CSS 标准（#003），多 class 组合已覆盖 |

#### 4.5 与浏览器不同的初值

`display: flex`、`flex-direction: column`、`position: relative`、`color` 白色、`font-size: 9px`，尺寸按 border-box 计算（`CssProperties`）。文档须写明。

### 5. 依据

#### 5.1 复用需求分层

| 复用对象 | 作者能否自己做到 | 归属 | 本文对应 |
|---|---|---|---|
| 行为（编辑、勾选、展开、焦点、模态） | 否，不内嵌 JS | 引擎能力 | 控件，已有（#014） |
| 结构 | 否，现无组合机制 | 引擎能力 | §3.4 组件 |
| 格式化与条件 | 否，表达式只有路径与 `!` | 引擎能力 | §3.6 |
| 纯视图状态 | 否，只能进 ViewModel | 引擎能力 | `state:` |
| 外观 | 能，公共 CSS 加 `<link>` | 内容 | §4.2 主题 |
| 现成组件（Tabs、面板） | 有组件机制后能 | 内容 | `ix-` 组件，后续 |

#### 5.2 sample 中的证据

- 外观：`.panel` 在 7 份 CSS 中分两族，配色逐字重复，尺寸各不相同（§4.2）；`storage/terminal.css` 的 `.search` 重写了 UA 已有的输入框外观。
- 行为：machine、storage、waypoints 用 `<div class="button">` 并各写 `.button` 样式，早于 C2 的 `<button>`。
- 格式化：`EnvView.entityHealth`、`MachineView.energyText`、`LootListView.count`、`TerminalView.summary`、`WaypointsView.count` 等 `Derived` 只做字符串拼接。
- 视图状态：character 的标签页、config 的 `listOpen`、`confirming` 都在 ViewModel 中。
- 结构：S8 指南书对标 Patchouli、GuideME，以"页面类型"复用为核心（按对标对象推断）。

#### 5.3 借鉴

| 决定 | 借鉴 | 放弃 |
|---|---|---|
| 组件按资源路径引入，可被资源包整体替换 | Unity UXML、Godot 场景、RmlUi 模板 | 全局注册表（Vue、Web Components）：多宿主、多副本下易撞名 |
| 保留外壳元素 | Web Components、Godot 实例根节点 | React 不保留根节点：使用方无法从外部定尺寸 |
| 显式参数，可双向 | React props、Svelte `bind:` | WPF DataContext 隐式继承：组件暗中依赖外层字段名 |
| 具名插槽 | Vue、Web Components、UMG Named Slot | — |
| 层叠层 | CSS `@layer`、WPF 属性值优先级 | Shadow DOM 加 `::part()`：主题须由组件逐一暴露 |
| 纯模板组件起步，Java 逻辑由宿主按路径登记 | RmlUi、UXML 模板；Godot 场景脚本、WPF UserControl | 模板中写类名（WPF `x:Class`）：relocate 后失效 |
| 指令写法 | Svelte 3/4（`bind:`、`on:`），Alpine（`x-data` → `state:`） | — |
| 状态与派生 | Solid signal/memo（`Value`/`Derived`，按帧批处理） | 虚拟 DOM 重渲染 |

#### 5.4 不引入 JS

只有"第三方新建带逻辑的界面"离不开通用脚本，它以界面由宿主以外的人定义为前提，与 #001 冲突；宿主需要时自行接入脚本，按 #004 以 JDK 类型交给 ViewModel。JS 不替代本文的任何部分，只叠加引擎、DOM/事件/ViewModel 桥接与响应式桥；动态类型与 #003 的加载期报错冲突；引擎体积（凭记忆：Rhino 约 1.3MB，独立 Nashorn 约 2MB，GraalJS 数十 MB）为 core jar（267KB）数倍且每个宿主各带一份。重新考虑的条件：真实宿主要求第三方在其界面写逻辑，且宿主自行接入做不到。

### 6. 待定

1. 库内样式表 href 的保留写法（`@ixchel/…` 为暂定）。
2. 格式化：内置函数（`fixed(x, 1)`、`pad(x, 2)`）还是一律写 `Derived`。
3. 插槽的后备内容（`<slot>` 内的默认子元素）是否支持。
4. 热重载时局部状态是否保留，按什么身份匹配。
5. 组件实例的子 Scope：`each` 行与 `if` 块现无子 Scope（绑定文档 §5），组件实例需要。
6. 宿主原生元素的登记入口（与 §3.4 的 Java 组件登记是否合并）。
7. 代码命名：`BindAttribute`、`isBindingAttribute` 同时承载 `bind:` 与 `on:`，加入 `state:` 时改为按指令命名。

### 7. 实现顺序

见 `ref/D-ixchel-roadmap-20261029.md`（阶段 D0、L1、L2、L3）。
