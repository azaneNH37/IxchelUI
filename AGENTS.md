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
