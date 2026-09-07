# Domain Docs

工程 skills 在探索代码库时，应按本文规则读取本仓库的 domain documentation。

项目文档正文默认使用中文；保留所有工具依赖的文件名、命令、配置键、固定标识、标签名和结构性标题的英文原文。如不确定某个字段是否会被 skill 解析，不要翻译。

## Before exploring, read these

- repo root 下的 **`CONTEXT.md`**，或者
- 如果 repo root 下存在 **`CONTEXT-MAP.md`**：它会指向每个 context 对应的 `CONTEXT.md`。读取和当前主题相关的文件。
- **`docs/adr/`**：读取与你将要修改区域相关的 ADRs。在 multi-context repos 中，也检查 `src/<context>/docs/adr/` 里的 context-scoped decisions。

如果这些文件不存在，**proceed silently**。不要把缺失当作问题提示，也不要预先建议创建它们。`/domain-modeling` skill（可通过 `/grill-with-docs` 和 `/improve-codebase-architecture` 触发）会在术语或决策真正被解析时按需创建。

## File structure

Single-context repo（大多数 repo）：

```text
/
|-- CONTEXT.md
|-- docs/
|   `-- adr/
|       |-- 0001-event-sourced-orders.md
|       `-- 0002-postgres-for-write-model.md
`-- src/
```

Multi-context repo（repo root 存在 `CONTEXT-MAP.md`）：

```text
/
|-- CONTEXT-MAP.md
|-- docs/
|   `-- adr/                          # system-wide decisions
`-- src/
    |-- ordering/
    |   |-- CONTEXT.md
    |   `-- docs/
    |       `-- adr/                  # context-specific decisions
    `-- billing/
        |-- CONTEXT.md
        `-- docs/
            `-- adr/
```

## Use the glossary's vocabulary

当输出中需要命名 domain concept（例如 issue title、refactor proposal、hypothesis、test name）时，使用 `CONTEXT.md` 中定义的术语。不要漂移到 glossary 明确避免的同义词。

如果你需要的 concept 还不在 glossary 中，这就是一个信号：要么你正在发明项目并不使用的语言（需要重新考虑），要么确实存在 gap（记录给 `/domain-modeling`）。

## Flag ADR conflicts

如果你的输出和已有 ADR 冲突，要明确指出，而不是静默覆盖：

> _Contradicts ADR-0007 (event-sourced orders), but worth reopening because..._
