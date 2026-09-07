# Issue tracker: GitHub

本仓库的 issues 和 specs 存放在 GitHub issues。所有操作都使用 `gh` CLI。

项目文档正文默认使用中文；保留所有工具依赖的文件名、命令、配置键、固定标识、标签名和结构性标题的英文原文。如不确定某个字段是否会被 skill 解析，不要翻译。

## Conventions

- **Create an issue**: `gh issue create --title "..." --body "..."`。多行正文使用 heredoc。
- **Read an issue**: `gh issue view <number> --comments`，用 `jq` 过滤 comments，并同时获取 labels。
- **List issues**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`，按需使用 `--label` 和 `--state` 过滤。
- **Comment on an issue**: `gh issue comment <number> --body "..."`
- **Apply / remove labels**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> --comment "..."`

从 `git remote -v` 推断 repo；在 clone 目录内运行时，`gh` 会自动完成这个推断。

## Pull requests as a triage surface

**PRs as a request surface: no.** _(如果本仓库把外部 PRs 当作 feature requests，可以把这里改成 `yes`；`/triage` 会读取这个 flag。)_

当设置为 `yes` 时，PRs 使用和 issues 相同的 labels 与 states，并使用对应的 `gh pr` 命令：

- **Read a PR**: `gh pr view <number> --comments`；用 `gh pr diff <number>` 查看 diff。
- **List external PRs for triage**: `gh pr list --state open --json number,title,body,labels,author,authorAssociation,comments`，然后只保留 `authorAssociation` 为 `CONTRIBUTOR`、`FIRST_TIME_CONTRIBUTOR` 或 `NONE` 的 PRs，排除 `OWNER`/`MEMBER`/`COLLABORATOR`。
- **Comment / label / close**: `gh pr comment`、`gh pr edit --add-label`/`--remove-label`、`gh pr close`。

GitHub 的 issues 和 PRs 共用同一套编号空间，所以裸 `#42` 可能是任意一种：先用 `gh pr view 42` 判断，失败时再回退到 `gh issue view 42`。

## When a skill says "publish to the issue tracker"

创建一个 GitHub issue。

## When a skill says "fetch the relevant ticket"

运行 `gh issue view <number> --comments`。

## Wayfinding operations

供 `/wayfinder` 使用。**map** 是一个单独的 issue，**child** issues 作为 tickets。

- **Map**: 一个带有 `wayfinder:map` label 的单独 issue，正文包含 Notes / Decisions-so-far / Fog。创建命令：`gh issue create --label wayfinder:map`。
- **Child ticket**: 一个作为 GitHub sub-issue 关联到 map 的 issue（通过 sub-issues endpoint 使用 `gh api`）。如果 sub-issues 未启用，则把 child 加到 map body 的 task list，并在 child body 顶部写入 `Part of #<map>`。Labels: `wayfinder:<type>`（`research`/`prototype`/`grilling`/`task`）。一旦被 claim，该 ticket 分配给 driving dev。
- **Blocking**: 使用 GitHub native issue dependencies 作为 canonical UI-visible representation。用 `gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>` 添加依赖边，其中 `<blocker-db-id>` 是 blocker 的 numeric database id（`gh api repos/<owner>/<repo>/issues/<n> --jq .id`，不是 `#number` 或 `node_id`）。GitHub 会报告 `issue_dependencies_summary.blocked_by`（只统计 open blockers，也就是 live gate）。如果 dependencies 不可用，则回退为 child body 顶部的 `Blocked by: #<n>, #<n>` 行。所有 blockers 关闭后，ticket 才算 unblocked。
- **Frontier query**: 列出 map 的 open children（`gh issue list --state open`，范围限制到 map 的 sub-issues / task list），过滤掉存在 open blocker（`issue_dependencies_summary.blocked_by > 0`，或 `Blocked by` 行里有 open issue）或已有 assignee 的条目；按 map 顺序取第一个。
- **Claim**: `gh issue edit <n> --add-assignee @me`，这是 session 的 first write。
- **Resolve**: `gh issue comment <n> --body "<answer>"`，然后 `gh issue close <n>`，再向 map 的 Decisions-so-far 追加 context pointer（gist + link）。
