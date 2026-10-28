---
tags: [survey, design, gotcha]
date: 2026-10-26
origin: 讨论 `UiContainerScreen` 如何接入 vanilla Slot 时，逐项核对 1.20.1（vanilla 与 Forge 47.3.19）、NeoForge 1.21.1 源码，对照 AUI、LDLib2 的 mixin 做法，并读 JEI/EMI/REI/Mouse Tweaks 源码确认第三方对槽位坐标的依赖；做了 AT 编译、final 字段反射写入的最小实验。槽位是 UI 库与其它 mod 冲突最集中的位置，故单独成文。
related: [DECISIONS.md#001, DECISIONS.md#002, DECISIONS.md#008, DECISIONS.md#009, ref/SD-ixchel-ui-positioning-and-adapter-design-20261025.md, ref/D-ixchel-module-breakdown-20261026.md, ref/SD-ixchel-viewmodel-and-binding-20261026.md]
---

# Ixchel UI: Container Slot Integration

## Summary

布局结果写回 `Slot.x/y`，vanilla 的命中、点击、拖拽与第三方 mod 都以它为准；`Slot.x/y` 是 `final`，1.20.1/1.21.1 用反射写入（#008，宿主零配置，JDK 26+ 收紧后改 AT）。命中判定、悬停高亮、覆盖层分层都能通过覆写 `UiContainerScreen` 的 protected/public 成员实现，不需要 mixin；1.20.1 自绘槽位物品需借零面积裁剪盖掉 vanilla 绘制，有可见代价，因此默认用 vanilla 绘制、增强绘制由宿主经 API 开启（#009）。

## Detail

### 1. vanilla 可见性与调用顺序

`AbstractContainerScreen`（1.20.1 vanilla 与 Forge 47.3.19 补丁后源码、NeoForge 1.21.1 源码已读）：

| 成员 | Forge 1.20.1 | NeoForge 1.21.1 | 对 Ixchel 的意义 |
|---|---|---|---|
| `Slot.x/y` | `public final int` | 同左 | 布局写回需绕过 final（§3） |
| `isHovering(Slot,DD)`、`findSlot` | private | private | 不能直接覆写 |
| `isHovering(int,int,int,int,DD)` | **protected**；private 的 `isHovering(Slot,DD)` 直接调用它，固定传 16×16 | 同左 | 子类覆写即可改槽位命中（§4） |
| `renderSlot` | **private** | protected | 1.20.1 无法跳过 vanilla 物品绘制（§5） |
| `renderSlotContents` | 无 | protected | — |
| 悬停高亮 | static `renderSlotHighlight(…, color)`，颜色取自可覆写的 `getSlotColor(int)` | protected `renderSlotHighlight(…, Slot, …)` + `getSlotColor` | 两版都能关掉 vanilla 高亮 |
| `renderFloatingItem`（手持物品） | private | private | 只能利用调用顺序（§5） |
| `renderLabels`、`renderBg`、`hasClickedOutside`、`containerTick` | protected | protected | — |
| `quickCraftSlots`、`isQuickCrafting` | protected | protected | 拖拽分发预览可自算 |
| `quickCraftingType`、`clickedSlot`、`draggingItem`、`isSplittingStack` | private | private | 类型可由 `mouseClicked` 的按键推出（0/1/选取物品键）；后三者只在触屏模式起作用 |

Forge 1.20.1 `render` 顺序：

```
renderBg → ContainerScreenEvent.Render.Background → super.render(renderables)
→ 槽位循环 [isActive 时 renderSlot；isHovering && isActive 时设 hoveredSlot 并画高亮]
→ renderLabels（pose 已平移到 leftPos/topPos）→ Render.Foreground → 手持物品 / 回弹物品
```

`renderSlot`、命中、`findSlot` 都先检查 `slot.isActive()`；`isActive=false` 的槽位不绘制也不可交互。

### 2. 槽位事务的硬约束

- `doClick` 对携带物原地 `grow()`；`safeInsert` 原地 `grow` 后以同一引用调用 `setByPlayer`。
- `Slot.remove()` 走 `container.removeItem`，绕过 `set()`。
- `initializeContents`、`setItem` 与预测回滚走 `slot.set()` 并更新 stateId；`broadcastChanges` 以 copy + `ItemStack.matches` 检测变化。
- 客户端在 `handleInventoryMouseClick` 先本地 `clicked` 预测，再发 `ServerboundContainerClickPacket(stateId, carried, changedSlots)`。

因此不能只在 `set()` 上观察槽位变化（推断：Slot 通道每帧轮询比较）；槽位交互全部交给 vanilla，不走 Action，不做乐观更新。

### 3. 布局写回 `Slot.x/y`

| | AUI（forge-1.20.1） | LDLib2（NeoForge 1.21） |
|---|---|---|
| 去掉 final | `META-INF/accesstransformer.cfg`：`public-f … Slot f_40220_` / `f_40221_` | mixin `SlotAccessor`（`@Accessor @Mutable`） |
| 何时写 | `SlotDataBinder.syncAllSlotPositions` 每帧比较后写 | `ItemSlot.onLayoutChanged` 写 `contentX - leftPos` |
| 槽位结构来源 | 服务端经 `extraData` 发 `SlotLayout`，客户端先建在 0,0 | 服务端与客户端都建 UI 树，挂载时 `menu.addSlot` |

两者都是独立 mod，自带 AT 或 mixin 不给任何人增加接入步骤；Ixchel 被 shade（#001）、零 mixin（#002），AT 的成本转到宿主。

AT 路线实测（mc 层临时探针，已撤销）：

- 未配 AT 时 `slot.x = …` 编译失败（`无法为最终变量x分配值`）。宿主声明的 AT 只作用于宿主自己的编译构件，库模块看不到。
- 库模块在 `legacyForge { accessTransformers.from(...) }` 中声明后，`mcpVersion` 原版模式下编译通过；reobf 后字节码为 `putfield Slot.f_40220_:I`。
- 运行期 AT 由 Forge 读取 **mod jar** 的 `META-INF/accesstransformer.cfg`；库被 shade 后只有宿主 jar 的那份生效。漏配时类照常加载，直到执行 `putfield` 才抛 `IllegalAccessError: Update to non-static final field … attempted from a different class`（JDK 21 最小复现）——即打开该界面时才崩。
- 名字：1.20.1 写 SRG（`f_40220_`/`f_40221_`，来自 `officialToSrg.tsrg`），1.21.1 写 mojmap。testmod 以 sourceSet 并入库，也须声明同一 AT。

反射路线实测（`Field.setAccessible(true)` + `setInt` / `MethodHandles.lookup().unreflectSetter`）：

| 检查 | JDK 17 | JDK 21 |
|---|---|---|
| 写普通类的 final 实例字段 | 成功 | 成功 |
| 已被 C2 编译的读取方读到新值 | 是 | 是 |
| `MethodHandle` setter 单次耗时 | 约 8 ns | 约 8 ns |
| record 组件 | `IllegalAccessException` | 同左 |

反射的风险：

- JEP 500（JDK 26 交付）：反射改 final 字段默认告警，后续版本默认抛异常；豁免参数 `--enable-final-field-mutation` 只对 boot layer 模块有效，mod 所在的用户层无法自行豁免。1.20.1/1.21.1 配套 Java 17/21 不受影响，玩家换更新 JDK 时会先告警。
- 模块开放：L1 实测两个平台 `Slot` 均在具名模块 `minecraft` 中，`isOpen("net.minecraft.world.inventory")` 为 true（对所有模块与对 testmod 模块均开放），写入后读回正确；`MethodHandle` setter 写一对 `x/y` 在游戏内约 7 ns（Forge 1.20.1 / Java 17）与 30 ns（NeoForge 1.21.1 / Java 21，启动时单次测量），每帧几十个槽位可忽略。
- 字段名：Forge 1.20.1 开发期为 `x`/`y`、生产为 SRG；NeoForge 1.21.1 运行期为 mojmap。按候选名依次查找，`UiContainerScreen` 初始化时即查找并缓存，失败立即报错。
- JIT 对普通类 final 实例字段默认不做常量折叠（实测）；仅 `-XX:+TrustFinalNonStaticFields` 等非默认配置下有风险。

坐标语义：`x/y` 是槽位内部 16×16 物品区的左上角（相对 `leftPos/topPos`）；槽位的边框、背景可以更大，但物品、命中区、第三方覆盖层都以它为准。

### 4. 命中判定

AUI、LDLib2 都在 `isHovering(Slot,DD)` 开头注入：AUI 用 `UiSlot` 的宽高与元素命中，LDLib2 用 `lastHoveredElement`。收益是弹窗、下拉菜单下的槽位不再高亮、不显示 tooltip、不响应点击，滚动区外不可命中，命中区可不为 16×16。

Ixchel 覆写 protected 的 `isHovering(int,int,int,int,DD)`：由 `x,y` 反查槽位元素，再判断遮挡与裁剪，两个平台都可行。Mouse Tweaks 经 `findSlot`、JEI/EMI/REI 经 `hoveredSlot` 拿到的都是这一判定的结果。

### 5. 绘制

| 需求 | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| 自定义悬停样式 | `getSlotColor` 返回 0，按 `hoveredSlot` 自画 | 同左，或覆写 `renderSlotHighlight` |
| 覆盖层位于槽位物品之上、手持物品之下 | 在 `renderLabels` 中以 z=300 画（L1 实测：槽位物品 100+150 之上、手持物品 232+150 之下） | 同左 |
| 槽位物品逐个裁剪、缩放、透明度动画 | 零面积裁剪盖掉 vanilla 的槽位循环，在 `renderLabels` 中自画 | 覆写 `renderSlot`，包一层裁剪/变换后调用 `super`，可逐槽位选择 |

1.20.1 零面积裁剪的依据：`GuiGraphics.renderItem` 每个物品末尾 `flush()`，`applyScissor` 允许 0 宽高。槽位循环前没有 protected 钩子，但 renderables 中最后一个恰在循环前执行：

```java
// 末尾的 renderable
g.enableScissor(0, 0, 0, 0);
@Override protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.disableScissor();
    ixchel.paintSlotItems(g);   // 按元素裁剪/变换画物品、数量、空槽剪影（getNoItemIcon）、拖拽分发预览
    ixchel.paintOverlays(g);
}
```

vanilla 的 `renderSlot` 仍执行，其副作用（把无效目标移出 `quickCraftSlots` 并重算剩余）保留。代价：

- 物品顶点构建两次：L1 实测（玩家背包 46 槽）`super.render` vanilla 约 700–840 µs、零面积裁剪加自绘约 745–805 µs，差值在采样噪声内，小于 0.1 ms。
- 其它 mod 注入 `renderSlot` 的装饰（物品边框、稀有度光效类，具体 mod 未核实）一并被盖掉；AUI、LDLib2 在 `renderSlot` HEAD 取消，同样丢失。
- 其它 mod 在 `ScreenEvent.Init.Post` 追加的 renderable 排在裁剪之后会不可见（L1 实测）；每帧把裁剪 renderable 移到末尾即恢复可见，`Screen.renderables` 是 public，直接 `remove` 后 `add`。
- 触屏模式的拆分预览依赖 private 字段，不支持。

不用 mixin 无法做到的只有两处：1.20.1 跳过 vanilla 物品绘制本身、两个平台截获手持物品绘制（AUI 截获 `renderFloatingItem`，LDLib2 自画拖拽预览）。

### 6. 第三方 mod 对槽位坐标的依赖

源码：`.ai/ref/` 下 JEI `1.20.1`、EMI `1.20`、REI `12.x-1.20`、Mouse Tweaks `minecraft-1.20.1` 分支。

| Mod | 读取方式 | 缓存 |
|---|---|---|
| JEI | 悬停 `getSlotUnderMouse()`（即 `hoveredSlot`）；可点击区 `getGuiLeft() + slot.x`，16×16（`Library/.../gui/helpers/ScreenHelper.java`） | 每次现算 |
| Mouse Tweaks | `@Invoker("findSlot")`；滚轮方向比较 `slot.y < selectedSlot.y` | 无 |
| EMI | 悬停 `getFocusedSlot`；拖放区 `slot.x - 1`，18×18，`dropStack`/`render` 每次重建；搜索高亮跳过 `!isEnabled()` | 无 |
| REI | 悬停 `hoveredSlot`；搜索高亮每帧遍历全部槽位画在 `slot.x/y`，**不检查 `isActive()`** | 无 |

AUI 没有这几个 mod 的集成；LDLib2 的 `integration/xei/*` 只处理非槽位元素（排除区、拖放、配方查询）。

由此的约束：

- 坐标写回后下一帧即对第三方生效，无需通知。
- 隐藏槽位须 `isActive=false` **且** `x=y=-2000`（vanilla 创造模式物品栏同样用 -2000，`CreativeModeInventoryScreen` 1.20.1 第 504-505 行），否则 REI 仍在原位画高亮。
- 隐藏发生在拖拽分发进行中时，须从 `quickCraftSlots` 移除（AUI `pruneInvalidQuickCraftSlot` 的做法），细节待实现时确认。

### 7. 冲突点的开关（#009）

原则：兼容优先，默认走 vanilla 路径；有冲突风险的增强必须有开关，由宿主显式开启，开启后按运行条件自动降级。

```java
UiContainerScreen.builder(menu, template)
    .slotItemRendering(SlotItemRendering.VANILLA)   // 默认；IXCHEL 为增强
    .build();
```

| 模式 | Forge 1.20.1 | NeoForge 1.21.1 | 其它 mod 的槽位装饰 |
|---|---|---|---|
| `VANILLA` | vanilla 画物品；滚动区内滚出视口的槽位按 §6 隐藏，边缘半截不裁剪 | 同左 | 保留 |
| `IXCHEL` | §5 零面积裁剪，整屏统一 | 覆写 `renderSlot`，可逐槽位 | 被接管的槽位上丢失 |

- 开关是 API 参数，不是库自带配置文件：多份重定位副本共存时库级配置无法归属，且库不是 mod，无法注册配置界面入口。宿主要给玩家选择，就接到自己的 `ForgeConfigSpec`/`ModConfigSpec`。
- 不按 `ModList` 检测冲突 mod 自动切换：名单不全，且尚无实际观察到的冲突。
- 配置界面组件（开关、滑条、下拉）可作为 testmod 演示或组件库条目，待有宿主需求再做；注册入口（`ConfigScreenHandler`/`IConfigScreenFactory`）始终在宿主。

### 8. 验证计划

| 步骤 | 内容 | 证明 |
|---|---|---|
| L0（已完成） | JVM 探针：final 字段反射写入、JIT、record | Java 17/21 语义与开销 |
| L1（已完成） | 两个开发宿主各一个“槽位随布局移动”的测试 Screen（`sample/forge-1.20.1` 与 `testmod/neoforge-1.21.1` 的 `probe/`，K 打开）：打印模块开放；悬停、点击、Shift、拖拽分发、双击收集、数字键、界面外丢弃；1.20.1 零面积裁剪、追加按钮可见性、覆盖层与手持物品的 z 顺序、重复绘制耗时 | 模块访问；vanilla 交互跟随新坐标（镜像与持续移动两种布局均跟随）；§3、§5 已按实测更新 |
| L2（已完成，Forge 1.20.1） | 机器面板（S2）装 JEI 或 EMI，均加 Mouse Tweaks（`-Pslotcompat=jei\|emi -Pcheck=machine`），含真实光标悬停检查；REI 未跑。原先挂在背包改装上，背包改装已删除；`IXCHEL` 绘制下滚动列表的槽位跟随改由存储终端（S3，`-Pcheck=storage`）检查 | §6 静态分析结论 |
| L3（已完成） | shade-a（relocate + reobf）在生产实例中以 `IXCHEL` 绘制打开玩家背包的 `UiContainerScreen`，真实点击移动物品，关闭后检查槽位坐标已还原（`ContainerCheck`，`-Dixchelshadea.check=container`） | `Slot.x/y` 与 `Screen.renderables` 的 SRG 名查找 |
| L4（可选） | JDK 26 启动 | JEP 500 告警形态与归属模块 |

### 9. 已定与后续

- 坐标写入用反射（#008）；改用 AT 的触发条件：目标平台 Java ≥ 26。
- `slotItemRendering` 默认 `VANILLA`（#009）。

### 10. 参考

- MC：1.20.1 vanilla（neoform `mergeWithSources` 产物）与 Forge 47.3.19 `forge-1.20.1-47.3.19-sources.jar` 的 `AbstractContainerScreen`、`AbstractContainerMenu`、`Slot`、`GuiGraphics`、`CreativeModeInventoryScreen`；NeoForge 1.21.1 `sourcesAndCompiledWithNeoForge` 产物；`officialToSrg.tsrg`。
- AUI：`targets/forge-1.20.1` 的 `mixin/AbstractContainerScreenMixin.java`、`screen/SlotDataBinder.java`、`META-INF/accesstransformer.cfg`、`build.gradle`。
- LDLib2：`core/mixins/ui/AbstractContainerScreenMixin.java`、`gui/ui/elements/ItemSlot.java`、`integration/xei/*`。
- JEI `1.20.1`、EMI `1.20`、REI `12.x-1.20`、Mouse Tweaks `minecraft-1.20.1`（GitHub 浅克隆）。
- JEP 500：https://openjdk.org/jeps/500
