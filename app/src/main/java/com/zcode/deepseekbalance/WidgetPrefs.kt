package com.zcode.deepseekbalance

import android.content.Context

/**
 * 小部件偏好设置。
 *
 * 外观相关（风格 / 圆角 / 文字大小 / 三处颜色）是**全局**的 —— 小部件上不再有设置按钮，
 * 统一在 App 主活动里设置，新建的小部件也直接沿用。
 * 另有全局的自动更新间隔、充值总量、最近一次余额缓存。
 */
class WidgetPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    // ================= 全局 =================

    fun intervalMinutes(): Int = prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL)

    fun setIntervalMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_INTERVAL, minutes).apply()
    }

    /** 用户自己填的「充值总量」，0 表示未设置。 */
    fun rechargeTotal(): Double =
        prefs.getString(KEY_RECHARGE, null)?.toDoubleOrNull() ?: 0.0

    fun setRechargeTotal(value: Double) {
        prefs.edit().apply {
            if (value > 0) putString(KEY_RECHARGE, value.toString()) else remove(KEY_RECHARGE)
        }.apply()
    }

    // ================= 外观（全局） =================

    fun style(): Int = prefs.getInt(KEY_STYLE, STYLE_FROSTED)

    fun setStyle(style: Int) {
        prefs.edit().putInt(KEY_STYLE, style).apply()
    }

    fun fontScale(): Float = prefs.getFloat(KEY_FONT_SCALE, 1f)

    fun setFontScale(scale: Float) {
        prefs.edit().putFloat(KEY_FONT_SCALE, scale).apply()
    }

    fun cornerRadiusDp(): Float = prefs.getFloat(KEY_CORNER, 24f)

    fun setCornerRadiusDp(radiusDp: Float) {
        prefs.edit().putFloat(KEY_CORNER, radiusDp).apply()
    }

    // ---- 颜色：target 取 bg / text / icon ----

    fun colorSource(target: String): Int = prefs.getInt("${target}_src", COLOR_ROLE)

    fun setColorSource(target: String, source: Int) {
        prefs.edit().putInt("${target}_src", source).apply()
    }

    fun colorRole(target: String): Int = prefs.getInt("${target}_role", defaultRole(target))

    fun setColorRole(target: String, role: Int) {
        prefs.edit().putInt("${target}_role", role).apply()
    }

    fun colorCustom(target: String): Int = prefs.getInt("${target}_custom", DEFAULT_CUSTOM)

    fun setColorCustom(target: String, color: Int) {
        prefs.edit().putInt("${target}_custom", color).apply()
    }

    // ================= 平台选择 =================

    /** 当前选中的平台（全局，App 与小部件共用）。 */
    fun provider(): Provider = Provider.of(prefs.getString(KEY_PROVIDER, null))

    fun setProvider(provider: Provider) {
        prefs.edit().putString(KEY_PROVIDER, provider.id).apply()
    }

    // ================= 图片轮播 =================

    fun carouselEnabled(): Boolean = prefs.getBoolean(KEY_CAROUSEL_ON, false)

    fun setCarouselEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CAROUSEL_ON, enabled).apply()
    }

    /** 用户选定的图片文件夹（SAF tree uri）。 */
    fun folderUri(): String? = prefs.getString(KEY_FOLDER_URI, null)

    fun setFolderUri(uri: String?) {
        prefs.edit().apply {
            if (uri.isNullOrEmpty()) remove(KEY_FOLDER_URI) else putString(KEY_FOLDER_URI, uri)
        }.apply()
    }

    /** 图片框右边缘距部件右边缘的距离。 */
    fun boxRightDp(): Float = prefs.getFloat(KEY_BOX_RIGHT, 16f)

    fun setBoxRightDp(value: Float) {
        prefs.edit().putFloat(KEY_BOX_RIGHT, value).apply()
    }

    /** 图片框上边缘距部件上边缘的距离。 */
    fun boxTopDp(): Float = prefs.getFloat(KEY_BOX_TOP, 24f)

    fun setBoxTopDp(value: Float) {
        prefs.edit().putFloat(KEY_BOX_TOP, value).apply()
    }

    fun boxWidthDp(): Float = prefs.getFloat(KEY_BOX_W, 72f)

    fun setBoxWidthDp(value: Float) {
        prefs.edit().putFloat(KEY_BOX_W, value).apply()
    }

    fun boxHeightDp(): Float = prefs.getFloat(KEY_BOX_H, 72f)

    fun setBoxHeightDp(value: Float) {
        prefs.edit().putFloat(KEY_BOX_H, value).apply()
    }

    /** 图片框自己的圆角。 */
    fun boxCornerDp(): Float = prefs.getFloat(KEY_BOX_CORNER, 16f)

    fun setBoxCornerDp(value: Float) {
        prefs.edit().putFloat(KEY_BOX_CORNER, value).apply()
    }

    fun boxBorderDp(): Float = prefs.getFloat(KEY_BOX_BORDER, 2f)

    fun setBoxBorderDp(value: Float) {
        prefs.edit().putFloat(KEY_BOX_BORDER, value).apply()
    }

    /** 轮播间隔（毫秒）。注意：桌面宿主固定按自己的基准推进，此值目前不生效，保留备用。 */
    fun carouselIntervalMs(): Int = prefs.getInt(KEY_CAROUSEL_MS, 3000)

    fun setCarouselIntervalMs(value: Int) {
        prefs.edit().putInt(KEY_CAROUSEL_MS, value.coerceIn(500, 60_000)).apply()
    }

    /**
     * 轮播倍数：桌面每 [BASE_ADVANCE_SECONDS] 秒推进一格，把每张图重复 k 遍，
     * 视觉上就变成每 k×基准 秒换一张。默认 1。
     */
    fun carouselMultiplier(): Int =
        prefs.getInt(KEY_CAROUSEL_K, 1).coerceIn(1, MAX_MULTIPLIER)

    fun setCarouselMultiplier(value: Int) {
        prefs.edit().putInt(KEY_CAROUSEL_K, value.coerceIn(1, MAX_MULTIPLIER)).apply()
    }

    /** 手动模式：相当于把倍数拉到极大，视觉上不自动换图，靠点击图片切换。 */
    fun carouselManual(): Boolean = prefs.getBoolean(KEY_CAROUSEL_MANUAL, false)

    fun setCarouselManual(value: Boolean) {
        prefs.edit().putBoolean(KEY_CAROUSEL_MANUAL, value).apply()
    }

    /** 手动点图的偏移量；每点一次 +1。 */
    fun carouselStep(): Int = prefs.getInt(KEY_CAROUSEL_STEP, 0)

    fun setCarouselStep(value: Int) {
        prefs.edit().putInt(KEY_CAROUSEL_STEP, value).apply()
    }

    /** 正在刷新余额时置 true，用于在小部件上显示「刷新中…」反馈。 */
    fun isRefreshing(): Boolean = prefs.getBoolean(KEY_REFRESHING, false)

    fun setRefreshing(value: Boolean) {
        prefs.edit().putBoolean(KEY_REFRESHING, value).apply()
    }

    /** 缩放方式：SCALE_CROP / SCALE_STRETCH / SCALE_FIT */
    fun scaleMode(): Int = prefs.getInt(KEY_SCALE_MODE, SCALE_CROP)

    fun setScaleMode(mode: Int) {
        prefs.edit().putInt(KEY_SCALE_MODE, mode).apply()
    }

    // ================= 余额缓存 =================

    fun cachedBalance(): String? = prefs.getString(KEY_CACHED_BALANCE, null)

    fun cachedAmount(): Double =
        prefs.getString(KEY_CACHED_AMOUNT, null)?.toDoubleOrNull() ?: -1.0

    fun cachedCurrency(): String = prefs.getString(KEY_CACHED_CURRENCY, "CNY") ?: "CNY"

    fun cachedUpdatedAt(): Long = prefs.getLong(KEY_CACHED_AT, 0L)

    fun cachedError(): String? = prefs.getString(KEY_CACHED_ERROR, null)

    /** 已用金额 = 充值总量 − 当前总额；未设置充值总量时返回 null。 */
    fun usedAmount(): Double? {
        val recharge = rechargeTotal()
        val total = cachedAmount()
        if (recharge <= 0 || total < 0) return null
        return recharge - total
    }

    fun saveResult(balance: String?, amount: Double?, currency: String?, error: String?) {
        val editor = prefs.edit()
        if (balance != null && amount != null) {
            editor.putString(KEY_CACHED_BALANCE, balance)
            editor.putString(KEY_CACHED_AMOUNT, amount.toString())
            if (currency != null) editor.putString(KEY_CACHED_CURRENCY, currency)
        }
        editor.putString(KEY_CACHED_ERROR, error)
        if (error == null) editor.putLong(KEY_CACHED_AT, System.currentTimeMillis())
        editor.apply()
    }

    private fun defaultRole(target: String) = when (target) {
        "bg" -> ROLE_PRIMARY_CONTAINER
        "text" -> ROLE_ON_SURFACE
        else -> ROLE_PRIMARY
    }

    companion object {
        const val STYLE_FROSTED = 0
        const val STYLE_TRANSPARENT = 1
        const val STYLE_SOLID = 2

        /** 可调颜色的位置：背景 / 文字 / 图标 / 图片框边框 */
        val TARGETS = listOf("bg", "text", "icon", "border")

        const val SCALE_CROP = 0
        const val SCALE_STRETCH = 1
        const val SCALE_FIT = 2

        /** 颜色来源 */
        const val COLOR_ROLE = 0    // 系统 Material 语义色
        const val COLOR_PALETTE = 1 // 调色板
        const val COLOR_HEX = 2     // 手输色号

        /** Material 3 语义角色下标，顺序必须与 MATERIAL_ROLES 一致。 */
        const val ROLE_PRIMARY = 0
        const val ROLE_ON_PRIMARY = 1
        const val ROLE_PRIMARY_CONTAINER = 2
        const val ROLE_ON_PRIMARY_CONTAINER = 3
        const val ROLE_SECONDARY = 4
        const val ROLE_SECONDARY_CONTAINER = 5
        const val ROLE_TERTIARY = 6
        const val ROLE_TERTIARY_CONTAINER = 7
        const val ROLE_ERROR = 8
        const val ROLE_SURFACE = 9
        const val ROLE_SURFACE_VARIANT = 10
        const val ROLE_OUTLINE = 11
        const val ROLE_ON_SURFACE = 12
        const val ROLE_ON_SURFACE_VARIANT = 13

        const val INTERVAL_OFF = 0
        const val DEFAULT_INTERVAL = 30

        const val DEFAULT_CUSTOM = 0xFF4D6BFE.toInt()

        val FONT_SCALES = floatArrayOf(0.85f, 1f, 1.25f, 1.5f)

        /**
         * 桌面宿主推进轮播的基准间隔（秒）。这是**实测值**，不是 API 能给的东西：
         * `AdapterViewFlipper` 的 setAutoStart / setFlipInterval 都不是 @RemotableViewMethod，
         * 只能靠 widget info 里的 autoAdvanceViewId 让宿主推进，节奏由宿主决定。
         * 本机（Launcher3 / Android 16）实测 18–23 秒，取 20 秒。
         */
        const val BASE_ADVANCE_SECONDS = 20

        /** 倍数上限；手动模式用这个极大的值等效于「不自动换」。 */
        const val MAX_MULTIPLIER = 180
        const val MANUAL_MULTIPLIER = 3600

        private const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_RECHARGE = "recharge_total"
        private const val KEY_STYLE = "style"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_CORNER = "corner"
        private const val KEY_CACHED_BALANCE = "cached_balance"
        private const val KEY_CACHED_AMOUNT = "cached_amount"
        private const val KEY_CACHED_CURRENCY = "cached_currency"
        private const val KEY_CACHED_AT = "cached_at"
        private const val KEY_CACHED_ERROR = "cached_error"
        private const val KEY_CAROUSEL_ON = "carousel_on"
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_BOX_RIGHT = "box_right"
        private const val KEY_BOX_TOP = "box_top"
        private const val KEY_BOX_W = "box_w"
        private const val KEY_BOX_H = "box_h"
        private const val KEY_BOX_CORNER = "box_corner"
        private const val KEY_BOX_BORDER = "box_border"
        private const val KEY_CAROUSEL_MS = "carousel_ms"
        private const val KEY_CAROUSEL_K = "carousel_k"
        private const val KEY_CAROUSEL_MANUAL = "carousel_manual"
        private const val KEY_CAROUSEL_STEP = "carousel_step"
        private const val KEY_REFRESHING = "refreshing"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_SCALE_MODE = "scale_mode"
    }
}
