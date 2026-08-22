# Pi Agent 设计研究与小万优化映射

## 研究基线

- 本地源码：`/Users/ocean/code/pi`
- 上游仓库：`https://github.com/earendil-works/pi`
- 分析 commit：`581d75a89cea21e50d6a26df840352f94427f633`
- commit 时间：2026-08-13T00:53:22+02:00
- 重点范围：`packages/agent`、`packages/coding-agent/src/core`

本文只把能够说明收益、失败边界和回归门槛的设计迁移到小万。Pi 面向桌面编程 Agent，小万同时操作 Android、终端、浏览器和特权能力，不能直接复制其默认并行或自扩展策略。

## 结论

Pi 的核心优势不是某一段 Prompt，而是把 Agent 拆成可组合的运行时协议：

1. 模型输出只是候选动作，必须经过上下文转换、参数校验、工具前置钩子和执行策略。
2. 会话、运行状态和外部副作用分开存储；副作用前后有明确的意图与结算边界。
3. 压缩不是简单删除历史，而是生成结构化检查点并保留最近上下文、分支信息和文件操作。
4. 运行中的用户消息分为 steering 和 follow-up，分别在当前工具轮后和 Agent 原本结束后注入。
5. 重试、取消、截断、并行和恢复都是显式状态，不依赖模型自行猜测。
6. Skill、扩展和搜索均采用“稳定身份 + 渐进披露”，避免把全部资源永久塞进系统提示。

所谓“自进化”因此应理解为：运行时持续改进可检索事实、Skill、失败教训和执行策略，而不是让模型无约束地修改自身规则或权重。

## 源码路径与机制

### Agent loop

`packages/agent/src/agent-loop.ts`：

- 每次请求前执行 `transformContext`，随后才转换为供应商消息。
- `finishReason=length` 且包含 tool call 时，所有调用都返回错误而不执行，避免截断参数碰巧通过 JSON 校验。
- 工具先完成参数准备与前置钩子，再执行，最后经过后置钩子。
- 纯并行工具可以并发完成，但 tool result 仍按模型原始调用顺序写回。
- 每轮结束后可刷新 context、model、thinking 和 tools。

`packages/agent/src/agent.ts`：

- 明确区分 steering 与 follow-up 队列。
- 队列可配置一次投递一条或全部投递。
- AbortController 同时覆盖模型流、工具执行和等待阶段。

### Durable harness

`packages/agent/docs/harness.md`、`packages/agent/src/harness/session`、`packages/agent/src/harness/reducer.ts`：

- conversation entry 为不可变树，lane 是树上的命名游标。
- facts 是可覆盖的命名状态，usage 是只追加账本。
- 外部请求和工具副作用采用 effect sandwich：先提交执行意图，再执行不确定副作用，最后提交结果。
- reducer 会拒绝不可能出现的日志组合，不静默“修复”损坏状态。
- operation state 是完整程序计数器，恢复时读取当前状态，而不是根据缺失记录猜测。

当前 commit 的 `packages/agent/src/harness/agent-harness.ts` 仍是部分实现骨架，`prompt`、`compact`、`fork`、`restore` 等路径仍包含 `HarnessNotImplemented`。因此 durable harness 适合作为演进方向，不能当作已经完全验证的可复制实现。

### Compaction

`packages/agent/src/harness/compaction/compaction.ts`：

- 优先使用供应商实际 usage，并估算 usage 之后新增消息的 token。
- 在 context window 前预留 summary/output 空间，而不是等到完全溢出。
- 检查点固定包含 Goal、Constraints、Progress、Decisions、Next Steps、Critical Context。
- 保留最近 token tail；若切在超长单轮中间，会单独总结该轮前缀。
- 文件读取和修改记录由工具消息确定性提取，再追加到摘要，减少模型遗漏。
- context overflow 只允许 compact-and-retry 一次，避免无限循环。

### Tool runtime

`packages/agent/src/harness/tools`：

- 路径解析、编辑 diff、写入和 shell 输出捕获各自隔离。
- shell 输出达到限制后，模型只接收尾部与截断元数据，完整输出写入临时文件。
- 工具可以持续上报 progress，完成后拒绝迟到更新。
- 单个工具可声明 sequential，覆盖全局 parallel 策略。

### Extensions, skills, search, telemetry

`packages/coding-agent/src/core/agent-session.ts`：

- input、before-agent、tool-call、tool-result、compaction 和 session 生命周期都有明确钩子。
- 扩展重载后，每轮会重新读取当前 runner、system prompt、model 和 tool 集合。
- 瞬态模型错误使用可取消的指数退避；额度、账单和配额耗尽会快速失败。

`packages/coding-agent/docs/skills.md`：

- 系统提示只携带 Skill 元数据；正文按任务或显式命令加载。

`packages/agent/src/search`：

- 搜索命中只保证稳定的 `(sessionId, entryId)` 身份。
- 搜索结果用 AsyncIterable 渐进返回，并支持 AbortSignal 取消。
- 索引是可重建派生状态，不是会话事实来源。

`packages/agent/src/harness/telemetry.ts`：

- provider、model、stop reason、token、cache、cost、首块延迟、重试和 operation outcome 使用低基数结构化字段。

## 与小万的对照

### 小万已有等价机制

