package com.rk.terminal.runtime

import com.rk.settings.Settings
import com.rk.terminal.ui.screens.settings.WorkingMode

object TerminalDistribution {
    data class Spec(
        val id: String,
        val displayName: String,
        val workingMode: Int,
        val rootfsDirectoryName: String,
        val rootfsArchiveName: String
    )

    val alpine = Spec(
        id = "alpine",
        displayName = "Alpine",
        workingMode = WorkingMode.ALPINE,
        rootfsDirectoryName = "alpine",
        rootfsArchiveName = "alpine.tar.gz"
    )

    val ubuntu = Spec(
        id = "ubuntu",
        displayName = "Ubuntu",
        workingMode = WorkingMode.UBUNTU,
        rootfsDirectoryName = "ubuntu",
        rootfsArchiveName = "ubuntu.tar.gz"
    )

    val supported: List<Spec> = listOf(alpine, ubuntu)

    fun selected(): Spec = fromWorkingMode(Settings.terminal_distribution)

    fun fromId(id: String?): Spec {
        return supported.firstOrNull { it.id == id?.trim()?.lowercase() } ?: alpine
    }

    fun fromWorkingMode(workingMode: Int): Spec {
        return supported.firstOrNull { it.workingMode == workingMode } ?: alpine
    }

    fun isLinuxWorkingMode(workingMode: Int): Boolean {
        return supported.any { it.workingMode == workingMode }
    }
}
