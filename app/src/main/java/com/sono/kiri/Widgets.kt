package com.sono.kiri

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Kiri {
    private val FALLBACK = arrayOf(
        "Toda manhã é um rascunho novo.",
        "Respira. Isso, devagar. Pronto.",
        "Eu fico aqui. Não é muito, mas é constante."
    )
    private val WD = arrayOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
    private val COL = intArrayOf(R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4, R.id.c5, R.id.c6)
    private val NAME = intArrayOf(R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.n5, R.id.n6)
    private val NUM = intArrayOf(R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6)
    private val MARK = intArrayOf(R.id.m0, R.id.m1, R.id.m2, R.id.m3, R.id.m4, R.id.m5, R.id.m6)
    private val DOTS = intArrayOf(R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5, R.id.t6)

    private fun data(ctx: Context): JSONObject? {
        val s = ctx.getSharedPreferences("kiri", Context.MODE_PRIVATE).getString("data", null) ?: return null
        return try {
            JSONObject(s)
        } catch (e: Exception) {
            null
        }
    }

    private fun key(c: Calendar): String = String.format(
        Locale.US, "%04d-%02d-%02d",
        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
    )

    private fun sleepy(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= 23 || h < 6
    }

    private fun dailyLine(d: JSONObject?, off: Int): String {
        val k = key(Calendar.getInstance())
        var h = 0
        for (ch in k) h = (h * 31 + ch.code) % 9973
        val arr = d?.optJSONArray("lines")
        if (arr == null || arr.length() == 0) return FALLBACK[(h + off * 37) % FALLBACK.size]
        return arr.optString((h + off * 37) % arr.length())
    }

    private fun color(s: String?, def: Int): Int {
        if (s.isNullOrBlank() || s == "null") return def
        return try {
            Color.parseColor(s.trim())
        } catch (e: Exception) {
            def
        }
    }

    private fun glyph(shape: String?): String = when (shape) {
        "triangle" -> "▲"
        "heart" -> "♥"
        "star" -> "★"
        "square" -> "■"
        "bar" -> "▬"
        else -> "●"
    }

    private fun clear(d: JSONObject?, k: String): Boolean =
        d?.optJSONObject("clear")?.optBoolean(k, false) ?: false

    private fun background(v: RemoteViews, id: Int, transparent: Boolean) {
        if (transparent) v.setInt(id, "setBackgroundColor", Color.TRANSPARENT)
        else v.setInt(id, "setBackgroundResource", R.drawable.widget_bg)
    }

    private fun openApp(ctx: Context, v: RemoteViews, id: Int) {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        v.setOnClickPendingIntent(id, pi)
    }

    private fun mascot(ctx: Context, v: RemoteViews, id: Int) {
        val f = File(ctx.filesDir, "mascot.png")
        if (f.exists()) {
            val b = BitmapFactory.decodeFile(f.path)
            if (b != null) {
                v.setImageViewBitmap(id, b)
                return
            }
        }
        v.setImageViewResource(id, if (sleepy()) R.drawable.kiri_sleep else R.drawable.kiri)
    }

    private fun addColored(sb: SpannableStringBuilder, text: String, c: Int) {
        val start = sb.length
        sb.append(text)
        sb.setSpan(ForegroundColorSpan(c), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun holidayName(day: JSONObject?): String? {
        val h = day?.optString("h", "") ?: return null
        return if (h.isEmpty() || h == "null") null else h
    }

    private fun todaySummary(d: JSONObject?, withEvents: Boolean): CharSequence {
        val k = key(Calendar.getInstance())
        val day = d?.optJSONObject("days")?.optJSONObject(k)
        val sb = SpannableStringBuilder()
        val m = day?.optJSONObject("m")
        val hol = holidayName(day)
        if (m != null) {
            addColored(sb, glyph(m.optString("s")) + " ", color(m.optString("c"), Color.WHITE))
            sb.append(m.optString("n"))
        } else if (hol != null) {
            addColored(sb, hol, color(d?.optString("hol"), Color.RED))
        } else {
            sb.append("Hoje livre")
        }
        val ev = day?.optJSONArray("e")
        if (ev != null && ev.length() > 0) {
            if (withEvents) {
                for (i in 0 until minOf(2, ev.length())) {
                    val e = ev.optJSONArray(i) ?: continue
                    sb.append("   ")
                    addColored(sb, "● ", color(e.optString(2), Color.WHITE))
                    val t = e.optString(0)
                    if (t.isNotEmpty()) sb.append(t).append(" ")
                    sb.append(e.optString(1))
                }
            } else {
                sb.append(" · ")
                for (i in 0 until minOf(4, ev.length())) {
                    val e = ev.optJSONArray(i) ?: continue
                    addColored(sb, "●", color(e.optString(2), Color.WHITE))
                }
                sb.append(" ").append(ev.length().toString()).append(" hoje")
            }
        }
        return sb
    }

    fun buildPet(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_kiri)
        mascot(ctx, v, R.id.img)
        openApp(ctx, v, R.id.root)
        return v
    }

    fun buildTalk(ctx: Context): RemoteViews {
        val d = data(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_talk)
        v.setTextViewText(R.id.line, if (sleepy()) "Zzz… boa noite." else dailyLine(d, 1))
        openApp(ctx, v, R.id.root)
        return v
    }

    fun buildBubble(ctx: Context): RemoteViews {
        val d = data(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_bubble)
        background(v, R.id.root, clear(d, "med"))
        mascot(ctx, v, R.id.img)
        v.setTextViewText(R.id.line, if (sleepy()) "Zzz… amanhã a gente continua." else dailyLine(d, 0))
        v.setTextViewText(R.id.today, todaySummary(d, false))
        openApp(ctx, v, R.id.root)
        return v
    }

    fun buildWeek(ctx: Context): RemoteViews {
        val d = data(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_week)
        background(v, R.id.root, clear(d, "week"))
        val holColor = color(d?.optString("hol"), Color.parseColor("#FF6B6B"))
        val today = key(Calendar.getInstance())
        val days = d?.optJSONObject("days")
        val c = Calendar.getInstance()
        val diff = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        c.add(Calendar.DAY_OF_MONTH, -diff)
        for (i in 0 until 7) {
            val k = key(c)
            val day = days?.optJSONObject(k)
            val dow = c.get(Calendar.DAY_OF_WEEK)
            val red = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY || holidayName(day) != null
            v.setTextViewText(NAME[i], WD[dow - 1])
            v.setTextColor(NAME[i], if (red) holColor else 0xB3FFFFFF.toInt())
            v.setTextViewText(NUM[i], c.get(Calendar.DAY_OF_MONTH).toString())
            v.setTextColor(NUM[i], if (red) holColor else Color.WHITE)
            val m = day?.optJSONObject("m")
            if (m != null) {
                v.setTextViewText(MARK[i], glyph(m.optString("s")))
                v.setTextColor(MARK[i], color(m.optString("c"), Color.WHITE))
            } else {
                v.setTextViewText(MARK[i], " ")
            }
            val dots = SpannableStringBuilder()
            val arr = day?.optJSONArray("d")
            if (arr != null) {
                for (j in 0 until minOf(4, arr.length())) addColored(dots, "●", color(arr.optString(j), Color.WHITE))
            }
            if (dots.isEmpty()) dots.append(" ")
            v.setTextViewText(DOTS[i], dots)
            val paint = color(day?.optString("p", ""), 0)
            val bg = when {
                paint != 0 -> (paint and 0x00FFFFFF) or (if (k == today) 0xAA000000.toInt() else 0x66000000)
                k == today -> 0x33FFFFFF
                else -> Color.TRANSPARENT
            }
            v.setInt(COL[i], "setBackgroundColor", bg)
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        v.setTextViewText(R.id.date, SimpleDateFormat("d 'de' MMM", Locale("pt", "BR")).format(Date()))
        v.setTextViewText(R.id.today, todaySummary(d, true))
        openApp(ctx, v, R.id.root)
        return v
    }
}

abstract class KiriBaseWidget : AppWidgetProvider() {
    abstract fun build(ctx: Context): RemoteViews

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, build(ctx))
    }
}

