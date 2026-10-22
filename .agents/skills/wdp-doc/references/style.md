<a id="page-style"></a>

# 页面风格

<a id="summary"></a>

## 摘要

页面级风格偏好，让 WDP 页面易于扫读且难以误读：简短的 Summary、受控技术表达、主要部分之间的 `-----` 分隔线、保持章节标题可见的 `<details>` 折叠，以及有节制的强调。

<a id="table-of-contents"></a>

## 目录

- [简短摘要](#short-summary)
- [受控技术表达](#controlled-technical-expression)
- [章节分隔线](#section-separators)
- [可折叠内容节](#foldable-content-sections)
- [强调纪律](#emphasis-discipline)
- [Dev Note](#dev-note)

<a id="short-summary"></a>

## 简短摘要

每个手写页面都以简短的 `Summary` 开头：一段三到五句的文字，说明读者能做什么或观察到什么、读者为何在意、运行模型，以及最重要的边界。目录与各章节承载细节；放置与章节顺序见 [structure-hierarchy.md](structure-hierarchy.md)。

<a id="controlled-technical-expression"></a>

## 受控技术表达

对 agent 或维护者必须准确解析的技术散文，采用一套清晰度纪律逐条评审。

- 点明主体与动作。主体重要时优先使用主动语态。
- 每个概念用一个稳定术语。不要为变化而轮换同义词。
- 偏好直接动词。存在精确动词时，替换名词化表达与含混的短语。
- 每句只放一条指令。三个或更多步骤或条件时使用列表。
- 拆开分号与长从句链。每段只谈一个主题。
- 删除没有依据的质量形容词与叠加的模糊限定。保留来源中的每个事实与每层不确定性。

把指令 20 词、描述 25 词当作评审提示，而非机械门禁。拆分会让某个条件或关系隐没时，保留更长的句子。绝不为了达到长度目标而删除或强化 `must`、`may`、`never`、时序、例外、数字或其他契约术语。[散文标准](../../wdp-prose-standard/SKILL.md)拥有完整命题规则。

<a id="section-separators"></a>

## 章节分隔线

用独占一行的 `-----` 水平线分隔页面的主要部分，其前后各留一个空行。直接跟在段落后面的水平线会被解析成 Setext 标题。典型布局这样分隔：使用节 → 折叠的开发者节 → Further Exploration → Dev Note。

<a id="foldable-content-sections"></a>

## 可折叠内容节

把面向开发者的细节与末尾的 Dev Note 折进 GitHub 原生的 `<details>`/`<summary>` 块。保持章节标题（H2 或 H3）及其 `<a id>` 锚点可见；只折叠标题下的内容。块内 `<summary>` 之后留一个空行，每行 Markdown 保持在第 0 列（缩进的内容会变成代码块），并在空行之后用 `</details>` 关闭。折叠内的标题、列表、表格与链接正常解析，并保留其锚点。

<a id="emphasis-discipline"></a>

## 强调纪律

粗体只留给改变行为的那条从句，或真正重要的那组比较。基准表里，把列标题与每行的最佳值加粗，如参考范例所示。

<a id="dev-note"></a>

## Dev Note

None.
