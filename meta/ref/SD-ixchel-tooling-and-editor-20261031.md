---
tags: [survey, design]
date: 2026-10-31
origin: E1 的 S8 模板三轮加载错误都要进游戏（每轮约 50 s）才暴露；后续的可视化编辑器与外部 agent 手写模板都需要运行前校验，先定工具的技术栈、位置与分发
related: [ref/D-ixchel-roadmap-20261029.md, ref/SD-ixchel-template-and-style-language-20261029.md, ref/D-ixchel-ci-workflows-20261026.md, DECISIONS.md#010]
---

# Ixchel UI: Tooling and Visual Editor

## Summary

linter 与预览只用 core 的加载器与布局，不另写解析：新增只依赖 core 的 JVM 工具 `tools/cli`（`:ixchel-tools`），对 agent 和 CI 是命令行，对编辑器是本地服务；可视化编辑器是 `tools/editor` 下的 TS 前端，构建产物打进工具 jar 由 `ixchel serve` 提供。工具不进 mod jar，不依赖 mc 层与加载器。浏览器内运行 core（TeaVM）放到最后，spike 通过再做。

## Detail

### 1. 起因

E1 写 S8 时三轮失败都是结构错误：组件文件含多个 `<template>`、组件文件链接 `@ixchel/dark.css`、参数名 `title` 与外壳元素属性冲突（及其连带的"未知标签"）。它们不需要游戏运行时，但只有进游戏加载才报出。外部作者的 agent 手写模板时最常见的错误也是写出子集之外的标签、属性与 CSS。

### 2. 架构

```
游戏运行时：  mc 适配层 ──> core
工具：        tools/cli（Java）──> core
              tools/editor（TS）──JSON over HTTP/WS──> tools/cli
              agent / CI ──> tools/cli
              editor 写回 html/css 源文件 ──开发期热重载──> 游戏
原生元素声明（数据）由 mc 层与 tools/cli 共读
```

- 事实来源只有 core：lint 走与游戏相同的加载与诊断路径，预览走相同的布局与 `DisplayList`。用 TS 重写解析或让浏览器直接渲染这份 HTML/CSS 都会形成两套语义（`display` 初值、IFC 规则、MC 字体、组件、绑定），不选。
- 游戏内热重载是最高保真的预览，编辑器预览不追求像素一致。
- 前提（已核对）：core 编译期只依赖 `dev.vfyjxf:taffy`，它是纯 Java 移植，jar 内无原生库；链接图（相对路径、递归加载、组件）在 core（L2），mc 层只按 id 读文本，换成按文件系统读即可离线加载。

### 3. 技术栈

| 部分 | 选择 | 理由 |
|---|---|---|
| lint / 预览引擎 | core（JVM） | 与游戏同一路径，零分歧 |
| CLI | Java fat jar（shadow），`java -jar ixchel-tools.jar` | mod 作者必有 Java；另给 Gradle 任务供 CI |
| 编辑器前端 | TypeScript + Vite + React；代码区 CodeMirror 6 | React 生态与 agent 熟悉度最好；CodeMirror 6 比 Monaco 轻，自带 HTML/CSS 语言与 lint 接口 |
| 预览渲染 | Canvas 2D 回放 `DisplayList` JSON | 命令只有 Rect/Text/Image/Custom/Clip/Transform 几类 |
| 前后端连接 | 本地进程 `ixchel serve`（HTTP/WS） | 工作量最小，无兼容风险 |

可选的浏览器内运行 core（编辑器可纯静态托管、不装 Java）：

| 方案 | 评价 |
|---|---|
| TeaVM（Apache-2.0，0.16.0，2026-10） | 字节码 AOT 到 JS / Wasm GC / C，产物小；官方定位是"不能把现有大代码库整体搬过去"，反射、资源、类加载受限，0.16 已支持方法反射、泛型信息与 `getResourceAsStream`，反射成员须按 policy 声明 |
| CheerpJ | 不改代码跑 JVM 字节码，商用许可受限、体积与启动成本大，不选 |
| GraalVM Web Image | 仍是实验阶段，不选 |

core 中会触到 TeaVM 限制的只有：`binding.compile.Compiler` 的 `getDeclaredFields`、`RecordComponent`、`MethodHandles.lookup().unreflect`（编辑器预览走无模型或 fixture 数据，不经这条路径；`MethodHandles` 大概率不支持，需要时改 `Method.invoke`）；`LibrarySheets` 的 `getResourceAsStream` 与 `ConcurrentHashMap`。解析、样式、布局、绘制主干是普通集合、record、lambda。以上适配度均为推断，以 spike 为准。

### 4. CLI

```
ixchel lint   <资源根> [--json]        # 无模型结构加载；file:line:col + 稳定错误码；有错误时退出码非 0
ixchel schema [--json]                 # 导出支持的标签、属性、CSS 属性与取值、库内主题名
ixchel render <模板 id> --size 427x240 --out a.png [--data fixture.json]
ixchel serve  --port N                 # 编辑器后端：load / lint / layout / hit；同时提供编辑器静态页面
```

`schema` 给 agent 写模板前查子集；`render` 让 agent 不进游戏看布局。之后可在 lint 上加 LSP（lsp4j），VS Code 与走 LSP 的 agent 直接得到诊断（可选）。

### 5. core 与 mc 层需要的改动

1. 原生元素声明数据化：`<mc-item>` 等现由 mc 层 `McElements.TAGS` 以代码登记，工具拿不到；抽出不依赖 MC 类的声明（标签名、载荷属性、允许的属性），mc 层与工具共读。
2. 无模型编译模式：模板不写 ViewModel 类名（语言文档已否决），lint 无从得知名字对应的模型，先跳过绑定名解析，只查结构。
3. 源码位置贯通到元素、属性与 CSS 声明：编辑器要按源码区间写回、保持最小 diff；现在只有指令与诊断带位置。这是编辑器的主要前置。
4. 工具版测量器：从用户本机 MC client jar 读字体字形宽度（不打包 Mojang 资源），读不到退近似宽度并在预览上标出；物品图标读 `textures/item/<id>.png`，没有则画占位框。

### 6. 仓库位置与分发

```
tools/
  cli/      Gradle 项目 :ixchel-tools（只依赖 :ixchel-core），包按契约：lint、render、schema、serve
  editor/   npm 项目（TS + Vite + React），dist/ 与 node_modules/ 不提交
```

- 构建：`tools/editor` 的 `npm ci && vite build` 产出 `dist/`，`:ixchel-tools` 的 `processResources` 拷进 jar 的 `/editor/`，再打 fat jar。Node 由 `com.github.node-gradle.node`（`download = true`）下到 Gradle 缓存，入口仍只有 `./gradlew build`；npm + `package-lock.json`。全量 `build` 因此多一次前端构建，嫌慢时再把 `:ixchel-tools` 移出默认 `build`。
- 前端开发：先起 `ixchel serve`，`tools/editor` 里 `npm run dev`，Vite 代理 API 到 serve；只改 Java 的人不碰 Node。
- 分发：前端没有单独分发物，随 jar 走；`ixchel serve` 启动后打开浏览器。jar 挂 GitHub Releases，与库同版本，随 `release` 工作流（#010，未实现）一起做。
- 不选：托管静态站点连本地 CLI（页面与 jar 分别发版会协议错配，https 页面访问 http://localhost 受浏览器私有网络访问限制）；npm / `npx` 分发（用户已需 Java，再要 Node 是多一个前提）；Electron / Tauri（分平台打包、签名、更新，工作量最大）。
- TeaVM spike 成功后，同一份 `dist/` 加 Wasm 发到 Pages（可与 Doc 阶段的 `docs/` 站点同处），jar 内置版保留。

### 7. 分阶段

| 阶段 | 内容 | 受益方 |
|---|---|---|
| T1 | `tools/cli` 的 lint 与 schema，sample/testmod 加挂在 `check` 上的 lint 任务；§5 第 1、2 项 | agent、CI、仓库自身 |
| T2 | render：工具版测量器，`DisplayList` 转 PNG，fixture 数据 | agent 离线看布局 |
| T3 | serve 与编辑器：源码区、结构树、Canvas 预览、预览点选定位源码；§5 第 3 项 | 人 |
| T4（可选） | 拖拽与属性面板编辑；TeaVM spike；LSP | 人 |

T1、T2 只在仓库内用（经 Gradle 任务），到 Doc 阶段再对外发布 jar；外部作者确实需要在 CI 里跑时再出 Gradle 插件。

### 8. 待定

- lint 如何检查绑定名：(a) 模型描述文件（如 `<模板>.model.json`，手写或由 Gradle 任务反射编译后的 ViewModel 生成），同时作为编辑器预览的 fixture 结构；(b) 宿主在代码里登记模板与模型类，工具扫描编译产物。倾向 (a)。
- 编辑器是否必须纯静态托管：若是，TeaVM spike 前移到 T3 之前。
