# Project Agent Instructions

<!-- Project-specific agent instructions.
     Global workflow (skills, corrections) is configured via the
     user-level AGENTS.md managed by `amadeus link`. -->

## Branching

禁止直接推送 `master`；所有变更在分支上提交，经 MR（GitHub PR）合入，合入方式只用 squash 且须 fast-forward（合入前先 rebase 到最新 `master`）。

早期开发期（仓库无其他协作者，见 meta #010）：PR 创建后直接 `gh pr merge --squash --admin`，不等 `check`；合入后 `check` 失败则另开修复 PR。仓库加入协作者或 ruleset 撤销 admin bypass 后，删除本段。

### PR 粒度

一个 PR 对应一批任务（用户一次交代的整体目标），不对应单个 task 或单次提交：

- 同批各 task 在同一分支上逐个提交；中途可以推送分支，但不开 PR。
- 随改动产生的文档与 `meta/` 修改提交到同一分支，不单独开 PR。
- 开 PR 的时机：全部 task 完成，meta 的 After Work 提议已经过用户确认并提交，或用户明确不需要。PR 描述按 task 分条列出改动。
- 只有两种情况单独开 PR：与本批无关的修复；用户明确要求先合入的部分。
- 不确定本批是否已经完结时，先问用户，不自行开 PR。

## 分包

包按模块对外承担的契约划分，不按技术类型（render、resource、util）划分，也不平铺。Java 跨包即 `public`，包边界就是结构性约束：划包时逐个回答"这个类替谁做什么、谁该看见它、调用该经过哪一层"。

划包过程中暴露的问题当作设计缺陷修掉，不靠挪文件掩盖：

- 同一职责散在多个类里（例：原版元素的标签、绘制、tooltip 分散在 `McPainter`、`McImageMeasurer`、`Tooltips`、`McElements`）；
- 某一层绕开本该经过的单元，自己重写一遍（例：加载器层的 HUD 自行加载、监听、画错误叠加层，没有复用 Screen 的文档生命周期）；
- 为了跨包调用才放宽的可见性（例：功能的意图包放在 `network/`，迫使 `MachineBlockEntity` 的设置方法改为 `public`）。

新增类先确定它属于哪个契约所在的包；找不到合适的包时，先重新审视划分，不要新建杂项包。
