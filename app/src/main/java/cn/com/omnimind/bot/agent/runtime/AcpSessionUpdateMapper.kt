@file:OptIn(com.agentclientprotocol.annotations.UnstableApi::class)

package cn.com.omnimind.bot.agent.runtime

import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PlanVariant
import com.agentclientprotocol.model.SessionConfigOption
import com.agentclientprotocol.model.SessionConfigSelectOptions
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallContent
import com.agentclientprotocol.model.ToolCallStatus

/**
 * The one translation from ACP session updates to the event vocabulary the
 * Flutter chat timeline consumes.
 *
 * This is deliberately a pure, side-effect-free mapping that owns no session or
 * process state, because it has two callers rather than one:
 *
 *  - [LocalAcpRuntime], which runs an ACP agent as a local child process, and
 *  - the remote PC Bridge, once it forwards ACP instead of the legacy
 *    `codex app-server` protocol.
 *
 * Keeping it here means the bridge migration reuses this mapping verbatim
 * instead of growing a second translator on the Dart side. The Dart reducer's
 * `codex/event` branch (~510 lines of legacy protocol handling) then becomes
 * unreachable and can be deleted outright rather than maintained in parallel.
 */
internal data class AcpUiEvent(
    val method: String,
    val params: Map<String, Any?>
)

/**
 * Maps one ACP session update to the UI event it produces, or `null` when the
 * update carries nothing the timeline renders.
 *
 * [threadId] scopes session-level updates. [turnId] supplies a local UI
 * identity for chunks whose optional ACP message id is absent, so separate
 * prompt turns can never overwrite one another.
 */
internal fun SessionUpdate.toAcpUiEvent(
    threadId: String,
    turnId: String? = null
): AcpUiEvent? = when (this) {
    is SessionUpdate.AgentMessageChunk -> AcpUiEvent(
        method = "item/agentMessage/delta",
        params = mapOf(
            "itemId" to (messageId?.value ?: "${turnId ?: threadId}-agent"),
            "delta" to content.textPayload()
        )
    )

    is SessionUpdate.AgentThoughtChunk -> AcpUiEvent(
        method = "item/reasoning/delta",
        params = mapOf(
            "itemId" to (messageId?.value ?: "${turnId ?: threadId}-reasoning"),
            "delta" to content.textPayload()
        )
    )

    is SessionUpdate.ToolCall -> AcpUiEvent(
        method = "item/started",
        params = mapOf("item" to toolPayload(this))
    )

    is SessionUpdate.ToolCallUpdate -> AcpUiEvent(
        method = if (status == ToolCallStatus.COMPLETED || status == ToolCallStatus.FAILED) {
            "item/completed"
        } else {
            "item/updated"
        },
        params = mapOf("item" to toolPayload(this))
    )

    is SessionUpdate.PlanUpdate -> AcpUiEvent(
        method = "turn/plan/updated",
        params = mapOf(
            "plan" to entries.joinToString("\n") {
                "- [${it.status.name.lowercase()}] ${it.content}"
            },
            "entries" to entries.map {
                mapOf(
                    "content" to it.content,
                    "priority" to it.priority.name.lowercase(),
                    "status" to it.status.name.lowercase()
                )
            }
        )
    )

    is SessionUpdate.PlanUpdateV2 -> AcpUiEvent(
        method = "turn/plan/updated",
        params = when (val variant = plan) {
            is PlanVariant.Items -> mapOf(
                "id" to variant.id,
                "plan" to variant.entries.joinToString("\n") { it.content }
            )
            is PlanVariant.Markdown -> mapOf("id" to variant.id, "plan" to variant.content)
            is PlanVariant.File -> mapOf("id" to variant.id, "plan" to variant.uri)
        }
    )

    is SessionUpdate.PlanRemoved -> AcpUiEvent(
        method = "turn/plan/updated",
        params = mapOf("id" to id, "plan" to "")
    )

    is SessionUpdate.CurrentModeUpdate -> AcpUiEvent(
        method = "thread/settings/updated",
        params = mapOf(
            "threadId" to threadId,
            "collaborationMode" to currentModeId.value
        )
    )

    is SessionUpdate.ConfigOptionUpdate -> AcpUiEvent(
        method = "acp/configOptions/updated",
        params = mapOf(
            "threadId" to threadId,
            "configOptions" to configOptions.map(::acpConfigOptionPayload)
        )
    )

    is SessionUpdate.SessionInfoUpdate -> title
        ?.takeIf { it.isNotBlank() }
        ?.let {
            AcpUiEvent(
                method = "thread/name/updated",
                params = mapOf("threadId" to threadId, "name" to it)
            )
        }

    is SessionUpdate.UsageUpdate -> AcpUiEvent(
        method = "acp/usage/updated",
        params = mapOf(
            "used" to used,
            "size" to size,
            "cost" to cost?.amount,
            "currency" to cost?.currency
        )
    )

    is SessionUpdate.AvailableCommandsUpdate -> AcpUiEvent(
        method = "acp/commands/updated",
        params = mapOf(
            "commands" to availableCommands.map {
                mapOf("name" to it.name, "description" to it.description)
            }
        )
    )

    is SessionUpdate.UnknownSessionUpdate -> AcpUiEvent(
        method = "acp/sessionUpdate/unknown",
        params = mapOf(
            "sessionUpdate" to sessionUpdateType,
            "raw" to rawJson.toString()
        )
    )

    // The client is the author of user messages, so a replayed echo of one adds
    // nothing to the timeline.
    is SessionUpdate.UserMessageChunk -> null
}

