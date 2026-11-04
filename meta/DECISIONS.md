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
- **decision**: 静态检查由只依赖 core 的 `ixchel-kit` 提供（lint、schema）；游戏内检查从 sample 提取：core 放驱动、运行器与端口，mc 层实现输入、转储与退出码，加载器层提供 tick/帧钩子，随现有 mc/加载器制品发布，`-Dixchel.playtest.<modid>` 开启（命名见 #026）；1.21.1 暂不适配。细节见 `ref/SD-ixchel-tooling-and-editor-20261031.md` §2、§7、§8
- **rejected**: 无头 testkit（游戏外实例化 ViewModel 跑 binding 与事件）——迫使宿主把 ViewModel 与 MC 解耦，覆盖面又被静态检查与游戏内检查夹住；按版本单独发布检查制品——多一套制品与 SRG 配方。检查的 Gradle 接线（`playtest` sourceSet、`-Pplaytest`）后由 #025 的插件提供

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
- **decision**: `tools/gradle-plugin`（included build，Java，随库同 group 与版本发布 `ixchel-gradle-plugin` 与插件标记）。只支持 MDG：与 `net.neoforged.moddev.legacyforge` 同用即 Forge 1.20.1，与 `net.neoforged.moddev` 同用即 NeoForge 1.21.1；应用即建 `shade`、加库依赖与 taffy、配 shadowJar（无 classifier，`jar` 改 `-slim`，1.20.1 另 reobf）、注册 `ixchelLint`。DSL：`ixchel { mod(modid, shadePackage); playtests(playtestModid) }`，`mod` 注册 MDG mod、按库包 relocate、给所有 run 设 `ixchel.devResources.<modid>`；`playtests` 建 `playtest` sourceSet，`-Pplaytest` 时加载测试 mod 并设 `ixchel.playtest.<modid>`（命名见 #026）。根项目存在 `:ixchel-core` 时用项目依赖，否则用插件内嵌版本的坐标。两个 sample 应用它
- **rejected**: 适配层接口兼容 ForgeGradle、Loom——没有使用方；继续给配方——配方长且易抄错，sample 不能当示例读；插件校验 MC 版本——未见误用

## #026 游戏外检查统称 lint，游戏内测试统称 playtest
- **decided**: 2026-11-01
- **context**: 两套检查沿用了临时脚本的命名："check" 同时指整个游戏内系统、一个具名测试项、一条断言、Gradle 生命周期任务 `check`、sourceSet `check`、`-Pcheck`、`ixchel.check.<modid>` 与 core 的静态入口 `UiDocument.check`；日志标签 `[ixchel-bench]` 来自早期基准脚本；`scenarios` 参数与 sample 场景 S1–S9 同名；`report` 有四种含义；日志的 "N passed" 数的是断言，用户按名选的却是测试项
- **decision**: 两个词根互不重叠。游戏外：lint——kit 的 `lint`/`schema`/`explain` 不变，插件任务 `ixchelLint`（插件注册的任务带 `ixchel` 前缀），core 入口 `UiDocument.lint`、`Bindings.lint`，错误码不变（#023）。游戏内：playtest——术语 playtest（具名测试项，如 `machine`）、assertion（一条 PASS/FAIL）、run（一次启动跑完所选测试项）、step（队列一步）；包 `core.playtest`（`PlaytestDriver`、`PlaytestRun`、`FrameBudget`、`Pump`、`InputPort`、`Dump`）与 `mc.playtest`（`Playtests` 注册表、`Playtest` 步骤 API），`IxchelForge.installPlaytests`，断言记录 `record`、汇总文本 `summary`、帧预算断言 `FrameBudget.expect`/`FrameTimer.expect`；插件 DSL `playtests(modid)`、sourceSet `playtest`、`-Pplaytest`、`-Dixchel.playtest.<modid>`；日志 `[ixchel-playtest] <测试项> <断言>: PASS|FAIL`，结尾 `<modid> done: <n> playtests, <p> passed, <f> failed`；输出集中在 `playtests/<modid>/`（`report.txt`、`dumps/`、`screenshots/`）；sample 测试 mod `ixchel_sample_playtest`，每场景一个 `*Playtest` 类。"场景"只指 sample 场景
- **rejected**: 保留 check 加限定（`ingameCheck`）——名字长，仍与 Gradle `check` 同根；`e2e`——看不出在游戏内；`probe`——已删除的 testmod 用过；`scenario`——与 sample 场景冲突；`lintUi`——插件任务无统一前缀；截图留在 `run/screenshots/`——与手动 F2 截图混在一起

