/**
 * Omnibot's interactive ACP surface for DeepSeek Harness.
 *
 * DeepSeek Harness' upstream ACP plugin intentionally exposes only committed
 * answer text. Omnibot also needs the standard ACP reasoning, tool-call, and
 * session-configuration messages that its chat UI already understands. This
 * plugin keeps the upstream agent spine and persistence composition, while
 * translating those additional durable DSH session events onto ACP.
 *
 * The implementation follows deepseek-ai/deepseek-harness at
 * 47f943859bef60e4160492346772ded9b24f765a (MIT) and remains an app-owned
 * integration layer rather than modifying the installed npm package.
 */

import { randomUUID } from 'node:crypto'
import { isAbsolute, join } from 'node:path'
import { Readable, Writable } from 'node:stream'
import {
  AgentSideConnection,
  ndJsonStream,
  PROTOCOL_VERSION,
  RequestError,
} from '@agentclientprotocol/sdk'
import z from '@deepseek-ai/schemastery'
import { installModelSelection } from '@deepseek-ai/dsh-agent'
import * as agentCore from '@deepseek-ai/dsh-agent-spine-demo'
import * as workspaceContext from '@deepseek-ai/dsh-agent-instructions'
import { createUserMessage, errorChain } from '@deepseek-ai/dsh-llm'
import { setSandboxMode } from '@deepseek-ai/dsh-sandbox-policy'
import { SessionId } from '@deepseek-ai/dsh-session'
import JsonlSessionPersistence, {
  JsonlCompressionSchema,
} from '@deepseek-ai/dsh-session-persistence-jsonl'
import * as sessionCheckpointPolicy from '@deepseek-ai/dsh-session-checkpoint-policy'
import SqliteSessionQueryEngine from '@deepseek-ai/dsh-session-query-sqlite'
import ToolRuntime from '@deepseek-ai/dsh-tools'

export const name = 'omnibot-acp-demo'

const DEFAULT_PERSISTENCE_ROOT = './.sessions'
const DEFAULT_PROVIDER = 'deepseek-official'
const DEFAULT_MODEL = 'deepseek-v4-pro'
const MODEL_IDS = ['deepseek-v4-flash', 'deepseek-v4-pro']
const REASONING_EFFORTS = ['off', 'high', 'max']
const PERMISSION_MODES = ['read-only', 'workspace-write', 'danger-full-access']

// Loader projects configuration through this schema before apply(). Keep the
// app composition's passthroughs aligned with upstream dsh-acp-demo so none of
// the configured spine, persistence, or tool options are silently discarded.
export const Config = z.object({
  provider: z.string().required(),
  model: z.string().required(),
  reasoningEffort: z.union(REASONING_EFFORTS.map(value => z.const(value))),
  permissionMode: z.union(PERMISSION_MODES.map(value => z.const(value))),
  maxParallelToolCalls: z.number().step(1).min(1),
  persona: z.string(),
  toolOrder: z.array(z.string()).default(undefined),
  tools: ToolRuntime.Config,
  dshHome: z.string(),
  sessionTitle: agentCore.SessionTitleConfigSchema,
  persistenceRoot: z.string().default(DEFAULT_PERSISTENCE_ROOT),
  packChunks: z.boolean().default(true),
  persistenceCompression: JsonlCompressionSchema,
  workspaceContext: z.union([z.const(false), workspaceContext.Config]).required(),
  skills: agentCore.SkillConfigSchema,
  toolBash: agentCore.ToolBashConfigSchema,
  jobs: agentCore.JobsConfigSchema,
  toolJobs: z.union([z.const(false), agentCore.ToolJobsConfigSchema]),
  goals: z.union([z.const(false), agentCore.GoalConfigSchema]),
})

function invalidParams(detail) {
  return RequestError.invalidParams(undefined, detail)
}

function internalError(detail) {
  return RequestError.internalError(undefined, detail)
}

function uniqueValues(...values) {
  return [...new Set(values.flat().filter(value => typeof value === 'string' && value.length > 0))]
}

function normalizeEffort(value) {
  return REASONING_EFFORTS.includes(value) ? value : 'max'
}

