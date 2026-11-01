# Active Decisions

<!-- Only keep decisions that still constrain the project.
     Delete invalidated decisions; git records the history.
     Test: if an agent didn't know this decision,
     would it choose differently? Yes -> keep. No -> delete. -->

## #001 交付形态为可重定位 shade 的库
- **decided**: 2026-10-25
- **context**: 依赖全局能力的 mod 型 UI 引擎只能以前置或 jar-in-jar 单实例分发，实际运行版本由玩家或加载器决定，宿主测过的版本不一定是运行的版本
- **decision**: 以 shade + relocate 进宿主 mod 为默认交付，宿主拥有运行版本；库没有 modid 与自有入口，初始化由宿主驱动；jar-in-jar 仅作可选方式
- **rejected**: 独立 mod / 前置交付——运行版本不归宿主；需要前置分发的场景已有 AUI/LDLib2 等方案覆盖

## #002 默认产物零 mixin
- **decided**: 2026-10-25
- **context**: mixin 配置由宿主声明，N 份重定位副本会对同一 vanilla 方法注入 N 次
- **decision**: 默认产物不含 mixin；唯一需要的场景（边移动边操作 HUD）届时由宿主自写 mixin 调用库提供的钩子，或单独发布不重定位、`require: 0` 可降级的可选运行时 mod
- **rejected**: 库内置 mixin——重复注入导致全局状态争夺与加载顺序相关冲突

## #003 Web 兼容口径
- **decided**: 2026-10-25
- **context**: 完整浏览器兼容带来规范长尾与 GPU 全局副作用（AUI 至少约 17k 行服务浏览器兼容）
- **decision**: 已实现的部分使用标准写法与语义，不承诺渲染结果等同浏览器；不内嵌 JS；不支持的特性在开发期报错
- **rejected**: 追求浏览器等价或静默忽略不支持特性——前者成本无界，后者使问题难以定位

## #004 数据端口只接受 JDK 类型
- **decided**: 2026-10-25
- **context**: 各份副本重定位后类名不同，外部 mod 无法实现库自身的接口
- **decision**: ViewModel 的数据来源以 JDK 类型（如 `Supplier<T>` + 变更监听）接入，任何外部数据来源都由宿主接入
- **rejected**: 暴露库自有接口供外部实现，或内置复制层 / 意图 RPC

## #005 MVP 双平台：Forge 1.20.1 完整、NeoForge 1.21.1 对齐
- **decided**: 2026-10-25
- **context**: 第一消费方 TACZ-RESPAWN 为 Forge 1.20.1 / Java 17；内核与适配层的边界须在不同加载器、顶点 API 时代、运行时命名（SRG/mojmap）下都成立
- **decision**: Forge 1.20.1 做完整 MVP；NeoForge 1.21.1 只做对齐最小集（弹药 HUD + 一个 Screen），两边跑同一套验收模板；`ixchel-core` 以 Java 17 编译与测试；shade 共存验证在 Forge 1.20.1 上做
- **rejected**: 只做 NeoForge 1.21.1——第一消费方用不上；两个平台都做完整 MVP——功能工作量翻倍，对验证没有额外收益

## #006 内核为单一 Gradle 模块，按包分层
- **decided**: 2026-10-26
- **context**: 内核各部分（CSS、DOM、样式、布局、事件、绑定）没有独立消费者；每多一个 artifact，宿主就多一项需要 shade 与 relocate 的依赖
- **decision**: `ixchel-core` 保持一个 Gradle 模块，内部按包单向依赖，包划分见 `ref/D-ixchel-module-breakdown-20261026.md` §2
- **rejected**: 拆成多个 Gradle 模块——没有独立消费者，只增加宿主的 shade 配置与版本对齐成本

## #007 自写严格模板解析器，不用 jsoup
- **decided**: 2026-10-26
- **context**: jsoup 按 HTML5 容错算法建树（隐式闭合、adoption agency、foster parenting），会静默改写模板结构，且依赖本身约 499KB
- **decision**: 自写类 XML 规则的严格解析器：标签必须闭合、属性必须加引号、未知标签/属性开发期报错并带文件:行:列
- **rejected**: jsoup——静默改写与 #003 的开发期报错冲突，且增加 shade 体积

