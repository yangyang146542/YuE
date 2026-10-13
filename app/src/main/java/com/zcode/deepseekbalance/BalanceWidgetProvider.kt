package com.zcode.deepseekbalance

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class BalanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 先用缓存画出来（立刻可见），再异步拉一次最新余额
        WidgetRenderer.renderAll(context)
        WidgetScheduler.schedule(context)
        requestRefresh(context)
    }

    /** 小部件被拖动改变尺寸后，背景位图需要按新尺寸重画。 */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        WidgetRenderer.render(context, appWidgetManager, appWidgetId)
    }

    override fun onEnabled(context: Context) {
        WidgetScheduler.schedule(context)
        requestRefresh(context)
    }

    override fun onDisabled(context: Context) {
        WidgetScheduler.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> refreshInBackground(context)

            // 点击图片区域：把轮播往前推一张，然后重绘（重绘会重设 remote adapter，回到第 0 帧）
            ACTION_ADVANCE -> {
                val prefs = WidgetPrefs(context)
                prefs.setCarouselStep(prefs.carouselStep() + 1)
                WidgetRenderer.renderAll(context)
            }
        }
    }

    /** 走 goAsync()，这样网络请求可以在 onReceive 返回后继续完成。 */
    private fun refreshInBackground(context: Context) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                val prefs = WidgetPrefs(appContext)
                // 先显示「刷新中…」，让点击立刻有可见反馈，也便于确认点击是否生效
                prefs.setRefreshing(true)
                WidgetRenderer.renderAll(appContext)
                WidgetRenderer.refreshNow(appContext)
                prefs.setRefreshing(false)
                WidgetRenderer.renderAll(appContext)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun requestRefresh(context: Context) {
        context.sendBroadcast(
            Intent(context, BalanceWidgetProvider::class.java).setAction(ACTION_REFRESH)
        )
    }

    companion object {
        const val ACTION_REFRESH = "com.zcode.deepseekbalance.action.WIDGET_REFRESH"
        const val ACTION_ADVANCE = "com.zcode.deepseekbalance.action.WIDGET_ADVANCE"
    }
}
