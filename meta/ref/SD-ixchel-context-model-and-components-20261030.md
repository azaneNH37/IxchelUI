---
tags: [survey, design]
date: 2026-10-30
origin: L2 组件设计讨论中，由组件闭包与命名问题收敛出模板的上下文模型，作为组件实现与后续语言特性的依据
related: [DECISIONS.md#001, DECISIONS.md#003, DECISIONS.md#015, DECISIONS.md#016, ref/SD-ixchel-template-and-style-language-20261029.md, ref/SD-ixchel-viewmodel-and-binding-20261026.md, ref/D-ixchel-roadmap-20261029.md]
---

# Ixchel 上下文模型、可分析性与组件系统

## Summary

模板中每个名字都有唯一、加载期确定的来源，模板只能触及 Java 显式暴露的东西；在此之上，四条不变式（封闭词法环境、可写性由类型决定、表达式而非代码、加载期一次展开）使读写目标、组件签名与错误全部可在加载期得出。组件是像原生元素一样带具名端口（参数、事件、插槽）的闭包：组件体只看得到端口与自己的局部状态，写权限由使用方在自己的文本中授予，根页面即端口为 ViewModel 的组件。模板组件不需要子 `Scope`。

## Detail

### 1. 上下文模型

#### 1.1 名字来源

| 位置 | 可见的名字 | 不可见 |
|---|---|---|
| 页面模板 | ViewModel 的非 `private` `Cell`/`Action` 字段；祖先元素与本元素的 `state:`；外层 `<for>` 的 `as` 行名；`$` 框架名 | — |
| 组件体 | 本组件的参数、事件；组件体内的 `state:`、`<for>` 行名；`$` 框架名 | 使用方的一切：ViewModel 字段、使用方的局部状态与行名 |
| 外壳元素上的指令、插槽内容 | 使用方的作用域（写在使用方文本里的都属于使用方） | 组件的参数与局部状态 |

- 外壳元素上的 `state:` 属于使用方：对该外壳元素的指令与插槽内容可见，对组件体不可见。
- 名字不遮蔽：新声明（局部状态、行名、参数、事件）与已可见的名字重名，加载期报错（局部状态的现有规则推广到全部来源）。
- 路径首段按上表解析，其后每段只能是 record 组件或 `List.size`；动作只能 `.pending`/`.error`（现有）。
- `$` 开头的名字只属于框架：字段、局部状态、行名、参数、事件都不得以 `$` 开头，加载期报错。

#### 1.2 可达性

模板只能触及 Java 显式暴露的东西：非 `private` 字段、record 结构、声明的 `Action`、类型为可写单元的状态。

- `private` 字段对模板不可见（L2 起名字表跳过 `private` 与 `static` 字段；字段值为 `null` 时加载期报 `field 'x' of Owner is null`）。
- `$` 命名空间只属于框架：值为 record 或基本类型，只读或按位置限定作用域，清单固定由框架给出；不放宿主对象（`Minecraft`、`LocalPlayer` 等）。原 `event` 已在 L2 改名 `$event`，以后的 i18n 参数同样放在 `$` 下。

#### 1.3 可写性

| 可写 | 只读 |
|---|---|
| 声明类型为 `Value<T>` 的：ViewModel 的 `state(...)` 字段、`<for>` 行里的 record 组件；局部状态；使用方以可写路径传入的组件参数；未传入、取默认值的组件参数 | 声明类型为 `Cell<T>`（`source(...)` 字段）或 `Derived<T>` 的；表达式结果；字面量或表达式传入的参数 |

"能不能写"是静态事实：由目标的声明类型或使用方传入的形式决定，加载期检查，不存在运行时才发现的写失败。为此 `source(...)` 返回只读的 `Cell<T>`，`Value<T>` 只由 `state(...)` 与 `state:` 产生（L2 拆分；此前两者都是 `Value`，`<for>` 行里 `Value` 组件的写入要到运行时才知道是不是 state）。

写能力随 `Value` 引用传递：使用方把含 `Value<T>` 组件的 record 以只读参数传入，组件体仍可写该组件，因为使用方在 Java 类型里已经给出了写权限。

### 2. 可分析性的四条不变式

| 不变式 | 保证 | 破坏它的特性举例 |
|---|---|---|
| 1. 封闭词法环境：名字来源唯一、加载期确定，组件只见端口 | 每次读写在加载期解析到确定目标；组件签名可由组件体推导 | 隐式继承外层上下文（WPF DataContext、QML 未限定访问外层 id）；运行时按名字查找 |
| 2. 可写性由类型决定，写权限只由使用方授予 | 写入要么落到使用方授权的状态上，要么加载期报错 | 任何参数都可赋值（QML 的赋值静默替换绑定） |
| 3. 表达式语言而非代码：路径、运算、调用动作、赋值 | 全部读写可被完整列举；无反射、无动态属性访问 | 表达式中任意方法调用；内嵌 JS（#015 已拒） |
| 4. 加载期一次展开，运行时不换父节点 | 树结构固定：词法可见即子树包含，可写引用不会悬空（§3.5） | 运行时把元素移到别处的父节点下；按运行时数据选择组件 |

新增语言特性（`provide:`、新的 `$` 名字、Java 组件等）须逐条检查这四条。

QML 的演进可作旁证：Qt 6 文档承认组件引用外层 id 时 "the engine and the QML tooling cannot generally know in advance what type, if any, such IDs will resolve to at run time"，为此加了 `pragma ComponentBehavior: Bound` 把组件限制在原始上下文中（[Structure of a QML Document](https://doc.qt.io/qt-6/qtqml-documents-structure.html)）；同期推 `required property` 取代上下文属性（凭记忆）。即事后向不变式 1 收紧。

### 3. 组件系统

#### 3.1 端口

组件像原生元素一样对外只有具名端口：

| 端口 | 声明 | 组件体内 | 对照根页面 |
|---|---|---|---|
| 参数 | `params="open message danger=false"`，`=` 后为默认值表达式 | 裸名读；可写时可赋值 | `Cell` 字段（读）、`state(...)` 字段（读写） |
| 事件 | `events="confirm"` | 当动作调用：无载荷时不带括号（现有规则），有载荷时 `confirm(表达式)` | `Action` 字段 |
| 插槽 | `<slot>`、`<slot name>` | 放置使用方内容；`<slot>` 的子元素为后备内容 | — |
| 局部状态 | 组件体内的 `state:` | 私有，不是端口 | 页面模板的 `state:` |

```html
<template name="ui-confirm" params="open message danger=false" events="confirm">
  <dialog bind:open="open">
    <span class="text" bind:text="message"></span>
    <div class="actions">
      <button on:click="open = false">Cancel</button>
      <button bind:class:danger="danger" on:click="confirm; open = false">OK</button>
    </div>
  </dialog>
</template>
```

- 参数名、事件名不得与 `TagRegistry.GLOBAL_ATTRIBUTES`（`class`、`id`、`style`、`tabindex`、`disabled`、`title`）或 `text`、`scrolltop`、`slot` 重名，加载期报错（`slot` 是外壳元素直接子元素指定插槽的属性）。这些是任何元素上都有含义的目标；`value`、`checked`、`open` 只在表单控件、复选框、`dialog` 上是双向目标，外壳元素不是这些元素，可作参数名。
- 事件名不得与 DOM 事件名（`click`、`keydown` 等 `on:` 支持的事件）重名，否则使用方无法再监听外壳元素上的同名 DOM 事件。
- 默认值表达式在组件环境中求值，只能引用写在它前面的参数与 `$` 名字，实例化时求值一次。
- 事件：组件体中对同一事件的全部调用须一致，要么都不带载荷，要么载荷静态类型相同，否则加载期报错；使用方未监听时调用为空操作；组件事件不冒泡；组件体中不能读事件的 `.pending`/`.error`。
- 插槽：
  - `<slot>` 不进元素树，与控制块相同；同名插槽在一个组件中只能出现一次。
  - 后备内容静态判定：使用方给了该插槽的内容（包括当前未挂载的 `<if>`），就不显示后备内容。
  - 使用方以 `slot="名"` 把内容放进具名插槽，可写在外壳元素的直接子元素或直接子控制块上；控制块内容中的元素不得写 `slot=`，块内容整体进块所在的插槽。
  - 组件体内 `<if>` 包住 `<slot>` 时，插槽内容随之挂载与卸载，其中的局部状态随之重建。

#### 3.2 使用处

| 使用方写法 | 参数在组件体内 |
|---|---|
| `p="文本"` | 只读字符串常量；布尔、数值用 `bind:` |
| `bind:p="表达式"` | 只读；组件体对它赋值在本次实例化处报错 |
| `bind:p="可写路径"` | 可写，写入落到使用方的状态（与 `<input bind:value>` 同一规则，无需 `bindable` 之类的声明） |
| 不写，参数有默认值 | 本实例的局部状态，初值为默认值，可写 |
| 不写，参数无默认值 | 加载期报错（必填） |

- `on:e="语句"`：`e` 为声明的事件时接收组件事件，载荷在语句中为 `$event`，类型取组件体中调用处的参数类型；不允许修饰符。`e` 未声明时是外壳元素上的 DOM 监听器。
- 外壳元素上的 `bind:p`：`p` 为声明的参数时传参（传 `Ref` 本身，是否可写看表达式是否为可写裸路径；不可写时只读，不报错）；否则按外壳元素自身的绑定目标解释（`class:*`、`style:*`、`disabled`、`scrolltop`、全局属性）；`bind:text` 在外壳元素上报错，其内容是组件体。
- 声明的参数名、事件名归组件；其它 HTML 属性与指令作用于外壳元素。外壳元素要重复或按条件出现，放进 `<for>`/`<if>` 块（语言文档 §3.5）。
- 组件体不能给自己的外壳元素加 class 或指令（无 `:host` 等价物），按参数变化的外观放在组件体内的元素上（§4）。
- 由"未传入的参数退化为局部状态"得到升级路径：组件内部状态要改由外部控制时，只把名字从 `state:` 挪到 `params` 并给默认值，组件体与已有使用方都不变。对照 Svelte 5 的 `$bindable(fallback)`（凭记忆）；它消除了 React 受控/非受控两套写法。

```html
<div state:confirming="false">
  <button on:click="confirming = true">Remove</button>
  <ui-confirm bind:open="confirming" message="Delete waypoint?" bind:danger="true" on:confirm="remove"></ui-confirm>
</div>
<for each="entries" as="r" key="r.id">
  <config-entry bind:entry="r" on:reset="reset(r)"></config-entry>
</for>
```

#### 3.3 编译划分

- 名字解析经一张加载期建好的名字表：每个名字对应一个值 `Ref` 或一个可调用项（`Action` 或事件端口）。页面以 ViewModel 字段建表（跳过 `private`），组件体以参数与事件建表，其余编译逻辑相同。
- 外壳元素上的指令与插槽内容在使用方环境编译。
- 组件体的 `<for>` 内不得出现 `<slot>`：不做作用域插槽，插槽内容看不到组件的行名。sample 中约 30 个 `each` 行体，页面状态只出现在行根元素上（选中 class、`select(r)`），行体内部读页面状态 0 处；行内动作只有 config 的 `editList(r)`、`reset(r)`、`removeItem(i)`（`config/panel.html:39,41,61`），均可经事件端口表达。
- 组件名须带连字符，经 `<link rel="component">` 在文件内引入（不做全局注册，#015）。`<link rel="component">` 须写在所有元素之前：解析器读到它时加载组件文件并登记组件名，之后的标签才能识别。组件身份按解析后的资源路径认定：同一文件经两条路径引入只加载一次，其样式表只进一次组件层；不同文件定义同名组件为加载错误；引用成环为加载错误（加载期检查要展开每个块与组件，递归组件会无限展开）。前缀规则见语言文档 §3.2。
- 没有 ViewModel 的文档以一个空 ViewModel 为根，组件参数与局部状态照常可用。

#### 3.4 类型

- 不写类型注解。每次实例化按使用方传入的真实类型检查组件体。组件体中的错误定位到组件文件，消息末尾附使用处（`… (in <ui-confirm> at file:line:col)`），`Diagnostic` 结构不变。
- 只被引入、从未实例化的组件只做解析与结构检查，组件体中的名字与类型不检查。
- 以后若需要更强的约束，升级路径是 HTML 中可选的结构声明，不是 Java 侧登记。

#### 3.5 生命周期与 `Scope`

- 模板组件实例只含绑定、DOM 监听器与局部状态，与 `<for>` 行相同（绑定文档 §5：行与 `if` 块没有子 `Scope`）；动作与订阅仍归 ViewModel。因此**模板组件不需要子 `Scope`**。
- 实例随 `<if>`/`<for>` 挂载创建、卸载丢弃；`<for>` 中按 `key` 保持身份。局部状态与参数默认值在实例化时求值一次；以参数为初值的局部状态（`state:draft="text"`）是快照，参数之后的变化不同步，需要重置时由使用方换 `key`。
- 引用不悬空：能传给组件的可写路径必须词法可见，词法可见意味着外壳元素在声明该状态的元素子树内（不变式 4），状态卸载时组件一同卸载。对比 C++ 按引用捕获的 lambda 可被带出作用域。
- 带 Java 逻辑的组件（后续，语言文档 §3.4）每实例一个子 ViewModel 与其 `Scope`；须与模板组件使用处同构：子 ViewModel 以同名字段接收参数，使用方无法区分实现方式（与 #015 "按领域而非实现划分前缀" 一致）。

#### 3.6 样式

按 #015 不做样式隔离，层叠见语言文档 §4。上下文模型只管名字与数据；页面状态影响组件外观的通道是 CSS 上下文：外壳元素上的 class、`bind:style:*`、继承属性，以及 L3 的 `var()`（如 `bind:style:--d` 在外壳元素上，组件 CSS 用 `var(--d)` 计算）。`var()` 宜与 L2 同时或稍后落地。

### 4. 待定与暂不做

| 项 | 状态 |
|---|---|
| `<for>`/`<if>` 块语法取代属性形式 `each`/`if` | 已定，随 L2 做，语法见语言文档 §3.5 |
| `<for>` 的空列表分支（`<empty>`，对照 Django `{% empty %}`、Angular `@empty`）、`<if>` 的 `<else>` | 待定。空列表：sample 有 5 处 `if="rows.size == 0"` 紧挨同一列表的 `each`（character、config、loot、machine、waypoints）；`<else>`：config 条目控件是 6 个按类型分支的 `if`（`config/panel.html:24-37`，互斥为【推断】） |
| `provide:`/`uses` 共享上下文 | 暂缓，触发条件为第一个复合组件（tabs/tab）。若做：使用方显式 `uses`，加载期静态解析到最近的显式提供者（祖先 `provide:`、外层组件内的 `provide:`、Java 给的文档根），只读，可带默认值 |
| 可写参数被组件写入时自动派发 `on:<名>change`（QML 每属性自动生成 `<名>Changed`） | 可选，首版不做 |
| 行内嵌动作（`entry.reset`） | 不做：`derive` 重建行时动作状态丢失，且破坏 record 相等截断 |
| `null` 显式化（`?.`/`??`） | 另议；有默认值的参数已避免"未传入即 `null`" |
| 可选的结构声明 | 待第一次真实需要 |
| 组件签名推导（对每个参数用到的路径、运算与写入，行多态式），用于错误信息、文档与资源包覆盖前后的签名差异 | 暂不做，触发条件为 Doc 阶段生成组件参考或资源包覆盖工具 |
| 按（组件，参数类型）缓存编译结果 | 暂不做：现有编译与实例化一体，每个块实例都重新解析名字，没有可缓存的编译结果；触发条件为 `FrameTimer` 显示实例化超预算 |
| 组件给自己的外壳元素加指令（`:host` 等价物） | 暂不做，触发条件为第一处组件需要按参数改变外壳元素的 class 或布局；候选：`<template>` 上的指令作用于外壳元素，在组件环境求值，与使用方写同一目标时报错 |

### 5. 对照

端口模型是共识；少见的是在标记层、不写脚本定义组件，这要求模板有可静态检查的表达式语言。

| 方案 | 输入 | 双向 | 输出 | 插槽 | 端口声明于 |
|---|---|---|---|---|---|
| UMG | `Instance Editable`/`Expose on Spawn` 变量（凭记忆） | UMG Viewmodel 绑定模式 `One Time to Widget`/`One Way to Widget`/`One Way to Viewmodel`/`Two Way`（[文档](https://dev.epicgames.com/documentation/en-us/unreal-engine/umg-viewmodel-for-unreal-engine)） | Event Dispatcher（凭记忆） | Named Slot | 蓝图/C++ |
| Unity UI Toolkit | `[UxmlAttribute]` | 运行时绑定 `BindingMode.TwoWay`（凭记忆） | C# 事件 | 单个 `content-container` | C# |
| Godot | `@export` | 无 | signal | 无 | GDScript（凭记忆） |
| LDLib2 | `bindDataSource(IDataProvider)` | 同一元素数据源 + 观察者 | `bindObserver(IObserver)` | XML `<template>` 无参数无插槽（`UITemplateElement` 清空子元素） | Java |
| AUI | `DialogWindow.Options` 等构造参数 | 无 | 回调 | `content().append` | Java API；无组件机制 |
| QML | `property`、`required property` | `property alias` 只指向内部元素属性 | `signal`、自动 `<名>Changed` | default property | QML 文件本身 |

QML 最接近，也提供了主要教训：父级 `open: page.confirming` 在组件内 `open = false` 后被静默替换为常量（[Raymii](https://raymii.org/s/articles/Qt_QML_Property_bindings_break_after_javascript_assignment.html)）。原因是属性槽位可装绑定也可装值、JS 命令式赋值直接替换（缺不变式 2、3）。本设计中写入只会落到使用方授权的状态或加载期报错。

其它依据：Jinja/Django 的 `include`/`with only` 上下文规则、Svelte 5 snippets、Solid、Elm、Scala `using`/Kotlin context parameters（`provide:`/`uses` 的参照）、WPF DataTemplate（隐式上下文的反例）、OCaml/Elm 行类型（签名推导）。均凭记忆，未逐一核对。
