package com.zcode.deepseekbalance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** 从用户选定的文件夹里枚举图片，并按目标尺寸 + 缩放模式解出每一帧。 */
object ImageRepository {

    private val IMAGE_EXT = setOf("webp", "png", "jpg", "jpeg", "gif", "bmp", "heic", "heif")

    /** 列出文件夹内的图片，按名称排序。 */
    fun listImages(context: Context): List<Uri> {
        val treeUri = WidgetPrefs(context).folderUri()?.let { Uri.parse(it) } ?: return emptyList()
        return try {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
            root.listFiles()
                .filter { it.isFile && isImage(it.name, it.type) }
                .mapNotNull { it.uri }
                .sortedBy { it.toString() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isImage(name: String?, mime: String?): Boolean {
        if (mime != null && mime.startsWith("image/")) return true
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return ext in IMAGE_EXT
    }

    /**
     * 解码并变换成正好 widthPx × heightPx 的位图；四角按 cornerRadiusPx 裁成圆角。
     * mode: WidgetPrefs.SCALE_CROP / SCALE_STRETCH / SCALE_FIT
     */
    fun loadFrame(
        context: Context,
        uri: Uri,
        widthPx: Int,
        heightPx: Int,
        mode: Int,
        cornerRadiusPx: Float
    ): Bitmap? {
        val w = widthPx.coerceIn(1, 512)
        val h = heightPx.coerceIn(1, 512)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)
                ?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (e: Exception) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, w, h)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val src = try {
            context.contentResolver.openInputStream(uri)
                ?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            null
        } ?: return null

        val out = transform(src, w, h, mode, cornerRadiusPx)
        if (out !== src) src.recycle()
        return out
    }

    /** 取 2 的幂，保证解码结果不小于目标尺寸。 */
    private fun sampleSize(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
        var sample = 1
        while (srcW / (sample * 2) >= dstW && srcH / (sample * 2) >= dstH) {
            sample *= 2
        }
        return sample
    }

    private fun transform(src: Bitmap, w: Int, h: Int, mode: Int, cornerRadiusPx: Float): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (cornerRadiusPx > 0f) {
            val path = Path().apply {
                addRoundRect(
                    RectF(0f, 0f, w.toFloat(), h.toFloat()),
                    cornerRadiusPx, cornerRadiusPx, Path.Direction.CW
                )
            }
            canvas.clipPath(path)
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val dst = Rect(0, 0, w, h)

        when (mode) {
            WidgetPrefs.SCALE_STRETCH -> canvas.drawBitmap(src, Rect(0, 0, src.width, src.height), dst, paint)

            WidgetPrefs.SCALE_FIT -> {
                val s = minOf(w.toFloat() / src.width, h.toFloat() / src.height)
                val dw = (src.width * s).toInt().coerceAtLeast(1)
                val dh = (src.height * s).toInt().coerceAtLeast(1)
                val left = (w - dw) / 2
                val top = (h - dh) / 2
                canvas.drawBitmap(
                    src, Rect(0, 0, src.width, src.height),
                    Rect(left, top, left + dw, top + dh), paint
                )
            }

            else -> { // SCALE_CROP：等比放大后居中裁剪
                val targetAspect = w.toFloat() / h
                val srcAspect = src.width.toFloat() / src.height
                val rect = if (srcAspect > targetAspect) {
                    val cw = (src.height * targetAspect).toInt().coerceAtLeast(1)
                    val left = (src.width - cw) / 2
                    Rect(left, 0, left + cw, src.height)
                } else {
                    val ch = (src.width / targetAspect).toInt().coerceAtLeast(1)
                    val top = (src.height - ch) / 2
                    Rect(0, top, src.width, top + ch)
                }
                canvas.drawBitmap(src, rect, dst, paint)
            }
        }
        return out
    }
}
