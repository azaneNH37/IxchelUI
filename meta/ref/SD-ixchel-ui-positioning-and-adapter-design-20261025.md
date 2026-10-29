---
tags: [survey, design]
date: 2026-10-25
origin: 对 ApricityUI（AUI）与 LDLib2 的 UI 实现做选型调研后，讨论"如需 HTML/CSS 式 UI，应如何组合两者思路与 Java 生态现有库"，记录核心观点与前瞻方案初版；同日经本地源码复核修正了 AUI 跨版本结构、槽位可见性、依赖体量、版本视野等表述，并把定位论证改为以分发结构为核心。初稿写于 Kumiko 仓库，因前提不同（独立 mod 生态 vs 可嵌入库）迁出另立 Ixchel UI。2026-10-26 补充边界判据（§3.4）、shade 配方（§8），并把 §11 第 3 步改为配方验证；同日改用自写模板解析器（#007），补 GuiGraphics 不合批的源码事实与圆角推荐路径，数据层与模块划分拆到独立文档，Kumiko 中仍需引用的结论已迁入这两份文档；同日槽位接入拆到独立文档，并更正"`renderSlot` 为 protected、槽位命中无法经重写改变"的表述（1.20.1 `renderSlot` 为 private；命中可经 protected 的 `isHovering(int…)` 覆写）。
related: [DECISIONS.md#001, DECISIONS.md#002, DECISIONS.md#003, DECISIONS.md#004, DECISIONS.md#006, DECISIONS.md#007, ref/D-ixchel-module-breakdown-20261026.md, ref/SD-ixchel-viewmodel-and-binding-20261026.md, ref/SDG-ixchel-container-slot-integration-20261026.md]
---

# Ixchel UI: Positioning and Adapter Design

## Summary

Ixchel UI 的定位是：**具备原版 GUI 部署性质（无前置、无版本冲突）的 Web 风格 UI 编写层 + 客户端数据需求声明**。AUI 与 LDLib2 以独立 mod 交付、使用全局能力，以前置或 jar-in-jar 单实例分发；Ixchel 以零全局副作用换取 shade + relocate，宿主测过的版本就是运行的版本。使用方的替代选项是手写原版 GUI；首要目标是作为自有 mod 的 UI 基础设施，开放给他人使用在自用验证后再评估。

本文为初版前瞻方案，尚无实现。前身 Kumiko 是思考过渡期的研究项目，不作为开发基础（见 Detail §2）。

## Detail

### 0. 动机

AUI 与 LDLib2 以独立 mod 交付，整局游戏一份引擎，分别服务 WebUI 编写体验与 GregTech 生态。Ixchel 的前提是被宿主像普通库一样嵌入，两者的以下设计在此前提下不适用：

| 方面 | 现有做法（证据） | 在嵌入前提下的问题 |
|---|---|---|
| 使用全局能力 | AUI 每 tick 调整主 target（`reconcileFabulousChainStencil`）、输入 mixin；LDLib2 对 ItemRenderer、ModelBakery、ShaderInstance、键鼠的 mixin；均 `defaultRequire: 1` | 多份副本共存时互相争夺 |
| 一个包覆盖多层 | LDLib2 约 16 万行含渲染、同步、持久化、编辑器、网络；`UIElement` 2.4k 行覆盖树/布局/样式/事件/序列化/编辑器；AUI common 含 `ServerPlayer`、网络、`SavedData` | 宿主只需 UI 层时无法单独取用 |
| 数据流接线 | 同步依附 ContainerMenu 会话；LDLib2 绑定分为 5 个接口；网络处理器直接寻找 Screen 刷新 | 视图与数据来源之间缺少宿主可控的契约 |
| 编写语言 | 原版手写坐标；LSS 自有语法（`hover-background`、`__hovered__`、`padding-all`）；AUI 持续扩展 Web 兼容（至少约 17k 行），未支持的 CSS 往往被忽略 | 本项目希望特性边界在开发期可见 |
| 分发与版本 | 前置或 jar-in-jar；LDLib2 对 kotlin-stdlib、yoga、taffy 的 jarJar 区间为开放的 `[x,)`（`build.gradle:178/217/221`）且不重定位 | 运行版本由玩家或加载器决定（见 §3） |
| 内核与 MC 的关系 | AUI common 316 个文件中 75 个直接 import MC/GL（`PoseStack` 33 处），跨版本靠 spi 层 + 每 target 适配；LDLib2 仅 1.21.1 | 本项目希望内核可脱离游戏测试，版本差异收敛到一层 |

由此得到本项目的四条硬约束：库而非平台（零全局副作用、可 shade、多份共存）；标准语言子集（标准 CSS 子集，开发期报错）；数据声明（ViewModel）；版本差异只在一层（纯 Java 内核 + 按渲染时代的薄适配层）。

### 1. 证据基线

| 项 | AUI（本地 fork，HEAD 2026-10-24，mod_version 1.2.8.1） | LDLib2（v2.2.40 / 发行 2.2.44） |
|---|---|---|
| 编写形态 | HTML + CSS + JS（kjs → rhino → 不执行，均为可选），"语法尽可能遵循 Web 标准" | XML（`ldlib2-ui.xsd`）+ LSS 方言 |
| 自身承诺 | 明确"不是浏览器，而是越来越像 Web 的子集"（`archived/guide/support.md:3-7`）；默认布局为纵向 flex 而非文档流；超出实现的 CSS 往往被忽略（`archived/agent.md:33`） | 无 Web 兼容承诺 |
| 布局 | 自写 Flex + Grid（约 3.3k 行，近期仍在扩展 Grid/table） | taffy-java（`dev.vfyjxf:taffy`，纯 Java，flex + grid + calc） |
| 圆角/裁剪 | 轴对齐矩形走 scissor；圆角、旋转/错切、clip-path 回退模板缓冲（`Mask.java:199-202` 的模式选择），stencil 不可用时降级 | `sdf_rect` 着色器；默认 scissor；`overflow-clip` 或 `opacity < 1` 触发私有图层（`UIElement.java:1879-1890`），UI 路径无模板 |
| 全局副作用 | 每 tick `reconcileFabulousChainStencil` 改写主 target（Forge/NeoForge 1.21.1 及以下 target）；输入 mixin；`defaultRequire: 1` | 大量渲染/输入 mixin（ItemRenderer、ModelBakery、ShaderInstance、Keyboard/Mouse 等），`defaultRequire: 1` |
| 跨版本 | common 作为共享源码树经 `srcDir` 编入各 target，`spi/` 隔离版本差异；8 个 target 覆盖 1.18.2~26.1，每 target 约 8~14k 行 | 仅 1.21.1 |
| 数据层 | JS + Menu 绑定 SPI（`AuiBindingBuilder`：player / saveddata / blockEntity / entity） | 5 个绑定接口 + 并行同步体系 |
| 体量 | common 约 85k 行（不含测试），其中至少约 17k 行服务浏览器兼容（DevTools、canvas、Grid、WebView、Mask/Filter、JS） | jar 7.7MB（含 kotlin-stdlib、taffy、yoga，未重定位）；`gui/ui` 35k 行 |
| 许可 | LGPL-2.1 | LGPL-3.0 |
| 采用 | Modrinth 下载约 67k（2026-10-25）；依赖方数量未复核 | GregTech 生态 |
| 活跃度 | 双人每日提交，已发布 26.1.x | — |

AUI 主题样本（15 套 `theme/*/*.css`，grep 行数）显示 Web 风格 CSS 的主要用量是 CPU 侧特性：`var()` 6365、属性选择器 1666、`:hover` 1226、伪元素 1061、`calc` 154、渐变 167、`@media` 79、`@keyframes` 73；涉及 GPU 状态的 mask 148（含变量名误计）、clip-path 20、filter 18。

### 2. 来源：Kumiko

Kumiko 是源于"现有方案不够简洁干净"这一直觉的研究项目，前提是独立 mod 及其上的生态；经长期比对后其结论收敛为 §0，后续不基于 Kumiko 开发。Ixchel UI 继承的结论已迁入本仓库，无需再查 Kumiko：

- 客户端数据原语的划分、默认保证与必须保留的差异，TACZ 四个场景与验收矩阵：`ref/SD-ixchel-viewmodel-and-binding-20261026.md` §1、§2、§7。
- LDLib2 内核中零 MC 依赖可参考的类：`ref/D-ixchel-module-breakdown-20261026.md` §10；vanilla Slot 硬约束与槽位接入：`ref/SDG-ixchel-container-slot-integration-20261026.md`。
- 布局只用 taffy，不引入 Yoga。

不继承：复制层、意图 RPC 与独立 mod 生态的设想。外部数据来源（其它 mod 的客户端缓存、网络副本等）一律由宿主接入；由于各份副本重定位后类名不同，数据端口只接受 JDK 类型（如 `Supplier<T>` + 变更监听）。

修正：Kumiko 的"DisplayList 仅在第二个渲染时代引入"不再沿用。§7 的绘制命令本身就是 DisplayList，且已有两个消费者（内核单元测试与 Painter），从第一天起即为内核与适配层的契约。

### 3. 核心定位

#### 3.1 分发结构决定差异

依赖全局能力（mixin、全局注册、固定 namespace 的 `assets/`、每 tick 全局改写）的 mod 型引擎要求整局游戏只有一份，因此只能：

| 方式 | 实际运行的版本 | 脆性来源 |
|---|---|---|
| 前置 | 玩家安装的版本 | 整合包作者手工对齐版本；引擎不移植到新 MC 版本则宿主被锁（LDLib2 仅 1.21.1） |
| jar-in-jar | 加载器在各宿主声明区间中选出的一份（推断：NeoForge/Forge 取区间交集内最高版本、无交集则加载失败；Fabric 按 modid 取最高版本） | 宿主测过的版本不一定是运行的版本；开放区间放大风险 |

mod 型引擎也无法改为 shade：多份副本会争夺 mixin、全局 ID 与 namespace。

Ixchel 的零全局副作用约束换来第三种方式：每个宿主内含一份重定位副本，**宿主测过的版本就是运行的版本**，与整合包中其它 mod 无关。代价是放弃服务端、网络与全局能力（见 §3.3）。

#### 3.2 层次与位置

| 层次 | 内容 | 做不到的后果 |
|---|---|---|
| 硬约束 | 真正嵌入：零全局副作用、可重定位 shade、多份共存，宿主拥有运行版本 | 退化为又一个前置/JiJ 的 UI mod，与 AUI/LDLib2 的分发方式相同 |
| 价值 | Web 风格编写（HTML + 标准 CSS 子集）+ 客户端数据需求声明（ViewModel） | 部署性质与手写原版相同但无编写收益，不如手写 |

- 嵌入是前提而非卖点：它满足"不引入前置"的需求，但使用理由仍须来自编写体验与数据层。
- 使用方的替代选项是**手写原版 GUI**：问题是"多 shade 约 0.23MB 依赖加内核自身（§6），换 HTML/CSS + ViewModel 是否值得"。
- 能接受前置的场景，AUI 已提供主题、DevTools、Grid/table 与 1.18.2~26.1 支持，LDLib2 在 GregTech 生态成熟；Ixchel 不追求同等功能覆盖。
- 对使用者可见的差别是部署性质与数据层；严格 CSS 与开发期报错是体验取舍；内核不依赖 MC 是内部质量。

"轻量"指**无全局副作用**，不是体积小；一个 mixin、一个固定 namespace 资源或一次主 target 改动即破坏前提。

与现有方案的对照：

| 维度 | AUI | LDLib2 | 本方案 |
|---|---|---|---|
| 编写 | HTML/CSS/JS（JS 可选） | XML + LSS | HTML + 标准 CSS 子集，无 JS |
| 全局副作用 | 有 | 有 | 无 |
| 分发 / 运行版本归属 | 前置；玩家决定 | 前置或 JiJ；玩家或加载器决定 | shade 进宿主；宿主决定 |
| 跨版本 | 共享 common（含 MC 依赖）+ spi + 每 target 适配，1.18.2~26.1 | 仅 1.21.1 | MC 无关内核 + 按渲染时代的薄适配层 |
| 数据层 | JS + Menu 绑定 SPI | 5 个绑定接口 + 并行同步体系 | ViewModel 声明数据需求 |

#### 3.3 目标与代价

- 首要目标：自有 mod（TACZ-RESPAWN 等）的 UI 基础设施，比较对象是"在自有 mod 里手写原版或依赖现有 UI mod"。TACZ 这类大量进入整合包的 mod 对运行版本可控的需求最强。
- 生态采用：仅在自用验证有效、且有第三方作者明确表达"要 Web 式 UI 但不接受前置"后再评估；目前无此证据。
- 代价（须写入对外文档）：无法提供需全局单例的能力（如边移动边操作 HUD 的输入捕获，见 §9）；多份副本不共享缓存，无法跨 mod 统一主题；修复需各宿主各自升级。
- 修复推送的分量：Iris、Sodium、ImmediatelyFast 等渲染兼容修复要每个宿主重新发版，宿主停更则缺陷留存。自用阶段库作者即宿主维护者，代价小；该代价随第三方宿主数量线性增长。Painter 尽量停在高层图元可缩小暴露面。
- 许可：shade 要求库本身与所有被 shade 的依赖均为宽松许可（§4）。

#### 3.4 边界判据

重定位只隔离类。类以外的一切（文件路径、配置名、资源命名空间、存档/NBT 键、线程名）按宿主 modid 分区；无法分区的进程级状态（GL/`RenderSystem`、GLFW 光标与回调、`Minecraft.screen`）只借用并原样归还。

| 需求 | 是否越界 | 处理 |
|---|---|---|
| 以宿主 modid 注册（shader、PIP 渲染器、HUD 层、资源重载监听） | 否 | 由宿主或加载器胶水层注册 |
| 网络频道 | 否（按宿主 modid 可共存） | 不做是范围决定（无服务端、无复制层），不是结构限制 |
| 访问受限成员 | 否 | 公开 API/事件 > 组合 > AT/AW 片段（幂等，写入宿主元数据）或反射（Forge 1.20.1 运行时为 SRG 名） |
| 库自带 mixin、固定 ID、固定 namespace 资源、跨副本共享类型或资源、全局协调者 | 是 | 只把全局唯一的部分拆成最小运行时 mod（#002 路线 2，API 只用 JDK 类型），库主体仍 shade；不整体升级为 mod |

对外类型：库的类型不出现在宿主公开 API；与其它 mod 交互只用原版/加载器类型，数据端口只用 JDK 类型（#004）。

SD 预见范围内越界的只有可交互 HUD（§9）；Fabric 26.1 访问 `guiRenderState` 用 AW 或反射即不越界。跨副本"带版本号资源 ID 去重"不可行：需要固定 namespace 与协调者，且 `TextureManager.register` 对同 ID 会关闭旧纹理（1.20.1、1.21.1 源码已核实）。

结论反转的条件：需求变成整合包级统一 UI（统一主题、跨 mod DevTools、共享输入捕获），该场景直接用 AUI；或第三方大量采用且渲染兼容修复频繁，此时只拆出全局部分。"库大、广泛使用、API 稳定"不构成理由：广泛使用放大仲裁风险，API 稳定只是拆出共享 mod 的前提。

### 4. 从 AUI / LDLib2 的取舍

两者均为 LGPL。只借鉴思路，不拷贝代码与 shader 源码：LGPL 代码进入 shade + relocate 产物会给每个宿主带来允许重新链接的义务，与 §3 的嵌入定位冲突。SDF 圆角矩形按公开公式自行实现；本库许可宜为 MIT 或 Apache-2.0（未决，§12）。

借鉴：

- taffy-java 作为布局引擎（LDLib2 生产使用；MIT）。
- `sdf_rect` 式着色器思路：逐角半径、边框、`fwidth` 抗锯齿、预乘 alpha。
- 默认 scissor 并与 `GuiGraphics` scissor 栈合并（LDLib2 `GUIContext`）。
- 圆角裁剪与子树 `opacity` 显式走私有图层 + 遮罩纹理，不用主 target 模板。
- 图层进出前 `flush()`，恢复进入前的 FBO 而非固定回 main。
- 字体是 `Font` 子类且可开关，步进一致保证布局不漂移（LDLib2 `LDLibFonts`）；本方案 MVP 进一步直接用 vanilla `Font` + 资源包 ttf 字体提供器。
- Surface 抽象（主窗口 / 离屏），供未来世界内屏幕。
- 状态伪类改写为状态类再匹配（LDLib2 `HierarchicalStyleMatcher`）。
- 每个 MC 原生元素作为一等标签（`item-slot` 等）。
- 文档化"能力边界 + 特性清单"（AUI `support.md` / `agent.md` 的做法），但不支持项在开发期报错而非静默忽略。
- 版本差异经接口隔离（AUI `spi/` 的做法），但边界放在内核与适配层之间，内核不 import MC。

不采用（与嵌入前提冲突，或存在已知风险）：

- LDLib2 `GUIContext.popVisualLayer` 用原生 `GL30.glBindFramebuffer` 恢复 FBO（`GUIContext.java:147`），绕过 `GlStateManager`，与 Iris 的绑定缓存不同步风险 [推断，未实测]；应走 `GlStateManager` 或 `RenderTarget.bindWrite`。
- 全局 mixin 面与 `defaultRequire: 1`（shade 副本会重复注入）。
- 未重定位的 jar-in-jar 依赖与开放版本区间。
- 每 tick 全局状态改写、主 target 模板缓冲回退、`-g:none`（无行号堆栈，不利于宿主排错）。
- 单个元素类承担树/布局/样式/事件/序列化/编辑器（LDLib2 `UIElement`），本项目按层拆分以便内核独立测试。

### 5. Web 兼容口径

- 目标是"已实现部分用标准写法与语义"，不承诺渲染结果等同浏览器。AUI 本身也没有此承诺。
- 纳入：选择器全集（属性、组合符、结构伪类、状态伪类）、`var()`、伪元素、`calc`、过渡、`@keyframes`、以 GUI 缩放/窗口尺寸重定义的 `@media`、圆角/边框/阴影/线性渐变。
- 默认 `display: flex; flex-direction: column`，不做文档流。
- 富文本只做 React Native 式嵌套文本（单叶子测量），不做完整行内格式化上下文。
- 不纳入：JS、完整表单语义、BOM/`fetch`。mask、clip-path、filter、backdrop 与旋转裁剪延后，且只允许私有图层实现。
- 子树 `opacity` 的正确语义需要私有图层（逐绘制乘 alpha 在子元素重叠时结果错误）。在私有图层可用之前 `opacity` 不纳入，开发期报错；过渡只作用于颜色、尺寸、位移等可逐绘制实现的属性。
- 半兼容成本：已支持特性与标准语义不一致仍会被视为缺陷；以开发期校验替代静默忽略。

### 6. 架构

```text
ui-core            纯 Java；编译期无 MC
  解析   自写严格模板解析器（#007）+ 自写 CSS Syntax L3 分词与选择器解析
  样式   选择器匹配（含优先级）+ 层叠 + var() + 过渡/关键帧
  布局   taffy-java + 文本测量回调
  事件   三阶段派发、焦点、hover
  数据   Value / Derived / Action / Scope（见 ref/SD-ixchel-viewmodel-and-binding-20261026.md）
  输出   绘制命令（DisplayList，内核与适配层的契约）
ui-mc-<时代>        仅 vanilla 类：Painter、TextMeasurer、UiView、宿主辅助
ui-<加载器>-<版本>  约百行胶水：资源重载监听、HUD 层、（日后）shader/pipeline 注册
```

库选型（Maven Central 复核）：

| 库 | 体量 | 备注 |
|---|---|---|
| 自写模板解析器 | 估计约 0.5k 行（推断） | 替代 jsoup 1.21.1（499KB）：其 HTML5 容错建树会静默改写结构（#007）；jsoup 选择器引擎本也无优先级、`:hover`、伪元素，层叠匹配器无论如何须自写 |
| taffy 1.1.5 | 233KB | Java 17 字节码、MIT；runtime 依赖 `fastutil 8.5.12`，宿主 shade 时须排除（MC 自带：1.20.1 为 8.5.9、1.21.1 为 8.5.12；taffy 引用的 11 个 fastutil 类在 8.5.9 中均存在，仅类级核对） |
| 自写 CSS 分词/选择器解析 | 估计 1~2k 行（推断） | 首选：严格解析、错误带位置，符合 #003 开发期报错 |
| 备选：ph-css 7.0.4 | 552KB + ph-commons 11.2.0 1.34MB | ph-css 8.x 改依赖 ph-commons 12 拆分的 6 个以上模块；容错方式偏浏览器宽松，与开发期报错目标相反 |

依赖合计：首选方案约 0.23MB（仅 taffy），选 ph-css 约 2.1MB。

工作量粗估（推断）：内核 8~12k 行（包级拆分见 `ref/D-ixchel-module-breakdown-20261026.md` §2，合计约 11.7k），每个适配层 1~2k 行；对照 AUI common 约 85k 行、LDLib2 `gui/ui` 35k 行；AUI 26.1 渲染适配核心（PIP renderer、`RenderService`、`PipelineRegistry`）约 1.45k 行。

Java 字节码目标：`ixchel-core` 以 Java 17 工具链编译与测试，供 1.20.1（Java 17）与 1.21.1（Java 21）共用（#005）。17 中 switch 模式匹配与 record 模式未转正，`DrawCommand` 等 sealed 层次以 visitor 分派保留穷尽检查；17 的默认字符集依平台（JEP 400 自 18 起改为 UTF-8），文本读写一律显式 UTF-8。

### 7. 桥：通道与距离

纯 Java 内核必然经过一座桥，但它是同 JVM、同渲染线程的接口调用，代价在语义落差而非运行时。不选纯 Java 时桥依然存在，只是散布（AUI common 316 个文件中有 75 个直接 import MC/GL）。

以 1.21.1 为准（已读 vanilla 与 NeoForge 21.1.264 源码）：

| 通道 | MC 侧 | 距离 | 落差 |
|---|---|---|---|
| 输入 | `GuiEventListener` 各方法一一对应 | 小 | 仅在自有 Screen 内可得；HUD 无输入 |
| 视口/时间 | `Screen.width/height`、`getGuiScale()`、`partialTick` | 小 | `@media` 语义需定义 |
| 矩形 | `GuiGraphics.fill(int…)`，仅整数；每次调用末尾 `flushIfUnmanaged()` 即 `endBatch()`（`fillGradient`、`drawString` 同，1.20.1 与 1.21.1 均已读源码） | 小~中 | 浮点布局需像素对齐；逐条调用即逐条提交，Painter 须自写顶点合批 |
| 文本测量 | `Font.width`、`Font.split`、`wordWrapHeight`，固定 `lineHeight` | 中 | 唯一的布局期同步回调；字号靠缩放，无字重、无整形 |
| 文本绘制 | `drawString` 有 int/float 重载 | 小 | 浮点坐标下像素字体发虚 |
| 图片 | `blit`、`blitSprite`（1.20.2+，推断）、`nine_slice`；`innerBlit` 每次调用 `BufferUploader.drawWithShader` | 中 | 原始尺寸异步可知，需重新布局；逐条 blit 即逐条 draw call |
| 裁剪 | `enableScissor(int×4)` 自带栈 | 小 | 仅整数矩形 |
| 自定义着色器 | `ShaderInstance(ResourceProvider, …)` + `RegisterShadersEvent` | 大，随时代变 | 适配层最厚部分；可传入自定义 `ResourceProvider` 从包路径读取 shader |
| 物品 | `renderItem` | 中~大 | 深度、z 偏移、与合批交互 |
| 容器槽位 | `Slot.x/y` 为 `public final int`；槽位命中、物品绘制的钩子可见性因版本而异 | 大 | 见下 |
| 原版控件 | 独立焦点/输入/渲染 | 大 | 不嵌入，自行实现 |

槽位：布局结果写回 `Slot.x/y`（反射绕过 final，#008），vanilla 命中、拖拽与 JEI/EMI/REI/Mouse Tweaks 都以它为准；命中判定通过覆写 protected 的 `isHovering(int,int,int,int,DD)` 感知遮挡与裁剪；1.20.1 的 `renderSlot` 为 private，自绘槽位物品需零面积裁剪且有可见代价，默认用 vanilla 绘制，增强由宿主开关开启（#009）。约束、实验与第三方依赖见 `ref/SDG-ixchel-container-slot-integration-20261026.md`。

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

渲染时代划分（Painter 视角）：

| 时代 | 差异点 |
|---|---|
| 1.20.1~1.20.4 | 顶点 API 为 `vertex().color().endVertex()`；1.20.1 无 `blitSprite`（推断） |
| 1.20.5~1.21.1 | 顶点 API 改为 `addVertex`/`setColor`（AUI 各 target 对照确认）；`GuiGraphics` 高层 API 与上一时代基本一致 |
| 1.21.2~1.21.5 | 着色器体系变化；1.21.5 引入 RenderPipeline |
| 1.21.6~1.21.x | GUI 延迟渲染（GuiRenderState）；FBO/私有图层需走 PIP |
| 26.1+ | `GuiGraphics` 改名 `GuiGraphicsExtractor`；游戏本体不再混淆；Java 25 |

"1.20.1~1.21.1 共用一个时代"只对 `GuiGraphics` 高层 API 成立；一旦自发顶点（SDF 圆角、任意角度渐变），1.20.x 与 1.21.x 即为两份 Painter。

端口随版本的稳定性：`TextMeasurer` 长期稳定；Painter 按上表变化；输入同样按版本变化（1.21.9 起改为事件对象，推断；26.1 `KeyboardHandler#charTyped` 签名变为 `(long, CharacterEvent)`，见 AUI `targets/neoforge-26.1/build.gradle` 注释）；`UiView` 的挂载签名（`render(GuiGraphics, …)`）也随版本变化，宿主挂载代码按版本分。

1.21.6+ 的前瞻：vanilla 延迟 GUI 渲染自行处理物品、文本与层级，Painter 停留在高层图元时迁移成本最低；AUI 在 26.1 把整棵 UI 渲染进全屏 PIP 纹理，物品需另行批处理（`FloatingItemBatch`），近期提交仍在修 PIP 覆盖顺序。因此自定义 shader 与私有图层只用于高层图元表达不了的部分。圆角矩形可能可作为自定义 pipeline 的 GUI 元素提交而不需 PIP（推断，未核对源码）。AUI 的 Fabric 26.1 target 经 accessor mixin 取 `guiRenderState`，NeoForge 26.1 有公开的 `submitPictureInPictureRenderState` 与 `RegisterPictureInPictureRenderersEvent`（按渲染状态类注册，重定位副本类名不同，可共存）。

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
    // 模板解析器自写（#007），无 jsoup 需要 relocate
    dependencies { exclude(dependency("it.unimi.dsi:fastutil")) } // MC 自带
}
```

运行时命名：NeoForge 1.20.5+ 为 mojmap 可直接用；26.1+ 游戏本体不混淆，各加载器均无需重映射（AUI `targets/fabric-26.1/build.gradle` 无 mappings 配置）。只剩 Forge 1.20.1（SRG）与 Fabric ≤1.21.x（intermediary）需按加载器发布已重映射产物，或由使用方先 shade 再重映射（推断，未实测）。

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

在其上提供 `UiScreen`（整页）、`UiHud`（只显示，回调交给加载器 HUD 注册）、`UiContainerScreen<M>`（继承；槽位布局写回 Menu 坐标，见 §7）。组合式嵌入允许已有界面（如 TACZ 工作台）逐块替换，是对采用最有利的一点。

扩展点只开放两个：自定义标签（产出绘制命令或嵌入 MC 内容）与自定义数据源。

shade 带来的约束：

| 约束 | 处理 |
|---|---|
| 库不得携带固定 namespace 的 `assets/` | 默认样式表、shader 源码放包路径下用 `Class.getResourceAsStream` 读取，随 relocate 迁移；shader 以宿主 modid 注册，经自定义 `ResourceProvider` 提供源码 |
| 各 mod 的 `UiView` 互不通用，主题/字形缓存不共享 | 接受；主题由资源包覆盖 `assets/<modid>/ui/` |
| 版本由使用方钉死 | 只需源码级 API 稳定 |
| 多版本使用方 | 模板/样式/ViewModel 共享，挂载代码按版本分 |
| MC 自带的传递依赖 | fastutil 等不 shade、不重定位 |
| 未重定位的依赖包、依赖自带的 `module-info.class` | 每个依赖一条 relocate；排除 `module-info.class`（含 `META-INF/versions/*/`）与签名文件；Forge 模块层下两个 jar 导出同一包会启动失败（经验，未本地复现） |
| ServiceLoader 注册文件 | `mergeServiceFiles()` |
| `minimize()` | 不用：会删除仅经反射或 ServiceLoader 访问的类 |
| 包内资源路径 | 用相对路径 `getResourceAsStream("default.css")`，不依赖 relocator 改写字符串常量 |
| 运行时命名（Forge 1.20.1） | 先 shade 再对 shadowJar 整体 reobf 一次（`obfuscation.reobfuscate`）；库模块发布 mojmap 还是 SRG 待 §11 第 3 步决定（对已是 SRG 的类再 reobf 推断为空操作，差别在消费方开发环境是否需先重映射） |
| 版本锁定 | 消费方构建对库用 `strictly`；同一库不再 jarJar/include；消费方发布物不把库及其依赖列为运行时依赖 |
| GPU 资源 | FBO、遮罩纹理惰性分配、用完释放，避免按副本数叠加 |

### 9. mixin 策略

- mixin 不是禁区，但与"多份共存"冲突：配置由宿主声明，N 份重定位副本会对同一 vanilla 方法注入 N 次，导致全局状态争夺、加载顺序相关的冲突，`@Overwrite`/`@Redirect` 直接互斥。默认产物零 mixin。
- AT / access widener 结果幂等，可用，但库模块编译期与宿主 jar 运行期须各有一份同内容的 AT，宿主漏配时运行到相关代码才抛 `IllegalAccessError`。可能的需求点：`Slot.x/y` 去 final（当前用反射，#008；Java ≥ 26 时改 AT）、26.1 Fabric 上访问 `guiRenderState`。
- 不用 mixin 的覆盖面（NeoForge 21.1.264 核对）：`ScreenEvent.MouseButtonPressed.Pre`、`ScreenEvent.KeyPressed.Pre`、`InputEvent.MouseButton.Pre`、`InputEvent.MouseScrollingEvent`、`RenderGuiLayerEvent.Pre` 均可取消；`InputEvent.Key` 不可取消。透明 Screen（`isPauseScreen` 为 false、不画背景）可做可交互覆盖层，代价是玩家不能移动。
- 唯一必须 mixin 的场景是"边移动边操作 HUD"（光标锁定与键盘拦截）。届时两条路线：
  1. 库提供静态钩子（`UiInputHooks`），mixin 由宿主在自己的配置中编写并承担冲突取舍。
  2. 单独发布不重定位的可选运行时 mod，注入点 `require: 0` 并可降级；各 shade 副本通过不重定位、仅用 JDK 类型的小 API 包（如 `ServiceLoader`）发现它。该运行时 mod 本身回到前置的分发结构（§3.1），仅在真实需求出现时建设。
- 若使用 mixin：不用 `@Overwrite`/`@Redirect`，优先 `@Inject` HEAD 条件取消或 MixinExtras `@WrapOperation`；平时惰性（参考 LDLib2 `RawInputGate`）；`defaultRequire: 0`；不改主 target，不做每 tick 全局改写。

### 10. 数据需求声明

模板只引用名字，ViewModel 用 `source` / `state` / `derive` / `action` 声明来源、本地状态、派生与动作，`Scope` 随宿主生命周期释放。模板表达式只做格式化与条件（语言文档 §3.6），复杂逻辑写成 `Derived`；模板引用未声明名字或使用不支持的 CSS 属性时开发期报错；数据源可接已有客户端缓存，不强制任何复制层（#004）。

语法、运行语义、实现、原型验证与待定项见 `ref/SD-ixchel-viewmodel-and-binding-20261026.md`。

数据层是"价值"层中对使用者可见、也是本项目自身需求的重点；MVP 验证须以 TACZ 场景中样板代码相对手写原版的实际减少量为准（验收矩阵见上述文档 §7）。

### 11. MVP 范围与验证顺序

| 做 | 不做 / 延后 |
|---|---|
| 两个平台（#005）：Forge 1.20.1 做完整 MVP，NeoForge 1.21.1 只做对齐最小集（弹药 HUD + 一个 Screen） | 其余平台与渲染时代 |
| Screen + 只显示 HUD | 可交互 HUD |
| §5 纳入的 CSS 特性 | JS、表单、BOM、子树 `opacity` |
| 边框、图片/九宫格、物品、文本、scissor；圆角/阴影/渐变的实现路径须先定（见下） | mask、clip-path、filter、backdrop、旋转裁剪 |
| ViewModel + 客户端状态/Menu 数据源 | 复制层 |
| 开发期校验与热重载 | 可视化编辑器、DevTools |

平台取舍（#005）：第一消费方 TACZ-RESPAWN 为 Forge 1.20.1 / Java 17，因此 1.20.1 做完整 MVP；NeoForge 1.21.1 跑同一套验收模板，用来验证内核与适配层的边界能跨加载器（Forge/NeoForge）、跨顶点 API 时代、跨运行时命名（SRG/mojmap）成立。两个平台都做完整 MVP 会让功能工作量翻倍，对验证没有额外收益。

圆角/阴影/渐变的实现路径（对比见 `ref/D-ixchel-module-breakdown-20261026.md` §6）：推荐但未定的是 C——CPU 生成覆盖率/SDF 纹理放入单张图集，自写 `POSITION_TEX_COLOR` 顶点做九宫格，纹理以宿主 modid 注册 `DynamicTexture`；无需注册 shader 且有抗锯齿。备选 A 为 CPU 三角化 + `POSITION_COLOR`（无 AA），B 为 SDF shader（MVP 即需 shader 注册，Painter 随顶点 API 时代分叉）。

验收场景为 TACZ 四个场景（`ref/SD-ixchel-viewmodel-and-binding-20261026.md` §1）：先弹药 HUD 与 Z 键改装 Screen，背包式改装验证 Slot 通道。里程碑见 `ref/D-ixchel-module-breakdown-20261026.md` §9。

验证顺序：

1. 纯 Java 内核原型，用单元测试从弹药 HUD 模板产出绘制命令，不碰 MC。
2. 适配层在游戏内绘制（两个平台），确认零 mixin、不改主 target，并与 Iris、ImmediatelyFast 共存显示正常。
3. 在 Forge 1.20.1 上重定位 shade 进两个测试 mod，故意使用不同版本，固定发布配方：relocate 覆盖全部依赖、排除 `module-info`、shadowJar 接 reobf，并决定库模块发布 mojmap 还是 SRG 产物。两份副本的类级共存由重定位保证；此步检验的是配方，以及类以外的全局泄漏（资源 ID、GL 状态），后者在有 shader/纹理注册代码后才有测试对象。现有 testmod 以 sourceSet 并入运行，不覆盖此链路。
4. 改装 Screen 验证事件、`Action` 与作用域生命周期，并与同场景手写原版实现对比代码量。

### 12. 风险与未决问题

- 需求未证实：嵌入只满足"不引入前置"的前提，使用理由须来自编写体验与数据层；"手写原版 GUI 的作者是否愿意多 shade 一层换取 HTML/CSS + ViewModel"无证据。第一消费方必须是自有 mod。
- 功能覆盖面会长期小于 AUI/LDLib2；本项目只按自身需求取舍，需要更全功能且能接受前置的场景直接用它们。
- 编写体验与数据层须在 TACZ 场景中相对手写原版体现实际收益，否则引入一层 UI 库不划算。
- 交互侧无 JS，LLM 生成的带脚本页面不可直接使用，需文档化边界。
- 1.21.6+ 延迟 GUI 渲染下自定义着色器与 FBO 需走 PIP 或自定义 pipeline，物品与文本的层级交错是主要难点（AUI 26.1 已踩坑）。
- 本库许可未定，倾向 MIT 或 Apache-2.0；须确保不引入 LGPL 代码。
- 未决：1.20.1 库模块发布 mojmap 还是 SRG 产物（§11 验证第 3 步）；圆角/阴影/渐变是否正式采用路径 C（§11）；模板绑定语法中的提议项与 i18n 写法、来源轮询频率、动作重入策略（见数据绑定文档 §8）；ViewModel 结构描述格式（供预览与 VS Code 补全）；`@media` 的 MC 语义；jar-in-jar 模式是否作为正式支持的分发方式。

### 13. 证据来源

- AUI 仓库（本地 fork，HEAD `7572f92`）：`archived/guide/support.md`、`archived/agent.md`、`common/build.gradle`、`common/src/main/java/com/sighs/apricityui/{render/Mask.java,spi/*}`、`targets/*/build.gradle`、`targets/neoforge-26.1/src/main/java/com/sighs/apricityui/client/gui/{ApricityGuiLayers.java,pip/ApricityUiPipRenderer.java}`、`targets/fabric-26.1/.../mixin/accessor/GuiGraphicsExtractorAccessor.java`、`CHANGELOG.md`、`theme/*/*.css`；Modrinth API（项目 `UfPdUeGy`）。
- LDLib2 仓库：`src/main/java/com/lowdragmc/lowdraglib2/gui/ui/rendering/{GUIContext,UIVisualLayer,OffscreenSurface}.java`、`gui/ui/UIElement.java`、`gui/ui/style/{StyleSelector,HierarchicalStyleMatcher}.java`、`gui/LDLibFonts.java`、`core/mixins/ui/*.java`、`ldlib2.mixins.json`、`ldlib2-ui.xsd`、`assets/ldlib2/lss/*.lss`、`build.gradle`、`gradle.properties`、`LICENSE`。
- MC 1.21.1（官方映射，vanilla 与 NeoForge 21.1.264 源码）：`Slot`、`AbstractContainerScreen`、`GuiGraphics`、`ShaderInstance`、`Font`、`GuiEventListener`。
- NeoForge 21.1.264：`InputEvent`、`ScreenEvent`、`RenderGuiLayerEvent`、`RegisterShadersEvent`。
- TACZ-RESPAWN：`gradle.properties`（Forge 1.20.1）。
- taffy-java：https://github.com/vfyjxf/taffy-java ；Maven Central：jsoup 1.21.1（已弃用，#007）、ph-css 7.0.4 / 8.2.3、ph-commons 11.2.0 / 12.5.1、taffy 1.1.5（pom）。
- ModDevGradle Legacy：https://github.com/neoforged/ModDevGradle/blob/main/LEGACY.md （同版本号的 `net.neoforged.moddev.legacyforge` 插件；`mcpVersion` 原版模式；`jar` 自动 reobf、`obfuscation.reobfuscate` 可用于 shadowJar）。
