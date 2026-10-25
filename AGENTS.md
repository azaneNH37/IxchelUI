# Project Agent Instructions

<!-- Project-specific agent instructions.
     Global workflow (skills, corrections) is configured via the
     user-level AGENTS.md managed by `amadeus link`. -->

## Branching

禁止直接推送 `master`；所有变更在分支上提交，经 MR（GitHub PR）合入，合入方式只用 squash 且须 fast-forward（合入前先 rebase 到最新 `master`）。