## #027 模板 i18n = 元素级消息 + 译文内具名标记
- **decided**: 2026-11-02
- **context**: 原定 `t="语言键"` 只是保留名；属性、参数、依赖数据的文本与句中样式词都没有写法，MC 汉化生态只认语言 JSON
- **decision**: `t="键"` 由译文给出元素内容，`t:<属性>="键"` 给属性值；译文在 MC 语言 JSON 中，`%s`/`%n$s` 按位置插入不带 `mark` 的子元素，`<名>…</名>` 复制带 `mark="名"` 的空子元素并填入文字（0..n 次，不嵌套、不带属性），`\n` 强制换行；加载期展开、先于绑定编译，切换语言随资源重载重建；外观只由模板与 CSS 决定，`:lang()` 按游戏语言区分；依赖数据的文本由 Java 返回 `Component`；原文只在 `en_us.json`；字面文本警告只在 kit、默认关闭。细节见 `ref/SD-ixchel-i18n-20261102.md`
- **rejected**: `$t()` 表达式函数与动态键——违反加载期检查与"不提供内置函数"；样式词各自取键——词形与范围脱离句子；`§` 码——样式交给译者、绕过 CSS；编号标签——译者看不懂；模板内写原文——两份原文漂移

## #028 UI 特效 = 资源文件定义的具名效果，经 CSS 引用
- **decided**: 2026-11-02
- **context**: 流光、溶解、CRT 等效果没有 Web 标准写法，库以 shade 分发、不带固定 namespace 的 assets、不用 mixin，而 MC 的 shader 程序按名字全局缓存
- **decision**: 特效为 `.ixeffect` 文件（`kind box|layer|backdrop` + `param 名 { syntax; inherits; initial-value }`（照搬 `@property`，`inherits` 必填）+ GLSL `effect()`，用库内原语库组合），由 `<link rel="effect">` 按资源路径引入；`box` 写 `background-image: paint(名)`，`layer` 写 `filter: -ix-paint(名)`，`backdrop` 写在 `backdrop-filter` 中；参数为自定义属性 `--名-参数`，按声明类型校验、可过渡；程序名为 `<宿主modid>_ixchel_fx<n>`（每次构造取新名，避开全局表复用旧编译结果），文档加载时按特效 id 编译、文本不变则复用，新程序编译成功后才关闭旧实例（库自带 shader 见 #033）；内置特效同格式，现从 `@ixchel/effects/…` 引入，TH1 起改为免链接（#034）。标准 CSS 能表达的效果走标准属性。细节见 `ref/SD-ixchel-ui-effects-20261102.md`
- **rejected**: 位置参数 `paint(名, 参数…)`——不能单独覆盖或过渡；包裹元素 `<ix-effect>`——特效属于样式，包裹会改结构且不能与标准 filter 串链；运行时节点图或万能 shader——调试与缓存成本高、无需求；全局特效注册表——多宿主下撞名（同 #015）；`PostChain`——只读固定资源路径

## #029 圆角、阴影、渐变走路径 C，圆角只做外观
- **decided**: 2026-11-02
- **context**: V1 要补圆角、`box-shadow`、线性渐变，路径 C 一直是推荐未定；圆角裁剪依赖 FX1 的私有图层，MC 界面又很少需要裁剪圆角子元素
- **decision**: 三者由 CPU 生成覆盖率/SDF 纹理放入单张图集，经 `POSITION_TEX_COLOR` 九宫格绘制，纹理以宿主 modid 注册；`<img>` 图集共用这条路径。V1 的 `border-radius` 只影响背景、边框与阴影，子元素不裁圆角；FX1 落地前 `overflow` 配圆角按矩形裁剪并由 lint 警告。注：`<img>` 共用图集未随 V1 落地
- **rejected**: SDF shader（路径 B）——V1 就要 shader 注册，且 Painter 按顶点 API 时代分叉；CPU 三角化（路径 A）——无抗锯齿，阴影只能近似；把 FX1 提到 V1 之前——为少用的裁剪提前引入图层

## #030 宿主原生元素 = 实现类声明 + 按 modid 注册
- **decided**: 2026-11-02
- **context**: 宿主要写自绘标签；kit 不运行游戏，标签与载荷类型必须能静态读取；shade 后同名类互不相通，但开发环境与未来的共用形态不应依赖这一点
- **decision**: 在实现类上用 `@NativeElement` 声明标签、普通属性与载荷名；载荷类型从 `NativePayload<T>`（绘制接口 `NativePainter<T>` 继承它）读取，绑定可传 `T` 或 `Supplier<T>`。宿主只调用 `McElements.register(modid, painter)`，声明与实现一起注册；kit 不初始化宿主类，读取同一注解和泛型信息。运行时与 kit 经同一个声明适配器构造元素定义；按模板 namespace 合并宿主登记。仍不与语言文档 §3.4 的 Java 组件合并
- **rejected**: 要求作者手写资源声明——与实现重复，载荷类型会漂移，注册和声明易漏一边；不分 modid 的全局表——多宿主下撞名（同 #015）

