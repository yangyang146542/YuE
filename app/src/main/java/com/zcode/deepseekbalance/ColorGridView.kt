package com.zcode.deepseekbalance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.ColorUtils

/**
 * 自绘调色板：32 色相 × 18 明度 = 576 格，一次触摸即可选色。
 * 每格都是不透明色，透明度由外部滑杆控制。
 */
class ColorGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val cols = 32
    private val rows = 18

    private val cells = IntArray(cols * rows)
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var selectedIndex = -1
    private var downX = 0f
    private var downY = 0f
    private var downIndex = -1
    private var picking = false

    /** 用户点选某个格子时回调（不含透明度）。 */
    var onColorPicked: ((Int) -> Unit)? = null

    init {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                cells[r * cols + c] = cellColor(c, r)
            }
        }
        ringPaint.strokeWidth = 3f * resources.displayMetrics.density
    }

    private fun cellColor(col: Int, row: Int): Int {
        val hue = col * 360f / cols
        val pure = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        val mid = rows / 2
        return when {
            row < mid -> blend(pure, Color.WHITE, (mid - row).toFloat() / mid * 0.88f)
            row == mid -> pure
            else -> blend(pure, Color.BLACK, (row - mid).toFloat() / (rows - 1 - mid) * 0.88f)
        }
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * f).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * f).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f).toInt()
        )
    }

    /** 共 576 格，满足「至少 512 色」。 */
    val colorCount: Int get() = cells.size

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthPx = MeasureSpec.getSize(widthMeasureSpec)
        val heightPx = (widthPx.toFloat() * rows / cols).toInt()
        setMeasuredDimension(widthPx, heightPx)
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val cellW = width.toFloat() / cols
        val cellH = height.toFloat() / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                cellPaint.color = cells[r * cols + c]
                canvas.drawRect(c * cellW, r * cellH, (c + 1) * cellW, (r + 1) * cellH, cellPaint)
            }
        }
        if (selectedIndex in cells.indices) {
            val r = selectedIndex / cols
            val c = selectedIndex % cols
            ringPaint.color = if (ColorUtils.calculateLuminance(cells[selectedIndex]) > 0.5)
                Color.BLACK else Color.WHITE
            canvas.drawRect(
                c * cellW + 2f, r * cellH + 2f,
                (c + 1) * cellW - 2f, (r + 1) * cellH - 2f,
                ringPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downIndex = indexAt(event.x, event.y)
                picking = false
                // 不调用 requestDisallowInterceptTouchEvent(true)：纵向滑动让外层 ScrollView 接管
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = kotlin.math.abs(event.x - downX)
                val dy = kotlin.math.abs(event.y - downY)
                if (dy > slop && dy > dx) {
                    // 纵向为主 → 交给 ScrollView，本次不选色
                    picking = false
                    return false
                }
                if (dx > slop || dy > slop) {
                    picking = true
                    pickAt(event.x, event.y)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                // 轻点（没有明显位移）才算选色
                if (!picking) pickAt(downX, downY)
                picking = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun indexAt(x: Float, y: Float): Int {
        if (width == 0 || height == 0) return -1
        val col = (x / (width.toFloat() / cols)).toInt().coerceIn(0, cols - 1)
        val row = (y / (height.toFloat() / rows)).toInt().coerceIn(0, rows - 1)
        return row * cols + col
    }

    private fun pickAt(x: Float, y: Float) {
        val index = indexAt(x, y)
        if (index >= 0 && index != selectedIndex) {
            selectedIndex = index
            invalidate()
            onColorPicked?.invoke(cells[index])
        }
    }

    /** 外部传入颜色时，反查并高亮匹配的格子。 */
    fun selectColor(argb: Int) {
        val rgb = argb and 0x00FFFFFF
        selectedIndex = cells.indexOfFirst { it and 0x00FFFFFF == rgb }
        invalidate()
    }
}
