---
tags: [survey, design]
date: 2026-10-28
origin: 现有 sample 都围绕 TACZ 一个消费方展开，容易限制设计思路；按大型科技、魔法、冒险整合包和知名 mod 的常见 UI 情境重新规划 sample
related: [DECISIONS.md#005, DECISIONS.md#009, ref/D-ixchel-module-breakdown-20261026.md, ref/SDG-ixchel-container-slot-integration-20261026.md]
---

# Ixchel UI: Sample Scenarios

## Summary

sample 改为 9 个场景，对标常见 mod UI 家族：角色面板、机器面板、存储终端、掉落物雷达、环境 HUD、配置面板、任务树、指南书、元素 tooltip。只做 Forge 1.20.1（#005）。sample 同时驱动路线图：每个场景列出需要的新库能力，实现顺序与状态见 `ref/D-ixchel-roadmap-20261029.md`；场景的检查（§4）通过，即为对应能力的验收。现有 refit 保留作性能基线，status、pickup、waypoints 并入新场景，背包改装在角色面板与机器面板落地后删除。美术用原版资源，缺的图标自画 16×16。

## Detail

### 1. 现状与缺口（2026-10-28，`master` 44d63f7）

- 元素：`div` `span` `img` `style`，mc 层原生元素 `item`、`item-slot`。没有按钮或表单元素，点击都写在 `div` 上。
- CSS：盒模型、flex、`position: relative|absolute`、`overflow`、背景色与背景图（精灵图区域、平铺）、`color`、`font-size`、transition。`display` 只有 `flex|none`。没有 border、opacity、z-index、transform、text-align、white-space、text-overflow。
- 伪类：`:hover` `:active` `:focus`。没有 `:disabled` `:checked`。
- 输入：`UiScreen` 与 `UiContainerScreen` 只转发了 `keyPressed`、点击和滚轮，没有 `charTyped`，也没有 `mouseDragged`。
- 文本：单个文本节点内可按宽度换行（`TextShaper`），跨 `span` 的行内流式排版不支持。
- 宿主：HUD、`UiScreen`、`UiContainerScreen`。没有世界空间宿主。tooltip 只有 `item` 的原版物品 tooltip。

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
- 数据：物品只走 Menu 的原版 Slot 事务；机器状态（进度、能量、水量、燃烧、所选配方、状态、红石模式、六面 IO 模式）由 sample 的复制层下发为 `MachineState` 快照，设置由类型化意图包上行（见阶段 B 结果）。
- 机制：燃料槽烧原版燃料产 FE，水桶倒入水槽（1000 mB/桶，每次加工消耗），输入槽、输出槽，配方取原版切石配方。
- 交互与视觉：配方选择网格（按当前输入过滤）、进度箭头、竖向能量条与水槽（悬停显示 `x / y FE`、`x / y mB`）、状态文字（空闲 / 缺能量 / 缺水 / 输出已满 / 红石禁用）、红石模式按钮、侧面 IO 配置页签（六面各自循环切换模式，页签用 transition 滑出）。
- 新能力：方块图集精灵作为背景（`minecraft:block/water_still` 加群系色 tint）、border。
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

#### S9 元素 tooltip

- 对标：mod 界面中的说明性悬停提示（按钮用途、配置项说明、物品附加信息）。
- 宿主：`UiScreen`。
- 数据：几个示例 `ItemStack`；一个随时间变化的值（如冷却倒计时）。
- 交互与视觉：一页内各类元素各自带 `<ix-tooltip>`：
  - 按钮：用途说明加按键提示（`inline-block` 小块）；
  - 表单控件：`<input>`、未展开的 `<select>`；
  - 正文中的行内 `<span>`、`<a>` 术语；
  - `<mc-item>` 三种模式并列：不写（原版）、纯自定义、自定义内含 `<mc-tooltip-lines>` 再加行；
  - 滚动列表中贴边的元素：tooltip 不被裁剪，碰屏幕边缘翻转；
  - 嵌套元素各有 tooltip 时最深者优先；与 `title` 纯文本并排对照；
  - 组件把 `<slot>` 放进 `<ix-tooltip>`；
  - tooltip 内容含绑定，显示期间实时变化；
  - 对话框内元素的 tooltip 画在对话框之上。
- 新能力：`<ix-tooltip>`、`<mc-tooltip-lines>`（路线图 E2）。S1、S6、S7 的迁移只覆盖说明文字，S9 覆盖其余元素种类与边界。

### 3. 实现与结果

实现顺序、各阶段范围与状态见 `ref/D-ixchel-roadmap-20261029.md`。本节记录已完成阶段的结果。

现有 sample 的处理（2026-10-28 重构后）：
- `refit` / `RefitBench`、`HudBench`、`probe`、弹药 HUD 已删除，不再保留手写原版对照基准；性能基线由 S4、S5 检查里的 `FrameTimer` 预算承担。
- `status` 并入 S5（效果列表进 S1），`pickup` 并入 S4，`waypoints` 并入 S5；`backpack` 已删除，L2 兼容检查（`-Pslotcompat`）挂在 S2。
- sample 改为普通 mod 布局（`registry/`、`network/`、`client/`、每场景一包一公开入口），检查是独立的第二个 mod `ixchelsamplecheck`（`src/check`），只经公开入口驱动；布局见 GROUND。这次重构暴露并处理的宿主 API 问题：模板 + CSS 列表三元组、HUD 注册六参数加单独的投影钩子、键盘只能 Java 侧挂监听、record 为排除 `ItemStack` 手写 `equals`；`bind:text`/`bind:style` 的自动转换库早已支持，是 sample 用法陈旧。

阶段 0、A 结果（2026-10-28）：
- 阶段 0 全部落地：`title` 属性走原版 tooltip（mc 层 `ItemTooltips` 改名 `Tooltips`）；`disabled` 为全局布尔属性，可 `bind:disabled`。
- S5 为 `env`，S4 为 `loot`（HUD + 列表界面，U 键）。`pickup` 已删除，拾取提示并入 S4 HUD。`status` 的生命、护甲、饥饿、经验已移入 S5，只剩效果列表，等 S1 接走后删除。路径点数据移到共享的 `waypoints/Waypoints`（不可变列表，变更即替换），列表界面与 S5 罗盘共用。
- S4 方向箭头暂用 8 方向 Unicode 箭头字形（unifont 回退可显示），不画精灵图；阶段 D 已改为 rotate（见阶段 D 结果）。
- 帧耗时（开发机 AMD 核显，`FrameTimer` 计 Pre→Post 的 CPU 时间）：S5 HUD 静止中位数 / p95 约 1.1–1.4 / 2.0–3.1 ms，每 tick 移动转向约 1.2–2.1 / 3.2–3.4 ms；加罗盘前移动约 0.9 / 1.8 ms。S4 列表 27 行静止约 3.1–3.6 / 4.1–6.9 ms，每 tick 重排约 4.0–4.1 / 6.3–6.6 ms。检查预算取 p95 的约两倍（S5 6 ms，S4 14 ms）。
- 由此的发现（未处理）：静止与变化帧耗时相当，开销在每帧回放显示列表，不在布局；`McPainter` 的裁剪只用 scissor，被裁掉的命令（罗盘条两侧刻度、列表滚出视口的行及其物品）照样提交。若要降，先在 Painter 剔除完全落在裁剪外的命令。

阶段 B 结果（2026-10-28）：
- S1 为 `character`（G 键），宿主是玩家自己的 `InventoryMenu`（容器 id 0，不需要打开包）。页签用 `display: none` 切换，隐藏页上的槽位仍算已放置；修了 `UiContainerScreen` 对未显示槽位报尺寸错误的问题。`status` 已删除。
- `<entity>`：mc 层原生元素，载荷为 `LivingEntity` 或其 `Supplier`；Screen 把指针传给 `McPainter.paint`，实体朝指针看，HUD 中朝前。
- 图集精灵：`url("sprite:<id>")` 指方块图集里的精灵（core 只认前缀，mc 层解析，逐次查找以跟随资源重载和动画）。tint 用标准写法 `background-blend-mode: multiply`：背景色乘到图上、自身不再画；与 CSS 的差异是图透明处露出父元素而非背景色。
- S2 状态同步不用 `ContainerData`（2026-10-28 改）：`DataSlot` 是原版为小数值界面设计的通道（按 16 位 short 同步，内存连接不经编码所以单人测不出），且把同步绑在 Menu 会话上。sample 改按引擎范式（Kumiko 复制层 + 意图 RPC 思路的最小实现，只在 sample，库仍不含复制层）：`network/Replication` 以 kind + 方块坐标为身份，客户端 `watch` 声明兴趣，服务端每 tick 末对每个观察者取可见快照（record，`equals` 比较）有变化才发，不可见（拆除、超出 8 格、登出）即结束观察、客户端副本读 `null`；UI 只读副本，经意图包（`PickRecipePacket`、`SetRedstonePacket`、`SetSidePacket`，设值而非切换，服务端校验可达与取值）修改。无客户端预测：点击效果滞后一次往返，检查在连续点击之间等待复制结果。新增一种复制对象只写快照 record、编解码与可见规则。
- S2 为 `machine`，侧面 IO 模式只存储与显示，不接管道。backpack 已删除，`-Pslotcompat` 改跑 `-Pcheck=machine`（JEI、EMI 均通过）。
- 缺口：backpack 带走了"`IXCHEL` 绘制下滚动列表中的槽位跟随滚动"这一开发期检查，S1、S2 都没有可滚动的真实槽位；`IXCHEL` 绘制本身仍由 shade-a 生产检查覆盖，但不含滚动。已由 S3 补上（见阶段 C1 结果）。

阶段 B 剩余：无（已合入 #46）。

阶段 C1 结果（2026-10-29）：
- core：标准 `<input>`（光标、选区、剪贴板，剪贴板经端口 `Clipboard` 由宿主提供）、双向 `bind:value`、`bind:scrolltop`（写入是滚动请求，布局截取到有效范围后回写）、鼠标修饰键精确过滤、`click` 只认左键（#012）；mc：`UiContainerScreen`/`UiScreen` 转发 `charTyped` 与鼠标修饰键。
- S3 为 `storage`（终端物品右键；索引入口不持物品，改按打开位置 8 格内判定有效）。同步走 `Replication` 的集合型 kind `Network.STORAGE`（2026-10-29 改，#013）：key 为 Menu 的 `containerId`，`view` 只在该 Menu 仍打开时返回各物品数量，每 5 tick 采样；条目标识 `StoredItem`（物品 + NBT）每个观察只发一次，之后按 id 发数量变化；取出、存入后 `Replication.refresh` 立即推送，与原版槽位同步同 tick 到达。`TakePacket` 携带物品本身，半组按服务端实时数量计算。箱子经 `InvWrapper` 读，避免大箱子重复计数。检查 `storage` 用 98 个箱子装入全部 1256 种物品。
- 虚拟网格写法：`.content` 按总行数定高并 `flex-shrink: 0`（否则被视口压缩，滚动范围只剩已建出的行），`padding-top` 把建出的行下移到视口处；行放在高度自适应的 `.rows` 里换行（定高盒里换行会把行拉开，`align-content` 不支持）；多建 2 行，因为行在滚动后一帧才更新。
- `IXCHEL` 滚动槽位检查由 S3 的背包区域补上：背包放在可滚动区域内，用 `IXCHEL` 绘制。
- 发现并修复：原先每次原生绘制后其后命令整体抬高 160，"物品 → 数量文字"交替时每格抬一次，约 70 格越过 Forge 1.20.1 GUI 远平面（z 10000），网格之后的背包区域整片不显示。`McPainter` 改为按重叠分层（`paint/Layers`，见模块拆解 §4）。未采用：压缩物品深度（仍线性增长）；每次原生绘制后清深度缓冲（打乱 ImmediatelyFast 等延迟批处理的顺序）；`<item>` 自画数量文字（只解决数量一种覆盖，可作为 `<item>` 功能另加）。
- 帧耗时：S3 每 tick 滚动一行，中位数 / p95 约 1.6–2.8 / 2.7–8.4 ms，预算 14 ms。该预算按单台开发机测得：2026-10-29 在另一次运行中（同为 AMD 核显，并行负载未控制）master 本身即为 13.6 / 28.4 ms，复制层改版后两次为 8.1 / 16.8 与 19.2 / 27.7 ms，均超预算，波动来自机器与负载而非改动。新检出的工作区没有 `run/options.txt` 时会停在首次启动的讲述人引导页，`BenchWorld` 不处理它，须先拷入该文件。

阶段 C2 结果（2026-10-29）：
- 控件用标准写法（#003、#014）：`<input type="text|number|checkbox|range">`、`<select>`/`<option>`、`<button>`、`<dialog>`；`bind:checked`、`bind:open`，`change` 在用户提交一次修改时触发（勾选、选中选项、滑条拖动在松开时只发一次），`close` 在 Escape 关闭对话框时触发；`:checked`、`:open`、`:disabled`、属性选择器、`accent-color`。默认外观在 core 的 `runtime/user-agent.css`，作者样式总能覆盖。
- 弹出层：展开的 `<select>` 列表与打开的 `<dialog>` 进入文档顶层 `TopLayer`，按打开顺序画在整棵树之上、不受祖先 `overflow` 裁剪，命中测试按同一顺序；模态对话框下方 inert（点击被吞、Tab 留在对话框内），对话框里的下拉叠在对话框之上。检查拿顶层内容的绘制位置时不减祖先滚动（`Element.scrollOffset()`，`Script` 与 `ContainerLayout` 共用）。
- `each` 行内的 `Value` 字段可双向绑定（`binding.Flat` 跟随外层 cell 换行），S6 每个配置项的草稿就是行里的 `Value`。
- mc：`UiScreen` 可带父界面，关闭时回到父界面；S6 经 `ConfigScreenHandler.ConfigScreenFactory` 从 mod 列表打开。
- S6 为 `config`：编辑 `ixchelsample-client.toml` 的真实规格（`SampleConfig`，4 个分类），分类、搜索、开关、滑条 + 数字框、枚举下拉、颜色、列表对话框（预设下拉）、恢复默认、已修改 / 越界标记、需重启提示、撤销、保存、有未保存修改时 Escape 先确认。检查 `config` 28 项，前后都把配置复位为默认值。
- 未验证：检查在已进入世界时经 mod 列表工厂以标题界面为父打开，真正无世界（`player == null`）的路径没有退出世界实测。

阶段 D 结果（2026-10-29）：
- core：`transform`（`translate`/`translateX|Y`/`scale`/`scaleX|Y`/`rotate`/`none`，百分比相对边框盒）与 `transform-origin`，计算值 `css.property.Transform`，矩阵 `Affine`；只在绘制与命中时生效，布局、滚动不受影响。`Painter` 以 `DrawCommand.PushTransform`/`PopTransform` 包住元素子树；命中测试与指针局部坐标（光标、滑条）沿祖先链逆变换，零缩放不可命中。
- 事件：`mousemove`（按住键时发给按下的元素，可用 `.left` 等过滤），`on:` 语句里可读 `$event.x`/`y`/`movementX`/`movementY`/`delta`（`binding.EventInfo`）。mc 层原有的 `mouseMoved` 转发已覆盖拖拽，`mouseDragged` 未接。
- mc：两个版本的 `McPainter` 把变换写进 pose；scissor 与重叠分层用变换后矩形的包围盒，旋转内容的裁剪因此偏宽。
- S7 为 `quests`（索引入口）：数据 `data/ixchelsample/quests.json`，3 章 99 个任务；已领取集合经快照 kind `Network.QUESTS` 复制，`ClaimQuestPacket` 由服务端校验前置与物品。画布拖拽平移、滚轮缩放 0.5–2、连线为旋转细 `div`、详情侧栏不随画布变换。检查 `quests` 21 项。
- S4：`Drops.heading` 给出整数角度，HUD 边缘与列表的箭头为 `minecraft:textures/item/arrow.png` 加 `rotate`，删去 8 方向字形；检查读 `transform` 计算值换算为扇区。
- 检查驱动：`Script.drag(sel, dx, dy)`；`paintedCenter` 取经变换与滚动后的绘制中心，`click`/`hover` 均改用它。
- 帧耗时：S7 静止中位数 / p95 约 2.2–2.6 / 2.8–4.1 ms，每 tick 平移约 2.1–2.3 / 3.1–3.8 ms，预算均 8 ms。

阶段 E1 结果（2026-10-31）：
- S8 为 `guide`（索引入口 `Guide.open`）。页面类型是 `ui/guide/` 下三个组件：`guide-book`（两张书页并排、按 spread 平移页带、目录 / 返回 / 翻页导航）、`guide-page`（参数 `heading`，正文是 `display: block` 的 `.text` 段落）、`guide-recipe`（3×3 网格、箭头、成品）；条目在 `ui/guide/entries/`，目录由 `ResourceManager.listResources` 生成并排序，标题取自文件名。分页由作者写一个个 `<guide-page>`，`GuideScreen` 渲染后按 `.page` 数回写页数。
- 返回历史放在静态 `Guide`（记录条目与 spread）：`HostedDocument.close()` 会关闭 view model，换条目即换屏，每屏新建 `GuideView`。配方由 `RecipeManager` 取 `ShapedRecipe`，各格按 `Source.polled(nanoTime)` 轮换 `Ingredient.getItems()`。
- 检查 `guide` 17 项：目录列出全部条目、段内链接、翻页按钮与 Left/Right、第二个 spread 上的链接、配方格轮换、Back 回到链接所在 spread、Backspace 回目录且历史清空、中文页、加载无诊断、帧耗时。
- 帧耗时（6 页长条目）：静止 p95 约 0.9–1.3 ms，逐 tick 翻页约 1.1–1.6 ms，预算 3 / 3.5 ms。
- 外观：超长单词在 `.text` 上 `overflow: hidden` 裁在文字区内；原版 `book.png` 只有左侧书脊，两页并排时右页同样在左侧有书脊（不支持镜像）。

阶段 E2 结果（2026-11-01）：
- S9 为 `tooltips`（索引入口），一页覆盖 §2 S9 全部元素种类：按钮（按键提示为 `inline-block` 小块，冷却倒计时按 `Source.polled` 每秒变化）、`<input>`、`<select>`、正文 `<span>` 与 `<a>`、`<mc-item>` 三种模式、24 行滚动列表、嵌套与 `title` 对照、组件 `help-badge`（`<slot>` 在 `<ix-tooltip>` 内）、对话框内按钮。检查 `tooltips` 21 项，含隐藏 tooltip 内的 `<mc-tooltip-lines>` 不取行、静止指针下绑定内容变化、贴边条目翻到指针左侧且留在屏内。
- 迁移：S1 属性行（名称 + 说明，`max-width: 160px`）、S6 配置项（名称、配置路径、注释、需重启提示，`max-width: 200px`）、S7 节点（标题、所需物品行带图标、状态）由 `title` 改为 `<ix-tooltip>`，S1、S6、S7 检查各加一项"悬停显示 tooltip"。接受的外观变化：外框与底色随主题（vanilla 主题紫框、dark 主题灰框），多行分色排版取代原版单色纯文本；S7 节点图标外的边框区不再有 tooltip。
- 写法：tooltip 内换行要靠作者给 `max-width`；`.key` 这类带边框的小块须 `inline-block`（`inline` 不能有盒属性），放在 `display: block` 的段落里。

阶段 I 结果（2026-11-02）：
- 1.20.1 全部场景与索引的界面文本改为语言键（`ixchel_sample.<场景>.<名>`，原版已有的键直接复用，如 `gui.done`、`item.minecraft.*`），`en_us.json`/`zh_cn.json` 各 255 键；1.21.1 refit 16 键，服务端拒绝原因以 `Component`（键加参数）经 `ComponentSerialization` 下发，客户端按自己的语言显示。两个 sample 打开 `i18n/literal-text`，`ixchelLint` 均为 0。
- 不翻译的是数据与符号：任务与章节标题（`quests.json`）、建好的路径点名、配置枚举值、单位（m、FE、mB）与罗盘方位。S6 配置项标签按 Forge 约定取 `<modid>.configuration.<名>`、`.tooltip`、`.section.<名>`，删去 `humanize`。
- `:lang(zh) .i` 让 S8 的强调在中文下不斜体、改用颜色；S8 `typesetting` 条目是排版样张，`translate="no" lang="en"`。
- 新增 playtest `language` 8 项：切到 `zh_cn` 并等资源重载及其淡出遮罩结束（遮罩期间点击被吞）后重开界面，`t`、`t:title`、参数、Java `Component` 与根 `lang="zh-cn"` 均为中文，中文目录加载无诊断，切回英文。既有测试只有 S6 预设选项名随原版键变为 "Spider Eye"。

### 4. 自动化检查

#### 4.1 现状

- `sample/.../src/check`（mod `ixchelsamplecheck`）：`Checks` 注册、`Script` 按选择器驱动（`click`、`hover`、`scroll`、`key`、`waitUntil`、`waitUntilServer`、`server`、`shot`，以及 `query`/`text`/槽位点击等共享 helper）、`FrameTimer` 帧耗时预算、`BenchWorld` 进存档。`-Pbench` 已删除。
- `testmod/shade-a/.../ContainerCheck` 自己又写了一套步骤队列，与 `Script` 重复。
- CI 只跑 `./gradlew build`。

2026-10-28 起，§4.2 的命名、驱动 API、顺序运行与 `report.txt` 已落地（`type` 随阶段 C、`drag` 随阶段 D 加入）；另加一条约定：两步之间必先画一帧（世界刚载入时 tick 会连续补跑而不出帧，文档尚未布局）。shade-a 的 `ContainerCheck` 仍独立。

#### 4.2 目标流程

- 命名：功能检查用 `-Pcheck=<名>[,<名>…]|all`；没有独立的计时基准开关，帧耗时作为检查项（`FrameTimer` 预算）跑在对应检查里。
- 驱动 API 改为按选择器操作：`click(sel)`、`hover(sel)`、`scroll(sel, dy)`、`drag(sel, dx, dy)`、`key(k)`、`type(text)`、`waitUntil(cond, maxTicks)`。坐标取自 `querySelectorAll` 得到的 box。固定 tick 等待只用在确实要等时间流逝的地方（例如效果倒计时）。
- 一次启动按顺序跑多个检查。每个检查在开始时自行重置状态（清背包、清空 sample 数据），不依赖前一个检查留下的状态。
- 结果：日志行格式不变，另外写 `run/checks/report.txt`（每项 PASS/FAIL 与汇总）。截图留在 `run/screenshots/`，只供人工核对，不做像素比对。
- 代码位置：sample 的 `src/check` 第二个 mod，业务代码不得引用它，它只经业务的公开入口与网络包驱动。（shade-a 及其 `ContainerCheck` 已删除，sample 自身即 shade 产物，见 #024。）
- 约定：每个场景都有检查；每个检查项写明它要发现的失败（AGENTS §6）。§2 中各场景的交互描述就是它的检查清单起点。
- CI：暂不跑游戏内检查，每次都要启动客户端并截图，成本太高（2026-10-28 决定）。core 的黄金测试继续放在 `build` 里。

### 5. 扩展，只记录不排期

- 世界空间 UI（Neat、ToroHealth 一类的头顶血条、伤害数字）：新宿主类型，要把文档渲染到世界空间的公告板上，并处理深度与光影兼容。
- 小地图 / 全屏地图（Xaero、JourneyMap）：需要动态纹理元素（每帧或按区块更新的 `NativeImage`）。
- 径向轮盘（Iron's Spells、Create 工具箱）：按角度命中，按住键时能否不阻塞移动受 #002 限制。
- 主菜单替换（FancyMenu 类整合包）：无世界上下文，S6 已验证父界面与 mod 列表入口，未在无世界时实测。
- NPC 对话 / 交易（Easy NPC、MCA）：打字机文本、服务端状态机。
- 图表（Mekanism 能量曲线一类）：需要折线或面积绘制命令。
