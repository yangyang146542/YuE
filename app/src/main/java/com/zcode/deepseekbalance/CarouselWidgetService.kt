package com.zcode.deepseekbalance

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** 给 AdapterViewFlipper 提供帧。翻页由桌面宿主驱动，App 不需要常驻。 */
class CarouselWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CarouselFactory(applicationContext)
}

class CarouselFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var uris: List<Uri> = emptyList()
    private var repeat = 1
    private var step = 0

    override fun onCreate() {
        load()
    }

    override fun onDataSetChanged() {
        load()
    }

    private fun load() {
        uris = ImageRepository.listImages(context)
        val prefs = WidgetPrefs(context)
        // 桌面每 BASE_ADVANCE_SECONDS 秒推进一格；把每张图重复 repeat 遍，
        // 视觉上就变成每 repeat × 基准 秒换一张。
        // 点图片手动切换与自动轮播并存：点击把 carouselStep +1，等同于立刻往前跳一张。
        repeat = prefs.carouselMultiplier()
        step = prefs.carouselStep()
    }

    override fun getCount(): Int = if (uris.isEmpty()) 0 else uris.size * repeat

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_carousel_item)
        if (uris.isEmpty()) return views

        val prefs = WidgetPrefs(context)
        val density = context.resources.displayMetrics.density
        val widthPx = (prefs.boxWidthDp() * density).toInt().coerceIn(1, 512)
        val heightPx = (prefs.boxHeightDp() * density).toInt().coerceIn(1, 512)
        val radiusPx = prefs.boxCornerDp() * density

        val index = ((position / repeat) + step) % uris.size
        val bitmap = ImageRepository.loadFrame(
            context,
            uris[index],
            widthPx,
            heightPx,
            prefs.scaleMode(),
            radiusPx
        )
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.carouselImage, bitmap)
        }
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

    override fun onDestroy() {
        uris = emptyList()
    }
}