class KiriWidget : KiriBaseWidget() {
    override fun build(ctx: Context): RemoteViews = Kiri.buildPet(ctx)
}

class TalkWidget : KiriBaseWidget() {
    override fun build(ctx: Context): RemoteViews = Kiri.buildTalk(ctx)
}

class BubbleWidget : KiriBaseWidget() {
    override fun build(ctx: Context): RemoteViews = Kiri.buildBubble(ctx)
}

class WeekWidget : KiriBaseWidget() {
    override fun build(ctx: Context): RemoteViews = Kiri.buildWeek(ctx)
}

object WidgetUpdater {
    fun updateAll(ctx: Context) {
        val m = AppWidgetManager.getInstance(ctx)
        val all: List<Pair<Class<*>, (Context) -> RemoteViews>> = listOf(
            Pair(KiriWidget::class.java, { c: Context -> Kiri.buildPet(c) }),
            Pair(TalkWidget::class.java, { c: Context -> Kiri.buildTalk(c) }),
            Pair(BubbleWidget::class.java, { c: Context -> Kiri.buildBubble(c) }),
            Pair(WeekWidget::class.java, { c: Context -> Kiri.buildWeek(c) })
        )
        for ((cls, build) in all) {
            val ids = m.getAppWidgetIds(ComponentName(ctx, cls))
            for (id in ids) m.updateAppWidget(id, build(ctx))
        }
    }
}