## #008 `Slot.x/y` 以反射写入
- **decided**: 2026-10-26
- **context**: 布局结果须写回 `public final` 的 `Slot.x/y`；AT 要求库编译期与宿主 jar 运行期各带一份，宿主漏配时打开界面才抛 `IllegalAccessError`
- **decision**: 1.20.1 / 1.21.1 用反射（`setAccessible` + `MethodHandle` setter）写入，宿主零配置；字段按开发名与 SRG 名依次查找，`UiContainerScreen` 初始化时缓存，失败立即报错；目标平台 Java ≥ 26（JEP 500 收紧）时改用 AT
- **rejected**: AT——给每个宿主增加一处漏配即运行期崩溃的接入步骤；mixin `@Mutable`——违反 #002

## #009 兼容优先，有冲突风险的增强须有特性开关
- **decided**: 2026-10-26
- **context**: 接管 vanilla 绘制或交互的增强（如 1.20.1 自绘槽位物品）会与注入同一位置的其它 mod 冲突
- **decision**: 默认走 vanilla 路径；存在冲突风险的增强必须提供开关，以宿主 API 参数暴露（如 `slotItemRendering`，默认 `VANILLA`），库不自带配置文件，宿主自行决定是否接入玩家配置
- **rejected**: 默认开启增强——冲突由玩家承担且难以定位；库级配置文件——多份重定位副本无法归属；按 `ModList` 自动切换——名单不全且无实际观察到的冲突

## #010 CI：PR 全量构建，版本以 gradle.properties 为准
- **decided**: 2026-10-26
- **context**: 仓库无 CI，`master` 只靠"PR + squash"约束；需要自动跑测，并为发版与文档站自动更新预留触发规则
- **decision**: `check` 在 `pull_request`→`master` 与 `push: master` 上跑全量 `./gradlew build`，仅 Linux，设为 required check，不用 secret、token 只读，缓存只由 `master` 写入；仓库 admin 可在 PR 上 bypass（`gh pr merge --squash --admin`，不等 CI，失败时在 master 上补修），不可直推 `master`，有协作者后撤销 bypass；不使用 `pull_request_target`；发版由 `v*` tag 触发，tag 须等于 `gradle.properties` 的非 SNAPSHOT 版本；文档为本仓库 `docs/` 下的 VuePress 站，随发版部署到本仓库 GitHub Pages（仓库转公开后启用）；暂不做远端发布，仍用 `publishToMavenLocal`。细节见 `ref/D-ixchel-ci-workflows-20261026.md`
- **rejected**: 按路径过滤 mc/loader 构建——required check 被跳过时 PR 会一直挂起；私有期间加 Windows runner——分钟数按 2 倍计，字符集问题已由显式 UTF-8 约定覆盖；CI 用 tag 注入版本号——源码中看不到发版号；文档另开仓库——参考页由代码生成，拆开后跨仓库同步需额外 token 且文档与代码不能同 PR 修改；GitHub wiki——无导航/搜索/组件，推送需另配权限；快速开发用脚本整体开关 required check——容易忘记切回，且每次切换都要改仓库设置；取消 PR 直推 `master`——违反项目分支规则

## #011 1.20.1 库模块发布 SRG 产物
- **decided**: 2026-10-27
- **context**: Forge 1.20.1 运行时为 SRG；宿主要 shade、relocate 后再 reobf 整个 jar，库产物的命名决定了消费方要不要先重映射
- **decision**: 沿用 legacyforge 默认，`ixchel-mc-1.20.1` 与 `ixchel-forge-1.20.1` 的 `jar` 发布为 SRG。消费方经 `obfuscation.createRemappingConfiguration(shade)` 把这两个模块重映射回 mojmap，`ixchel-core` 直接 shade，shadowJar 之后 `obfuscation.reobfuscate(tasks.shadowJar, …)`，这些由 Gradle 插件完成（#025）。MDG 的重映射配置本身不传递依赖，所以三个模块逐个列出；reobf 变体在 `.module` 中不声明依赖这一缺陷因此不影响消费方，未修。该链路已在生产实例中与另一份不同版本的副本共存运行
- **rejected**: 发布 mojmap 产物——legacyforge 下要额外关掉默认 reobf 并另配发布变体；消费方的 reobf 本来就要对 shade 后的整个 jar 再做一次，不会更省事，且未实测