## #031 元素以消费阶段的切面组合定义
- **decided**: 2026-11-02
- **context**: 仅把标签比较换成 `Content`/`Layer` 枚举，仍要求解析、布局、绘制、输入各自维护元素种类分支；core、mc 与宿主会继续走不同定义路径
- **decision**: `ElementType` 保存声明与类型安全的切面表；各阶段拥有消费接口，`html` 在其上装配内置定义，复杂元素的校验、测量、绘制、默认交互归同一个定义类，select 与 option 同属一个单元。DOM 不引用阶段或内置实现；通用 CSS 排版、事件路由、焦点与模态约束仍由引擎执行。原生声明也组合这些切面；宿主入口暂不接受自定义 interaction/layout，Java 跨包必须 public 的内部接口不代表宿主扩展承诺。`style`/`link` 属于模板资源语法，不再登记为运行时元素
- **rejected**: 封闭枚举加分散分支——新元素迫使所有阶段同步修改；元素继承并直接引用引擎——使 DOM 反向依赖流水线；现在开放宿主布局与交互——暂无消费场景，不提前冻结测量、捕获与焦点契约

## #032 进出动画 = 进出单元上的三个状态伪类
- **decided**: 2026-11-03
- **context**: V3 初版入场用 `@starting-style`（CSS，首帧瞬间）、离场用块属性 `leave`（模板，整段阶段），同一对动作分在两层两种写法，整页与顶层条目也无从复用
- **decision**: 进出单元（`<if>`/`<for>` 副本的顶层元素、文档顶层元素、顶层条目）的根元素带引擎维护的伪类：`:-ix-entering`（插入起到子树过渡与有限动画结束）、`:-ix-exiting`（移除起到结束，之后才真正移除，期间 inert、停绑定）、`:-ix-absent`（进场首帧与整个离场，即共享的"不在场"外观）；后代经组合子选择，外层进出时内层不单独播；中途反悔两阶段互换、过渡反向；样式表不用这些伪类时立即进出。作者只写 CSS，模板与 Java 无动画 API。删除 `@starting-style` 语法与块属性 `leave`（写了报错），引擎也不再有起始样式机制：进场首帧即普通的 `:-ix-absent` 样式。UiScreen 关闭与 UiHud 显隐用同一写法，经加载器绘制钩子画残影，另开阶段 V4，不需 mixin；`display: none` 显隐不算进出。细节见 `ref/D-ixchel-roadmap-20261029.md` V3、V4
- **rejected**: 块属性 `leave`/`enter`（Vue `<Transition>` 式）——整页无块可挂，资源包不能单独加动画；元素属性 `closed="类"`——仍要动模板；`@starting-style` 管入场——首帧瞬间与离场阶段不对称，进场时长只能写在常态规则里；单个 `:-ix-absent`——进出外观或 `@keyframes` 不同时无写法；`:-ix-entering` 只匹配首帧——名字暗示整段，写在其中的 `transition` 不生效

## #033 库自带 shader 从 classpath 构造，不走 RegisterShadersEvent
- **decided**: 2026-11-03
- **context**: FX1 原定固定 shader 经加载器 `RegisterShadersEvent` 注册、随资源重载重建；FX2 的滤镜、合成与背景 shader 不是资源包内容
- **decision**: mc 层首次使用时用 classpath `ResourceProvider` 构造 `ShaderInstance`，程序名 `<modid>_ixchel_<名>`，加载器层不参与。`GameRenderer.reloadShaders` 只关闭它自己 `shaders` 表里的实例，不碰这些程序，F3+T 后照常工作（effects playtest 重载复查）。shader JSON 不写 blend 节点，绘制顺序为 `FilterShaders.apply` → 设混合状态 → 建网格 → `FilterShaders.draw`：`apply()` 会按默认不透明 `BlendMode` 关掉 blending
- **rejected**: `RegisterShadersEvent` 注册——每次资源重载重编译不变的库 shader，还要加载器胶水层转交实例；在 shader JSON 写 blend 节点——合成的混合方式随 `mix-blend-mode` 变化，一份 JSON 只能固定一种

