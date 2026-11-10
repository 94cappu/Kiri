package com.sono.kiri

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import org.json.JSONArray

object Notifier {
    private const val CHANNEL = "lembretes"

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, ctx.getString(R.string.ch_name), NotificationManager.IMPORTANCE_HIGH)
            ch.description = ctx.getString(R.string.ch_desc)
            nm.createNotificationChannel(ch)
        }
    }

    private fun alarmIntent(ctx: Context, id: Int, title: String, text: String): PendingIntent {
        val i = Intent(ctx, NotifyReceiver::class.java)
            .putExtra("id", id)
            .putExtra("title", title)
            .putExtra("text", text)
        return PendingIntent.getBroadcast(
            ctx, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun schedule(ctx: Context, json: String) {
        val prefs = ctx.getSharedPreferences("kiri", Context.MODE_PRIVATE)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val old = prefs.getString("notif_ids", "") ?: ""
        for (s in old.split(",")) {
            val id = s.toIntOrNull() ?: continue
            am.cancel(alarmIntent(ctx, id, "", ""))
        }
        val arr = try {
            JSONArray(json)
        } catch (e: Exception) {
            JSONArray()
        }
        val ids = ArrayList<String>()
        val now = System.currentTimeMillis()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val at = o.optLong("at", 0L)
            if (at <= now) continue
            val id = o.optInt("id", i)
            val pi = alarmIntent(ctx, id, o.optString("title", "Kiri"), o.optString("text", ""))
            try {
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
                ids.add(id.toString())
            } catch (e: Exception) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                ids.add(id.toString())
            }
        }
        prefs.edit()
            .putString("notif_ids", ids.joinToString(","))
            .putString("notif_json", json)
            .apply()
    }

    fun granted(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun show(ctx: Context, id: Int, title: String, text: String) {
        if (!granted(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setLargeIcon(java.io.File(ctx.filesDir, "mascot_notif.png").let { f -> (if (f.exists()) BitmapFactory.decodeFile(f.path) else null) ?: BitmapFactory.decodeResource(ctx.resources, R.drawable.kiri20_face) })
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        ctx.getSystemService(NotificationManager::class.java)?.notify(id, n)
    }
}

class NotifyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notifier.show(
            ctx,
            intent.getIntExtra("id", 0),
            intent.getStringExtra("title") ?: "Kiri",
            intent.getStringExtra("text") ?: ""
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val json = ctx.getSharedPreferences("kiri", Context.MODE_PRIVATE).getString("notif_json", "[]") ?: "[]"
            Notifier.schedule(ctx, json)
        }
    }
}
