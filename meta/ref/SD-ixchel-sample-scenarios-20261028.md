---
tags: [survey, design]
date: 2026-10-28
origin: 现有 sample 都围绕 TACZ 一个消费方展开，容易限制设计思路；按大型科技、魔法、冒险整合包和知名 mod 的常见 UI 情境重新规划 sample
related: [DECISIONS.md#005, DECISIONS.md#009, ref/D-ixchel-module-breakdown-20261026.md, ref/SDG-ixchel-container-slot-integration-20261026.md]
---

# Ixchel UI: Sample Scenarios

## Summary

sample 改为 9 个场景，对标常见 mod UI 家族：角色面板、机器面板、存储终端、掉落物雷达、环境 HUD、配置面板、任务树、指南书、增强 tooltip。只做 Forge 1.20.1（#005）。sample 同时驱动路线图：每个场景列出需要的新库能力，按 §3 的顺序实现；场景的检查（§4）通过，即为对应能力的验收。现有 refit 保留作性能基线，status、pickup、waypoints 并入新场景，背包改装在角色面板与机器面板落地后删除。美术用原版资源，缺的图标自画 16×16。

## Detail

### 1. 现状与缺口（2026-10-28，`master` 44d63f7）

- 元素：`div` `span` `img` `style`，mc 层原生元素 `item`、`item-slot`。没有按钮或表单元素，点击都写在 `div` 上。
- CSS：盒模型、flex、`position: relative|absolute`、`overflow`、背景色与背景图（精灵图区域、平铺）、`color`、`font-size`、transition。`display` 只有 `flex|none`。没有 border、opacity、z-index、transform、text-align、white-space、text-overflow。
- 伪类：`:hover` `:active` `:focus`。没有 `:disabled` `:checked`。
- 输入：`UiScreen` 与 `UiContainerScreen` 只转发了 `keyPressed`、点击和滚轮，没有 `charTyped`，也没有 `mouseDragged`。
- 文本：单个文本节点内可按宽度换行（`TextShaper`），跨 `span` 的行内流式排版不支持。
- 宿主：HUD、`UiScreen`、`UiContainerScreen`。没有 tooltip 宿主，也没有世界空间宿主。

### 2. 场景

每节依次写：对标、宿主、数据来源、交互与视觉、需要的新能力、原版资源。

#### S1 角色面板

- 对标：Apotheosis、PMMO、Curios 一类冒险/RPG 包的角色界面。
- 宿主：`UiContainerScreen` + `InventoryMenu`，自己的快捷键，不替换原版 E 背包（避免 #009 的冲突面）。
- 数据：盔甲槽、副手槽、背包槽（真实槽位），`AttributeMap`（最大生命、护甲、韧性、攻击伤害、攻速、移速、幸运），`MobEffectInstance` 列表（剩余时间、等级），统计页走 `ServerboundClientCommandPacket` `REQUEST_STATS`，异步拿到 `StatsCounter`。
- 交互与视觉：页签（装备 / 属性 / 效果 / 统计），属性悬停时显示说明 tooltip，玩家纸娃娃跟随鼠标，效果快结束时闪烁（取代 status HUD 中的效果列表）。
- 新能力：`<entity>` 原生元素（mc 层，封装 `InventoryScreen.renderEntityInInventoryFollowsMouse`）、border、自定义元素上的 tooltip、页签切换。
- 资源：`textures/gui/container/inventory.png`、`textures/mob_effect/*`、`textures/item/empty_armor_slot_*`。

#### S2 机器面板

- 对标：Mekanism、Thermal、GregTech 的机器界面。
- 宿主：sample 注册一个“动力切石机”方块，带 BlockEntity 与 Menu，界面用 `UiContainerScreen`。
- 数据：服务端 → 客户端走 `ContainerData` int（进度、最大进度、能量、容量、水量、所选配方、状态、红石模式、六面 IO 模式）；客户端 → 服务端走 `menu.clickMenuButton`（原版切石机就用它），不另写网络包。
- 机制：燃料槽烧原版燃料产 FE，水桶倒入水槽（1000 mB/桶，每次加工消耗），输入槽、输出槽，配方取原版切石配方。
- 交互与视觉：配方选择网格（按当前输入过滤）、进度箭头、竖向能量条与水槽（悬停显示 `x / y FE`、`x / y mB`）、状态文字（空闲 / 缺能量 / 缺水 / 输出已满 / 红石禁用）、红石模式按钮、侧面 IO 配置页签（六面各自循环切换模式，页签用 transition 滑出）。
- 新能力：方块图集精灵作为背景（`minecraft:block/water_still` 加群系色 tint）、border、`ContainerData` 的绑定写法（库提供辅助还是留给宿主，实现时定，见 #004）。
- 资源：`textures/gui/container/stonecutter.png`、`furnace.png`（箭头、火焰），能量条用纯色加 border，必要时自画 16×16。

#### S3 存储终端

- 对标：AE2、Refined Storage、Tom's Simple Storage 的终端。
- 宿主：手持 sample 物品“终端”右键打开。`UiContainerScreen` 的 Menu 只放玩家背包槽，物品网格是虚拟的，不对应槽位。
- 数据：服务端扫描半径 R 内带物品能力的 BlockEntity，汇总后以快照加增量用自定义包下发；取出与存入走客户端 → 服务端包。
- 交互与视觉：搜索框（`@modid` 前缀按命名空间过滤）、按名称 / 数量 / mod 排序及正反序、数量缩写（1.2k、3.4M）。左键取一组，右键取半组，Shift 直接进背包；光标上拿着物品时点网格即存入。网格悬停显示物品 tooltip。
- 规模：基准用全部原版物品各一组（约 1300 种）。
- 新能力：单行文本输入（光标、选区、剪贴板、`charTyped`）、虚拟化网格（只为可见行建节点，滚动位置可绑定）、虚拟网格与真实槽位共存时的拿取语义。

#### S4 掉落物雷达

- 对标：冒险包里的掉落物拾取与高亮类 mod。
- 宿主：HUD 加 `UiScreen`。由 pickup toasts 演化而来，拾取记录保留为 HUD 的一个子区域。
- 数据：客户端扫描附近 `ItemEntity`，用 `WorldProjection` 投影到屏幕。
- 交互与视觉：HUD 在掉落物上方显示名称、数量、距离，屏幕外的掉落物在屏幕边缘显示箭头。列表界面按距离排序，显示方向箭头，可按稀有度过滤；“拾取”按钮发包，服务端在距离内才执行。
- 新能力：方向箭头的旋转（transform rotate，与 S7 共用），否则只能用 8 方向精灵图。`each` 按实体 id 加 key，排序每 tick 变化时测开销。

#### S5 环境 HUD

- 对标：Jade、WTHIT 的准星信息，Serene Seasons、Tough As Nails 的环境状态，罗盘条。
- 宿主：HUD。status HUD 的生命、护甲、饥饿、经验并入这里；waypoints 并入罗盘条，列表界面保留。
- 数据：`Minecraft.hitResult`（方块：名称、命名空间、挖掘工具提示、方块状态属性；实体：生命条、护甲），群系、时间、天气、脚下光照、坐标、朝向。
- 交互与视觉：准星信息面板尺寸随内容变化；横向罗盘条按偏航角平移，路径点标记跟着走。
- 新能力：基本都已具备，主要测每 tick 数据变化时的帧耗时。小地图需要动态纹理元素，列为扩展（§5）。

#### S6 配置面板

- 对标：Cloth Config、Configured。
- 宿主：`UiScreen`，通过 `ConfigScreenHandler.ConfigScreenFactory` 从 mod 列表打开，此时没有世界、`player == null`。游戏内也能打开。
- 数据：sample 的真实 `ForgeConfigSpec`，覆盖 bool、int 范围、double 范围、enum、string、`List<String>`、颜色（int 十六进制）。
- 交互与视觉：左侧分类栏、搜索过滤配置项。每项有标签、注释 tooltip、控件、恢复默认按钮、已修改标记。整体有保存、取消、撤销。数值越界时标红并禁用保存，需要重启的项给出提示，Tab 切换焦点。
- 新能力：表单控件（开关、滑条、数字输入、文本输入、下拉、列表编辑、色块），`:disabled`/`:checked`；脱离父裁剪的弹出层（下拉、确认框），需要 z-index 或顶层；Tab 焦点顺序；无世界上下文。控件用标准写法（`<input type>`、`<select>`、`<button>`）还是库自定义元素，实现时按 #003 定。

#### S7 任务 / 技能树

- 对标：FTB Quests、PMMO 与 Ars Nouveau 的技能/字形树。技能树同属一类，不单独做 sample。
- 宿主：`UiScreen`。
- 数据：sample 资源里的任务图 JSON（节点坐标、前置、图标物品、任务类型“持有 N 个某物品”）。状态（锁定 / 可做 / 已完成）由客户端按背包计算，领奖发包给服务端校验。
- 交互与视觉：左侧章节列表；画布可拖拽平移、滚轮缩放（0.5–2）；节点之间连直线，悬停显示 tooltip，点击打开详情侧栏（侧栏不随画布变换）。约 100 个节点。
- 新能力：transform（translate、scale、rotate），命中测试按逆变换；画线（旋转细矩形，或原生线元素）；`mouseDragged`。
- 资源：`textures/gui/advancements/backgrounds/stone.png`、`textures/gui/advancements/widgets.png`。

#### S8 指南书

- 对标：Patchouli、GuideME。
- 宿主：`UiScreen`。
- 数据：书页就是 sample 资源里的 Ixchel 模板，目录由资源列表生成，热重载天然可用。
- 交互与视觉：双页书（两张 `textures/gui/book.png` 并排）、目录、条目间链接与“返回”历史、翻页（按钮与方向键）。正文有粗体、颜色、行内物品图标、图片，嵌入 3×3 合成配方（备选原料按时间轮换）。分页由作者用显式页元素划分，不自动分页。
- 新能力：跨 `span` 与行内元素的流式排版（`display: inline` 或等价物），行内链接的命中测试。

#### S9 增强 tooltip

- 对标：Legendary Tooltips、Apotheosis 的词缀 tooltip。
- 宿主：新增 tooltip 宿主，在 `RenderTooltipEvent.Pre` 中取消原版并绘制文档。文档复用，悬停物品换掉时只换 VM 数据。
- 数据：悬停的 `ItemStack`、已装备的同部位物品。
- 交互与视觉：按稀有度着色的边框、标题、图标、附魔列表、属性与当前装备对比（绿增红减）、耐久条，按住 Shift 显示详情。尺寸随内容，贴近光标，碰到屏幕边缘时翻转。
- 新能力：tooltip 宿主。替换原版 tooltip 与其它 mod 冲突面大，由宿主 API 参数开启（#009）。性能：鼠标扫过网格时每帧都可能换物品。

### 3. 实现顺序

原则：先做共用的小能力，再按能力族推进；每个场景先打通最薄的一条路径。

| 阶段 | 场景 | 本阶段引入的库能力 |
|---|---|---|
| 0 | — | border、opacity、text-align、`white-space: nowrap` + `text-overflow: ellipsis`、`:disabled`、自定义元素 tooltip |
| A | S5 环境 HUD、S4 掉落物雷达 | 基本已有；status、pickup、waypoints 在这里迁移 |
| B | S1 角色面板、S2 机器面板 | `<entity>`、方块图集精灵 + tint、`ContainerData` 绑定；完成后删除背包改装 |
| C | S3 存储终端、S6 配置面板 | `charTyped` 与文本输入、虚拟网格、表单控件、弹出层 / z-index、Tab 焦点、无世界上下文 |
| D | S7 任务树、S4 方向箭头 | transform 与逆变换命中、画线、`mouseDragged` |
| E | S8 指南书、S9 增强 tooltip | 行内流式排版、tooltip 宿主 |

现有 sample 的处理：
- `refit` / `RefitBench` 不动，作为 M2–M4 的性能基线。
- `status` 并入 S5（效果列表另进 S1），`pickup` 并入 S4，`waypoints` 并入 S5。
- `backpack` 在 S1、S2 覆盖 `UiContainerScreen` 之后删除；L2 兼容检查（`-Pslotcompat`）改挂到 S2。

阶段 0、A 结果（2026-10-28）：
- 阶段 0 全部落地：`title` 属性走原版 tooltip（mc 层 `ItemTooltips` 改名 `Tooltips`）；`disabled` 为全局布尔属性，可 `bind:disabled`。
- S5 为 `env`，S4 为 `loot`（HUD + 列表界面，U 键）。`pickup` 已删除，拾取提示并入 S4 HUD。`status` 的生命、护甲、饥饿、经验已移入 S5，只剩效果列表，等 S1 接走后删除。路径点数据移到共享的 `waypoints/Waypoints`（不可变列表，变更即替换），列表界面与 S5 罗盘共用。
- S4 方向箭头暂用 8 方向 Unicode 箭头字形（unifont 回退可显示），不画精灵图；阶段 D 有 rotate 后再换。
- 帧耗时（开发机 AMD 核显，`FrameTimer` 计 Pre→Post 的 CPU 时间）：S5 HUD 静止中位数 / p95 约 1.1–1.4 / 2.0–3.1 ms，每 tick 移动转向约 1.2–2.1 / 3.2–3.4 ms；加罗盘前移动约 0.9 / 1.8 ms。S4 列表 27 行静止约 3.1–3.6 / 4.1–6.9 ms，每 tick 重排约 4.0–4.1 / 6.3–6.6 ms。检查预算取 p95 的约两倍（S5 6 ms，S4 14 ms）。
- 由此的发现（未处理）：静止与变化帧耗时相当，开销在每帧回放显示列表，不在布局；`McPainter` 的裁剪只用 scissor，被裁掉的命令（罗盘条两侧刻度、列表滚出视口的行及其物品）照样提交。若要降，先在 Painter 剔除完全落在裁剪外的命令。

### 4. 自动化检查

#### 4.1 现状

- `sample/.../check/Script`：步骤按固定 tick 间隔排队，提供 `expect`、`expectServer`、`onServer`、`shot`、`hoverAt`、`clickAt`。用 `-Pbench=<名>` 一次启动只跑一个检查，结束后退出，结果只写在日志里。
- `testmod/shade-a/.../ContainerCheck` 自己又写了一套步骤队列，与 `Script` 重复。
- `HudBench`、`RefitBench` 是计时基准，与功能检查共用 `-Pbench`。
- CI 只跑 `./gradlew build`。

2026-10-28 起，§4.2 的命名、驱动 API（`type`、`drag` 除外，分别随阶段 C、D 加入）、顺序运行与 `report.txt` 已落地；另加一条约定：两步之间必先画一帧（世界刚载入时 tick 会连续补跑而不出帧，文档尚未布局）。shade-a 的 `ContainerCheck` 仍独立。

#### 4.2 目标流程

- 命名：`-Pbench=<名>` 只留给计时基准，功能检查改用 `-Pcheck=<名>[,<名>…]|all`。
- 驱动 API 改为按选择器操作：`click(sel)`、`hover(sel)`、`scroll(sel, dy)`、`drag(sel, dx, dy)`、`key(k)`、`type(text)`、`waitUntil(cond, maxTicks)`。坐标取自 `querySelectorAll` 得到的 box。固定 tick 等待只用在确实要等时间流逝的地方（例如效果倒计时）。
- 一次启动按顺序跑多个检查。每个检查在开始时自行重置状态（清背包、清空 sample 数据），不依赖前一个检查留下的状态。
- 结果：日志行格式不变，另外写 `run/checks/report.txt`（每项 PASS/FAIL 与汇总）。截图留在 `run/screenshots/`，只供人工核对，不做像素比对。
- 代码位置：先在 sample 内整理 `check` 包。shade-a 的 `ContainerCheck` 在 relocate 后也要用，等第二个消费方真正需要时再抽成开发期共享源码，不发布（AGENTS §4）。
- 约定：每个场景都有检查；每个检查项写明它要发现的失败（AGENTS §6）。§2 中各场景的交互描述就是它的检查清单起点。
- CI：暂不跑游戏内检查，每次都要启动客户端并截图，成本太高（2026-10-28 决定）。core 的黄金测试继续放在 `build` 里。

### 5. 扩展，只记录不排期

- 世界空间 UI（Neat、ToroHealth 一类的头顶血条、伤害数字）：新宿主类型，要把文档渲染到世界空间的公告板上，并处理深度与光影兼容。
- 小地图 / 全屏地图（Xaero、JourneyMap）：需要动态纹理元素（每帧或按区块更新的 `NativeImage`）。
- 径向轮盘（Iron's Spells、Create 工具箱）：按角度命中，按住键时能否不阻塞移动受 #002 限制。
- 主菜单替换（FancyMenu 类整合包）：无世界上下文，S6 会先验证。
- NPC 对话 / 交易（Easy NPC、MCA）：打字机文本、服务端状态机。
- 图表（Mekanism 能量曲线一类）：需要折线或面积绘制命令。