/**
 * Whether an ACP session update belongs to a specific prompt turn.
 *
 * Timeline updates (messages, reasoning, tool calls, plans) render inside a
 * turn and are meaningless without one. Session-scoped updates (title, mode,
 * config, usage, available commands) apply to the thread and are still worth
 * forwarding between turns.
 */
internal fun SessionUpdate.isTurnScoped(): Boolean = when (this) {
    is SessionUpdate.AgentMessageChunk,
    is SessionUpdate.AgentThoughtChunk,
    is SessionUpdate.ToolCall,
    is SessionUpdate.ToolCallUpdate,
    is SessionUpdate.PlanUpdate,
    is SessionUpdate.PlanUpdateV2,
    is SessionUpdate.PlanRemoved -> true
    else -> false
}

internal fun acpConfigOptionPayload(option: SessionConfigOption): Map<String, Any?> {
    val base = linkedMapOf<String, Any?>(
        "id" to option.id.value,
        "name" to option.name,
        "description" to option.description,
        "category" to option.category?.value,
        "currentValue" to option.acpCurrentValuePayload()
    )
    when (option) {
        is SessionConfigOption.Select -> {
            base["type"] = "select"
            base["options"] = option.acpFlatOptions().map {
                mapOf(
                    "value" to it.value.value,
                    "name" to it.name,
                    "description" to it.description
                )
            }
        }
        is SessionConfigOption.BooleanOption -> {
            base["type"] = "boolean"
        }
    }
    return base
}

private fun SessionConfigOption.Select.acpFlatOptions() = when (val value = options) {
    is SessionConfigSelectOptions.Flat -> value.options
    is SessionConfigSelectOptions.Grouped -> value.groups.flatMap { it.options }
}

private fun SessionConfigOption.acpCurrentValuePayload(): Any? = when (this) {
    is SessionConfigOption.Select -> currentValue.value
    is SessionConfigOption.BooleanOption -> currentValue
}

private fun ContentBlock.textPayload(): String = when (this) {
    is ContentBlock.Text -> text
    is ContentBlock.ResourceLink -> title ?: name
    is ContentBlock.Image -> uri ?: ""
    is ContentBlock.Audio -> ""
    is ContentBlock.Resource -> resource.toString()
}

private fun toolPayload(update: SessionUpdate.ToolCall): Map<String, Any?> =
    linkedMapOf(
        "id" to update.toolCallId.value,
        "type" to acpToolItemType(update.kind?.name),
        "title" to update.title,
        "status" to update.status?.name?.lowercase(),
        "content" to update.content.toolContentPayload(),
        "locations" to update.locations.map {
            mapOf("path" to it.path, "line" to it.line?.toLong())
        },
        "rawInput" to update.rawInput?.toString(),
        "rawOutput" to update.rawOutput?.toString()
    )

private fun toolPayload(update: SessionUpdate.ToolCallUpdate): Map<String, Any?> =
    linkedMapOf(
        "id" to update.toolCallId.value,
        "type" to acpToolItemType(update.kind?.name),
        "title" to update.title,
        "status" to update.status?.name?.lowercase(),
        "content" to update.content?.toolContentPayload(),
        "locations" to update.locations?.map {
            mapOf("path" to it.path, "line" to it.line?.toLong())
        },
        "rawInput" to update.rawInput?.toString(),
        "rawOutput" to update.rawOutput?.toString()
    )

internal fun acpToolItemType(kind: String?): String = when (kind) {
    "EXECUTE" -> "commandExecution"
    "EDIT", "DELETE", "MOVE" -> "fileChange"
    "SEARCH", "FETCH" -> "webSearch"
    "THINK" -> "plan"
    else -> "tool"
}

private fun List<ToolCallContent>.toolContentPayload(): List<Map<String, Any?>> = map {
    when (it) {
        is ToolCallContent.Content -> mapOf(
            "type" to "content",
            "text" to it.content.textPayload()
        )
        is ToolCallContent.Diff -> mapOf(
            "type" to "diff",
            "path" to it.path,
            "oldText" to it.oldText,
            "newText" to it.newText
        )
        is ToolCallContent.Terminal -> mapOf(
            "type" to "terminal",
            "terminalId" to it.terminalId
        )
    }
}
