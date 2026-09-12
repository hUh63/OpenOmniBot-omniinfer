package com.rk.libcommons

import android.content.Context
import java.io.File
import com.rk.terminal.BuildConfig
import com.rk.terminal.runtime.TerminalDistribution

private fun getFilesDir(): File{
    return if (application == null){
        if (BuildConfig.DEBUG){
            File("/data/data/com.rk.terminal.debug/files")
        }else{
            File("/data/data/com.rk.terminal/files")
        }
    }else{
        application!!.filesDir
    }
}

fun localDir(): File {
    return File(getFilesDir().parentFile, "local").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun alpineDir(): File{
    return localDir().child("alpine").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun alpineHomeDir(): File{
    return alpineDir().child("root").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun terminalRootfsDir(workingMode: Int): File {
    val distribution = TerminalDistribution.fromWorkingMode(workingMode)
    return localDir().child(distribution.rootfsDirectoryName).also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun terminalHomeDir(workingMode: Int): File {
    return terminalRootfsDir(workingMode).child("root").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun selectedTerminalHomeDir(): File {
    return terminalHomeDir(TerminalDistribution.selected().workingMode)
}

fun localBinDir(): File {
    return localDir().child("bin").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun localLibDir(): File {
    return localDir().child("lib").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun File.child(fileName:String):File {
    return File(this,fileName)
}

fun File.createFileIfNot():File{
    if (exists().not()){
        createNewFile()
    }
    return this
}
