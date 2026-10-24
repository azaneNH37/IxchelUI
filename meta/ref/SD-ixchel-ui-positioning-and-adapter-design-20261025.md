---
tags: [survey, design]
date: 2026-10-25
origin: 对 ApricityUI（AUI）与 LDLib2 的 UI 实现做选型调研后，讨论"如需 HTML/CSS 式 UI，应如何组合两者思路与 Java 生态现有库"，记录核心观点与前瞻方案初版。初稿写于 Kumiko 仓库，因前提不同（独立 mod 生态 vs 可嵌入库）迁出另立 Ixchel UI。
related: [Kumiko 仓库 meta/ref/S-kumiko-product-value-and-primitives-20260922.md, Kumiko 仓库 meta/ref/SD-ldlib2-kernel-extraction-and-module-design-20260921.md, Kumiko 仓库 meta/DECISIONS.md#002]
---

# Ixchel UI: Positioning and Adapter Design

## Summary

Ixchel UI 的定位是：**可像普通库一样嵌入的 Web 风格 UI 编写层 + 客户端数据需求声明**。"真正的嵌入模式"（零全局副作用、可重定位 shade、多份共存）是必须守住的硬约束，也是相对 AUI 与 LDLib2 的主要差异；但采用理由仍是编写体验与数据层，不是嵌入本身。实现上采用纯 Java 内核 + 仅含 vanilla 类的薄适配层 + 极薄加载器胶水，默认产物不含 mixin，MVP 只覆盖一个渲染时代与 TACZ 场景，其余能力在消费验证后再扩展。

本文为初版前瞻方案，尚无实现。前身 Kumiko 是思考过渡期的研究项目，不作为开发基础（见 Detail §2）。

## Detail

### 0. 动机："不够干净"的根源

现有 MC UI 方案是**接管宿主的平台**，而不是**宿主使用的库**；直觉上的"不干净"来自边界没有守住：

| 表现 | 证据 | 越过的边界 |
|---|---|---|
| 改写全局游戏状态 | AUI 每 tick 改写主 target、输入 mixin；LDLib2 对 ItemRenderer、ModelBakery、ShaderInstance、键鼠的 mixin；均 `defaultRequire: 1` | 库越过边界改宿主与整个游戏 |
| 一个包承担所有层 | LDLib2 约 16 万行含渲染、同步、持久化、编辑器、网络；`UIElement` 2.4k 行混合树/布局/样式/事件/序列化/编辑器；`ModularUI` 内含同步管理 | 职责不分层 |
| 数据流靠手工接线 | 同步依附 ContainerMenu 会话；LDLib2 绑定分裂为 5 个接口；网络处理器直接寻找 Screen 刷新 | 视图与数据来源之间无契约 |
| 编写语言是方言或在追浏览器 | 原版手写坐标；LSS 的 `hover-background`、`__hovered__` 魔法类、`padding-all`；AUI 至少约 17k 行追浏览器兼容，不支持的 CSS 静默忽略 | 语言不标准、边界不明 |
| 分发强加给使用者 | 均需独立安装；jar-in-jar 依赖不重定位、版本区间开放、捎带 kotlin-stdlib | 依赖关系由库决定而非使用方 |
| 跨版本靠整份拷贝 | AUI 每个 target 近乎完整拷贝；LDLib2 仅 1.21.1 | 版本差异未隔离在一层 |

对应的四条边界即本项目的硬约束：库而非平台（零全局副作用、可 shade、多份共存）；标准语言而非方言（标准 CSS 子集，开发期报错）；数据声明而非接线（ViewModel）；版本差异只在一层（纯 Java 内核 + 按渲染时代的薄适配层）。

### 1. 证据基线

| 项 | AUI（v1.2.8.1 fork） | LDLib2（v2.2.40 / 发行 2.2.44） |
|---|---|---|
| 编写形态 | HTML + CSS + JS，"语法尽可能遵循 Web 标准" | XML（`ldlib2-ui.xsd`）+ LSS 方言 |
| 自身承诺 | 明确"不是浏览器，而是越来越像 Web 的子集"（`archived/guide/support.md:3-7`）；默认布局为纵向 flex 而非文档流；超出实现的 CSS 静默忽略（`archived/agent.md:33`） | 无 Web 兼容承诺 |
| 布局 | 自写 Flex + Grid（约 3.3k 行） | taffy-java（`dev.vfyjxf:taffy`，纯 Java，flex + grid + calc） |
| 圆角/裁剪 | 圆角、旋转、clip-path 一律回退主 target 模板缓冲（`Mask.java:199-202`） | `sdf_rect` 着色器；默认 scissor；`overflow-clip` 显式触发私有图层 + 遮罩纹理（`UIElement.java:1879-1890`），UI 路径无模板 |
| 全局副作用 | 每 tick `reconcileFabulousChainStencil` 改写主 target；输入 mixin；`defaultRequire: 1` | 大量渲染/输入 mixin（ItemRenderer、ModelBakery、ShaderInstance、Keyboard/Mouse 等），`defaultRequire: 1` |
| 体量 | jar 约 2.4MB；common 约 84.8k 行，其中至少约 17k 行服务浏览器兼容（DevTools、canvas、Grid、WebView、Mask/Filter、JS） | jar 7.7MB（含 kotlin-stdlib、taffy、yoga，未重定位）；`gui/ui` 35k 行 |
| 采用 | Modrinth 依赖方 0、整合包 0 | GregTech 生态 |

AUI 主题样本（15 套 `theme/*/*.css`，grep 行数）显示 Web 风格 CSS 的主要用量是 CPU 侧特性：`var()` 6365、属性选择器 1666、`:hover` 1226、伪元素 1061、`calc` 154、渐变 167、`@media` 79、`@keyframes` 73；涉及 GPU 状态的 mask 148（含变量名误计）、clip-path 20、filter 18。

### 2. 来源：Kumiko

Kumiko 是源于"现有方案不够简洁干净"这一直觉的研究项目，前提是独立 mod 及其上的生态；经长期比对后其结论收敛为 §0，后续不基于 Kumiko 开发。Ixchel UI 继承的结论：

- 客户端数据原语的划分（可观察/派生值、目标及访问策略、动作、作用域、显示宿主），见 Kumiko 仓库 `meta/ref/S-kumiko-product-value-and-primitives-20260922.md` §7。
- TACZ 四个场景作为验收样本（同一文档 §5）。
- LDLib2 内核解剖：`StyleEngine`、`UIEventDispatcher` 零 MC 依赖可参考（Kumiko 仓库 `meta/ref/SD-ldlib2-kernel-extraction-and-module-design-20260921.md`）。
- 抽象需要现有消费者：DisplayList 仅在出现第二个渲染时代适配层时引入，MVP 的 Painter 直接调用 `GuiGraphics` 高层 API。
- 布局只用 taffy，不引入 Yoga。

不继承：复制层、意图 RPC 与独立 mod 生态的设想。外部数据来源（其它 mod 的客户端缓存、网络副本等）一律由宿主接入；由于各份副本重定位后类名不同，数据端口只接受 JDK 类型（如 `Supplier<T>` + 变更监听）。

### 3. 核心定位

| 层次 | 内容 | 做不到的后果 |
|---|---|---|
| 硬约束 | 真正嵌入：零全局副作用、可重定位 shade、多份共存，引用心智接近"按平台分产物的普通库" | 退化为又一个独立安装的 UI mod，与 AUI/LDLib2 正面竞争且处于劣势 |
| 价值 | Web 风格编写（HTML + 标准 CSS 子集）+ 客户端数据需求声明（ViewModel） | 可嵌入但无采用理由 |

"轻量"指**无全局副作用**，不是体积小：jsoup + ph-css + taffy 约 1.3MB 可接受；一个 mixin、一个固定 namespace 资源或一次主 target 改动即破坏前提。

与现有方案的差异：

| 维度 | AUI | LDLib2 | 本方案 |
|---|---|---|---|
| 编写 | HTML/CSS/JS | XML + LSS | HTML + 标准 CSS 子集，无 JS |
| 全局副作用 | 有 | 有 | 无 |
| 分发 | 独立 mod | 独立 mod | shade 进宿主（可选 jar-in-jar） |
| 跨版本 | 每 target 近乎完整拷贝 | 仅 1.21.1 | 纯 Java 内核 + 按渲染时代的薄适配层 |
| 数据层 | JS | 5 个绑定接口 + 并行同步体系 | ViewModel 声明数据需求 |

### 4. 从 AUI / LDLib2 的取舍

借鉴：

- taffy-java 作为布局引擎（LDLib2 生产使用）。
- `sdf_rect` 式着色器：逐角半径、边框、`fwidth` 抗锯齿、预乘 alpha。
- 默认 scissor 并与 `GuiGraphics` scissor 栈合并（LDLib2 `GUIContext`）。
- 圆角裁剪显式 opt-in，走私有图层 + 遮罩纹理，不用主 target 模板。
- 图层进出前 `flush()`，恢复进入前的 FBO 而非固定回 main。
- 字体是 `Font` 子类且可开关，步进一致保证布局不漂移（LDLib2 `LDLibFonts`）；本方案 MVP 进一步直接用 vanilla `Font` + 资源包 ttf 字体提供器。
- Surface 抽象（主窗口 / 离屏），供未来世界内屏幕。
- 状态伪类改写为状态类再匹配（LDLib2 `HierarchicalStyleMatcher`）。
- 每个 MC 原生元素作为一等标签（`item-slot` 等）。
- 文档化"能力边界 + 特性清单"（AUI `support.md` / `agent.md` 的做法），但不支持项在开发期报错而非静默忽略。

不照搬：

- LDLib2 `popVisualLayer` 用原生 `GL30.glBindFramebuffer` 恢复 FBO，绕过 `GlStateManager`，与 Iris 的绑定缓存不同步风险 [推断，未实测]；应走 `GlStateManager` 或 `RenderTarget.bindWrite`。
- 两者的全局 mixin 面与 `defaultRequire: 1`。
- 未重定位的 jar-in-jar 依赖与开放版本区间。
- AUI 的每 tick 全局状态改写、模板缓冲回退、`-g:none`（无行号堆栈）。
- LDLib2 `UIElement` 2.4k 行的职责混合。

### 5. Web 兼容口径

- 目标是"已实现部分用标准写法与语义"，不承诺渲染结果等同浏览器。AUI 本身也没有此承诺。
- 纳入：选择器全集（属性、组合符、结构伪类、状态伪类）、`var()`、伪元素、`calc`、过渡、`@keyframes`、以 GUI 缩放/窗口尺寸重定义的 `@media`、圆角/边框/阴影/线性渐变。
- 默认 `display: flex; flex-direction: column`，不做文档流。
- 富文本只做 React Native 式嵌套文本（单叶子测量），不做完整行内格式化上下文。
- 不纳入：JS、完整表单语义、BOM/`fetch`。mask、clip-path、filter、backdrop 与旋转裁剪延后，且只允许私有图层实现。
- 半兼容成本：已支持特性与标准语义不一致仍会被视为缺陷；以开发期校验替代静默忽略。

### 6. 架构

```text
ui-core            纯 Java；编译期无 MC
  解析   jsoup（HTML，含选择器引擎）+ ph-css（CSS3 语法）
  样式   选择器匹配 + 层叠 + var() + 过渡/关键帧
  布局   taffy-java + 文本测量回调
  事件   三阶段派发、焦点、hover
  数据   Value / Derived / Action / Scope
  输出   绘制命令（MVP 内部直接交给 Painter）
ui-mc-<时代>        仅 vanilla 类：Painter、TextMeasurer、UiView、宿主辅助
ui-<加载器>-<版本>  约百行胶水：资源重载监听、HUD 层、（日后）shader 注册
```

库选型：jsoup 1.21.1（499KB）、ph-css 7.0.4（552KB，依赖 ph-commons 12，122KB）、taffy 1.1.5（233KB，97 类，无 natives）。ph 系列若嫌重，可自写 CSS Syntax L3 分词器（估计 1~2k 行，推断）。

工作量粗估（推断）：内核 8~12k 行，每个适配层 1~2k 行；对照 AUI common 约 85k 行、LDLib2 `gui/ui` 35k 行。

### 7. 桥：通道与距离

纯 Java 内核必然经过一座桥，但它是同 JVM、同渲染线程的接口调用，代价在语义落差而非运行时。不选纯 Java 时桥依然存在，只是散布（AUI common 316 个文件中有 75 个直接 import MC/GL）。

以 1.21.1 为准（已读源码）：

| 通道 | MC 侧 | 距离 | 落差 |
|---|---|---|---|
| 输入 | `GuiEventListener` 各方法一一对应 | 小 | 仅在自有 Screen 内可得；HUD 无输入 |
| 视口/时间 | `Screen.width/height`、`getGuiScale()`、`partialTick` | 小 | `@media` 语义需定义 |
| 矩形 | `GuiGraphics.fill(int…)`，仅整数 | 小~中 | 浮点布局需像素对齐或自发顶点 |
| 文本测量 | `Font.width`、`Font.split`、`wordWrapHeight`，固定 `lineHeight` | 中 | 唯一的布局期同步回调；字号靠缩放，无字重、无整形 |
| 文本绘制 | `drawString` 有 int/float 重载 | 小 | 浮点坐标下像素字体发虚 |
| 图片 | `blit`、`blitSprite`、`nine_slice` | 中 | 原始尺寸异步可知，需重新布局 |
| 裁剪 | `enableScissor(int×4)` 自带栈 | 小 | 仅整数矩形 |
| 自定义着色器 | `ShaderInstance` + `RegisterShadersEvent` | 大，随时代变 | 适配层最厚部分 |
| 物品 | `renderItem` | 中~大 | 深度、z 偏移、与合批交互 |
| 容器槽位 | `Slot.x/y` 为 `public final int` | 大 | 见下 |
| 原版控件 | 独立焦点/输入/渲染 | 大 | 不嵌入，自行实现 |

槽位：自有子类可重写 public 的 `AbstractContainerScreen.isHovering(Slot, double, double)` 与 protected 的 `renderSlot`（私有 `findSlot` 内部调用 `isHovering`），不需 mixin。第三方 mod（JEI/EMI、Mouse Tweaks 类）直接读 `Slot.x/y` 可能错位（推断），稳妥做法是让布局对齐 Menu 预设坐标。

内核端口：

```java
interface TextMeasurer { TextMetrics measure(FontRef font, float size, StyledText text, float maxWidth); } // 布局期同步
interface Painter { void paint(DrawCommands cmds); }                 // 每渲染时代一份
interface ResourceResolver { CompletableFuture<ImageInfo> image(String ref); }
interface Clock { long nanos(); }
interface UiRoot { void frame(float w, float h, float scale); boolean dispatch(InputEvent e); }
// 数据端口：ViewModel 的 source(...) 由适配层提供客户端状态与 Menu 数据源
```

绘制命令约 10 种：`Rect`（含 SDF 参数）、`Text`、`Image`、`NineSlice`、`Item`、`Push/PopClip`、`Push/PopTransform`、预留 `Push/PopLayer`。

端口随版本的稳定性（推断，仅核对 1.21.1）：`TextMeasurer` 长期稳定；输入在较新版本改为事件对象；`Painter` 是唯一需按渲染时代重写的端口（1.21.2+ 着色器体系变化，1.21.5+ RenderPipeline，1.21.6+ GUI 延迟渲染）。因此 MVP 的 Painter 只用 `fill`、`drawString`、`blit`、`renderItem`、`enableScissor`，需要圆角时才引入自定义着色器。

### 8. 使用方嵌入形态

构建：

```groovy
dependencies {
    shade "com.azane.ixchel:ixchel-core:0.1.0"
    shade "com.azane.ixchel:ixchel-mc-1.21.1:0.1.0"
    shade "com.azane.ixchel:ixchel-neoforge-1.21.1:0.1.0"
}
shadowJar {
    relocate "com.azane.ixchel", "com.example.mymod.shadow.ixchel"
    relocate "dev.vfyjxf.taffy", "com.example.mymod.shadow.taffy"
    relocate "org.jsoup",        "com.example.mymod.shadow.jsoup"
    relocate "com.helger",       "com.example.mymod.shadow.helger"
}
```

运行时命名：NeoForge 1.20.5+ 为 mojmap 可直接用；Forge 1.20.1 为 SRG、Fabric 为 intermediary，需按加载器发布已重映射产物或由使用方先 shade 再重映射（推断，未实测）。

初始化由宿主驱动（shade 后的库没有自己的 mod 入口）：

```java
public MyMod(IEventBus modBus) { UiNeoForge.init(MODID, modBus); }
```

挂载核心是组合式 `UiView`，对外只有 `resize`、`render`、6 个输入转发、`close`：

```java
final UiView view = UiView.of(Ui.load(MODID, "ui/smith.html"), new SmithViewModel());
@Override public void render(GuiGraphics g, int mx, int my, float pt) { super.render(g, mx, my, pt); view.render(g, mx, my, pt); }
@Override public boolean mouseClicked(double x, double y, int b) { return view.mouseClicked(x, y, b) || super.mouseClicked(x, y, b); }
@Override public void removed() { view.close(); }
```

在其上提供 `UiScreen`（整页）、`UiHud`（只显示，回调交给加载器 HUD 注册）、`UiContainerScreen<M>`（继承，重写 `isHovering`/`renderSlot`）。组合式嵌入允许已有界面（如 TACZ 工作台）逐块替换，是对采用最有利的一点。

扩展点只开放两个：自定义标签（产出绘制命令或嵌入 MC 内容）与自定义数据源。

shade 带来的约束：

| 约束 | 处理 |
|---|---|
| 库不得携带固定 namespace 的 `assets/` | 默认样式表、shader 源码放包路径下用 `Class.getResourceAsStream` 读取，随 relocate 迁移；shader 以宿主 modid 注册 |
| 各 mod 的 `UiView` 互不通用，主题/字形缓存不共享 | 接受；主题由资源包覆盖 `assets/<modid>/ui/` |
| 版本由使用方钉死 | 只需源码级 API 稳定 |
| 多版本使用方 | 模板/样式/ViewModel 共享，挂载代码按版本分 |

### 9. mixin 策略

- mixin 不是禁区，但与"多份共存"冲突：配置由宿主声明，N 份重定位副本会对同一 vanilla 方法注入 N 次，导致全局状态争夺、加载顺序相关的冲突，`@Overwrite`/`@Redirect` 直接互斥。默认产物零 mixin。
- AT / access widener 结果幂等，可用，但需使用方抄入自己的 AT 文件；当前设计不需要。
- 不用 mixin 的覆盖面（NeoForge 21.1.251 核对）：`ScreenEvent.MouseButtonPressed.Pre`、`ScreenEvent.KeyPressed.Pre`、`InputEvent.MouseButton.Pre`、`InputEvent.MouseScrollingEvent`、`RenderGuiLayerEvent.Pre` 均可取消；`InputEvent.Key` 不可取消。透明 Screen（`isPauseScreen` 为 false、不画背景）可做可交互覆盖层，代价是玩家不能移动。
- 唯一必须 mixin 的场景是"边移动边操作 HUD"（光标锁定与键盘拦截）。届时两条路线：
  1. 库提供静态钩子（`UiInputHooks`），mixin 由宿主在自己的配置中编写并承担冲突取舍。
  2. 单独发布不重定位的可选运行时 mod，注入点 `require: 0` 并可降级；各 shade 副本通过不重定位、仅用 JDK 类型的小 API 包（如 `ServiceLoader`）发现它。仅在真实需求出现时建设。
- 若使用 mixin：不用 `@Overwrite`/`@Redirect`，优先 `@Inject` HEAD 条件取消或 MixinExtras `@WrapOperation`；平时惰性（参考 LDLib2 `RawInputGate`）；`defaultRequire: 0`；不改主 target，不做每 tick 全局改写。

### 10. 数据需求声明

模板只引用名字，ViewModel 声明来源、派生与动作：

```html
<span bind:text="loaded"></span>/<span bind:text="reserve"></span>
<button on:click="reload" bind:disabled="!canReload">装填</button>
```

```java
final class AmmoView extends ViewModel {
  final Value<ItemStack> held      = source(ClientSources.mainHand());
  final Derived<Integer> loaded    = derive(held, GunData::loadedAmmo);
  final Derived<Integer> reserve   = derive(held, inv(), Ammo::reserve);
  final Derived<Boolean> canReload = derive(loaded, reserve, (a, r) -> r > 0);
  final Action<Void> reload        = action(() -> Intents.send(new Reload()));
}
```

原语划分参考 Kumiko 仓库 `meta/ref/S-kumiko-product-value-and-primitives-20260922.md` §7：可观察/派生值 → `Value`/`Derived`；目标及访问策略 → `source(...)`；动作 → `Action`（回执不等于新状态）；作用域 → ViewModel 随宿主生命周期释放；物品位置与转移 → 仅 `UiContainerScreen` 挂 Slot 通道，MVP 后。表达式仅支持路径与 `!`，复杂逻辑写成 `Derived`。模板引用未声明名字或使用不支持的 CSS 属性时，开发期报错。数据源可接已有客户端缓存，不强制任何复制层。

### 11. MVP 范围与验证顺序

| 做 | 不做 / 延后 |
|---|---|
| 1.20.1~1.21.1 共用的 `GuiGraphics` 时代 | 1.21.2+ 各时代适配层 |
| Screen + 只显示 HUD | 可交互 HUD |
| §5 纳入的 CSS 特性 | JS、表单、BOM |
| 圆角/边框/阴影/渐变、图片/九宫格、物品、文本、scissor | mask、clip-path、filter、backdrop、旋转裁剪 |
| ViewModel + 客户端状态/Menu 数据源 | 复制层 |
| 开发期校验与热重载 | 可视化编辑器、DevTools |

验收场景沿用 Kumiko 调研中的 TACZ 场景（同一文档 §5）：先 §5.3 弹药 HUD 与 §5.2 改装 Screen，§5.4 背包式改装验证 Slot 通道。

验证顺序：

1. 纯 Java 内核原型，用单元测试从弹药 HUD 模板产出绘制命令，不碰 MC。
2. 1.21.1 适配层在游戏内绘制，确认零 mixin、不改主 target，并与 Iris、ImmediatelyFast 共存显示正常。
3. 重定位 shade 进两个测试 mod，故意使用不同版本，确认两份共存。此步不过则"vendor 友好"不成立，方案失去主要差异。
4. 改装 Screen 验证事件、`Action` 与作用域生命周期。

### 12. 风险与未决问题

- 需求未证实：AUI 在 Modrinth 上依赖方 0、整合包 0；第一消费方必须是自有 mod。
- 与 LDLib2 对比：差异只在可嵌入、标准 CSS 与数据层，须在 TACZ 场景体现为实际收益，否则只是"功能更少的 LDLib2"。
- 交互侧无 JS，LLM 生成的带脚本页面不可直接使用，需文档化边界。
- 1.21.6+ 延迟 GUI 渲染下自定义着色器与 FBO 需走 PIP 机制，适配层可能远超 1~2k 行（推断，未核对源码）。
- ph-css 体量与 API 风格；自写分词器的替代成本。
- 未决：模板绑定语法的最终形态；`@media` 的 MC 语义；是否以及何时引入 DisplayList 作为跨时代契约；jar-in-jar 模式是否作为正式支持的分发方式。

### 13. 证据来源

- AUI 仓库：`archived/intro.md`、`archived/guide/support.md`、`archived/guide/web-api.md`、`archived/agent.md`、`common/src/main/java/com/sighs/apricityui/render/Mask.java`、`theme/*/*.css`、`theme/theme-spec.md`、`common/build.gradle`。
- LDLib2 仓库：`src/main/java/com/lowdragmc/lowdraglib2/gui/ui/rendering/{GUIContext,UIVisualLayer,OffscreenSurface}.java`、`gui/ui/UIElement.java`、`gui/ui/style/{StyleSelector,HierarchicalStyleMatcher}.java`、`gui/LDLibFonts.java`、`core/mixins/ui/*.java`、`ldlib2-ui.xsd`、`assets/ldlib2/lss/*.lss`、`build.gradle`、`gradle.properties`。
- MC 1.21.1（官方映射）：`Slot`、`AbstractContainerScreen`、`GuiGraphics`、`Font`、`GuiEventListener`。
- NeoForge 21.1.251：`InputEvent`、`ScreenEvent`、`RenderGuiLayerEvent` 的可取消性。
- taffy-java：https://github.com/vfyjxf/taffy-java ；Maven Central：jsoup 1.21.1、ph-css 7.0.4、ph-commons 12.0.0。
