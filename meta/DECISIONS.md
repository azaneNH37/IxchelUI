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
