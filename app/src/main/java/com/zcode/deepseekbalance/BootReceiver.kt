package com.zcode.deepseekbalance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 重启后闹钟会被清空，这里重新排程并刷新一次。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WidgetScheduler.schedule(context)
            WidgetRenderer.renderAll(context)
        }
    }
}
