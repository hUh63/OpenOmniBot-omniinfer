package cn.com.omnimind.uikit.loader.cat

data class PetSpriteAtlasSpec(
    val columns: Int,
    val rows: Int,
    val cellWidth: Int,
    val cellHeight: Int,
    val spriteVersionNumber: Int
) {
    companion object {
        private const val CODEX_COLUMNS = 8
        private const val CODEX_CELL_WIDTH = 192
        private const val CODEX_CELL_HEIGHT = 208

        fun detect(width: Int, height: Int): PetSpriteAtlasSpec? {
            if (width != CODEX_COLUMNS * CODEX_CELL_WIDTH) {
                return null
            }
            return when (height) {
                9 * CODEX_CELL_HEIGHT -> PetSpriteAtlasSpec(
                    columns = CODEX_COLUMNS,
                    rows = 9,
                    cellWidth = CODEX_CELL_WIDTH,
                    cellHeight = CODEX_CELL_HEIGHT,
                    spriteVersionNumber = 1
                )
                11 * CODEX_CELL_HEIGHT -> PetSpriteAtlasSpec(
                    columns = CODEX_COLUMNS,
                    rows = 11,
                    cellWidth = CODEX_CELL_WIDTH,
                    cellHeight = CODEX_CELL_HEIGHT,
                    spriteVersionNumber = 2
                )
                else -> null
            }
        }
    }
}

enum class PetActionCategory {
    IDLE,
    RUN,
    WAVE,
    JUMP,
    WAITING,
    FAILED,
    REVIEW
}

enum class PetAnimationAction(
    val wireName: String,
    val rowIndex: Int,
    val frameDurationsMs: IntArray,
    val category: PetActionCategory
) {
    IDLE(
        "idle",
        0,
        intArrayOf(280, 110, 110, 140, 140, 320),
        PetActionCategory.IDLE
    ),
    RUNNING_RIGHT(
        "running-right",
        1,
        intArrayOf(120, 120, 120, 120, 120, 120, 120, 220),
        PetActionCategory.RUN
    ),
    RUNNING_LEFT(
        "running-left",
        2,
        intArrayOf(120, 120, 120, 120, 120, 120, 120, 220),
        PetActionCategory.RUN
    ),
    WAVING(
        "waving",
        3,
        intArrayOf(140, 140, 140, 280),
        PetActionCategory.WAVE
    ),
    JUMPING(
        "jumping",
        4,
        intArrayOf(140, 140, 140, 140, 280),
        PetActionCategory.JUMP
    ),
    FAILED(
        "failed",
        5,
        intArrayOf(140, 140, 140, 140, 140, 140, 140, 240),
        PetActionCategory.FAILED
    ),
    WAITING(
        "waiting",
        6,
        intArrayOf(150, 150, 150, 150, 150, 260),
        PetActionCategory.WAITING
    ),
    RUNNING(
        "running",
        7,
        intArrayOf(120, 120, 120, 120, 120, 220),
        PetActionCategory.RUN
    ),
    REVIEW(
        "review",
        8,
        intArrayOf(150, 150, 150, 150, 150, 280),
        PetActionCategory.REVIEW
    );

    val frameCount: Int
        get() = frameDurationsMs.size

    companion object {
        fun fromWireName(value: String): PetAnimationAction? {
            return when (value.trim().lowercase().replace('_', '-')) {
                "idle", "rest" -> IDLE
                "running-right", "run-right", "move-right", "right" -> RUNNING_RIGHT
                "running-left", "run-left", "move-left", "left" -> RUNNING_LEFT
                "waving", "wave", "hello" -> WAVING
                "jumping", "jump", "done", "success" -> JUMPING
                "failed", "failure", "error" -> FAILED
                "waiting", "wait", "blocked", "approval" -> WAITING
                "run", "running", "working", "processing", "task" -> RUNNING
                "review", "reviewing", "thinking" -> REVIEW
                else -> null
            }
        }
    }
}
