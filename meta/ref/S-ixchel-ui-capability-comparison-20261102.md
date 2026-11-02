---
tags: [survey]
date: 2026-11-02
origin: 路线图 I 阶段完成后，需要知道相对 AUI、LDLib2 的 UI 能力差距落在哪里，并据此重排后续阶段
related: [ref/D-ixchel-roadmap-20261029.md, ref/SD-ixchel-ui-positioning-and-adapter-design-20261025.md, ref/SD-ixchel-ui-effects-20261102.md, DECISIONS.md#003, DECISIONS.md#029]
---

# Ixchel UI: 能力覆盖对比

## Summary

差距集中在盒子外观词汇、尺寸与排版的表达力、带内部状态的控件；特效不是主要差距。Ixchel 领先的是静态检查、模板内绑定与组件、CJK 排版、shade 零 mixin 交付。AUI 最接近浏览器，LDLib2 最接近完整的 mod 开发框架。

## Detail

### 1. 口径

AUI `snow@3201a95`（2026-11-02）、LDLib2 `1.21@f3c298a`（2026-10-31）的源码，未构建运行。Ixchel 按 Forge 1.20.1 计。● 完整，◐ 部分，○ 无。

AUI 的 `snow` 分支 README 自称只维护 Forge 1.16.5，其他 target 保留供移植参考，但 10 个 target 的 `ci.enabled` 都为 true，不能据此认为都在维护。`feature/fluid`、`neoforge/21.1` 等分支未分析。

代码量（`src/main` 的 Java/Kotlin）：Ixchel core 16.1k + mc 1.20.1 3.6k；AUI common 92.7k + forge-1.20.1 target 11.6k；LDLib2 `gui/ui` 41.9k（`gui` 全部 52.2k）+ Compose 9.1k。

### 2. 能力表

| 能力 | Ixchel | AUI | LDLib2 |
|---|---|---|---|
| 编写形态 | ◐ HTML、CSS、类型化表达式 | ● HTML、CSS、JS（Rhino） | ● XML、LSS、Java、Kotlin、Compose、KubeJS |
| 可视化编辑器 | ○ | ◐ DevTools 改 DOM 与样式并保存 | ● `UIEditor` |
| 热重载 | ● 保留 ViewModel | ● 按依赖刷新 | ◐ 仅资源重载 |
| flex | ● | ● | ● |
| grid | ○ | ● 含 subgrid | ● |
| table | ○ | ● | ○ |
| `calc()`、相对单位 | ○ | ◐ 线性 calc、min/max/clamp、em/rem/vw/vh | ○ |
| 跨元素行内流 | ● | ● | ○ |
| CJK 断行 | ● 含避头尾 | ◐ 无避头尾 | ◐ vanilla `font.split` |
| 富文本 `Component` | ● | ◐ 不渲染样式 | ● |
| 自定义字体 | ○ | ● `@font-face` | ◐ SDF 渲染 |
| i18n | ● 译文内标记 | ◐ | ◐ |
| 选择器 | ◐ 8 个伪类，无伪元素 | ● 约 30 个伪类，有伪元素 | ◐ 无属性选择器 |
| `var()` | ● | ● | ○ |
| CSS 嵌套 | ● | ○ | ○ |
| 不支持写法 | ● 开发期报错 | ◐ 警告后忽略 | ◐ 日志后跳过 |
| 圆角 | ○ | ● | ◐ 经 `rect()`/`sdf()` |
| 渐变、box-shadow | ○ | ● | ○ |
| opacity 整组语义 | ○ | ● | ● |
| filter、backdrop、blend、mask、clip-path | ○ | ● | ◐ 仅 overflow 遮罩 |
| 九宫格 | ○ | ● | ● |
| 用户 shader | ○ | ○ | ● |
| transition | ● | ● | ● |
| `@keyframes` | ○ | ● | ◐ 仅 Java、Compose |
| Tab 焦点 | ● | ○ | ○ |
| 富 tooltip | ● | ◐ 纯文本 | ● |
| 拖放 | ● U2，4 个事件 | ○ | ● 6 个事件 |
| IME 系统预编辑 | ○ | ○ 只有 JS 可调的 composition 方法 | ○ |
| 控件音效 | ● U1，默认点击音 | ○ | ● `UISoundUtils` |
| 媒体音频 | ○ | ● `<audio>`，OpenAL | ○ |
| 内置元素 | ◐ 约 12 个 | ● 22 个标签 | ● 35 个 |
| Canvas、Markdown、WebView | ○ | ● | ◐ 绘制钩子 |
| 物品、HUD、容器槽位 | ● | ● | ● |
| 实体 | ● | ● 含 GeckoLib | ◐ 经 `Scene` |
| 3D 场景 | ○ | ◐ | ● |
| 流体 | ○ | ○ | ● |
| 世界内 UI | ○ | ● | ○ |
| JEI/EMI/REI | ◐ 坐标兼容 | ○ | ● 排除区、查询、幽灵拖放 |
| 模板内声明式绑定 | ● 类型检查 | ○ | ◐ 列表、条件仅 Compose |
| 服务端同步、RPC | ○ 库外 | ◐ 槽位绑定 | ● |
| 模板组件 | ● | ○ | ◐ |
| 宿主原生元素 | ● U3 | ● | ● |
| 第三方注入 | ○ | ● | ◐ |
| 静态检查、错误码 | ● 138 个码 | ○ | ○ |
| 运行时 inspector | ○ | ● 含 WebSocket 与 MCP | ● |
| 游戏内自动测试 | ● 1.20.1 | ◐ | ● 服务端步骤、拖拽、滚动未实测 |
| Gradle 插件 | ● | ○ | ○ |
| 平台 | ◐ Forge 1.20.1、NeoForge 1.21.1 缩减 | ● 10 个 target | ◐ NeoForge 1.21.1 |
| 零 mixin、shade 共存 | ● | ○ | ○ |
| 离屏 FBO | ○ | ● | ◐ |

### 3. 使用频率

AUI 18 套主题（15.3k 行 CSS）的正则计数。主题是 Web 风格，会高估圆角和阴影在 MC 像素风 UI 中的权重，只看量级。

| 特性 | 用量 | Ixchel |
|---|---|---|
| `var()` | 12112 | ● |
| 属性选择器（带运算符的仅 16） | 4984 | ● |
| `:hover` | 3017 | ● |
| `::before`、`::after` | 2705 | ○ |
| `box-shadow` | 1581 | ○ |
| `border-radius` | 489 | ○ |
| `font-family` | 419 | ○ |
| `line-height` | 406 | ○ |
| 渐变 | 265 | ○ |
| `:not`、`:is`、`:where` | 261 | ○ |
| `calc()` | 174 | ○ |
| `letter-spacing` | 164 | ○ |
| em、rem | 130 | ○ |
| `animation` | 126 | ○ |
| `+`、`~` | 95 | ○ |
| 结构伪类 | 90 | ○ |
| mask | 32 | ○ |
| grid | 31 | ○ |
| clip-path | 23 | ○ |
| `filter` | 17 | ○ |
| `backdrop-filter` | 1 | ○ |

### 4. 由此得到的取舍

- 特效用量比盒子外观低两个数量级，V1 排在 FX 之前。
- 控件数量差距一半可由模板组件补齐：tabs、tree、进度条进 U4；必须进引擎的是滚动条、拖拽。
- sample 中斑马纹、首末行一类需求没有实例，结构伪类要改脏标记规则，不排期；`<for index>` 把表达式放在模板，不动选择器引擎。
- 不引入：Compose、Canvas、WebView、`<audio>`、世界内 UI、光标样式；流体与 `Scene` 由宿主原生元素实现。
