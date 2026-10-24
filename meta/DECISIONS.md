# Active Decisions

<!-- Only keep decisions that still constrain the project.
     Delete invalidated decisions; git records the history.
     Test: if an agent didn't know this decision,
     would it choose differently? Yes -> keep. No -> delete. -->

## #001 交付形态为可重定位 shade 的库
- **decided**: 2026-10-25
- **context**: 现有 MC UI 方案均以独立 mod 交付并要求使用者安装，依赖关系由库决定
- **decision**: 以 shade + relocate 进宿主 mod 为默认交付；库没有 modid 与自有入口，初始化由宿主驱动；jar-in-jar 仅作可选方式
- **rejected**: 独立 mod 交付——多份副本共存与"像普通库一样引用"是核心约束

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
