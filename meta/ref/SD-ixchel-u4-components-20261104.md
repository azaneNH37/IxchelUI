---
tags: [survey, design]
date: 2026-11-04
origin: 规划 U4（组件库）时，对照 AUI 与 LDLib2 的现成控件，并按科技、魔法、冒险、实用类 mod 的常见界面整理需要内置的元素与组件
related: [DECISIONS.md#003, DECISIONS.md#030, DECISIONS.md#034, DECISIONS.md#035, ref/D-ixchel-roadmap-20261029.md, ref/S-ixchel-ui-capability-comparison-20261102.md, ref/SD-ixchel-themes-20261104.md, ref/SD-ixchel-context-model-and-components-20261030.md]
---

# Ixchel UI U4 Built-in Elements and Components (WIP)

## Summary

讨论中，§5 的问题未定。初步结论：U4 不只是 tabs、tree、进度条，按实现方式分为标准元素补齐、库模板组件、新引擎元素、mc 层原生元素四类；建议 U4 做库组件主体、`<details>` 与 radio、`<mc-item>` 修饰与轮播，其余按场景另开阶段。

## Detail

### 1. 现成控件对照

源码：AUI `common/.../element` 与 `targets/forge-1.20.1/.../element`；LDLib2 `gui/ui/elements`（均为本地克隆，未构建运行）。

| 来源 | 控件 |
|---|---|
| AUI | 标准 HTML 为主：`input`（text、number、checkbox、radio、range、color、file、date 等）、`textarea`、`select`、`pre`、`canvas`、`svg`、`iframe`、`audio`；MC：`item`、`slot`、`container`、`recipe`（按配方 id 生成槽位）、`ingredient`（多候选轮播，悬停暂停，间隔可由 CSS 或属性设）、`sprite`（帧动画）、`texture`、`theme-switcher`。无 tabs、tree、进度条 |
| LDLib2 | basic：`button`、`label`、`toggle`（含 group）、`switch`、`text-field`、`text-area`、`selector`、`search-component`、`progress-bar`（填充方向、插值）、`slider`、`tag-field`；container：`scroller-view`、`virtual-scroller`、`split-view`、`tab-view`、`tree-list`、`menu`、`graph-view`（节点图平移缩放）；inventory：`item-slot`、`fluid-slot`（容量）、`inventory-slots`；misc：`scene`（3D）、`color-selector`、`code-editor`、`structured-tag-editor`（NBT）、`localized-number-text`、`inspector` |
| Ixchel | `div`、`span`、`a`、`img`、`button`、`input`（text、number、checkbox、range）、`select`/`option`、`dialog`、`ix-tooltip`、`ix-drag-preview`；`mc-item`、`mc-entity`、`mc-slot`、`mc-tooltip-lines` |

sample 中已重复手写的控件：tabs（character、effects）、竖向能量条与水箱（machine）、血条（env HUD）、toast（loot HUD）、选中行列表（index、waypoints）。

### 2. 场景

| 类型 | 典型界面 | 共性需求 |
|---|---|---|
| 科技（Mekanism、Thermal、AE2、RS、GT、IE） | 机器、终端、合成计划 | 竖向能量条、流体罐（流体贴图平铺 + 刻度）、进度箭头与燃烧火焰（贴图按比例裁切）、红石模式等循环按钮、六面配置、过滤用幽灵槽、大数字（1.2k、12.5 kFE）、数量输入（±，Shift ×10）、侧边抽屉标签 |
| 魔法（Botania、Ars、Thaumcraft、Blood Magic、Iron's Spells） | 法典、研究树、法术选择 | 翻页书、可平移缩放的节点树与连线、径向菜单、法力条、图标冷却扫描、带物品链接的富文本 |
| 冒险/RPG（Curios、Apotheosis、FTB Quests、Easy NPC、Waystones） | 角色面板、任务、对话、HUD | 属性行带条、血条掉血拖尾、Boss 条、对话框（逐字显示与选项）、toast、罗盘、稀有度边框、装饰槽占位图标、按键提示 `[R]` |
| 实用（Cloth/YACL 配置、Jade、小地图、整理） | 配置、信息 HUD、列表 | 开关、带数值滑块、枚举循环、取色器、按键捕获、列表编辑、搜索与过滤标签、分页、右键菜单、可折叠分组、可排序表格 |
| 原版对照 | 统计、进度、创造物品栏、切石机、村民交易、配方书 | 统计是带标签的可排序表格；进度是标签加可平移的树；配方书与交易是分页或滚动网格加禁用态 |

### 3. 分类与建议

#### 3.1 标准元素补齐（引擎，#003）

| 元素 | 场景 | 建议 |
|---|---|---|
| `<details>`/`<summary>` | 配置分类、任务章节、可折叠分组 | U4，代价小，复用 `open` |
| `<input type="radio">` + `name` 分组 | 配置枚举、分段选择 | U4（原列"不排期"） |
| `<textarea>` | 告示牌、书本、笔记 | 仍不排期 |
| `<progress>` | 进度条 | 不做，用 `ix-bar`（#040） |

#### 3.2 库组件（`ix-*`，模板 + CSS，经 `@ixchel/components` 包或按文件引入，主题按部件类穿外观，#034、#039、#040）

| 组件 | 内容 |
|---|---|
| `ix-tabs` | 只做标签头，`bind:value`；面板由使用方 `<if test="tab == …">` 写：组件体 `<for>` 内不能放 `<slot>`，插槽名定义时固定，数量可变的面板做不进组件 |
| `ix-bar` | 输入 `value` + `max`（比例在组件内 `value / max`，不再由 Java 算百分比），四个填充方向，可选贴图裁切填充（`background-position` 锚定在起点）；可选掉血拖尾：第二层填充用更慢的 `transition` |
| `ix-stepper` | 数字输入加 −/+，`on:click.shift` ×10，范围用 `?:` 夹取 |
| `ix-cycle` | 原版 CycleButton 式循环按钮，左键下一项、右键上一项（组件体内 `on:click`/`on:click.right`）；表达式无下标，当前项用 `<for index>` 嵌 `<if test="i == value">` |
| `ix-pager` | 上一页 / 页码 / 下一页 |
| `ix-tree-row` | 树的一行（缩进、展开箭头）；数据由 ViewModel 给展平列表（组件自引用报 `load/component-cycle`，不能递归） |
| `ix-search` | 输入框加清除按钮 |

开关、列表行、徽标、按键提示不做组件，用表面类（`.ix-switch` 加在 checkbox 上、`.ix-row`、`.ix-badge`、`.ix-key`）交给主题；显示真实绑定键的是 `<mc-key>`。`ix-*` 都是模板组件，不带 ViewModel（#035）。

#### 3.3 新引擎元素（另开阶段）

| 元素 | 场景 | 难点 |
|---|---|---|
| `ix-viewport` 平移缩放视口 | 研究树、任务树（S7 现为手写） | 滚轮缩放、限位、连线 |
| `ix-menu` 右键菜单 | 列表、终端 | 指针处进 `TopLayer`、点外部关闭（与 select 弹层同类）；右键事件能否取到按键待核实 |
| `ix-radial` 径向菜单 | 法术、工具选择 | 按角度命中扇区、按住键弹出 |
| 按键捕获、取色器 | 配置界面 | 按键名与改键走 mc 层 |

冷却扫描不做元素：做成内置特效 `ix-cooldown`（box，参数 `--ix-cooldown-progress`），已并入 TH4。

#### 3.4 mc 层原生元素

| 项 | 现状 | 建议 |
|---|---|---|
| `<mc-item>` 修饰 | `ItemElement` 只调 `renderItem`，数量、耐久条、冷却都不画 | U4：默认画原版修饰；`count="…"` 覆盖数量文字（AE2 式 1.2k）；`decorations="none"` 关闭 |
| `<mc-item>` 轮播 | 无 | `bind:item` 接受 `List<ItemStack>`，悬停暂停；用于配方与指南（对应 AUI `ingredient`） |
| `<mc-fluid>` | 能力对比文档 §4：流体由宿主原生元素实现 | 待定，§5-3 |
| `<mc-key>` | 无 | 显示 `KeyMapping` 当前绑定键，随改键变化；要读外部数据，做成 Java 组件（#035） |
| `mc-player-inventory` | sample 三处手写下标列表 | 已移到 L4，作为第一个库 Java 组件 |
| 方块与多方块预览 | 无 | 不进 U4，属 3D 场景 |

### 4. 依赖

- 库组件按文件显式 link（`@ixchel/components/<名>.html`，不设包，#039、#041）；`.ix-*` 类名依赖 TH1；组件外观写进 TH4 的四套主题（路线图依赖 TH4 → U4、L5 → U4）。

### 5. 待定

1. U4 范围：§3.1 的 `<details>` 与 radio、§3.2 全部、§3.4 的 `<mc-item>` 修饰与轮播进 U4；§3.3、`<mc-fluid>`、`<mc-key>` 各自另开阶段。
2. 进度条写法：已定（2026-11-04，#040）做 `ix-bar` 组件，主题按 `.ix-bar-fill` 等部件类穿外观，不做 `<progress>`。
3. 流体：mc 层只依赖原版类，`FluidStack` 与流体贴图查询（`IClientFluidTypeExtensions`）是加载器 API。维持宿主实现（sample 示范一份），或库在加载器层提供（违反"加载器层只做注册"）。
4. 数字格式化（1.2k、mB、mm:ss、百分比）：已定（2026-11-04）表达式语言加一小组纯函数（紧凑数字、时长、百分比），函数名与签名在 U4 开工前定。
