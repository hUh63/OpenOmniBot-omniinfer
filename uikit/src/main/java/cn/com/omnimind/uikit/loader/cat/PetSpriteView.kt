package cn.com.omnimind.uikit.loader.cat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.ImageView
import kotlin.math.min

class PetSpriteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ImageView(context, attrs) {
    private val animationHandler = Handler(Looper.getMainLooper())
    private val sourceRect = Rect()
    private val targetRect = RectF()
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = false
    }
    private var atlasBitmap: Bitmap? = null
    private var atlasSpec: PetSpriteAtlasSpec? = null
    private var currentAction = PetAnimationAction.IDLE
    private var currentFrame = 0
    private var loopCurrentAction = true
    private var fallbackAction: PetAnimationAction? = null
    private var animationActive = true
    private var frameScheduled = false

    private val advanceFrameRunnable = object : Runnable {
        override fun run() {
            frameScheduled = false
            val bitmap = atlasBitmap ?: return
            if (bitmap.isRecycled || !animationActive) return
            val nextFrame = currentFrame + 1
            if (nextFrame >= currentAction.frameCount) {
                if (loopCurrentAction) {
                    currentFrame = 0
                } else {
                    val fallback = fallbackAction
                    if (fallback != null) {
                        play(fallback, loop = true)
                        return
                    }
                    currentFrame = currentAction.frameCount - 1
                    invalidate()
                    return
                }
            } else {
                currentFrame = nextFrame
            }
            invalidate()
            scheduleNextFrame()
        }
    }

    fun setSpriteAtlas(bitmap: Bitmap, spec: PetSpriteAtlasSpec): Boolean {
        if (bitmap.isRecycled ||
            bitmap.width != spec.columns * spec.cellWidth ||
            bitmap.height != spec.rows * spec.cellHeight
        ) {
            return false
        }
        clearSpriteAtlas()
        super.setImageDrawable(null)
        atlasBitmap = bitmap
        atlasSpec = spec
        play(PetAnimationAction.IDLE, loop = true)
        return true
    }

    fun clearSpriteAtlas() {
        animationHandler.removeCallbacks(advanceFrameRunnable)
        frameScheduled = false
        atlasBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
        atlasBitmap = null
        atlasSpec = null
        currentFrame = 0
        fallbackAction = null
        invalidate()
    }

    fun hasSpriteAtlas(): Boolean {
        return atlasBitmap?.isRecycled == false && atlasSpec != null
    }

    fun play(
        action: PetAnimationAction,
        loop: Boolean,
        fallback: PetAnimationAction? = null
    ): Boolean {
        if (!hasSpriteAtlas() || action.rowIndex >= (atlasSpec?.rows ?: 0)) {
            return false
        }
        if (currentAction == action &&
            loopCurrentAction == loop &&
            fallbackAction == fallback &&
            frameScheduled
        ) {
            return true
        }
        animationHandler.removeCallbacks(advanceFrameRunnable)
        frameScheduled = false
        currentAction = action
        currentFrame = 0
        loopCurrentAction = loop
        fallbackAction = fallback
        invalidate()
        scheduleNextFrame()
        return true
    }

    fun setAnimationActive(active: Boolean) {
        if (animationActive == active) return
        animationActive = active
        animationHandler.removeCallbacks(advanceFrameRunnable)
        frameScheduled = false
        if (active) {
            scheduleNextFrame()
        }
    }

    fun release() {
        animationActive = false
        clearSpriteAtlas()
        super.setImageDrawable(null)
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = atlasBitmap
        val spec = atlasSpec
        if (bitmap == null || bitmap.isRecycled || spec == null) {
            super.onDraw(canvas)
            return
        }
        val column = currentFrame.coerceIn(0, currentAction.frameCount - 1)
        val left = column * spec.cellWidth
        val top = currentAction.rowIndex * spec.cellHeight
        sourceRect.set(
            left,
            top,
            left + spec.cellWidth,
            top + spec.cellHeight
        )
        val availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val availableHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)
        val scale = min(
            availableWidth.toFloat() / spec.cellWidth,
            availableHeight.toFloat() / spec.cellHeight
        )
        val targetWidth = spec.cellWidth * scale
        val targetHeight = spec.cellHeight * scale
        val targetLeft = paddingLeft + (availableWidth - targetWidth) / 2f
        val targetTop = paddingTop + (availableHeight - targetHeight) / 2f
        targetRect.set(
            targetLeft,
            targetTop,
            targetLeft + targetWidth,
            targetTop + targetHeight
        )
        canvas.drawBitmap(bitmap, sourceRect, targetRect, spritePaint)
    }

    private fun scheduleNextFrame() {
        if (!animationActive || !hasSpriteAtlas()) return
        val duration = currentAction.frameDurationsMs[
            currentFrame.coerceIn(0, currentAction.frameCount - 1)
        ]
        frameScheduled = true
        animationHandler.postDelayed(advanceFrameRunnable, duration.toLong())
    }
}
