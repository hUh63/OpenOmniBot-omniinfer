package com.rk.terminal.ui.screens.terminal

import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.application
import com.rk.terminal.runtime.EmbeddedRuntimeInstaller

object Rootfs {
    val reTerminal = application!!.filesDir

    init {
        if (reTerminal.exists().not()){
            reTerminal.mkdirs()
        }
    }

    var isDownloaded = mutableStateOf(isFilesDownloaded())
    fun isFilesDownloaded(): Boolean{
        return application?.let(EmbeddedRuntimeInstaller::isCurrentDistributionReady) == true
    }
}