## #034 主题 = 页面显式选择的外观包
- **decided**: 2026-11-04
- **context**: 拟定四套内置主题时，原 #018（共用规则 + `:root` 配色）只能换颜色、带不了规则与特效，挂在通用类名 `.panel`、`.title` 上，且每页要链接主题与库资源；原版、全息、赛博需要各自的装饰、动画与特效
- **decision**: 主题 = `--ix-*` 变量（按角色命名的公开接口）+ 给库词汇（标准元素、`ix-*` 组件、`.ix-*` 表面类）穿外观的规则 + 自带的 `@keyframes` 与内置特效，主题文件开头至多一个 `@import` 基底主题（`@import` 只在主题文件开放）。页面用至多一个 `<link rel="theme" href>` 显式选择，内置为 `@ixchel/themes/<名>`（classpath），项目主题按普通路径（资源包可覆盖）；推荐宿主写一份自己的主题供页面链接，资源包覆盖它即可换皮；mod 级默认主题留给以后的宿主配置文件，页面选择永远优先，没选则只有 UA 样式。组件不能链接主题，经变量跟随页面。主题不改盒子尺寸，框线用 inset `box-shadow` 与背景层画。内置特效与 `ix-*` 组件免链接，项目资源仍显式链接。内置主题之间仍共用一份基于变量的规则以免重复。`:root` 匹配模板顶层元素；替换后无效的普通属性按 unset 处理并报告一次，无效的自定义属性视为未定义（沿用原 #018）。细节见 `ref/SD-ixchel-themes-20261104.md`
- **rejected**: 只用 mod 级默认主题——页面不能自我说明，lint 与编辑器要额外解析配置；每个主题只换配色——全息、赛博的装饰与动画无处安放；表面做成 `<ix-panel>` 元素——不能与作者类叠用，每种底板都要新元素；子树换主题——要求主题规则只经变量，与自带规则冲突

## #035 组件可带每实例的子 ViewModel
- **decided**: 2026-11-04
- **context**: 带数据的复用件（玩家背包、按方块读能量与红石的面板控件）只能让每个页面 ViewModel 各自取数再传入，sample 已有三份背包下标列表；语言文档 §3.4 原草图要每个界面以 `Components.of` 登记工厂
- **decision**: 组件分模板组件与 Java 组件，使用处同构。Java 组件 = 组件文件 + `@UiTemplate` 指向它的 ViewModel 类 + `UiComponents.register(modid, 类, 无参工厂)`；签名仍写在 `<template params events>`，类以 `Param<T>`（只读）、`TwoWay<T>`（双向，写回使用方路径，未传入时为本实例局部状态）、`Event<T>`（`emit`）字段按名一一对应，不符报 `component/signature-mismatch`；`ModelShape.Kind` 为 `CELL`/`ACTION`/`EVENT`（按组件体内用法），`Member.port` 标出对外签名；子模型随外壳元素挂载、真正移除（离场结束）时关闭，热重载重建；组件只经事件与使用方通信。数据入口只有 ViewModel：组件的输入只有参数，宿主与客户端状态由组件 ViewModel 的来源读取，不设框架值（`$` 只留给 `$event`）；容器界面的菜单即 `player.containerMenu`（原版点击要求它与界面菜单一致）。写 ViewModel 的判据：要读未传入的外部数据、有 UI 外副作用、表达式算不出的计算或持有资源，否则写模板组件；库的 `ix-*` 不带 ViewModel。数值拓宽（`Integer` → `Long`/`Float`/`Double`，`Double` → `Float`）对所有绑定生效，字面量属性仍为 `String`。库组件的来源与登记见 #037。细节见 `ref/SD-ixchel-context-model-and-components-20261030.md` §3.7
- **rejected**: ViewModel 作参数（使用方须知实现方式，`<for>` 行与 Java 列表的身份须人工对齐）；每屏 `Components.of` 登记（每个用到的界面重复登记）；参数以 `Cell` 声明（与 `source` 字段不可区分，kit 无法静态读取）；签名只写在 Java 类（组件文件自身说明不了接口，默认值无处写表达式）；工厂带构造参数（依赖只应来自端口与客户端状态）；宿主注入的 `$` 框架值作参数默认值（L4-L5 曾有 `$menu`/`$player`）——绕过 ViewModel 的第二个数据入口，存在性随界面种类而定，模板与 kit 都看不到