## #012 文本输入与事件过滤语义
- **decided**: 2026-10-29
- **context**: S3 需要单行输入与带修饰键的点击；HTML/CSS 没有"修饰键过滤"的标准语义，滚动位置要能双向绑定
- **decision**: 用标准 `<input>`（光标、选区、剪贴板由 core 实现，剪贴板经端口 `Clipboard` 由宿主提供），`bind:value` 双向；`bind:scrolltop` 的写入只是滚动请求，由布局截取到有效范围后回写；鼠标事件过滤的修饰键精确匹配（`.left` 要求无修饰键，`.left.shift` 要求恰好按住 Shift）；`click` 只由左键触发（同 DOM，右键用 `.right`）；光标 500ms 闪烁
- **rejected**: 自定义输入元素——违反 #003；修饰键宽松匹配（Vue 默认）——`.left` 与 `.left.shift` 会同时触发，网格的"取整组"与"移入背包"无法区分

## #013 sample 服务端状态只经复制层下发
- **decided**: 2026-10-29
- **context**: S3 的存储是按 Menu 会话、约 1300 条的状态，原复制层只支持以方块坐标为身份、每 tick 整份比较的小快照，#48 因此让 S3 自发增量包
- **decision**: 扩展 `network/Replication` 而不是让场景自建同步通道：kind 自带 key 编解码（方块坐标、容器 id 等）、按 kind 设采样间隔、意图处理后 `refresh` 立即推送；新增集合型 kind，每个观察维护条目 id 表，标识只发一次、之后只发增删改，观察的首条消息带 `first` 让客户端丢弃上一次观察的在途增量。新场景的服务端状态声明为快照型或集合型 kind，不另写客户端包
- **rejected**: 场景自发增量包（#48 的 `StorageSyncPacket`）——每个场景各写一套会话身份、节奏与 id 表；在快照比较上加 delta codec——每 tick 仍全量生成快照，条目只能以 NBT 字符串为 key 重复传输，并要伪造方块坐标作身份

## #014 表单控件与弹出层
- **decided**: 2026-10-29
- **context**: S6 需要开关、滑条、数字框、下拉、确认框；下拉列表必须跨出可滚动条目列表的裁剪，确认框要遮住并屏蔽下方界面
- **decision**: 控件用标准 `<input type>`、`<select>`/`<option>`、`<button>`、`<dialog>`（#003），由 core 实现状态、编辑与绘制，默认外观来自 user-agent 样式表，作者样式可覆盖；展开的下拉列表与打开的对话框进入文档顶层，按打开顺序画在整棵树之上、不受祖先裁剪，命中测试同序；模态对话框下方 inert。只有这两类元素进顶层，右键菜单、popover 等等第二个场景需要时再加（E2 起 `<ix-tooltip>` 也进顶层，单独存放与绘制、不参与命中，见 #020）
- **rejected**: `z-index` + `position: fixed`——要引入完整的层叠上下文且与裁剪交互复杂，模板作者自己算位置；sample 自建弹出层——选项不能用 CSS 写样式、每个场景重写一遍

