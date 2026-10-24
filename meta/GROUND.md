---
verified: 2026-10-25
by: agent
---

# Ground

## What this is

Ixchel UI：面向 Minecraft 的可嵌入 Web 风格 UI 库（HTML + 标准 CSS 子集 + 客户端数据需求声明），以 shade 进宿主 mod 的方式交付，追求零全局副作用。当前仅有定位与前瞻方案，尚无代码。

## Architecture

尚无实现。候选分层（纯 Java 内核 / 仅 vanilla 类的适配层 / 加载器胶水）见 `ref/SD-ixchel-ui-positioning-and-adapter-design-20261025.md`，不视为已实现架构。

## Modules

（无）

## In progress

- 定位与适配层方案初版已完成（见上述 SD）；构建骨架、内核原型均未开始。

## Not here

- 不是独立安装的 mod：没有 modid、没有自己的入口，初始化由宿主驱动。
- 默认产物不含 mixin、不注册全局 ID、不携带固定 namespace 的 `assets/`、不改主 render target。
- 不内嵌 JS 引擎，不追求浏览器渲染等价。
- 不含复制层或意图 RPC；外部数据来源由宿主接入。
- 前身 Kumiko 仅为研究过渡项目，不是依赖或对接对象。
