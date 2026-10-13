package com.zcode.deepseekbalance

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 负责把小部件画出来，以及拉取余额后刷新所有小部件。 */
object WidgetRenderer {

    private const val BG_MAX_WIDTH_PX = 512

    /** 背景面板相对部件边缘的内缩量，必须与布局中 widgetBg 的 layout_margin 一致。 */
    private const val PANEL_INSET_DP = 8f

    private const val BALANCE_SP = 28f
    private const val LABEL_SP = 12f
    private const val STATUS_SP = 11f

    private const val TARGET_BG = "bg"
    private const val TARGET_TEXT = "text"
    private const val TARGET_ICON = "icon"
    private const val TARGET_BORDER = "border"

    /** 文字区与图片框之间留的空隙。 */
    private const val TEXT_GAP_DP = 12f

    /** 按缓存内容重绘所有小部件（不发网络请求）。 */
    fun renderAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, BalanceWidgetProvider::class.java))
        for (id in ids) {
            render(context, manager, id)
        }
    }

    /** 拉取余额并刷新所有小部件。会阻塞，请在后台线程调用。 */
    fun refreshNow(context: Context) {
        val prefs = WidgetPrefs(context)
        val provider = prefs.provider()
        val key = KeyStore(context).load(provider)
        if (key.isEmpty()) {
            renderAll(context)
            return
        }
        try {
            val result = BalanceApi.fetch(provider, key)
            prefs.saveResult(
                balance = symbolFor(result.currency) + result.total,
                amount = result.numericTotal,
                currency = result.currency,
                error = null
            )
        } catch (e: ApiError) {
            prefs.saveResult(null, null, null, shortError(e))
        } catch (e: Exception) {
            prefs.saveResult(null, null, null, "未知错误")
        }
        renderAll(context)
    }

    fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val prefs = WidgetPrefs(context)
        val style = prefs.style()
        val scale = prefs.fontScale()

        val bgColor = resolveColor(context, prefs, TARGET_BG, style)
        val textColor = resolveColor(context, prefs, TARGET_TEXT, style, bgColor)

        val views = RemoteViews(context.packageName, R.layout.widget_balance)

        views.setImageViewBitmap(
            R.id.widgetBg,
            roundedBackground(
                context, manager, widgetId,
                if (style == WidgetPrefs.STYLE_TRANSPARENT) Color.TRANSPARENT else bgColor,
                prefs.cornerRadiusDp()
            )
        )

        val secondary = withAlpha(textColor, 0.75f)
        views.setTextColor(R.id.widgetLabel, secondary)
        views.setTextColor(R.id.widgetBalance, textColor)
        views.setTextColor(R.id.widgetUpdated, secondary)

        views.setTextViewTextSize(R.id.widgetLabel, TypedValue.COMPLEX_UNIT_SP, LABEL_SP * scale)
        views.setTextViewTextSize(R.id.widgetBalance, TypedValue.COMPLEX_UNIT_SP, BALANCE_SP * scale)
        views.setTextViewTextSize(R.id.widgetUpdated, TypedValue.COMPLEX_UNIT_SP, STATUS_SP * scale)

        val hasKey = KeyStore(context).load(prefs.provider()).isNotEmpty()
        views.setTextViewText(
            R.id.widgetBalance,
            if (hasKey && prefs.cachedBalance() != null) prefs.cachedBalance()!!
            else context.getString(R.string.value_placeholder)
        )
        views.setTextViewText(R.id.widgetUpdated, statusText(context, prefs, hasKey))

        // 整块部件可点击 —— 单击非图片区域即手动刷新余额。
        // 根视图和背景图都挂同一个 PendingIntent：不同桌面对根视图的点击分发行为不一致，
        // 铺两层能保证只要没点中图片框就一定能触发刷新。
        val refreshPending = WidgetScheduler.refreshPendingIntent(context)
        views.setOnClickPendingIntent(R.id.widgetRoot, refreshPending)
        views.setOnClickPendingIntent(R.id.widgetBg, refreshPending)
        views.setOnClickPendingIntent(R.id.widgetTextArea, refreshPending)

        renderCarousel(context, views, prefs, widgetId, style, bgColor)

        manager.updateAppWidget(widgetId, views)
    }

    // ================= 图片轮播框 =================

    private fun renderCarousel(
        context: Context,
        views: RemoteViews,
        prefs: WidgetPrefs,
        widgetId: Int,
        style: Int,
        bgColor: Int
    ) {
        val enabled = prefs.carouselEnabled() && !prefs.folderUri().isNullOrEmpty()
        if (!enabled) {
            views.setViewVisibility(R.id.imageBox, View.GONE)
            return
        }

        val boxWidthDp = prefs.boxWidthDp()
        val boxHeightDp = prefs.boxHeightDp()
        val rightDp = prefs.boxRightDp()
        val topDp = prefs.boxTopDp()

        // 位置（距右边界 / 上边界）与宽高都是动态设定的（API 31+）
        views.setViewVisibility(R.id.imageBox, View.VISIBLE)
        views.setViewLayoutWidth(R.id.imageBox, boxWidthDp, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(R.id.imageBox, boxHeightDp, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutMargin(
            R.id.imageBox, RemoteViews.MARGIN_END, rightDp, TypedValue.COMPLEX_UNIT_DIP
        )
        views.setViewLayoutMargin(
            R.id.imageBox, RemoteViews.MARGIN_TOP, topDp, TypedValue.COMPLEX_UNIT_DIP
        )

        // 文字区让出右侧空间，避免和图片框重叠
        views.setViewLayoutMargin(
            R.id.widgetTextArea,
            RemoteViews.MARGIN_END,
            rightDp + boxWidthDp + TEXT_GAP_DP,
            TypedValue.COMPLEX_UNIT_DIP
        )

        // 圆角描边边框（中间透明，叠在图片之上）
        val density = context.resources.displayMetrics.density
        val borderColor = resolveColor(context, prefs, TARGET_BORDER, style, bgColor)
        val borderDp = prefs.boxBorderDp()
        if (borderDp <= 0f) {
            // 粗细为 0 必须整块隐藏：Paint.strokeWidth = 0 是「hairline」模式，
            // 仍然会画出 1px 实线，所以「不画」只能靠不显示这一层。
            views.setViewVisibility(R.id.imageBoxBorder, View.GONE)
        } else {
            views.setViewVisibility(R.id.imageBoxBorder, View.VISIBLE)
            views.setImageViewBitmap(
                R.id.imageBoxBorder,
                borderBitmap(
                    (boxWidthDp * density).toInt().coerceAtLeast(1),
                    (boxHeightDp * density).toInt().coerceAtLeast(1),
                    borderColor,
                    prefs.boxCornerDp() * density,
                    borderDp * density
                )
            )
        }

        // 轮播：翻页完全由桌面宿主驱动（balance_widget_info.xml 的 autoAdvanceViewId），
        // 翻页间隔由平台决定（实测约 10 秒）。
        // setAutoStart / setFlipInterval 都不是 @RemotableViewMethod，一旦调用宿主就抛
        // ActionException，整个部件会变成「无法加载微件」——已经踩过，别再调。
        val adapterIntent = Intent(context, CarouselWidgetService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        views.setRemoteAdapter(R.id.carousel, adapterIntent)

        // 点击图片区域 = 手动换下一张
        val advanceIntent = Intent(context, BalanceWidgetProvider::class.java)
            .setAction(BalanceWidgetProvider.ACTION_ADVANCE)
        val advancePending = PendingIntent.getBroadcast(
            context,
            4301,
            advanceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.imageBox, advancePending)
    }

    /** 画一张正好等于框像素尺寸的圆角描边位图（中间透明）。 */
    private fun borderBitmap(
        widthPx: Int,
        heightPx: Int,
        color: Int,
        radiusPx: Float,
        strokePx: Float
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        // strokeWidth <= 0 会被 Paint 当成 hairline（1px 实线），所以这里直接不画，
        // 返回一张全透明位图 —— 与调用方「隐藏边框层」是双保险。
        if (strokePx <= 0f) return bitmap
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            this.color = color
        }
        val inset = strokePx / 2f
        val r = radiusPx.coerceAtMost(minOf(widthPx, heightPx) / 2f - inset).coerceAtLeast(0f)
        canvas.drawRoundRect(
            RectF(inset, inset, widthPx - inset, heightPx - inset), r, r, paint
        )
        return bitmap
    }

    // ================= 配色 =================

    /**
     * 解析某个位置（bg/text/icon）的颜色。
     *
     * - 来源 = 系统 Material：取该位置默认的语义角色；纯色风格下默认角色会按背景亮度自动取深/浅
     * - 来源 = 调色板 / 色号：用用户选定的 ARGB（含透明度）
     */
    private fun resolveColor(
        context: Context,
        prefs: WidgetPrefs,
        target: String,
        style: Int,
        resolvedBg: Int = Color.TRANSPARENT
    ): Int {
        val source = prefs.colorSource(target)
        if (source != WidgetPrefs.COLOR_ROLE) {
            return prefs.colorCustom(target)
        }

        val role = prefs.colorRole(target)
        val isDefaultRole = role == defaultRole(target)

        return when (target) {
            TARGET_BG -> when (style) {
                WidgetPrefs.STYLE_SOLID -> WidgetSettingsActivity.resolveRoleColor(context, role)
                WidgetPrefs.STYLE_TRANSPARENT -> Color.TRANSPARENT
                else -> ContextCompat.getColor(context, R.color.widget_bg_frosted)
            }

            else -> {
                // 纯色风格 + 使用默认角色时，按背景亮度自动取前景色，保证可读
                if (style == WidgetPrefs.STYLE_SOLID && isDefaultRole) {
                    contentColorFor(resolvedBg)
                } else {
                    WidgetSettingsActivity.resolveRoleColor(context, role)
                }
            }
        }
    }

    private fun defaultRole(target: String) = when (target) {
        TARGET_BG -> WidgetPrefs.ROLE_PRIMARY_CONTAINER
        TARGET_TEXT -> WidgetPrefs.ROLE_ON_SURFACE
        else -> WidgetPrefs.ROLE_PRIMARY
    }

    /** 背景深则用白字，背景浅则用深字。 */
    private fun contentColorFor(background: Int): Int =
        if (ColorUtils.calculateLuminance(background) > 0.5) 0xFF1A1C1E.toInt()
        else 0xFFFFFFFF.toInt()

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha * 255).toInt().coerceIn(0, 255) shl 24)

    // ================= 绘制 =================

    /** 按小部件当前尺寸与设定的圆角，画一张背景位图。 */
    private fun roundedBackground(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        fillColor: Int,
        radiusDp: Float
    ): Bitmap {
        val options = manager.getAppWidgetOptions(widgetId)
        val density = context.resources.displayMetrics.density
        // options 给的是「可调整范围」而不是当前尺寸：实测当前宽 ≈ MIN_WIDTH、当前高 ≈ MAX_HEIGHT
        // （本机 min=271x72 / max=505x150，实际渲染 272x151dp）。用这两者估宽高比，
        // 位图才不会被 fitXY 拉伸成椭圆。
        val rawWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val rawMinHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        val rawMaxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, rawMinHeight)
        val rawHeight = maxOf(rawMinHeight, rawMaxHeight)

        val inset = (2 * PANEL_INSET_DP).toInt()
        val widthDp = (rawWidth - inset).coerceAtLeast(1)
        val heightDp = (rawHeight - inset).coerceAtLeast(1)

        val fullWidth = widthDp * density
        val fullHeight = heightDp * density
        val shrink = if (fullWidth > BG_MAX_WIDTH_PX) BG_MAX_WIDTH_PX / fullWidth else 1f
        val bitmapWidth = (fullWidth * shrink).toInt().coerceAtLeast(1)
        val bitmapHeight = (fullHeight * shrink).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
        val radius = radiusDp * density * shrink
        canvas.drawRoundRect(
            RectF(0f, 0f, bitmapWidth.toFloat(), bitmapHeight.toFloat()),
            radius, radius, paint
        )
        return bitmap
    }

    // ================= 文本 =================

    private fun statusText(context: Context, prefs: WidgetPrefs, hasKey: Boolean): String {
        // 点击刷新后立刻显示「刷新中…」，让点击有可见反馈
        if (prefs.isRefreshing()) return context.getString(R.string.widget_refreshing)

        if (!hasKey) return context.getString(R.string.widget_no_key)

        prefs.cachedError()?.let { return context.getString(R.string.widget_update_failed, it) }

        val parts = mutableListOf<String>()
        prefs.usedAmount()?.let {
            parts += context.getString(R.string.widget_used, money(it, prefs.cachedCurrency()))
        }
        prefs.cachedUpdatedAt().takeIf { it > 0L }?.let { parts += formatTime(it) }

        return parts.joinToString("  ·  ").ifEmpty { context.getString(R.string.widget_never_updated) }
    }

    fun money(amount: Double?, currency: String): String? {
        if (amount == null) return null
        return symbolFor(currency) + String.format(Locale.US, "%.2f", amount)
    }

    private fun symbolFor(currency: String) = when (currency.uppercase()) {
        "CNY" -> "¥"
        "USD" -> "$"
        else -> ""
    }

    private fun shortError(e: ApiError): String = when (e) {
        is ApiError.Unauthorized -> "API Key 无效"
        is ApiError.InsufficientBalance -> "余额不足"
        is ApiError.RateLimited -> "请求过于频繁"
        is ApiError.ServerError -> "服务端错误 ${e.code}"
        is ApiError.Parse -> "返回无法解析"
        is ApiError.Network -> "网络连接失败"
    }

    private fun formatTime(at: Long): String {
        val then = Calendar.getInstance().apply { timeInMillis = at }
        val now = Calendar.getInstance()
        val sameDay = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "HH:mm" else "MM-dd HH:mm"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(at))
    }
}
