package com.sono.kiri

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.JavascriptInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class Bridge(
    private val ctx: Context,
    private val onDark: (Boolean) -> Unit,
    private val onAskNotif: () -> Unit
) {

    @JavascriptInterface
    fun scheduleNotifs(json: String) {
        Notifier.schedule(ctx, json)
    }

    @JavascriptInterface
    fun requestNotifPermission() {
        onAskNotif()
    }

    @JavascriptInterface
    fun notifGranted(): Boolean = Notifier.granted(ctx)

    @JavascriptInterface
    fun setDark(dark: Boolean) {
        onDark(dark)
    }

    @JavascriptInterface
    fun isSystemDark(): Boolean {
        val mode = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    @JavascriptInterface
    fun saveWidgetData(json: String) {
        ctx.getSharedPreferences("kiri", Context.MODE_PRIVATE).edit().putString("data", json).apply()
        WidgetUpdater.updateAll(ctx)
    }

    @JavascriptInterface
    fun saveCounterImage(dataUrl: String) {
        val file = File(ctx.filesDir, "counter.jpg")
        if (dataUrl.isEmpty()) {
            file.delete()
        } else {
            try {
                val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
                val scale = 600f / max(bmp.width, bmp.height)
                val out = if (scale < 1f) {
                    Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                } else {
                    bmp
                }
                FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            } catch (e: Exception) {
                return
            }
        }
        WidgetUpdater.updateAll(ctx)
    }

    @JavascriptInterface
    fun saveFrame(name: String, dataUrl: String) {
        if (name != "sit" && name != "face" && name != "closeup" && name != "petimg" && name != "notif") return
        writeImage(File(ctx.filesDir, "mascot_" + name + ".png"), dataUrl)
        WidgetUpdater.updateAll(ctx)
    }

    @JavascriptInterface
    fun saveSleepImage(dataUrl: String) {
        writeImage(File(ctx.filesDir, "mascot_sleep.png"), dataUrl)
        WidgetUpdater.updateAll(ctx)
    }

    private fun writeImage(file: File, dataUrl: String) {
        if (dataUrl.isEmpty()) {
            file.delete()
            return
        }
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
