package com.zcode.deepseekbalance

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 用 AlarmManager 做小部件的自动更新调度。
 *
 * 用「单次闹钟 + 每次触发后重新排程」的方式，这样间隔可以自由设定；
 * setAndAllowWhileIdle 在 Doze 下也能触发，且不需要 SCHEDULE_EXACT_ALARM 权限。
 */
object WidgetScheduler {

    private const val REQUEST_CODE = 4201

    fun refreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BalanceWidgetProvider::class.java).apply {
            action = BalanceWidgetProvider.ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 取消旧闹钟并按当前设置的间隔重新排程；间隔为 0 表示关闭自动更新。 */
    fun schedule(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = refreshPendingIntent(context)
        manager.cancel(pendingIntent)

        val minutes = WidgetPrefs(context).intervalMinutes()
        if (minutes <= 0) return

        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (e: SecurityException) {
            manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(refreshPendingIntent(context))
    }
}
