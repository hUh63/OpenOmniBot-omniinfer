package cn.com.omnimind.bot.agent

import java.io.File

/** Bundled code is replaceable; skill-local learning data belongs to the user. */
internal fun refreshBuiltinSkillAssets(targetDir: File, copyAssets: () -> Unit) {
    targetDir.listFiles().orEmpty().filter { it.name != "data" }.forEach { file ->
        // 删不掉（只读子文件 / 被占用）就跳过这一项。原来这里 check() 抛异常会
        // 一路冒到 listSkillsForManagement()，一个坏的内置 asset 足以让整个技能
        // 管理页和 skills_list 工具全部失败。
        if (!file.deleteRecursively()) {
            android.util.Log.w("BuiltinSkillAssets", "cannot refresh builtin skill file: ${file.name}")
        }
    }
    copyAssets()
}
