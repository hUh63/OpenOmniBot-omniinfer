package cn.com.omnimind.baselib.util

import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.view.View

/**
 * Shape构建器工具类，用于通过代码方式创建各种形状
 * 替代XML中的shape定义
 */
class ShapeBuilder {
    private var shapeType: Int = GradientDrawable.RECTANGLE
    private var solidColor: Int = Color.TRANSPARENT
    private var strokeColor: Int = Color.TRANSPARENT
    private var strokeWidth: Int = 0
    private var strokeDashWidth: Float = 0f
    private var strokeDashGap: Float = 0f
    private var cornerRadius: Float = 0f
    private var cornerRadii: FloatArray? = null
    private var gradientType: Int = GradientDrawable.LINEAR_GRADIENT
    private var gradientColors: IntArray? = null
    private var gradientOrientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT
    private var gradientCenterX: Float = 0.5f
    private var gradientCenterY: Float = 0.5f
    private var gradientRadius: Float = 0.5f

    /**
     * 设置形状类型
     * @param type GradientDrawable.RECTANGLE, GradientDrawable.OVAL, GradientDrawable.LINE, GradientDrawable.RING
     */
    fun setShape(type: Int): ShapeBuilder {
        this.shapeType = type
        return this
    }

    /**
     * 设置纯色填充
     * @param color 颜色值
     */
    fun setSolidColor(color: Int): ShapeBuilder {
        this.solidColor = color
        return this
    }

    /**
     * 设置边框
     * @param width 边框宽度
     * @param color 边框颜色
     */
    fun setStroke(width: Int, color: Int): ShapeBuilder {
        this.strokeWidth = width
        this.strokeColor = color
        return this
    }

    /**
     * 设置虚线边框
     * @param width 边框宽度
     * @param color 边框颜色
     * @param dashWidth 虚线长度
     * @param dashGap 虚线间隔
     */
    fun setStroke(width: Int, color: Int, dashWidth: Float, dashGap: Float): ShapeBuilder {
        this.strokeWidth = width
        this.strokeColor = color
        this.strokeDashWidth = dashWidth
        this.strokeDashGap = dashGap
        return this
    }

    /**
     * 设置圆角半径
     * @param radius 圆角半径
     */
    fun setCornerRadius(radius: Float): ShapeBuilder {
        this.cornerRadius = radius
        return this
    }

    /**
     * 设置四个角的圆角半径
     * @param topLeft 左上角
     * @param topRight 右上角
     * @param bottomRight 右下角
     * @param bottomLeft 左下角
     */
    fun setCornerRadii(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float): ShapeBuilder {
        this.cornerRadii = floatArrayOf(
            topLeft, topLeft,
            topRight, topRight,
            bottomRight, bottomRight,
            bottomLeft, bottomLeft
        )
        return this
    }

    /**
     * 设置线性渐变
     * @param colors 渐变颜色数组
     * @param orientation 渐变方向
     */
    fun setLinearGradient(colors: IntArray, orientation: GradientDrawable.Orientation): ShapeBuilder {
        this.gradientType = GradientDrawable.LINEAR_GRADIENT
        this.gradientColors = colors
        this.gradientOrientation = orientation
        return this
    }

    /**
     * 设置径向渐变
     * @param colors 渐变颜色数组
     * @param centerX 中心点X坐标（0-1）
     * @param centerY 中心点Y坐标（0-1）
     * @param radius 渐变半径（0-1）
     */
    fun setRadialGradient(colors: IntArray, centerX: Float, centerY: Float, radius: Float): ShapeBuilder {
        this.gradientType = GradientDrawable.RADIAL_GRADIENT
        this.gradientColors = colors
        this.gradientCenterX = centerX
        this.gradientCenterY = centerY
        this.gradientRadius = radius
        return this
    }

    /**
     * 设置扫描渐变
     * @param colors 渐变颜色数组
     * @param centerX 中心点X坐标（0-1）
     * @param centerY 中心点Y坐标（0-1）
     */
    fun setSweepGradient(colors: IntArray, centerX: Float, centerY: Float): ShapeBuilder {
        this.gradientType = GradientDrawable.SWEEP_GRADIENT
        this.gradientColors = colors
        this.gradientCenterX = centerX
        this.gradientCenterY = centerY
        return this
    }