## #015 模板与样式语言的扩展方向
- **decided**: 2026-10-29
- **context**: sample 各场景重复配色、结构与只做格式化的 `Derived`，纯视图状态也只能进 ViewModel；模板没有组合机制，作者无法自行复用结构
- **decision**: 组件为由模板定义的自定义元素：按资源路径以 `<link rel="component">` 引入，保留外壳元素，显式参数与事件，具名插槽，不做样式隔离；模板加表达式、`on:` 语句与 `state:` 局部状态，逻辑仍在 Java；样式按 UA、组件、主题、作者四层排序，层由 link 位置决定；自定义元素前缀 `ix-`（与 MC 无关）、`mc-`（MC 概念）由库保留，按领域而非实现划分。细节见 `ref/SD-ixchel-template-and-style-language-20261029.md`
- **rejected**: 内嵌 JS——只有第三方新建带逻辑界面离不开它，与 #001 冲突且破坏加载期报错；全局组件注册表——多宿主、多副本下撞名；Shadow DOM 隔离——主题与资源包须经组件逐一暴露的 part 才能换皮；按实现方式（模板/原生）分前缀——实现互换时标签名被迫改变

## #016 模板上下文模型与控制块
- **decided**: 2026-10-30
- **context**: L2 组件需要确定闭包边界、写权限与类型检查方式；属性形式的 `each`/`if` 让同一元素上的指令分属行内外两个作用域，且一行只能是一个元素
- **decision**: 模板中每个名字来源唯一、加载期确定，模板只触及 Java 显式暴露的东西（ViewModel `private` 字段不可见，`$` 只放框架值，`event` 改名 `$event`）；可写性只由声明类型决定：`source(...)` 声明为只读的 `Cell<T>`，`Value<T>` 只表示 state；组件体只见自己的参数、事件与局部状态，写权限由使用方传入可写路径授予，未传入的参数取默认值成为实例的局部状态、无默认值即必填；不写类型注解，按每次实例化的真实类型检查（签名推导与编译缓存暂不做）；模板组件不引入子 Scope；`each`/`if` 改为不渲染节点的 `<for each as key>`/`<if test>` 控制块，一次切换不留兼容。四条可分析性不变式与细节见 `ref/SD-ixchel-context-model-and-components-20261030.md`
- **rejected**: 隐式继承外层上下文（WPF DataContext）——组件暗中依赖外层名字，无法加载期解析；参数声明 `bindable`——写权限应由使用方在自己的文本中给出；Java 侧登记组件类型——破坏纯 HTML 定制与资源包覆盖；保留属性形式的 `each`/`if` 作兼容——两套写法并存，作用域规则仍要为旧写法保留；`Value` 兼任 source 与 state、写入时运行时检查——可写性不能由类型决定；组件给外壳元素加指令（`:host` 等价物）——暂无真实需要，以后可纯增量加入

## #017 包间内部接缝用一次性领取的访问接口
- **decided**: 2026-10-30
- **context**: binding 的模型与模板编译器共享内部不变式（不标脏的写入、每帧 poll、框架持有的单元格），Java 没有 internal，shade 时排除 `module-info` 使 JPMS 不可用，按契约拆包就得把这些成员交到另一个包
- **decision**: 模型内部经 `binding.ModelAccess` 交给 `binding.compile`：实例由 `ModelAccess.claim()` 只发一次（`Bindings` 加载时领取），实现类 `Bridge` 与它触及的成员保持包内可见，依赖方向为 compile → binding；以后别的包需要模型内部时同样扩充这个接口，不公开成员
- **rejected**: 公开成员并标 `@ApiStatus.Internal`、以 ArchUnit 限制调用方——宿主仍可绕过脏标记、改写 `Action` 的 pending；由 binding 调用 compile 的 `install`——两包循环依赖，违反 #006；保持单包——模型 API、表达式语言、模板绑定三份契约混在 25 个类中

