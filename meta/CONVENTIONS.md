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
- 模板解析器自写，不引入 jsoup (#007)；core 内包依赖单向，新增包先登记到 `ref/D-ixchel-module-breakdown-20261026.md` §2 (#006)

## 嵌入边界
- 库自带资源（默认样式表、shader 源码）放包路径下，用 `Class.getResourceAsStream` 读取；不放 `assets/<固定 namespace>/` (#001)
- 需要 ResourceLocation 时使用宿主 modid 作为 namespace
- 不注册全局 ID，不改主 render target，不做每 tick 全局状态改写
- 库自有类型不出现在宿主公开 API；与其它 mod 交互只用原版/加载器类型，数据端口只用 JDK 类型 (#004)
- 不在副本间共享资源 ID 或做跨副本去重；纹理、shader 一律以宿主 modid 注册（`TextureManager.register` 同 ID 会关闭旧纹理）
- 无法按 modid 分区的进程级状态（GL/`RenderSystem`、GLFW 光标与回调、`Minecraft.screen`）只借用并原样归还
- FBO、遮罩纹理等 GPU 资源惰性分配、用完释放
- 反射访问 MC 成员须处理运行时名：Forge 1.20.1 为 SRG

## 宿主接入
- Java 侧只传模板 `ResourceLocation`，样式表由模板首行 `<link rel="stylesheet" href="…"/>` 声明（相对模板目录或 `ns:path`）；不再有"模板 + CSS 列表"的构造形态
- 按键在模板里用 `on:keydown.<key>[.shift|.ctrl|.alt]` 过滤，顶层元素的键处理器是文档级；不在 Java 侧对 `root()` 挂原始监听
- 轮询来源用 `ClientSources.polled/player`，其等值对 `ItemStack`、record、List 结构比较；不为排除 `ItemStack` 手写 record `equals`

## 渲染
- 恢复 FBO 走 `GlStateManager` 或 `RenderTarget.bindWrite`，不直接调用原生 GL 绑定
- 进出私有图层前先 `flush()`，恢复进入前绑定的 FBO 而非固定回 main
- 圆角裁剪显式 opt-in，走私有图层 + 遮罩纹理；不使用主 target 模板缓冲
- Painter 不逐条调用 `GuiGraphics.fill/blit/drawString`（每次调用都会提交一次）；自写顶点，只在渲染状态或纹理切换时提交；覆盖率纹理与图片共用一张图集

## 容器槽位
- `Slot.x/y` 是槽位内部 16×16 物品区左上角，布局结果写回它；隐藏槽位须 `isActive=false` 且 `x=y=-2000`（REI 高亮不检查 `isActive`），见 `ref/SDG-ixchel-container-slot-integration-20261026.md`
- `Slot.x/y` 用反射写入，不用 AT/mixin (#008)
- 接管 vanilla 绘制/交互且可能与其它 mod 冲突的增强，默认关闭，以宿主 API 参数开启 (#009)
