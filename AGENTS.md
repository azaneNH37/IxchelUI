# Project Agent Instructions

<!-- Project-specific agent instructions.
     Global workflow (skills, corrections) is configured via the
     user-level AGENTS.md managed by `amadeus link`. -->

## Branching

禁止直接推送 `master`；所有变更在分支上提交，经 MR（GitHub PR）合入，合入方式只用 squash 且须 fast-forward（合入前先 rebase 到最新 `master`）。

早期开发期（仓库无其他协作者，见 meta #010）：PR 创建后直接 `gh pr merge --squash --admin`，不等 `check`；合入后 `check` 失败则另开修复 PR。仓库加入协作者或 ruleset 撤销 admin bypass 后，删除本段。
