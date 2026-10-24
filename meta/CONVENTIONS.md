# Conventions

<!-- Group by domain, not by do/don't.
     Only write rules not inferable from code patterns.
     Keep under 40 lines. Exceeding -> review and trim. -->

## Stack
- 内核模块编译期 classpath 不含 MC；MC 类只出现在适配层，加载器 API 只出现在加载器胶水层
- 布局只用 `dev.vfyjxf:taffy`，不引入 Yoga
- 包名与 groupId 前缀统一为 `com.azane.ixchel`；artifactId 为 `ixchel-<层>[-<MC 版本>]`，MC 版本写在 artifactId 而非 version
- 不发布 Maven Central；开发期用 `publishToMavenLocal`
- `ixchel-core` 用 Java 17 工具链（#005）；sealed 层次（如 `DrawCommand`）以 visitor 分派，保留 17 下缺失的 switch 穷尽检查
- 文本读写一律显式 `StandardCharsets.UTF_8`：Java 17 默认字符集依平台，中文 Windows 上为 GBK
- 1.20.1 模块用 `net.neoforged.moddev.legacyforge`（mc 层用 `mcpVersion` 原版模式），1.21.1 模块用 `net.neoforged.moddev`；两个插件版本号相同

## 嵌入边界
- 库自带资源（默认样式表、shader 源码）放包路径下，用 `Class.getResourceAsStream` 读取；不放 `assets/<固定 namespace>/` (#001)
- 需要 ResourceLocation 时使用宿主 modid 作为 namespace
- 不注册全局 ID，不改主 render target，不做每 tick 全局状态改写

## 渲染
- 恢复 FBO 走 `GlStateManager` 或 `RenderTarget.bindWrite`，不直接调用原生 GL 绑定
- 进出私有图层前先 `flush()`，恢复进入前绑定的 FBO 而非固定回 main
- 圆角裁剪显式 opt-in，走私有图层 + 遮罩纹理；不使用主 target 模板缓冲
