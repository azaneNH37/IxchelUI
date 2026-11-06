---
tags: [survey]
date: 2026-11-06
origin: 写文档与录制演示前，需要知道 AUI、LDLib2 的内置主题与文档怎样呈现视效，以及参考优秀前端视效时 Ixchel 缺哪些 CSS 特性与特效
related: [ref/S-ixchel-ui-capability-comparison-20261102.md, ref/SD-ixchel-themes-20261104.md, ref/SD-ixchel-ui-effects-20261102.md, ref/D-ixchel-roadmap-20261029.md, DECISIONS.md#003, DECISIONS.md#028]
---

# Ixchel UI: 参考前端视效的 CSS 缺口

## Summary

在 3406 个自带 CSS 的前端组件中，只有 16% 不用到 Ixchel 缺失的特性；按"svg 图标、`+`/`~` 与结构伪类、`@media` 可替换"计为 36%。低难度的一批（`outline`、`letter-spacing`、`inset`、`radial-gradient`/`repeating-*`、`background-position` 可动画、`skew`、`aspect-ratio`）补齐后约到 55%，其后依次是 `z-index`、3D 变换、伪元素文字。动效是 Ixchel 相对两家的长处：LDLib2 的 10 套主题没有一条过渡或动画规则，AUI 的 17 套主题以 100–200ms 过渡和 `steps()` 为主。录制按 LDLib2 的做法，在 playtest 上加确定性时钟与逐帧编码。

## Detail

### 1. 口径

- 语料：uiverse.io 的开源组件库 [galaxy](https://github.com/uiverse-io/galaxy)（MIT，3802 个文件，其中 3406 个带 `<style>`，其余只用 Tailwind 类，不计）；CodePen 上按游戏 HUD、科幻、毛玻璃、霓虹等关键词找到的 37 个 pen。前者偏网页小部件（按钮、加载动画占一半），后者偏游戏与科幻风格，两者合看。
- 方法：按正则判断每个文件是否用到某个特性，统计"用到"而非"离不开"；`calc()` 内的 `+` 先剥掉。"贪心解锁"每步选一项，使最多文件变为"所用特性全部已支持"。
- 已支持与否按 `docs/reference/css.md` 与 core 源码（`BoxSyntaxes`、`SelectorParser`、`Syntaxes`、`CssParser`、`Keyframes`）核对。
- CodePen 主站与列表页（热门、精选、标签）对脚本返回 Cloudflare 403，单个 pen 的编译后源码可从 `https://cdpn.io/<user>/fullpage/<id>` 读取，无需登录。
- 脚本与 pen 源码在 `.ai/tmp/css-corpus/scan.py`、`.ai/tmp/codepen/`（不提交）。

### 2. 缺失特性频率与解锁顺序

| 缺失特性 | 用到的文件 | 贪心解锁（替换前提下） | 判断 |
|---|---|---|---|
| svg 元素、`filter: url()` | 29.8% | 计为可替换 | 多为内联图标，属元素范畴 |
| `z-index` | 28.4% | +10% → 46% | 不少删去不影响画面；要改绘制与命中顺序 |
| `outline` | 19.1% | +11% → 57% | 低难度 |
| `+`/`~` | 18.6% | 计为可替换 | 多为 checkbox 开关写法，由绑定代替 |
| `letter-spacing` | 12.5% | +7% → 64% | 科幻与 HUD 标题几乎都用（TH3 曾定不做） |
| 结构伪类 | 12.4% | 计为可替换 | 错峰动画与列表装饰；改脏标记规则（能力对比 §4） |
| 3D 变换 | 10.2% | +6% → 71% | 卡片倾斜；图层合成与命中都要支持透视 |
| 伪元素文字、`attr()` | 7.6% | +5% → 76% | 伪元素现只做装饰 |
| `radial-gradient` | 7.9% | +4% → 80% | 走现有 CPU 生成纹理入图集的路径 |
| `inset` 简写 | 5.5% | +4% → 84% | 很低 |
| `background-position` 动画 | 3.8% | +2% → 86% | 流光、渐变流动 |
| `skew` | 2.8% | +2% → 88% | 已有 2D 矩阵 |
| `clip-path` | 3.0% | +2% → 90% | 可走私有图层加遮罩 |
| `aspect-ratio` | 2.7% | +2% → 93% | — |
| `repeating-*-gradient` | 2.3% | +2% → 94% | 多数可用 `linear-gradient` 加 `background-size` 平铺代替 |
| `background-clip: text` | 1.8% | +2% → 96% | 图层遮罩 |
| `conic-gradient` | 2.1% | +2% → 97% | 旋转渐变边框最常见；可写 `.ixeffect` 代替 |
| `mask` | 1.6% | +2% → 99% | 图层遮罩 |
| `@font-face` | 0.2%（CodePen 37 个中 20 个） | — | `font-family` 只认 MC 字体 id |

已支持特性的用量：`border-radius` 81%、`transform` 72%、`transition` 62%、`box-shadow` 54%、伪元素 44%、`@keyframes` 41%、`linear-gradient` 29%、`filter` 11%、`backdrop-filter` 4%。

### 3. 内置主题对照

| | AUI（17 套，纯 CSS） | LDLib2（10 套 LSS） | Ixchel（4 套） |
|---|---|---|---|
| 动效 | 100–200ms 过渡与 `steps()` 像素跳帧；少数主题有循环（arwes 扫描光带、mecha 警示条闪烁、terminal 光标） | 无：lss 目录无 transition、animation、keyframes；悬停与按下是三张贴图瞬切，唯一内建动效是 Switch 写死的 0.1s | 过渡、`@keyframes`、进出伪类、`.ixeffect` |
| 外观手段 | token 变量 + 伪元素、渐变、`clip-path`、mask、`@font-face`；Create、AE 用九宫格 PNG | `sdf()` 圆角矩形（无渐变阴影）与九宫格图集 | 变量 + `corner-shape`、`backdrop-filter`、`text-shadow`、box/layer 特效 |
| 最出彩 | t13 蒸汽波（透视网格地面、条纹落日）、t02 Create（黄铜木纹九宫格）、t08/09 arwes 科幻 HUD、t15 机甲（警示斜纹、切角） | dusk 家族（一套设计 6 个调色板，1px 发丝线三级圆角）、ore（像素凸起按钮） | holo、amber（TH5 重做后） |

AUI 主题的可移植性：arwes、mecha、Create、Ore 现有能力即可（切角用 `corner-shape`，斜纹用 `Stripes`，`steps()` 已支持）；蒸汽波的透视地面缺 perspective 与 `clip-path`，可改为 box 特效按 `ix_time` 画滚动网格；印象派油画依赖斜向 repeating 渐变与椭圆 `clip-path`。AUI 引擎本身不支持 `text-shadow`、radial/conic 渐变、skew、perspective（其 `theme-spec.md` §7）。

### 4. CodePen 中可直接移植的样式

| pen | 效果 | 移植方式 |
|---|---|---|
| [Ahmod-Musa/YPGGZgg](https://codepen.io/Ahmod-Musa/pen/YPGGZgg) | 全息卡：四角括号、扫描亮线、扫描线底纹、标题 RGB 分裂 | 纯 CSS；扫描线用 `linear-gradient` 加 2px `background-size`；分裂用 `-ix-paint(Glitch)` |
| [travis-g/XNLBaa](https://codepen.io/travis-g/pen/XNLBaa) | CRT：闪烁、横向扫描线、RGB 竖纹 | 照搬 |
| [wahidn/XWKONrw](https://codepen.io/wahidn/pen/XWKONrw) | 选关菜单：悬停栏 flex-grow 变宽，背景放大加模糊 | `flex-grow` 过渡、`filter`；JS 切类改 `bind:class` |
| [brundolf/XKddbJ](https://codepen.io/brundolf/pen/XKddbJ) | 主菜单：色条展开、选项延迟淡入 | 过渡加 delay；装饰波浪（radial）删去 |
| [RockStarwind/pozBLqd](https://codepen.io/RockStarwind/pen/pozBLqd) | 双血条：扣血后残影延迟收缩 | 宽度绑定、残影层 `transition-delay`；skew 与 conic 高光须待补 |
| [hyukio68/LYRQVrg](https://codepen.io/hyukio68/pen/LYRQVrg) | 毛玻璃卡，信息栏滑上 | `backdrop-filter` 模糊游戏画面 |
| [kevinpowell/QWdRzON](https://codepen.io/kevinpowell/pen/QWdRzON) | 霓虹按钮与地面倒影 | 发光阴影；倒影用 `scaleY` 加 `blur` 代替 `rotateX` |
| [thecoderashok/zYbogyR](https://codepen.io/thecoderashok/pen/zYbogyR) | 跟随鼠标的彩虹描边 | box 特效读 `ix_mouse`，不需 JS |
| [Ahmod-Musa/MYaaEQV](https://codepen.io/Ahmod-Musa/pen/MYaaEQV) | 旋转渐变边框加光晕 | box 特效（Glint 变体） |
| [chasebank/qZNLyx](https://codepen.io/chasebank/pen/qZNLyx) | 故障文字 | `Glitch` 加 `Chromatic` |

被挡下的代表：Jiironimo/ogYzxaG、simeydotme/PrQKgo（3D 倾斜加 conic/radial 箔片）、gwannon/LYjvOLK（2077 主题，大量 `clip-path`、`attr()`、skew）、gvdan/MLmYwm（SAO 血条，`clip-path` 多边形）、lbebber/rawQKR（SVG 滤镜）。

JS 行为的映射：切类名用 `bind:class`；数值用 `bind:style`；指针位置用特效的 `ix_mouse`；指针驱动的 3D 倾斜数值可传入，但缺 `rotateX/Y` 与 perspective。

### 5. 两家的文档与录制

- AUI：文档站 [doc.sighs.cc/ApricityUI](https://doc.sighs.cc/ApricityUI/)，简介页按能力分组贴社区 mod 截图，showcase 为熔炉换皮、头顶血条、消息气泡、涂鸦板；宣传视频（BV1dfuW6pErR，7 分钟）演示游戏内资源管理器、Ore 示例页、AI 生成页面、DevTools，界面为浅色扁平 Web 风；文档另链一段整合包视频（BV1yzGJ6hEcp），其中 AUI 界面为任务树、世界内羊皮纸面板与 HUD。
- LDLib2：文档站 [LowDragMC-Doc](https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/)，入门教程每步配结果图或 GIF，组件页含截图、多语言用法、XML、属性表与内部选择器表；文档图与视频由脚本在真实客户端生成（`UIScenario`/`UIRecording`）。
- LDLib2 录制管线（`uitest.md`、`uitest/record/`）：`RenderFrameEvent.Post` 每帧推进一步；`VideoClock` 替换 `Util.timeSource`，take 期间每帧推进 1/fps 秒，客户端 tick、集成服务器、动画同受控，录制快于或慢于实时都不影响成片；`ServerPace` 让集成服务器追上视频时间；画面 blit 到副本再画叠加层（光标、点击波纹、按键、字幕、聚光灯），GPU 片元着色器转 I420，3 个 PBO 加 fence 异步回读，writer 线程以 rawvideo 写入 ffmpeg stdin（NVENC 或 libx264）；取景按选择器加 padding，zoom 移动采样区域；4K 窗口加大 GUI 缩放输出 1080p；光标按 Fitts 定律与最小加加速度曲线移动，节奏随机数以场景名为种子；录制指令在测试模式为空操作，断言失败时录制构建失败。
- 与 Ixchel playtest 的对应：驱动、步骤队列、合成输入、截图、转储、报告、退出码已有；缺逐帧钩子、可注入的文档时钟（`UiDocument.clock` 现为 `System::nanoTime`，过渡、动画、`ix_time` 都走墙钟）、游戏时间步进、编码、叠加层、取景、录制开关。