## #036 来源可脱离 ViewModel 按 key 共享（Feed）
- **decided**: 2026-11-04
- **context**: 一个界面里多个组件按同一方块各自轮询，读取贵或 `<for>` 实例多时重复；Java 组件构造时还读不到参数，按参数打开的资源（watch、订阅）无处挂
- **decision**: 来源拆为静态定义 `Feed<K,T>`（`polled` 只依赖 key；`opened` 第一个引用时打开资源、最后一个释放时关闭；默认每周期读一次，`perTick()` 按客户端 tick）与按 key 共享的实例；实例表挂在各 `Feed` 上，`Feed` 作为静态常量即每份库副本一张，引用经 `Scope` 计数。周期为一次 `Bindings.update()`（core 内计数，同一帧两个文档各算一次），tick 由 mc 层经 `Feed.tickClock` 提供（`Gui#getGuiTicks`，无世界时也计数）。ViewModel 以 `source(feed, key)`/`source(feed, keyCell)` 持引用，key 变化时释放旧实例、取得新实例。只读；不做共享可写状态与共享派生。细节见 `ref/SD-ixchel-viewmodel-and-binding-20261026.md` §10
- **rejected**: 每文档一张表（页面 ViewModel 先于文档构造，构造时接不通）；共享可写状态（破坏 `Value` 只属于一个 ViewModel 的语义）；共享派生族（派生惰性且廉价，无观察到的需要）；由上层统一取数往下传值作为唯一方式（仍是默认做法，但组件不能单独按 `pos` 使用）

## #037 库资源统一按 `@ixchel/` 路径解析，库词汇为每层一张预引入表
- **decided**: 2026-11-04
- **context**: L4 后 `@ixchel/` 背后有三套实现（`LibrarySheets` 名字表、`LibraryEffects` 名字列表、`UiHosting.LibraryComponent` 每组件一个读取函数），Java 组件分宿主与库两张表；库组件不能 link 自己的样式表，只能内联；库组件只在容器界面传入，其它界面报未知标签；core 将来自带的 `ix-*` 组件无处放，TH1 的 `@ixchel/themes/` 会是第四套
- **decision**: `@ixchel/<路径>` 是与 `ns:path` 并列的文件命名空间：同一套 `resolve`（库文件内的相对路径在 `@ixchel/` 下解析，`..` 不越出）与读取，读取按前缀分派——`@ixchel/` 读库（classpath，按层的锚点类相对读取，随 relocate 移动），其余经宿主；库文件不进热重载文件表，资源包不能覆盖。库 = 文件根 + 预引入表（标签 → `@ixchel/` 组件文件）+ 库 Java 组件，core 一份、mc 层 `extend` 一份，与 `TagRegistry` 同形；内容在代码中声明，不枚举 classpath。预引入表中的标签在每个文档都已知、首次使用时加载，显式 link 同一文件等价；只有 `@ixchel/` 文件可声明 `ix-`/`mc-` 名。Java 组件只有一张表，按文件 id，宿主条目与库条目同表。主题与特效经同一读取器；页面 link 的库主题仍进主题层（TH1 改为 `rel="theme"`）。只能用于容器界面的库组件在其它界面报错位置在使用处。宿主扩展（原生元素、Java 组件）仍按 modid 登记，不进库
- **rejected**: 保持各资源种类各一套（新增种类各加一套）；库文件放 `assets/<固定 namespace>/` 让资源包覆盖（违反 #001）；枚举 classpath 目录得出库内容（jar 内不可靠）；库组件只许内联样式（与项目组件写法分叉）

## #038 不在顶层显示的子树内，组件实例暂停轮询
- **decided**: 2026-11-04
- **context**: `<mc-tooltip-lines>` 为"隐藏 tooltip 不取行"专设了原生内容种类 `content=lines` 与 mc 层每帧找元素、填行的循环（E2）。L4 后它可写成 Java 组件，但组件实例每帧 poll，存储终端每格一个 tooltip 时回到 E2 要避免的开销
- **decision**: 组件子模型的外壳元素位于一个顶层种类元素（带 `LayerFacet`：`<ix-tooltip>`、`<ix-drag-preview>`、`<dialog>`）之内、而该元素当前不在顶层时，跳过它的 poll（首次 poll 同样推迟）；元素进入顶层的那一帧在样式与布局之前 poll，同帧显示新值。只看顶层状态，不看 `display: none`（计算值晚于绑定一帧，会先显示旧值）。`<mc-tooltip-lines>` 改为 mc 层库 Java 组件（`params="item"`，每行一个元素，空行保留行高），删除 `NativeElement.lines`、`TagRegistry.hasLineContent` 与 `TooltipLines`
- **rejected**: 保留原生元素（为一个元素设一种内容种类，宿主写不出同类控件）；按 `display: none` 暂停（晚一帧）；由组件自己判断可见性（子模型拿不到文档，#035 只经端口通信）