## #018 库内主题 = 共用规则 + 配色变量
- **decided**: 2026-10-30
- **context**: sample 的两族外观（原版灰、暗色浮层）结构相同、配色不同；库内样式表不开放 `@import`（语言文档 §4.4），两份主题若各写一套完整规则会逐字重复
- **decision**: 主题由共用规则 `themes/theme.css`（值全部为 `var(--ix-*)`）加只在 `:root` 上设 `--ix-*` 的配色表组成，`LibrarySheets` 按名字展开为这两份，共用规则只进一次；`--ix-*` 是对页面与组件公开的接口；`:root` 匹配模板的顶层元素；替换后无效的普通属性按 unset 处理并报告一次，无效的自定义属性视为未定义
- **rejected**: 每份主题各写完整规则——结构逐字重复；开放 `@import` 组合——与 §4.4 的暂缓冲突；`:root` 选中隐式根节点——给它加底色、边框会影响布局与绘制

## #019 富文本：`Component` 拆为样式片段，进入 core 行内排版
- **decided**: 2026-10-31
- **context**: S8 正文要跨 `span` 续排并保留 `Component` 的颜色与格式；原定方向是把 `Component` 作为不透明宿主文本对象，由 mc 层 `StringSplitter` 测量与换行
- **decision**: mc 层把绑定的 `Component` 拆成带样式的文字片段（颜色、粗体、斜体、下划线、删除线、混淆），与模板中的 `<span>` 走同一套 core 行内排版；`TextMeasurer` 按样式测量。行内上下文由 `display: block` 建立，行内元素不支持盒属性。细节见 `ref/D-ixchel-roadmap-20261029.md` E1
- **rejected**: 不透明宿主文本对象——`Component` 不能与周围文字同行续排，且 core、mc 两套换行

## #020 tooltip 用库元素 `<ix-tooltip>`，不走 Web 标准
- **decided**: 2026-10-31
- **context**: 文档内 tooltip 只有 `title` 纯文本；S1、S6、S7 要富内容，滚动容器中的 tooltip 不能被裁剪；MC tooltip 跟随光标、立即出现、鼠标穿透
- **decision**: 任意元素的 `<ix-tooltip>` 子元素，悬停时进入顶层，按内容定尺寸、贴光标、贴边翻转；`<mc-item>` 不带它时走原版 tooltip；原版物品行由 `<mc-tooltip-lines bind:item>` 提供。细节见 `ref/D-ixchel-roadmap-20261029.md` E2
- **rejected**: `popover="hint"` + `interestfor` + anchor positioning——只能锚定元素、不能跟随光标，触发者限于 `<a>`/`<button>`，以 `id` 关联违反上下文模型不变式 1；标准写法加私有扩展——写法像标准、行为不同，违反 #003

## #021 模板与 ViewModel 的关联由 ViewModel 类上的 `@UiTemplate` 声明
- **decided**: 2026-10-31
- **context**: 静态检查绑定名与类型须事先知道模板配哪个类，而关联原本只在运行时按实例建立
- **decision**: ViewModel 类加 `@UiTemplate(String[])`，值为完整资源 id，路径部分可通配；挂载 API 主路径从注解取模板（`new UiScreen(view)`），显式 id 重载（`new UiScreen(id, view)`）作通配与出口，组合不在注解范围内时照常加载并在开发期警告；HUD 注册显式给类（`registerHud(modBus, EnvView.class, EnvView::new)`）；编译器改读成员表（实例反射或类反射），ViewModel 字段须声明精确类型。细节见 `ref/SD-ixchel-tooling-and-editor-20261031.md` §3–§4
- **rejected**: 手写模型描述文件——作者不会维护，与代码漂移；模板里写 ViewModel 类名——语言文档已否决；Gradle DSL 清单——重复声明；开发期运行时记录关联——新写的 UI 当场无法检查；扫描挂载调用——实现脆弱；严格要求显式 id 必须在注解内——模板 id 来自数据（整合包）时无路可走

