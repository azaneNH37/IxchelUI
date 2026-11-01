---
tags: [survey, design]
date: 2026-10-26
origin: 讨论 ViewModel 原语如何与 UI 交互、如何实现、性能是否可接受，并把 Kumiko 调研中与数据层相关的结论（TACZ 场景、原语保证、必须保留的差异、验证矩阵）迁入本仓库，使数据层设计不再需要查阅 Kumiko。
related: [DECISIONS.md#003, DECISIONS.md#004, ref/SD-ixchel-ui-positioning-and-adapter-design-20261025.md, ref/D-ixchel-module-breakdown-20261026.md, ref/SDG-ixchel-container-slot-integration-20261026.md, ref/SD-ixchel-context-model-and-components-20261030.md]
---

# Ixchel UI: ViewModel and Data Binding

## Summary

数据层把"观察什么、展示什么、操作什么"做成默认路径：模板只引用名字，ViewModel 用 `source` / `state` / `derive` / `action` 声明来源、本地状态、派生与动作，`Scope` 管寿命。实现是显式依赖的"推脏、拉值"图，只在客户端线程、帧开始时刷新；一次性原型验证了无毛刺、相等截断、惰性、回执跨线程与关闭后丢弃，绑定层每帧为微秒级。

模板语法中 ✅ 为已定，🟡 为提议待定。§1、§2、§7 迁自 Kumiko 调研，是验收依据；源码观察与设计判断已分开标注。

## Detail

### 1. 需求来源：TACZ 四个场景（迁自 Kumiko）

证据范围：TACZ 上游 `https://github.com/MCModderAnchor/TACZ` 提交 `b43eb84c38e9768d8e73c8b14f0b845669704b38`（Forge 1.20.1）的源码观察；第一消费方 TACZ-RESPAWN 以此为基线。第四个场景是扩展需求，不是已有功能。未做移植、多人实验或性能测量。

共同链路：

```text
游戏数据 + 本地展示状态 → 派生数据 → 视图 → 用户事件 ─┬→ 修改本地状态
                                                    └→ 执行业务操作 → 游戏数据变化
```

| 场景 | 源码观察 | 对数据层的要求 |
|---|---|---|
| 工作台（`GunSmithTableScreen`） | BE 只存工作台资源 ID；`GunSmithTableMenu` 不注册 Slot，只提供打开上下文、配方过滤和合成入口；配方、背包、资源已有客户端来源；搜索、分页、选中项是本地状态；合成请求在服务端校验后同步背包并通知界面刷新 | 选择、材料、预览的联动由派生关系维护；不为此复制一份配方或背包 |
| Z 键改装（`GunRefitScreen`） | 普通 `Screen`，配件"槽"是显示物品的按钮而非 Slot；安装/拆卸走业务命令，服务端重读真实物品；UI 选择还驱动第一人称模型的改装视角；镭射颜色滑动时临时改客户端物品 NBT，关闭时提交（`HSVSliderGroup`）；刷新消息还触发客户端配件属性缓存更新 | 本地状态有 UI 树之外的消费者；草稿/预览与权威数据分离；响应式重画不能替代业务缓存失效 |
| 弹药 HUD（`GunHudOverlay`） | 读取当前枪械、背包、客户端资源，无专属同步会话；展示弹量、膛内子弹、备弹、弹药箱/虚拟备弹、容量、图标等派生值；绘制函数兼做定期缓存计算，部分模式下还修改客户端枪械数据 | 更新与绘制分离；多个组件不重复计算；小 HUD 不承担网络或会话成本 |
| 背包式改装（扩展） | 在改装基础上增加背包栏、真实配件槽与物品转移 | 必须接入 vanilla Menu/Slot 的携带物、拖拽、快捷移动与预测；编辑目标需明确移动/锁定/失效策略；新增容器能力时只新增槽位与转移声明，不改写原有显示的数据关系 |

### 2. 原语、默认保证与边界（迁自 Kumiko 并映射到 Ixchel）

原语不是给胶水代码起名，而是框架对某种关系承担保证：若使用者仍需在各处补刷新、失效检查和清理，原语就没有兑现。

| Kumiko 候选原语 | Ixchel 落点 | 保证 | 明确不负责 |
|---|---|---|---|
| 可观察值、本地状态、派生值 | `source` / `state` / `derive` | 依赖失效、缓存、多消费者共享、绑定更新 | 自动发现任意 Java 对象的内部修改（来源须声明快照与比较，§5） |
| 目标及访问策略 | 由 mc 层 `ClientSources` 提供的来源（🟡 见下） | 显示目标与操作目标一致、有效性可观察、按声明策略失效 | 发明物品身份、猜测授权策略 |
| 动作 | `action` | 参数、结果与执行边界明确，结果不依附具体 Screen | 决定业务合法性；把回执当新状态 |
| 物品位置与转移 | `UiContainerScreen` 的 Slot 通道（M5，见 `ref/SDG-ixchel-container-slot-integration-20261026.md`） | 声明后接入 Menu/Slot 与 vanilla 事务 | 替代配件规则、重写 vanilla 事务 |
| 作用域 | `Scope` | 绑定与本地资源有所有者，释放后不再更新失效视图 | 强制所有共享数据随一个视图关闭 |
| 显示宿主 | `UiView` / `UiScreen` / `UiHud` / `UiContainerScreen` | 渲染、尺寸、输入、挂载一致，数据表达不随宿主变化 | 抹平 HUD 无输入与容器交互的差异 |

统一时**不能删除**的差异：

- 客户端可操作提示与服务端权威校验并存。
- 命令回执与实际状态到达是两件事；收到成功不保证新状态已可读。
- 不同"背包统计"的业务含义不同（配方 Ingredient、弹药箱、配件兼容），不能混成一个算法。
- 普通值赋值与物品事务语义不同。
- HUD 跟随当前手持物，编辑界面锁定操作目标；背包索引不是稳定物品身份，`ItemStack.matches()` 也不等于实例身份。
- 安装配件、退弹、刷新业务缓存等领域规则留在业务代码。

由此得出的设计约束：

- **目标策略在来源上声明**（🟡）：`ClientSources.mainHand()` 跟随；编辑界面用锁定型来源，附带 `valid` 值，目标移动或消失时变为 false，由模板决定显示。
- **草稿**用 `state` + `derive` 预览 + 提交 `action` 组合，不建通用编辑事务框架。
- **本地状态可被 UI 树外读取**（🟡）：`Value.get()` 与 `subscribe(Runnable) → AutoCloseable` 对宿主代码公开，订阅归 `Scope` 所有。
- **业务缓存失效由业务代码负责**；数据层只保证"来源变了 → 视图变"。

### 3. 模板语法

```html
<span class="title" bind:text="gunName"></span>                       <!-- ✅ 值 → 文本 -->
<div class="ammo" bind:class:empty="isEmpty">                         <!-- 🟡 布尔 → 类名切换 -->
  <span bind:text="loaded"></span>/<span bind:text="reserve"></span>
</div>
<div class="slot" each="attachments" as="a" key="a.type"
     bind:class:selected="a.selected" on:click="select(a)">           <!-- 🟡 列表渲染；动作带一个路径参数 -->
  <item bind:item="a.stack"></item>
</div>
<div class="detail" if="hasSelection">…</div>                          <!-- 🟡 条件挂载 -->
<button t="tacz.gui.reload" on:click="reload" bind:disabled="!canReload"
        bind:class:busy="reload.pending"></button>                    <!-- ✅ t = 语言键；✅ on:/bind:/! -->
```

```java
final class GunModView extends ViewModel {
    final Cell<ItemStack>    gun          = source(ClientSources.mainHand());
    final Cell<Inventory>    inv          = source(ClientSources.inventory());
    final Value<String>      selectedType = state(null);
    final Derived<Integer>   loaded       = derive(gun, GunData::loadedAmmo);
    final Derived<Integer>   reserve      = derive(gun, inv, Ammo::reserve);
    final Derived<Boolean>   isEmpty      = derive(loaded, n -> n == 0);
    final Derived<Boolean>   canReload    = derive(loaded, reserve, gun, (l, r, g) -> r > 0 && l < GunData.capacity(g));
    final Derived<List<SlotRow>> attachments = derive(gun, selectedType, SlotRow::listOf);
    final Derived<Boolean>   hasSelection = derive(selectedType, Objects::nonNull);
    final Action<Void>       reload       = action(() -> Net.send(new ReloadIntent()));  // 返回 CompletableFuture
    final Action<SlotRow>    select       = action(row -> selectedType.set(row.type()));
    record SlotRow(String type, Component typeName, ItemStack stack, boolean selected) { … }
}
// 宿主：public GunModScreen() { super(MODID, "ui/gun_mod.html", new GunModView()); }
```

| 项 | 规则 | 状态 |
|---|---|---|
| 表达式 | L1 起为语言文档 §3.6 的表达式（字面量、路径、`List.size`、算术、比较、`&& \|\|`、`?:`），整条编译为一个 `Derived`；需要函数或 Java 常量的逻辑仍写 `Derived` | ✅ |
| 绑定目标 | `text`、任意属性、`class:<名字>`（M2）；`if`、`each`（M3，`each` 必须带 `as` 与 `key`，`key` 是从行名出发的路径；`each` 与 `if` 不能在同一元素上；L2 改为 `<for>`/`<if>` 控制块，见语言文档 §3.5）；`style:<属性>` 接受 CSS 语法的 String，长度类属性也接受数值（按 `px`）；双向：`value`（输入框、`<select>`）、`checked`、`open`（`<dialog>`）、`scrolltop`，目标须是 state `Value` 字段或 `each` 行里类型为 `Value<T>` 的组件 | ✅ |
| 动作与语句 | `on:x` 的值是语句：`act`、`act(表达式)`、赋值 `目标 = 表达式`，以 `;` 分隔，从左到右执行；加载期按 `Action<A>` 的 `A` 与赋值目标类型检查。事件名为 `click`、`mousedown`、`mouseup`、`mousemove`、`wheel`、`keydown`、`keyup`；语句可读 `$event.x`/`y`/`movementX`/`movementY`/`delta`。键事件可过滤：`on:keydown.<键名>[.shift\|.ctrl\|.alt]`（键名见 `event.Keys`），只在匹配时运行并消费；顶层元素上的键处理器注册到文档根，不需焦点即触发（`<for>`/`<if>` 块内的顶层元素除外）。局部状态 `state:名="初值"` 见语言文档 §3.6 | ✅ |
| i18n | `t="语言键"`、`t:<属性>="语言键"`，译文插入参数子元素、复制 `mark` 子元素；依赖数据的文本由 Java 返回 `Component`；字面文本警告只在 kit、默认关闭（`ref/SD-ixchel-i18n-20261102.md`，#027） | ✅ |
| 值转文本 | `String.valueOf`；格式化（如补零）写进 `Derived` 或模板表达式。`Component` 由 mc 层 `TextConverter` 拆成带颜色与格式的样式片段，与周围文字进入同一套 core 行内排版（#019）；CSS 未设的部分继承，混淆只能来自 `Component` | ✅ |
| 交互状态 | `:hover` / `:active` / `:focus` 由 event 包写状态位，`:checked` / `:open` / `:disabled` 由控件与顶层写，均不经 ViewModel | ✅ |
| 校验 | 未声明的名字、record 上不存在的组件、不支持的 CSS 属性 → 开发期报错带文件:行:列 | ✅ |

### 4. 运行语义

| 环节 | 规则 | 原因 |
|---|---|---|
| 名字解析 | 加载期反射读取 `Value` / `Derived` / `Action` 字段，字段名即绑定名；路径按字段泛型解析到 record 组件并缓存为 `MethodHandle`。模板可见哪些名字、哪些可写，见 `ref/SD-ixchel-context-model-and-components-20261030.md` §1 | 开发期报错；宿主代码不经 MC reobf，名字稳定；同一信息导出为编辑器用的 ViewModel 结构描述 |
| 失效与取值 | 变化只向下游传"可能脏"，帧开始时对脏绑定取值 | 一帧多次变化只算一次；`if` 隐藏的子树不取值 |
| 写 DOM | 与上次值相同则跳过 | 避免无效的样式/布局失效 |
| `each` | 按 `key` 比对：新增挂载、删除卸载、保留者复用节点并只更新行值 | hover、焦点、过渡状态不丢 |
| 动作 | `Action` 只发出；`pending` 表示进行中；新状态只能经来源回来 | 不做乐观更新，客户端显示不与服务端状态分叉 |
| 生命周期 | `removed()` → `view.close()` → `Scope` 关闭订阅、丢弃未完成回执 | 关闭后回执不会写入已销毁的 DOM |

一帧的顺序：处理收件箱（跨线程投递的回执与推送）→ 轮询来源 → 刷新脏绑定 → DOM 变脏后样式、布局、绘制。输入事件触发的 `state.set` 在下一帧生效。

### 5. 实现

总体：显式依赖、版本号、推脏拉值。依赖在构造时写死，只能指向已构造的节点，因此**从构造上不可能成环**，也不需要线程局部的依赖追踪上下文；代价是条件分支里没用到的依赖变化也会触发一次重算。

M2 已实现的最小集（`core/.../binding`）：节点基类命名为 `Cell`（`dom.Node` 已占用该名）；只有轮询型 `Source.polled`，没有推送型；`ViewModel` 的 `source`/`state`/`derive`（1~3 参数）；绑定目标为 `text`、`class:<名字>`、普通属性，表达式为字段名加可选 `!`，路径报"尚未支持"。"推脏"简化为整个 ViewModel 一个脏标记：没有任何来源或 state 变化时，这一帧不读取任何节点；有变化时遍历全部绑定，`Derived` 按上游 version 判断是否需要重算。`action`、`Scope`、`subscribe`、`each`/`if` 留到 M3。`Component` 由 mc 层 `McResources` 用 `getString()` 拍平。

M3 补齐（`core/.../binding`）：`action(Runnable|Consumer)` 为同步动作；`task(Supplier|Function)` 返回 `CompletableFuture`，有 `pending`/`error`（同名无法区分同参数个数的 lambda，故异步另起名 `task`）。`Scope` 只有 ViewModel 一级：收件箱（任意线程 `post`，帧开始执行）与所拥有的订阅，`close()` 后丢弃；`subscribe(cell, listener)` 放在 ViewModel 上（订阅归它的 `Scope`），在帧开始、单元格版本变化时回调。`each` 行与 `if` 块没有子 `Scope`：动作与订阅都属于 ViewModel，行里只有绑定与 DOM 监听器，随节点一起丢弃。路径经 record 组件逐级取值，每级一个 `Derived`（相等截断）；`each` 行值存在不通知模型的行单元格里。块内容在加载期用一份不挂载的副本校验，未挂载时出错也在加载期报告。

设计期草图（实现中基类为 `Cell`、无 `markDirty`，见上两段）：

```java
abstract sealed class Node<T> permits Value, Derived {
    final List<Node<?>> dependents; final List<Binding<?>> observers;
    long version;                                  // 值真正变化时 +1
}
final class Value<T> extends Node<T> {             // 只表示 state；L2 起 source 产出只读的 Cell
    void set(T v) { if (same.test(value, v)) return; value = v; version++; scope.markDirty(this); }
}
final class Derived<T> extends Node<T> {
    T get() {
        if (clean) return value;
        // 先刷新上游，再比较各上游 version 与上次记录；有变化才重算
        // 重算结果与旧值相等则 version 不变（相等截断），下游视为未变
    }
}
```

| 原语 | 要点 |
|---|---|
| `source` | 轮询型 `Source.polled(Supplier, snapshot, same)` 与推送型 `Source.pushed(Supplier, subscribe)`；都只接 JDK 类型（#004）。每帧轮询一次，先 `same(快照, live)` 比较，不同才 `snapshot` 复制。推送回调可在任意线程，只做 `scope.post(...)` |
| `state` | 对外公开 `set` 的 `Value`；仅客户端线程，开发期断言 |
| `derive` | 1~3 个参数（`Function` / `BiFunction` / 自定义 `Fn3`），更多先合成 record；惰性：无人读取则不计算；函数抛异常时开发期显示叠加层，发布版保留旧值并记一次日志 |
| `action` | 处理器返回 `CompletableFuture` 时置 `pending`，完成后经 `scope.post` 回客户端线程清 `pending`、写 `error`；进行中再次触发默认丢弃；同步处理器没有 `pending` |
| `Scope` | 拥有节点、轮询来源、订阅句柄、收件箱、脏队列；只有 ViewModel 一级，`<for>` 行、`<if>` 块与模板组件实例都没有子 `Scope`（上下文模型文档 §3.5）。不变式：**绑定挂上时立即刷新一次**（未被读取过的节点可能停在"可能脏"而不再向下传播） |

可变对象的比较语义属于 mc 层：`ItemStack` 不覆写 `equals` 且会原地修改，因此来源必须提供快照与内容比较。

```java
static Source<ItemStack> mainHand() {
    return Source.polled(() -> player().getMainHandItem(), ItemStack::copy,
            (a, b) -> ItemStack.isSameItemSameTags(a, b) && a.getCount() == b.getCount());
}   // 1.20.5+ 改为 isSameItemSameComponents（推断，未核对源码）
```

record 中包含 `ItemStack` 时 record 的 `equals` 退化为引用比较，相等截断失效、下游多刷新；结果仍正确，高频场景用 `.withEquals(...)` 覆盖。

### 6. 验证与性能

一次性原型（本地脚本，未入库）按上述算法实现，以下检查全部通过：

| 检查 | 结果 |
|---|---|
| 初次刷新每个绑定只写一次 DOM | 通过 |
| 无变化时无 DOM 写入 | 通过 |
| 可变对象原地修改经快照比较被检测；相等截断阻止下游 | 通过 |
| 菱形依赖：两侧都被截断时不重算；变化时只重算一次且读到一致值 | 通过 |
| 进行中再次触发动作被丢弃；`pending` 反映到类名 | 通过 |
| 回执在其它线程完成，下一帧在客户端线程清 `pending`；弹药数不因回执改变 | 通过 |
| `Scope` 关闭后迟到的回执被丢弃 | 通过 |
| 无人读取的 `Derived` 计算次数为 0 | 通过 |

未覆盖：路径绑定、`each`、`if` 已在 M3 由 `UiDocumentBindingTest` 覆盖；子 `Scope` 未实现（见 §5 M3 补齐）。

性能（同一原型，JIT 预热后取中位数）：50 个轮询来源、200 个派生加绑定，无变化一帧 0.5 µs，一个来源变化 2.9 µs。绑定层不是瓶颈；整帧预算见 `ref/D-ixchel-module-breakdown-20261026.md` §7。

### 7. 验收矩阵（迁自 Kumiko）

| 价值主张 | 场景 | 验证要求 |
|---|---|---|
| 简单场景不被复杂化 | 弹药 HUD | 无网络或会话；接入成本不膨胀；更新与绘制分离 |
| 派生关系替代手动刷新 | 工作台 | 搜索、选择变化后列表/材料/预览正确联动，不丢服务端校验 |
| 目标、动作、预览关系明确 | Z 键改装 | 目标失效、命令失败、编辑结束有确定行为；业务更新不依赖具体 Screen |
| 能力增长不改写普通数据表达 | 改装 → 背包式改装 | 保留原信息绑定与非 Slot 动作，主要新增槽位、转移与目标策略 |
| 共享数据与独立寿命 | HUD 与改装界面同时存在 | 关闭其一不破坏另一个，不重复维护可共享统计，不混淆目标语义 |

公平基线是同等完整、允许正常抽取业务服务与组件的手写原版实现，功能、失败处理与交互语义一致。除代码量外检查：

- 新增字段或消费者需要改哪些位置？
- 更换数据源是否改动视图？
- 关闭、切换目标、失败时还需要多少手工防护？
- 能否解释数据来源、依赖与目标失效原因？
- 是否只是把原有胶水搬进一个更大的 ViewModel？
- 更新频率、重复计算、分配是否出现不可接受代价？必须测量。

失败信号：使用者换了术语但仍手动维护刷新、失效与生命周期；或 UI 变短但数据适配与调试更复杂。

### 8. 待定

- ~~§3 中剩余 🟡 项：i18n 写法（`t` 属性）。~~ 已定，见 `ref/SD-ixchel-i18n-20261102.md`（#027）。
- 锁定型目标来源的 API 形态与 `valid` 语义（M5 前定）。
- 动作进行中再次触发：丢弃（默认）、排队或并发。
- `derive` 参数上限：3（默认）或可变参数。

### 9. 参考

- Kumiko 仓库 `meta/ref/S-kumiko-product-value-and-primitives-20260922.md`（§1、§2、§7 的来源，已迁入，无需再查）。
- LDLib2 的数据层校准（Kumiko 调研的源码观察）：LDLib2 不只有轮询绑定，`IDataProvider` 有订阅，`TrackData` 是可写值加通知；已有 XML 模板、编辑器与实时预览。因此"外部化模板"与"有绑定"不是 Ixchel 的差异点，差异在部署性质与标准 CSS。
- Angular Signals（依赖、失效与缓存作为一等机制）：https://angular.dev/guide/signals
- React 的最小状态与派生数据：https://react.dev/learn/thinking-in-react
