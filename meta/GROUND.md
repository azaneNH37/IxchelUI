---
verified: 2026-10-26
by: agent
---

# Ground

## What this is

Ixchel UI：面向 Minecraft 的可嵌入 Web 风格 UI 库（HTML + 标准 CSS 子集 + 客户端数据需求声明），以 shade 进宿主 mod 的方式交付，零全局副作用，宿主拥有实际运行的版本；使用方的替代选项是手写原版 GUI，首要目标是自有 mod 的 UI 基础设施。

## Architecture

```
testmod-forge-1.20.1    → ixchel-forge-1.20.1    → ixchel-mc-1.20.1 → ixchel-core
testmod-neoforge-1.21.1 → ixchel-neoforge-1.21.1 → ixchel-mc-1.21.1 ↗
```

依赖单向；可见性由编译期 classpath 强制：core 不含 MC（Java 17，两个平台共用），mc 层只见原版（1.20.1 用 legacyforge `mcpVersion` 模式，1.21.1 用 NeoForm 模式），加载器层可见加载器 API。1.20.1 模块用 Java 17，1.21.1 模块用 Java 21。公共构建配置在 `build-logic` 约定插件中。目标分层与渲染设计见 `ref/SD-ixchel-ui-positioning-and-adapter-design-20261025.md`，模块与包划分见 `ref/D-ixchel-module-breakdown-20261026.md`，数据绑定见 `ref/SD-ixchel-viewmodel-and-binding-20261026.md`，容器槽位接入见 `ref/SDG-ixchel-container-slot-integration-20261026.md`；平台分工见 #005。

目录按层分组，项目名保持扁平（项目名即 artifactId），映射写在 `settings.gradle.kts`：`:ixchel-mc-1.20.1` 位于 `mc/1.20.1/`。

## Modules

- ixchel-core (`core/`): 纯 Java 内核（模板/CSS/样式/布局/事件/绑定），单一模块内按包分层 (#006)，Java 17；M1 原型完成（模板→样式→布局→DisplayList，弹药 HUD 黄金测试通过），事件/绑定/动画未开始，[draft]
- ixchel-mc-1.20.1 (`mc/1.20.1/`): 仅 vanilla 类的适配层（Forge 1.20.1 完整 MVP），尚无源码，[draft]
- ixchel-mc-1.21.1 (`mc/1.21.1/`): 仅 vanilla 类的适配层（NeoForge 1.21.1 对齐最小集），尚无源码，[draft]
- ixchel-forge-1.20.1 (`loader/forge-1.20.1/`): Forge 加载器胶水，尚无源码，[draft]
- ixchel-neoforge-1.21.1 (`loader/neoforge-1.21.1/`): NeoForge 加载器胶水，尚无源码，[draft]
- testmod-forge-1.20.1 (`testmod/forge-1.20.1/`): Forge 1.20.1 开发期宿主 mod，库模块源码并入其中运行，不发布，[draft]
- testmod-neoforge-1.21.1 (`testmod/neoforge-1.21.1/`): NeoForge 1.21.1 开发期宿主 mod，同上，[draft]

## In progress

- 双平台构建骨架已就绪（`build` 与两个 testmod 的 `runClient` 通过）；core M1 完成，下一步 M2（mc 1.20.1 绘制与 HUD 宿主），M1 已知限制见 `ref/D-ixchel-module-breakdown-20261026.md` §3。
- CI：`check`（`.github/workflows/check.yml`，PR 与 `master` 上全量 `./gradlew build`）已上线，`master` ruleset 要求它通过，admin 可在 PR 上用 `--admin` 越过；`release` 与 `docs` 流程未实现。见 #010 与 `ref/D-ixchel-ci-workflows-20261026.md`。
- 1.20.1 库模块发布 mojmap 还是 SRG 产物未定，待 shade 配方验证；目前 legacyforge 对两个 1.20.1 库模块的 `jar` 都自动 reobf 为 SRG。
- testmod 以 sourceSet 并入方式运行库，不经过 shade/relocate；shade 链路尚无覆盖。

## Not here

- 不是独立安装的 mod：没有 modid、没有自己的入口，初始化由宿主驱动。
- 默认产物不含 mixin、不注册全局 ID、不携带固定 namespace 的 `assets/`、不改主 render target。
- 不内嵌 JS 引擎，不追求浏览器渲染等价。
- 不含复制层或意图 RPC；外部数据来源由宿主接入。
- 不依赖 jsoup；模板由自写严格解析器解析 (#007)。
- 前身 Kumiko 仅为研究过渡项目，不是依赖或对接对象。