- `AgentToolConcurrencyPolicy` 已采用“默认串行、纯读白名单并行”，比 Pi 的默认全局并行更适合 Android 副作用工具。
- `AgentOrchestrator` 已保持并行执行、原调用顺序写回，并支持工具手动中断。
- `AgentConversationContextCompactor` 已保留当前轮并持久化历史摘要。
- `WorkspaceMemoryService`、Skill loader、长期记忆索引和 `memory_load` 已具备渐进披露基础。
- `streamTurnWithRetry` 已使用协程 `delay`，取消运行时不会继续等待重试。
- `SubagentDispatcher` 已有受限 profile、轮数和输出预算。

### 小万上下文窗口审计

当前链路由四层组成：

1. `models_dev_catalog_service.dart` 从 models.dev 的 `limit.context` 读取模型窗口；供应商接口显式返回的 `contextLimit` 优先，models.dev 只做补全。
2. `chat_page_model_context.dart` 将所选模型的 `contextLimit` 和用户手动阈值写入会话，并随 `modelOverride` 发给原生层；上下文圆环展示 `latestPromptTokens / promptTokenThreshold`。
3. `AssistsCoreManager` 校验 provider profile 后构造 `AgentModelOverride`；本次审计发现直连 Agent 分支此前遗漏 `contextLimit`，导致压缩器可能退回 128k 默认值，现已补齐。会话阈值若大于当前模型窗口，现在会被模型窗口夹紧，避免切换到小窗口模型后仍按旧阈值运行。
4. `AgentOrchestrator` 读取每轮 usage，`AgentConversationContextCompactor` 持久化 token 使用并把旧历史替换为结构化 summary；当前用户轮、后续 assistant/tool tail 保留为原始消息。

审计前的主要风险是：压缩要等 prompt token 超过完整窗口才触发；判断未计入本轮 completion；下一请求先被 provider 拒绝时没有恢复路径；部分兼容服务以 `length + 0 output` 静默表示窗口耗尽。它们都会让长任务在最需要续接时突然中断。

### 本轮直接采用

1. **截断工具调用保护**：`finishReason` 表示长度截断时，不解析、不校验、不执行任何 tool call；逐个写回“未执行”结果，让模型用完整参数重新发起。
2. **结构化压缩检查点**：压缩摘要固定 Goal、Constraints、Progress、Decisions、Next Steps、Critical Context，降低长任务续接时遗漏约束和当前状态的概率。
3. **重试分类补强**：补充 HTTP 500/524/529 等瞬态失败；429 若明确是 quota、billing 或账户限额则快速失败，避免无意义等待。
4. **模型窗口闭环**：修复直连 Agent `contextLimit` 丢失；会话阈值不得超过当前模型容量，UI 仍展示真实容量和 prompt 使用量。
5. **提前压缩与完整占用**：实际触发点为 `capacity - reserve`。reserve 取窗口的 1/8，最少 2048、最多 16384，且小窗口最多预留一半；判断使用供应商 `total_tokens` 与 `prompt + completion` 的较大值，而不是只看旧 prompt。
6. **有界 overflow 恢复**：识别主流 provider 的 context overflow 错误，以及输入达到 99% 容量时的 `length + 0 output`；压缩成功后原轮重试一次且不消耗正常轮数，第二次溢出立即停止。额度、限流和 throttling 不会被误判为上下文溢出。
7. **上一轮已采用**：动态 Skill/记忆移出缓存系统提示；记忆去重和限量；失败签名合并、脱敏、上限与可验证参数修复闭环。

### 候选后续优化

这些方向有价值，但需要数据库迁移、UI 语义或真实设备故障注入，不能在没有专项验证时直接并入：

1. **Steering/follow-up 队列**：允许用户在小万运行中追加“立即调整”或“完成后继续”。需要明确消息持久化、取消和多端同步语义。
2. **Durable operation state**：为模型请求、工具执行和等待权限记录 intent/result，进程重启后避免重复副作用。应先从 terminal、browser、privileged action 三类非幂等工具试点。
3. **确定性压缩附件**：从 tool result 中提取已读文件、已改文件、生成 artifact 和未完成工具，独立于模型摘要保存。
4. **完整工具输出 artifact**：大输出在模型上下文中保留 head/tail 和路径，完整内容存 workspace 临时文件并可按需读取。
5. **统一 before/after tool hook**：把权限、安全策略、结果裁剪、失败学习和 telemetry 从编排器分支收敛为有顺序、有错误语义的 hook chain。
6. **可取消的跨会话搜索**：命中先返回稳定 entry id，再按需加载正文；为后续 SQLite FTS 或远端索引保留替换空间。
7. **结构化成本与恢复指标**：记录 stop reason、重试原因、缓存 token、压缩前后 token、截断工具调用数和重复副作用防护结果。

## 正向改进门槛

后续参考 Pi 的改动必须同时满足：

1. 默认行为更安全；对 Android 写操作、终端和特权工具不扩大并发范围。
2. 失败时可回退；不得因学习、压缩或扩展失败阻断用户主任务。
3. 没有隐式副作用重放；恢复和重试要区分模型请求、只读工具和非幂等工具。
4. 有聚焦测试覆盖新分支，并保留现有编排器、记忆、压缩和并发策略测试。
5. 能观测收益：至少对应安全事件、token、延迟、成功率或恢复率中的一项。
6. 不把 Pi 的未实现 harness 骨架、桌面 shell 假设或无限制自修改直接带入小万。