function normalizePermissionMode(value) {
  return PERMISSION_MODES.includes(value) ? value : 'workspace-write'
}

function permissionOptionValue(mode) {
  if (mode === 'read-only') return 'read-only'
  if (mode === 'danger-full-access') return 'agent-full-access'
  return 'agent'
}

function sandboxModeForOption(value) {
  if (value === 'read-only') return 'read-only'
  if (value === 'agent-full-access') return 'danger-full-access'
  if (value === 'agent') return 'workspace-write'
  throw invalidParams(`unknown permission value: ${value}`)
}

function sessionConfigOptions(record, configuredModels) {
  return [
    {
      id: 'model',
      name: 'Model',
      description: 'DeepSeek model used by the next model step.',
      type: 'select',
      category: 'model',
      currentValue: record.selection.current.model,
      options: uniqueValues(record.selection.current.model, configuredModels).map(value => ({
        value,
        name: value,
      })),
    },
    {
      id: 'reasoning_effort',
      name: 'Reasoning effort',
      description: 'DeepSeek thinking effort used by the next model step.',
      type: 'select',
      category: 'thought_level',
      currentValue: record.selection.current.reasoningEffort,
      options: REASONING_EFFORTS.map(value => ({ value, name: value })),
    },
    {
      id: 'mode',
      name: 'File permission',
      description: 'File sandbox mode used by subsequent tools in this session.',
      type: 'select',
      category: 'mode',
      currentValue: permissionOptionValue(record.permissionMode),
      options: [
        { value: 'read-only', name: 'Read only' },
        { value: 'agent', name: 'Workspace write' },
        { value: 'agent-full-access', name: 'Full access' },
      ],
    },
  ]
}

