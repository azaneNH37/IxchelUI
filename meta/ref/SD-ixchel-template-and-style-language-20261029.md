---
tags: [survey, design]
date: 2026-10-29
origin: 讨论 user-agent.css 时引出主题、控件与组件化，经与前端框架、游戏引擎对照，收敛为模板与样式语言的目标形态
related: [DECISIONS.md#001, DECISIONS.md#003, DECISIONS.md#004, DECISIONS.md#007, DECISIONS.md#014, ref/SD-ixchel-viewmodel-and-binding-20261026.md, ref/SD-ixchel-sample-scenarios-20261028.md, ref/SD-ixchel-context-model-and-components-20261030.md]
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
| HTML 元素 | core 的 `HtmlElements` 装配的 HTML 子集，按标准语义；定义模式见 #031 | — |
| 原生元素（native element） | 宿主 Java 实现的元素，如 `mc-item`；实现类用 `@NativeElement` 声明，绘制经 `NativePainter<T>`，宿主用 `McElements.register` 登记（#030） | Web 语境的"原生"指内置 HTML 元素，本项目不这么用 |
| 组件（component） | 由模板定义的自定义元素；定义在组件文件中 | — |
| 自定义元素（custom element） | 原生元素与组件的统称：非 HTML、带连字符的元素 | — |
| 组件实例 | 组件在模板中的一次使用 | — |
| 外壳元素（host element） | 组件实例在元素树中的根元素，标签名即组件名 | 不译作"宿主元素"，避免与宿主 mod 混淆 |
| HTML 属性（attribute） | 标签上的 `name="value"` | 与 CSS 属性区分，必要时写全 |
| CSS 属性（property） | `color`、`width` 等 | — |
| 组件参数（param） | 组件声明的输入，经外壳元素上的 HTML 属性或指令传入 | 不称"属性" |
| 事件参数（event） | 组件声明的输出，使用方用 `on:<名>` 传入动作 | — |
| 插槽（slot） | 组件模板中由使用方填入内容的位置，`<slot>` | 不与 `mc-slot`（容器槽位）混淆 |
| 指令（directive） | 带前缀的 HTML 属性：`<指令>:<参数>[.<修饰符>]*="<值>"`，如 `on:keydown.up.shift` | 代码中为 `dom.Directive` |
| 指令参数 / 修饰符 | `on:keydown.up` 中的 `keydown` / `up` | 与组件参数区分 |
| 控制块 | 不渲染节点、按数据挂载内容的标签：`<for>`、`<if>`（§3.5） | 不称"元素"：它们不在元素树中 |
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
assets/ixchel_sample/ui/
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
<template name="ui-confirm" params="open message label" events="confirm">
  <dialog bind:open="open">
    <span class="text" bind:text="message"></span>
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

    <if test="help">
      <span class="hint">Click a row or use Up/Down; Delete removes. Distances update as you move.</span>
    </if>
    <div class="list">
      <if test="count == 0">
        <span class="empty">No waypoints yet. Add one where you stand.</span>
      </if>
      <for each="rows" as="r" key="r.id">
        <waypoint-row bind:row="r" tabindex="0" bind:class:selected="r.id == selectedId" on:click="select(r)"></waypoint-row>
      </for>
    </div>
    <div class="buttons">
      <button title="Saves the block you stand on, in this dimension" on:click="add">Add here</button>
      <button bind:disabled="selectedId < 0" on:click="confirming = true">Remove</button>
    </div>
  </ui-panel>

  <ui-confirm bind:open="confirming" message="Remove this waypoint?" label="Remove" on:confirm="remove"></ui-confirm>
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

    final Cell<List<Waypoint>> waypoints = source(Source.polled(Waypoints::all));
    final Value<Integer> selectedId = state(-1);
    final Cell<BlockPos> playerPos = source(ClientSources.player(LocalPlayer::blockPosition, BlockPos.ZERO));
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
<mc-slot class="slot" bind:menuslot="fuelSlot"></mc-slot>
<for each="inventory" as="s" key="s"><mc-slot class="slot" bind:menuslot="s"></mc-slot></for>
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
- 项目组件 `name` 不得以 `ix-`、`mc-` 开头，加载期报错；`@ixchel/` 下的库组件文件不受此限（#037）。原生元素只由 mc 层登记（宿主没有登记入口，见路线文档 D0 结果），`mc-slot` 等不受此限。
- 不含连字符的模板保留标签：`template`、`slot`（HTML 标准元素，按 §3.4 的组件语义）与控制块 `for`、`if`（非 HTML，§3.5）。
- 撞名只可能发生在一个宿主内部：库按宿主 shade（#001），文档可用的标签只来自 HTML、库与该宿主。同一文档中两个组件同名，加载期报错。

#### 3.3 顶层声明

> 文件种类、`<link>`、`<style>`、`@import` 与包的规则由资源规范（`ref/D-ixchel-resource-spec-20261105.md`，#041）取代：视图以 `<page>`/`<component>` 为根，`<link href>` 不写 `rel`，css 以 `@link` 引入 partial 与特效。本节与 §4.4 中与之冲突的描述以资源规范为准。

- `<link rel="stylesheet" href>`（现有）：相对模板目录或 `ns:path`。库的样式以主题提供，页面以 `<link rel="theme" href="@ixchel/themes/<名>"/>` 选择（#034，TH1 起；L3 的 `@ixchel/<名>.css` 已删除）。
- `<link rel="component" href>`：路径规则同上；被引入的组件文件可再引入组件。组件文件随模板与样式表加入热重载监听列表（`McResources.Loaded.files`）。
- `@ixchel/<路径>`（#037）：库的文件命名空间，与 `ns:path` 同一套解析与读取，读取按前缀分派到库（classpath）；库文件内的相对路径在 `@ixchel/` 下解析，`..` 不越出。库文件不热重载，资源包不能覆盖。库 href 不带扩展名，按引用种类补（#039）；库的组件、特效与项目资源一样须显式 link，或经 `<link rel="package">` 引入包（`@ixchel/mc/components`、容器界面用 `@ixchel/mc/container`）。

#### 3.4 组件

- 定义：`<template name="<组件名>" params="<参数…>" events="<事件…>">`，空格分隔；参数可带默认值 `p=<表达式>`。`<template>` 沿用标准含义（不渲染、按实例克隆的内容）。
- 外壳元素：组件实例在树中保留外壳元素，模板内容成为它的子元素。使用方的 CSS 给外壳元素定尺寸与位置，组件自己的 CSS 管内部。
- 端口与使用处写法、名字的可见范围、可写性、插槽、类型检查、实例生命周期：见 `ref/SD-ixchel-context-model-and-components-20261030.md` §1、§3。要点：组件体只见自己的参数、事件与局部状态；外壳元素上的指令与插槽内容在使用方作用域中解析；写权限由使用方传入可写路径授予；未传入的参数取默认值成为实例的局部状态，无默认值即必填；参数名、事件名与全局属性重名在加载期报错。
- 加载期解析并校验每个组件文件，诊断定位到组件文件的行列。
- 带 Java 逻辑的组件（L4，#035）：组件文件 + `@UiTemplate` 指向它的 ViewModel 类 + 宿主按 modid 登记无参工厂（`UiComponents.register(modid, 类, 工厂)`），签名仍在 `<template>`；模板中不写 Java 类名，relocate 后类名会变（#001）。细节见上下文模型文档 §3.7。

#### 3.5 指令

语法：`<指令>:<指令参数>[.<修饰符>]*="<值>"`。

| 指令 | 指令参数 | 值 | 现状 |
|---|---|---|---|
| `bind:` | 目标：HTML 属性名、`text`、`class:<名>`、`style:<CSS 属性>`、双向目标、组件参数。双向与否由目标与元素种类共同决定：`value`（复选框以外的表单控件）、`checked`（复选框）、`open`（`dialog`）、`scrolltop`（任意元素）；其余为单向，`value` 在其它元素上是普通属性 | 表达式（双向目标只接受可写的裸路径） | 已实现（L1），组件参数已实现（L2） |
| `on:` | 事件名或组件事件名；修饰符为键名与 `shift`/`ctrl`/`alt`、鼠标键（#012） | 语句 | 已实现（L1），组件事件已实现（L2） |
| `state:` | 状态名 | 初值表达式（只求值一次） | 已实现（L1） |

控制块（L2 起，取代元素上的 `each`/`if`/`as`/`key` 属性，一次切换、不留兼容；元素上出现这些属性在加载期报错）：

```html
<for each="表达式" as="名" key="路径"> 内容 </for>
<if test="表达式"> 内容 </if>
```

- 不渲染节点：内容按位置成为父元素的子节点；CSS 选择器、游戏内测试（`Playtest`）的选择器与诊断看到的树中没有 `for`/`if`。
- 内容可以是任意多个元素与文本；`<for>` 每行、`<if>` 每次挂载各实例化一份。行或挂载的局部状态写在内容中的元素上。
- 块上只允许自己的属性（`for`：`each`、`as`、`key`、`index`；`if`：`test`），另可带 `slot`（作为外壳元素的直接子块时，见上下文模型文档 §3.1）；其它属性与指令加载期报错。`as` 行名只对块内容可见，`key` 是从行名出发的路径（规则同现有 `each`）。副本的进出动画由 CSS 伪类 `:-ix-absent`/`:-ix-entering`/`:-ix-exiting` 表达，块上没有动画属性（#032）。
- 顶层键处理器的规则不变：块内的元素不注册到文档根（绑定文档 §3）。
- `test` 沿用 XSLT/JSTL 的 `<if test>`。块的挂载机制沿用现有 `Block`（以前一兄弟定位，`Bindings.java:977-1013`，本就不需要占位节点）；需要改的是一份实例由单个根变为多个根（`Instance.root`）。

`t="语言键"`、`t:<属性>="语言键"` 与子元素上的 `mark="名"`（阶段 I）：见 `ref/SD-ixchel-i18n-20261102.md`（#027）。

`state:` 可写在任何元素上，作用于该元素的子树；在组件模板中则属于组件实例，写在外壳元素上的属于使用方（上下文模型文档 §1.1）。`state:x` 声明新名字 x，与 ViewModel 的 `state(...)` 字段同类（可写 `Value`），不能与已可见的名字重名；区别只在作用范围（元素子树）与存活时间（每个挂载副本各自初始化）。

#### 3.6 表达式与语句

```
expr    := or ('?' expr ':' expr)?
or      := and ('||' and)*
and     := eq ('&&' eq)*
eq      := rel (('==' | '!=') rel)*
rel     := add (('<' | '<=' | '>' | '>=') add)?      比较不连写
add     := mul (('+' | '-') mul)*
mul     := unary (('*' | '/' | '%') unary)*
unary   := ('!' | '-') unary | primary
primary := 数字 | '单引号字符串' | true | false | 路径 | '(' expr ')'
路径    := 名字 ('.' 名字)*
stmts   := stmt (';' stmt)* ';'?                     只在 on: 值中
stmt    := 路径 '=' expr | 动作名 ('(' expr ')')?
```

- 字面量：不带小数点的数字为 `Integer`，带小数点为 `Double`；字符串用单引号，`\'`、`\\` 转义；没有 `null` 字面量。
- 路径：首段依次查局部状态、`as` 行名、ViewModel 字段（各来源的可见范围见上下文模型文档 §1）；之后每段为 record 组件，`List` 另有 `size`（`Integer`）。动作字段只能 `.pending`/`.error`。不提供任意方法调用、函数定义、循环。
- 类型（加载期检查，错误带值内行列）：
  - 数值为 `Integer`/`Long`/`Float`/`Double` 及其基本类型，二元运算按 Java 数值提升（`int / int` 仍是整除）。
  - `+` 有一侧静态类型为 `String` 时是拼接，另一侧按 `bind:text` 的转换成文本（`null` 为空）。
  - `== !=`：两侧都是数值时按数值比较，否则两侧类型须有继承关系，运行时 `Objects.equals`。`< <= > >=` 只接受数值。
  - `! && ||` 与 `?:` 的条件只接受 `Boolean`；`?:` 两支类型相同、同为数值（提升）或一方可赋给另一方。
  - `null`（路径途经的 record 为 `null` 时产生）：算术结果为 `null`，大小比较为 `false`，布尔运算按 `false`。整数除以零在运行时抛出，与抛异常的 `Derived` 一样报一次、保留上次的值。
- 编译：裸路径仍是原来的单元（双向绑定只接受裸路径）；其余表达式编译为一个 `Derived`，输入是表达式里出现的路径单元，计算时求值整棵树，`&& || ?:` 短路。
- 语句只在 `on:` 值中，从左到右执行，后面的语句读得到前面的赋值；一条抛异常则报告并停止其余。调用：动作字段，参数是一个表达式，类型须可赋给动作参数。赋值目标：声明类型为 `Value<T>` 的单元，即局部状态、ViewModel 的 `state(...)` 字段、`<for>` 行里的 `Value` 组件；`Cell<T>`（`source(...)` 字段）与 `Derived` 只读。值类型须可赋给目标，允许数值放宽（`Integer` → `Long`/`Float`/`Double` 等）。
- 局部状态 `state:名="初值表达式"`：类型取初值的静态类型；作用于该元素自己的指令与子树；名字不得与已可见的局部状态、`as` 行名、ViewModel 字段重名。初值在元素实例化时求值一次：文档加载时，`if` 块每次挂载、`each` 每行新建时；块卸载即丢弃。需要 ViewModel（与其它指令相同）。
- 属性值里的 `&`：按 HTML 规范，`&` 后不是字母或 `#` 时是字面字符，`a && b` 无需写成 `&amp;&amp;`。

### 4. 样式语言

#### 4.1 层叠顺序

| 顺序（低→高） | 来源与层 | 包含 |
|---|---|---|
| 1 | user-agent 来源 | `user-agent.css`（现有） |
| 2 | 作者来源·组件层 | 组件文件中 link 的样式表 |
| 3 | 作者来源·主题层 | 页面 link 的 `@ixchel/<名>.css` 库内样式表（现有） |
| 4 | 作者来源·不分层 | 页面模板中 link 的样式表、`<style>`、`style` 属性与 `bind:style:*`（现有顺序） |

- 层间按层的顺序决定，不比较优先级；同层内按优先级与书写顺序（现有 `StyleEngine.CASCADE_ORDER`）。
- 语义同 CSS Cascade 5 的 `@layer`：未分层的作者样式高于任何层；`!important` 声明的层顺序反转。库提供的样式表（UA、主题）不使用 `!important`。
- 层由样式表被 link 的位置决定，不开放 `@layer` 语法。

#### 4.2 主题

> TH1 起本节由 `ref/SD-ixchel-themes-20261104.md`（#034）取代：页面用 `<link rel="theme">` 选择主题，主题可带规则、`@keyframes` 与内置特效并 `@import` 基底，表面类改为 `.ix-*`，变量按角色重排。以下为 L3 的现状。

- 只含视觉：颜色、边框、文字色，以及 `:hover`、`:focus`、`:disabled` 等状态；不含 `width`、`height`、`margin`。
- 首批 `vanilla`（原版容器灰：`#C6C6C6` 底、`#555555` 边、`#404040` 字）与 `dark`（暗色浮层：`#101418E0` 底、`#FFFF55` 标题），来源为 sample 现有两族外观。
- 按 #001 放 classpath，以相对 core 类的路径读取（同 `UiDocument.UserAgent`），relocate 后可用；不随资源重载热更新。
- UA 样式表保持为不引入主题时的兜底外观。
- 构成（L3 实现，原 #018，现 #034）：每份主题 = 共用规则 `themes/theme.css`（`.panel`、`.title`、`button` 及 `:hover`/`:focus`、`.selected`、`:disabled`，值全部为 `var(--ix-*)`）加只在 `:root` 上设变量的配色表；同时链接多份时共用规则只进一次。
- `--ix-*` 变量是对页面与组件公开的接口，作者 CSS 用它们表达角色色：

| 变量 | 角色 | vanilla | dark |
|---|---|---|---|
| `--ix-surface` / `--ix-frame` | 面板底色 / 边框（完整 `border` 值） | `#C6C6C6` / `1px solid #555555` | `#101418E0` / `none` |
| `--ix-text` / `--ix-title` | 正文 / 标题 | `#404040` / `#404040` | `#FFFFFF` / `#FFFF55` |
| `--ix-text-muted` / `--ix-text-dim` | 次要文字 / 提示与空状态 | `#606060` / `#707070` | `#AAAAAA` / `#777777` |
| `--ix-well` / `--ix-hover` | 内凹区底色 / 行悬停叠加 | `#8B8B8B` / `#FFFFFF40` | `#00000060` / `#FFFFFF20` |
| `--ix-selected` / `--ix-selected-text` | 选中底色 / 字色 | `#E0E0E0` / `#202020` | 同左 |
| `--ix-button` / `--ix-button-hover` / `--ix-button-text` / `--ix-button-frame` | 按钮 | `#A0A0A0` / `#B8B8B8` / `#404040` / `1px solid #555555` | `#FFFFFF30` / `#FFFFFF60` / `#FFFFFF` / `none` |
| `--ix-button-disabled` / `--ix-button-disabled-text` | 禁用按钮 | `#A0A0A0` / `#707070` | `#FFFFFF10` / `#777777` |
| `--ix-accent` / `--ix-positive` / `--ix-negative` / `--ix-warning` | 强调 / 正向 / 错误 / 警告 | `#FFFF55` / `#3F7F3F` / `#AA2020` / `#C07020` | `#FFFF55` / `#55FF55` / `#FF5555` / `#FFAA00` |

#### 4.3 组件样式

- 组件的样式表与组件文件放在一起，由组件文件 link，进入组件层；库组件同样 link 自己旁边的样式表（#037）。组件文件中的 `<style>` 同样进组件层。
- 不做样式隔离：作者与主题可以选中组件内部元素。组件样式的选择器以组件名开头；组件约定由使用方添加的 class（如 `waypoint-row.selected`）属于组件的公开接口。

#### 4.4 复用手段

| 手段 | 现状 | 计划 |
|---|---|---|
| 一个元素带多个 class、选择器列表 | 支持 | — |
| 多个样式表 | 支持，经模板中的多个 `<link>` | — |
| 嵌套（CSS Nesting） | 报错（`CssParser`："At-rules and nested rules are reported as errors"） | 随组件实现；解析期展开，不改样式引擎 |
| `var()` 自定义属性 | 支持（L3）：带回退，自定义属性按普通层叠且总是继承，`bind:style:--x` 可写；替换后无效的属性取继承值或初值并报告一次 | — |
| `@import` | 报错（不支持任何 at-rule） | 只在主题文件开头开放：至多一个 `@import` 导入基底主题，另有 `@-ix-effect` 引入特效（TH1，#034、#039）；其它样式表仍由模板侧 `<link>` 组合 |
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
| 格式化与条件 | L1 前否，表达式只有路径与 `!` | 引擎能力 | §3.6 |
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

1. ~~库内样式表 href 的保留写法~~ 已定（L3）：`@ixchel/<名>.css`，只能由页面链接；首批 `vanilla.css`、`dark.css`。
2. ~~格式化：内置函数还是一律写 `Derived`。~~ 已定（L1）：不提供内置函数。sample 中只做格式化的 `Derived` 绝大多数是拼接与算术；需要函数的只有 `EnvView` 的血量取整与宽度上限，`MachineView` 等引用 Java 常量（容量）的也不能搬进模板。格式化函数等出现第二处真实需要再加。
3. ~~插槽的后备内容~~ 已定：支持，`<slot>` 的子元素即后备内容（上下文模型文档 §3.1）。
4. ~~热重载时局部状态是否保留。~~ 已定（L1）：不保留，重载后按初值重建。模板结构可变，没有可靠的身份去匹配旧状态；需要跨重载保留的状态放在 ViewModel（它跨重载保留）。
5. ~~组件实例的子 Scope~~ 已定：模板组件实例不需要子 Scope，带 Java 逻辑的组件每实例一个（上下文模型文档 §3.5）。
6. ~~宿主原生元素的登记入口~~ 已定（U3）：不与 §3.4 的 Java 组件合并，见 #030。
7. ~~代码命名~~ 已做（L1）：`BindAttribute` → `Directive`，`isBindingAttribute` → `isDirective`，`Element.bindings()` → `directives()`。
8. `null` 是否显式化（`?.`、`??`）。现行规则（§3.6）是隐式的：路径途经 `null` 时算术得 `null`、大小比较为 `false`、布尔运算按 `false`，可能掩盖本该报出的错误；尚未观察到实际问题，组件参数的默认值已消除"未传入即 `null`"。等出现第一处被吞掉的错误再定。
9. ~~tooltip 的写法~~ 已定（E2 前）：库元素 `<ix-tooltip>`，不用 `popover="hint"` + `interestfor` + anchor positioning，理由与语义见路线图 E2。
10. ~~链接~~ 已定（E1 前）：标准 `<a>`，库不解释 `href`，导航由 ViewModel 动作完成。
11. ~~i18n 写法~~ 已定（I 前）：元素级消息，`t`/`t:<属性>`，译文内 `%n$s` 插入参数子元素、`<名>…</名>` 复制 `mark` 子元素，`:lang()` 按语言区分外观；见 `ref/SD-ixchel-i18n-20261102.md`（#027）。
12. ~~V1 盒子外观的语言范围~~ 已定（V1 前）：伪元素只做装饰，`content` 只收 `""`/`none`，因为 CSS 字符串会绕开 i18n 与字面文本 lint；背景按标准做多层列表，避免为叠图多套元素；渐变只做 `linear-gradient`；`box-shadow` 含 `inset` 且可过渡；`border-image` 与 `image-rendering: pixelated` 同批，因 MC 九宫格多为像素风；`:not`/`:is`/`:where` 收完整选择器列表。逐项范围见路线图 V1。
13. ~~V2 尺寸与排版的语义~~ 已定（V2 前）：块容器用 taffy 标准块布局（外边距折叠、匿名块），不映射为 flex，符合 #003；`line-height: normal` 保持现行行高；vw/vh 以 GUI 视口为准；`Length` 改为"百分比 + 像素"，使 px 与 % 之间的过渡按标准插值；grid 接 taffy 的全部写法、不做 subgrid。逐项范围见路线图 V2。
14. ~~V3 动画的时间与事件~~ 已定（V3 前）：用墙钟，游戏暂停不冻结，视效不跟逻辑时间；只加 `on:animationend`，`animationiteration` 无使用方。补定（V3 中，#032）：进出动画不用 `@starting-style` 与块属性 `leave`，改为进出单元上的三个状态伪类，作者只写 CSS。逐项范围见路线图 V3。

### 7. 实现顺序

见 `ref/D-ixchel-roadmap-20261029.md`（阶段 D0、L1、L2、L3、E1、E2、I）。
