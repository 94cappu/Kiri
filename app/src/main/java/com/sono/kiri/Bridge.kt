package com.sono.kiri

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.JavascriptInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class Bridge(private val ctx: Context) {

    @JavascriptInterface
    fun saveWidgetData(json: String) {
        ctx.getSharedPreferences("kiri", Context.MODE_PRIVATE).edit().putString("data", json).apply()
        WidgetUpdater.updateAll(ctx)
    }

    @JavascriptInterface
    fun saveImage(dataUrl: String) {
        val file = File(ctx.filesDir, "mascot.png")
        if (dataUrl.isEmpty()) {
            file.delete()
        } else {
            try {
                val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
                val scale = 360f / max(bmp.width, bmp.height)
                val out = if (scale < 1f) {
                    Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                } else {
                    bmp
                }
                FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (e: Exception) {
                return
            }
        }
        WidgetUpdater.updateAll(ctx)
    }
}
