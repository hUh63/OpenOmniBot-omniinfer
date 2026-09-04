package cn.com.omnimind.bot.agent

/**
 * One timing policy for the Conversation -> ACP Session -> Turn lifecycle.
 *
 * The Provider watchdog only detects a dead transport and therefore fires
 * before the ACP host watchdog. The ACP runtime remains the owner of the
 * visible turn/failed terminal event.
 */
internal object AgentTurnTimingPolicy {
    const val PROVIDER_STREAM_IDLE_TIMEOUT_MS = 90_000L
    const val ACP_TURN_IDLE_TIMEOUT_MS = 120_000L
}