    /**
     * 构建Drawable
     * @return GradientDrawable对象
     */
    fun build(): GradientDrawable {
        val drawable = GradientDrawable()
        
        // 设置形状
        drawable.shape = shapeType
        
        // 设置填充颜色
        if (gradientColors != null) {
            // 如果设置了渐变，则使用渐变
            when (gradientType) {
                GradientDrawable.LINEAR_GRADIENT -> {
                    drawable.colors = gradientColors
                    drawable.orientation = gradientOrientation
                }
                GradientDrawable.RADIAL_GRADIENT -> {
                    drawable.colors = gradientColors
                    drawable.gradientType = GradientDrawable.RADIAL_GRADIENT
                    drawable.setGradientCenter(gradientCenterX, gradientCenterY)
                    drawable.gradientRadius = gradientRadius
                }
                GradientDrawable.SWEEP_GRADIENT -> {
                    drawable.colors = gradientColors
                    drawable.gradientType = GradientDrawable.SWEEP_GRADIENT
                    drawable.setGradientCenter(gradientCenterX, gradientCenterY)
                }
            }
        } else {
            // 否则使用纯色填充
            drawable.setColor(solidColor)
        }
        
        // 设置边框
        if (strokeWidth > 0) {
            if (strokeDashWidth > 0 && strokeDashGap > 0) {
                drawable.setStroke(strokeWidth, strokeColor, strokeDashWidth, strokeDashGap)
            } else {
                drawable.setStroke(strokeWidth, strokeColor)
            }
        }
        
        // 设置圆角
        if (cornerRadii != null) {
            drawable.cornerRadii = cornerRadii
        } else if (cornerRadius > 0) {
            drawable.cornerRadius = cornerRadius
        }
        
        return drawable
    }

    /**
     * 为View设置背景
     * @param view 目标View
     */
    fun applyTo(view: View) {
        view.background = build()
    }

    companion object {
        /**
         * 创建矩形
         * @param color 填充颜色
         */
        fun rectangle(color: Int): ShapeBuilder {
            return ShapeBuilder().setShape(GradientDrawable.RECTANGLE).setSolidColor(color)
        }

        /**
         * 创建圆角矩形
         * @param color 填充颜色
         * @param radius 圆角半径
         */
        fun roundedRectangle(color: Int, radius: Float): ShapeBuilder {
            return ShapeBuilder().setShape(GradientDrawable.RECTANGLE).setSolidColor(color).setCornerRadius(radius)
        }

        /**
         * 创建带边框的圆角矩形
         * @param fillColor 填充颜色
         * @param strokeColor 边框颜色
         * @param strokeWidth 边框宽度
         * @param radius 圆角半径
         */
        fun roundedBorderedRectangle(fillColor: Int, strokeColor: Int, strokeWidth: Int, radius: Float): ShapeBuilder {
            return ShapeBuilder()
                .setShape(GradientDrawable.RECTANGLE)
                .setSolidColor(fillColor)
                .setStroke(strokeWidth, strokeColor)
                .setCornerRadius(radius)
        }

        /**
         * 创建椭圆
         * @param color 填充颜色
         */
        fun oval(color: Int): ShapeBuilder {
            return ShapeBuilder().setShape(GradientDrawable.OVAL).setSolidColor(color)
        }

        /**
         * 创建线
         * @param color 线条颜色
         * @param strokeWidth 线条宽度
         */
        fun line(color: Int, strokeWidth: Int): ShapeBuilder {
            return ShapeBuilder().setShape(GradientDrawable.LINE).setSolidColor(color).setStroke(strokeWidth, color)
        }

        /**
         * 创建带边框的形状
         * @param fillColor 填充颜色
         * @param strokeColor 边框颜色
         * @param strokeWidth 边框宽度
         */
        fun bordered(fillColor: Int, strokeColor: Int, strokeWidth: Int): ShapeBuilder {
            return ShapeBuilder().setShape(GradientDrawable.RECTANGLE).setSolidColor(fillColor).setStroke(strokeWidth, strokeColor)
        }

        /**
         * 创建线性渐变矩形
         * @param colors 渐变颜色数组
         * @param orientation 渐变方向
         */
        fun linearGradient(colors: IntArray, orientation: GradientDrawable.Orientation): ShapeBuilder {
            return ShapeBuilder().setShape(GradientDrawable.RECTANGLE).setLinearGradient(colors, orientation)
        }

        /**
         * 创建径向渐变椭圆
         * @param colors 渐变颜色数组
         * @param centerX 中心点X坐标
         * @param centerY 中心点Y坐标
         * @param radius 渐变半径
         */
        fun radialGradient(colors: IntArray, centerX: Float, centerY: Float, radius: Float): ShapeBuilder {
            return ShapeBuilder().setShape(GradientDrawable.OVAL).setRadialGradient(colors, centerX, centerY, radius)
        }
    }
}