function safeJson(value) {
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

function toolKind(name) {
  const normalized = name.toLowerCase()
  if (normalized === 'read' || normalized.includes('read_file')) return 'read'
  if (normalized.includes('delete') || normalized === 'rm') return 'delete'
  if (normalized.includes('move') || normalized === 'mv') return 'move'
  if (normalized.includes('write') || normalized.includes('edit') || normalized.includes('patch')) return 'edit'
  if (normalized.includes('grep') || normalized.includes('glob') || normalized.includes('search')) return 'search'
  if (normalized.includes('fetch') || normalized.includes('web')) return 'fetch'
  if (normalized.includes('think')) return 'think'
  if (normalized.includes('bash') || normalized.includes('shell') || normalized.includes('exec')) return 'execute'
  return 'other'
}

function toolTitle(name) {
  return name.replaceAll('_', ' ').replaceAll('-', ' ').trim() || 'Tool call'
}

function textFromBlocks(blocks) {
  return blocks.flatMap(block => {
    if (block.type === 'text') return [block.text]
    if (block.type === 'reasoning') return [block.text]
    if (block.type === 'image') return [`[image attachment ${block.attachment.attachmentId}]`]
    return []
  }).join('\n')
}

function toolContent(blocks) {
  const text = textFromBlocks(blocks)
  if (text.length === 0) return []
  return [{
    type: 'content',
    content: { type: 'text', text },
  }]
}

function turnEndToStopReason(reason) {
  switch (reason.kind) {
    case 'max-tokens':
      return 'max_tokens'
    case 'interrupted':
      return 'cancelled'
    default:
      return 'end_turn'
  }
}

function thoughtMessageId(sessionId, turn, step) {
  return `${sessionId}-turn-${turn}-step-${step}-thought`
}

function acpPromptToText(prompt) {
  return prompt.flatMap(block => {
    if (block.type === 'text') return [block.text]
    if (block.type === 'resource_link') {
      return [`\n[resource_link name=${JSON.stringify(block.name)} uri=${JSON.stringify(block.uri)}]\n`]
    }
    return []
  }).join('')
}

function promptHasUnsupportedContent(prompt) {
  return prompt.some(block => block.type !== 'text' && block.type !== 'resource_link')
}

const interactiveAcp = {
  name: 'omnibot-acp',
  inject: ['agents', 'sandboxPolicy'],
  apply(ctx, config) {
    const agents = ctx.agents
    const logger = ctx.logger
    const sessions = new Map()
    const configuredModels = uniqueValues(config.model ?? DEFAULT_MODEL, MODEL_IDS)
    let closed = false
    let conn

    const ownedRecord = agent => {
      const record = sessions.get(agent.session.id)
      return record?.agent === agent ? record : undefined
    }

    const requireSession = sessionId => {
      const record = sessions.get(sessionId)
      if (record === undefined) throw invalidParams(`unknown session: ${sessionId}`)
      return record
    }

    const notify = notification => {
      void conn.sessionUpdate(notification).catch(error => {
        logger.warn(`omnibot-acp: session/update failed: ${String(error)}`)
      })
    }

    const settlePrompt = (record, reason) => {
      const inflight = record.inflight
      if (inflight === undefined) return
      record.inflight = undefined
      inflight.resolve(reason)
    }

    ctx.on('session/event', (session, event) => {
      const record = sessions.get(session.header.id)
      if (record === undefined || record.agent.session !== session) return
      try {
        if (event.type === 'assistant/chunk' && event.data.chunk.type === 'reasoning-delta') {
          const text = event.data.chunk.text
          if (text.length > 0) {
            notify({
              sessionId: record.agent.session.id,
              update: {
                sessionUpdate: 'agent_thought_chunk',
                // DSH starts a new model step after every tool round. Keep a
                // separate ACP thought identity per step so the UI does not
                // append every reasoning phase into the first thought card.
                messageId: thoughtMessageId(
                  record.agent.session.id,
                  event.data.turn,
                  event.data.step,
                ),
                content: { type: 'text', text },
              },
            })
          }
        } else if (event.type === 'assistant/message') {
          for (const block of event.data.message.content) {
            if (block.type === 'text' && block.text.length > 0) {
              notify({
                sessionId: record.agent.session.id,
                update: {
                  sessionUpdate: 'agent_message_chunk',
                  // A DSH turn can contain several assistant messages, one
                  // per model step. Keep their stable DSH identities so the
                  // client does not merge pre-tool narration and the final
                  // answer into one card anchored before the tool call.
                  messageId: event.data.message.id,
                  content: { type: 'text', text: block.text },
                },
              })
            } else if (block.type === 'image') {
              notify({
                sessionId: record.agent.session.id,
                update: {
                  sessionUpdate: 'agent_message_chunk',
                  messageId: event.data.message.id,
                  content: {
                    type: 'text',
                    text: `[image attachment ${block.attachment.attachmentId}]`,
                  },
                },
              })
            }
          }
        } else if (event.type === 'tool/call') {
          notify({
            sessionId: record.agent.session.id,
            update: {
              sessionUpdate: 'tool_call',
              toolCallId: event.data.callId,
              title: toolTitle(event.data.name),
              kind: toolKind(event.data.name),
              status: 'in_progress',
              rawInput: safeJson(event.data.arguments),
            },
          })
        } else if (event.type === 'tool/result') {
          const result = event.data.message.content[0]
          notify({
            sessionId: record.agent.session.id,
            update: {
              sessionUpdate: 'tool_call_update',
              toolCallId: result.toolCallId,
              status: result.isError ? 'failed' : 'completed',
              content: toolContent(result.content),
              rawOutput: event.data.meta ?? {
                content: textFromBlocks(result.content),
                ...(event.data.error === undefined ? {} : { error: event.data.error }),
              },
            },
          })
        }
      } finally {
        const inflight = record.inflight
        if (inflight !== undefined && event.type === 'turn/end' && inflight.turn === event.data.turn) {
          if (event.data.reason.kind === 'error') {
            record.inflight = undefined
            inflight.reject(internalError(`turn failed: ${event.data.reason.error.message}`))
          } else {
            inflight.endReason = event.data.reason
          }
        }
      }
    })

    ctx.on('agent/inbox/claimed', ({ agent, message, turn }) => {
      const inflight = ownedRecord(agent)?.inflight
      if (inflight !== undefined && inflight.messageId === message.id) inflight.turn = turn
    })

    ctx.on('agent/error', ({ agent, turn, error }) => {
      const record = ownedRecord(agent)
      const inflight = record?.inflight
      if (record === undefined || inflight === undefined || inflight.turn === turn) return
      record.inflight = undefined
      inflight.reject(internalError(`turn failed: ${errorChain(error)}`))
    })

    ctx.on('approval/request', (request, next) => {
      const record = ownedRecord(request.agent)
      if (record === undefined || request.callId === undefined) return next()
      return conn.requestPermission({
        sessionId: record.agent.session.id,
        toolCall: { toolCallId: request.callId },
        options: [
          { optionId: 'allow-once', name: 'Allow once', kind: 'allow_once' },
          { optionId: 'reject-once', name: 'Reject', kind: 'reject_once' },
        ],
      }).then(({ outcome }) => {
        if (outcome.outcome === 'cancelled') return 'cancelled'
        return outcome.optionId === 'allow-once' ? 'allowed-once' : 'rejected'
      })
    })

    const makeAgent = connection => {
      conn = connection
      return {
        initialize() {
          return Promise.resolve({
            protocolVersion: PROTOCOL_VERSION,
            agentInfo: { name: 'deepseek-harness-acp', version: 'omnibot-1' },
            agentCapabilities: {
              promptCapabilities: { image: false, audio: false, embeddedContext: false },
            },
            authMethods: [],
          })
        },

        authenticate() {
          return Promise.resolve()
        },

        async newSession(params) {
          if (closed) throw internalError('the ACP bridge has been disposed')
          if (!isAbsolute(params.cwd)) throw invalidParams(`cwd must be an absolute path: ${params.cwd}`)
          if ((params.additionalDirectories?.length ?? 0) > 0) {
            throw invalidParams('additionalDirectories is not supported')
          }
          if (params.mcpServers.length > 0) throw invalidParams('mcpServers is not supported')

          const sessionId = SessionId(randomUUID())
          const selection = {
            current: {
              provider: config.provider ?? DEFAULT_PROVIDER,
              model: config.model ?? DEFAULT_MODEL,
              reasoningEffort: normalizeEffort(config.reasoningEffort),
            },
            assembled: undefined,
          }
          const handle = await agents.create({
            sessionId,
            meta: { cwd: params.cwd },
            agentOptions: {
              provider: selection.current.provider,
              model: selection.current.model,
            },
            setup(agentCtx) {
              installModelSelection(agentCtx, selection)
            },
          })
          if (closed) {
            await handle.dispose()
            throw internalError('connection closed during session/new')
          }
          const record = {
            agent: handle.agent,
            dispose: () => handle.dispose(),
            inflight: undefined,
            selection,
            permissionMode: normalizePermissionMode(config.permissionMode),
          }
          setSandboxMode(record.agent.session, record.permissionMode)
          sessions.set(sessionId, record)
          return {
            sessionId,
            configOptions: sessionConfigOptions(record, configuredModels),
          }
        },

        setSessionConfigOption(params) {
          const record = requireSession(SessionId(params.sessionId))
          if (record.inflight !== undefined) {
            throw invalidParams('session configuration cannot change during a prompt')
          }
          if (params.configId === 'model') {
            if (!configuredModels.includes(params.value)) {
              throw invalidParams(`unknown model value: ${params.value}`)
            }
            record.selection.current = {
              ...record.selection.current,
              model: params.value,
            }
          } else if (params.configId === 'reasoning_effort') {
            if (!REASONING_EFFORTS.includes(params.value)) {
              throw invalidParams(`unknown reasoning effort: ${params.value}`)
            }
            record.selection.current = {
              ...record.selection.current,
              reasoningEffort: params.value,
            }
          } else if (params.configId === 'mode') {
            const mode = sandboxModeForOption(params.value)
            record.permissionMode = mode
            setSandboxMode(record.agent.session, mode)
          } else {
            throw invalidParams(`unknown session config option: ${params.configId}`)
          }
          return Promise.resolve({
            configOptions: sessionConfigOptions(record, configuredModels),
          })
        },

        async prompt(params) {
          if (closed) throw internalError('the ACP bridge has been disposed')
          const record = requireSession(SessionId(params.sessionId))
          if (record.inflight !== undefined) {
            throw invalidParams('a prompt is already in flight for this session')
          }
          if (promptHasUnsupportedContent(params.prompt)) {
            throw invalidParams('only text and resource_link prompt content is supported')
          }
          const text = acpPromptToText(params.prompt)
          if (text.trim().length === 0) throw invalidParams('empty prompt')
          if (ctx.agents.get(record.agent.id) !== record.agent) {
            throw internalError('prompt was not queued: the agent was disposed outside the bridge')
          }

          const message = createUserMessage({
            content: [{ type: 'text', text }],
            source: { kind: 'user' },
          })
          const stopReason = await new Promise((resolve, reject) => {
            const inflight = {
              resolve,
              reject,
              messageId: message.id,
              turn: undefined,
              endReason: undefined,
            }
            record.inflight = inflight
            try {
              record.agent.followup(message)
            } catch (error) {
              record.inflight = undefined
              throw internalError(`prompt was not queued: ${error instanceof Error ? error.message : String(error)}`)
            }
            void record.agent.whenIdle().then(() => {
              if (record.inflight !== inflight) return
              record.inflight = undefined
              const end = inflight.endReason
              resolve(end === undefined ? 'cancelled' : turnEndToStopReason(end))
            })
          })
          return { stopReason: stopReason === 'max_tokens' ? 'end_turn' : stopReason }
        },

        cancel(params) {
          const record = sessions.get(SessionId(params.sessionId))
          if (record === undefined) return Promise.resolve()
          record.agent.cancel({ kind: 'user' })
          settlePrompt(record, 'cancelled')
          return Promise.resolve()
        },
      }
    }

    const stream = ndJsonStream(
      Writable.toWeb(process.stdout),
      Readable.toWeb(process.stdin),
    )
    conn = new AgentSideConnection(makeAgent, stream)

    let quiescing
    const quiesce = () => {
      if (quiescing !== undefined) return quiescing
      closed = true
      const records = [...sessions.values()]
      sessions.clear()
      for (const record of records) {
        record.agent.cancel({ kind: 'user' })
        settlePrompt(record, 'cancelled')
      }
      quiescing = (async () => {
        const subagents = ctx.get('subagents')
        if (subagents !== undefined) {
          try {
            await subagents.drainContinuableDescendants(records.map(record => record.agent))
          } catch (error) {
            logger.warn(`omnibot-acp: continuable subagent teardown failed: ${String(error)}`)
          }
        }
        const results = await Promise.allSettled(records.map(record => record.dispose()))
        const failures = results.filter(result => result.status === 'rejected').map(result => result.reason)
        if (failures.length > 0) {
          throw new AggregateError(failures, `Omnibot ACP teardown failed for ${failures.length} session(s)`)
        }
      })()
      return quiescing
    }

    void conn.closed
      .catch(error => logger.warn(`omnibot-acp: connection closed with an error: ${String(error)}`))
      .then(quiesce)
      .catch(error => logger.warn(`omnibot-acp: connection-close teardown failed: ${String(error)}`))
    ctx.effect(() => quiesce, 'omnibot-acp.connection')
  },
}

export async function apply(ctx, config) {
  const persistenceRoot = config.persistenceRoot ?? DEFAULT_PERSISTENCE_ROOT
  const goals = config.goals ?? {}
  await ctx.effect(async function* () {
    const spine = ctx.plugin(agentCore, { ...agentCore.pickSpineConfig(config), goals })
    await spine
    yield spine.dispose

    const persistence = ctx.plugin(JsonlSessionPersistence, {
      root: persistenceRoot,
      ...(config.packChunks === undefined ? {} : { packChunks: config.packChunks }),
      ...(config.persistenceCompression === undefined
        ? {}
        : { compression: config.persistenceCompression }),
    })
    await persistence
    yield persistence.dispose

    const checkpoint = ctx.plugin(sessionCheckpointPolicy)
    await checkpoint
    yield checkpoint.dispose

    const query = ctx.plugin(SqliteSessionQueryEngine, {
      path: join(persistenceRoot, 'session-query.db'),
    })
    await query
    yield query.dispose

    const transport = ctx.plugin(interactiveAcp, {
      provider: config.provider,
      model: config.model,
      reasoningEffort: config.reasoningEffort,
      permissionMode: config.permissionMode,
    })
    await transport
    yield transport.dispose
  }, 'omnibot-acp-demo.composition')
}
