package com.zcode.deepseekbalance

import android.app.Application
import com.google.android.material.color.DynamicColors

/**
 * 在 Application 启动时应用 Material You 动态取色。
 * Android 12（API 31）及以上会跟随壁纸取色；API 30 自动回退到主题里的 seed 配色。
 */
class DeepSeekApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