## #022 游戏外只做静态检查，运行时验证只在游戏内，游戏内检查随库发布
- **decided**: 2026-10-31
- **context**: agent 写 UI 需要离线校验与可重复的游戏内验证；sample 的检查框架只服务 sample，且退出码不反映失败
- **decision**: 静态检查由只依赖 core 的 `ixchel-kit` 提供（lint、schema）；游戏内检查从 sample 提取：core 放驱动、运行器与端口，mc 层实现输入、转储与退出码，加载器层提供 tick/帧钩子，随现有 mc/加载器制品发布，`-Dixchel.check.<modid>` 开启；1.21.1 暂不适配。细节见 `ref/SD-ixchel-tooling-and-editor-20261031.md` §2、§7、§8
- **rejected**: 无头 testkit（游戏外实例化 ViewModel 跑 binding 与事件）——迫使宿主把 ViewModel 与 MC 解耦，覆盖面又被静态检查与游戏内检查夹住；按版本单独发布检查制品——多一套制品与 SRG 配方。检查的 Gradle 接线（`check` sourceSet、`-Pcheck`）后由 #025 的插件提供

## #023 诊断带稳定的符号错误码
- **decided**: 2026-10-31
- **context**: lint 的 `--json`、文档、agent 修复与编辑器都需要不依赖消息原文的错误标识，而 `Diagnostic` 只有严重级别、消息与位置
- **decision**: 错误码为英文符号名 `领域/名称`（如 `binding/unknown-name`），由报告它的包内实现 `DiagCode` 的 enum 定义，严重级别由码决定，可附 hint；码是公开契约，不改名、不复用；测试断言码与位置，不断言消息原文。细节见 `ref/SD-ixchel-tooling-and-editor-20261031.md` §6
- **rejected**: 数字码（如 `IX0123`）——对 agent 与读者不透明；集中在 core 的单一码表——mc 层的码要回流 core，违反依赖方向

## #024 sample 即使用方形态，库 jar 声明 GAMELIBRARY
- **decided**: 2026-11-01
- **context**: sample 把库源码并入自己的 mod 运行，使用方"以 jar 依赖开发、shade 发布"的路径从未运行过，shade 只由两个独立测试宿主覆盖，sample 构建脚本也不能当配方看
- **decision**: core、mc、loader 的 jar 清单写 `FMLModType: GAMELIBRARY`，宿主开发期以普通依赖接入（FML 把它载入 game 层）；两个 sample 按使用方写法构建（shade + relocate，1.20.1 另 reobf），两者都应用 #025 的插件，仓库内外的差别（项目依赖还是 Maven 坐标）在插件内；不再保留独立 shade 宿主
- **rejected**: 库源码 sourceSet 并入宿主——使用方的开发期路径无人运行；保留 shade-a/b——两份副本共存已在生产实例验证，重复确认没有新信息；代价是 IDE 中不能热替换库类（模板与 CSS 热重载不受影响）

## #025 ModDevGradle 宿主的构建由 Gradle 插件 `com.azane.ixchel` 完成
- **decided**: 2026-11-01
- **context**: 使用方要抄的构建配方（shade 配置属性、relocate 每个库包、Forge 1.20.1 的重映射与 reobf、kit 的 lint 任务、devResources 与检查接线）有五十多行，sample 脚本也因此难读
- **decision**: `tools/gradle-plugin`（included build，Java，随库同 group 与版本发布 `ixchel-gradle-plugin` 与插件标记）。只支持 MDG：与 `net.neoforged.moddev.legacyforge` 同用即 Forge 1.20.1，与 `net.neoforged.moddev` 同用即 NeoForge 1.21.1；应用即建 `shade`、加库依赖与 taffy、配 shadowJar（无 classifier，`jar` 改 `-slim`，1.20.1 另 reobf）、注册 `lintUi`。DSL：`ixchel { mod(modid, shadePackage); checks(checkModid) }`，`mod` 注册 MDG mod、按库包 relocate、给所有 run 设 `ixchel.devResources.<modid>`；`checks` 建 `check` sourceSet，`-Pcheck` 时加载检查 mod 并设 `ixchel.check.<modid>`。根项目存在 `:ixchel-core` 时用项目依赖，否则用插件内嵌版本的坐标。两个 sample 应用它
- **rejected**: 适配层接口兼容 ForgeGradle、Loom——没有使用方；继续给配方——配方长且易抄错，sample 不能当示例读；插件校验 MC 版本——未见误用

