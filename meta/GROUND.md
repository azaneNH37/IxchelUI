---
verified: 2026-10-25
by: agent
---

# Ground

## What this is

Ixchel UI：面向 Minecraft 的可嵌入 Web 风格 UI 库（HTML + 标准 CSS 子集 + 客户端数据需求声明），以 shade 进宿主 mod 的方式交付，追求零全局副作用。当前仅有定位与前瞻方案，尚无代码。

## Architecture

```
testmod-neoforge-1.21.1 → ixchel-neoforge-1.21.1 → ixchel-mc-1.21.1 → ixchel-core
```

依赖单向；可见性由编译期 classpath 强制：core 不含 MC，mc 层只见原版（NeoForm vanilla 模式），neoforge 层可见加载器 API。公共构建配置在 `build-logic` 约定插件中。目标分层与渲染/数据设计见 `ref/SD-ixchel-ui-positioning-and-adapter-design-20261025.md`。

## Modules

- ixchel-core: 纯 Java 内核（DOM/CSS/布局/事件/ViewModel），尚无源码，[draft]
- ixchel-mc-1.21.1: 仅 vanilla 类的适配层，尚无源码，[draft]
- ixchel-neoforge-1.21.1: NeoForge 加载器胶水，尚无源码，[draft]
- testmod-neoforge-1.21.1: 开发期宿主 mod，库模块源码并入其中运行，不发布，[draft]

## In progress

- 构建骨架已就绪（`build` 与 `runClient` 通过）；内核原型未开始。

## Not here

- 不是独立安装的 mod：没有 modid、没有自己的入口，初始化由宿主驱动。
- 默认产物不含 mixin、不注册全局 ID、不携带固定 namespace 的 `assets/`、不改主 render target。
- 不内嵌 JS 引擎，不追求浏览器渲染等价。
- 不含复制层或意图 RPC；外部数据来源由宿主接入。
- 前身 Kumiko 仅为研究过渡项目，不是依赖或对接对象。
