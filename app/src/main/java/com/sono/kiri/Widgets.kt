package com.sono.kiri

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
    private val WD_PT = arrayOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
    private val WD_EN = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val WD_ZH = arrayOf("日", "一", "二", "三", "四", "五", "六")
    private var lang = "pt"
    private fun tl(pt: String, en: String, zh: String): String = when (lang) { "en" -> en; "zh" -> zh; else -> pt }
    private fun days(n: Int): String = tl(if (n == 1) "$n dia" else "$n dias", if (n == 1) "$n day" else "$n days", "$n 天")
    private val COL = intArrayOf(R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4, R.id.c5, R.id.c6)
    private val NAME = intArrayOf(R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.n5, R.id.n6)
    private val NUM = intArrayOf(R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6)
    private val MARK = intArrayOf(R.id.m0, R.id.m1, R.id.m2, R.id.m3, R.id.m4, R.id.m5, R.id.m6)
    private val DOTS = intArrayOf(R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5, R.id.t6)
    private val GCOL = intArrayOf(R.id.g0, R.id.g1, R.id.g2, R.id.g3, R.id.g4, R.id.g5, R.id.g6, R.id.g7, R.id.g8, R.id.g9, R.id.g10, R.id.g11, R.id.g12, R.id.g13)
    private val GNAME = intArrayOf(R.id.gn0, R.id.gn1, R.id.gn2, R.id.gn3, R.id.gn4, R.id.gn5, R.id.gn6, R.id.gn7, R.id.gn8, R.id.gn9, R.id.gn10, R.id.gn11, R.id.gn12, R.id.gn13)
    private val GNUM = intArrayOf(R.id.gd0, R.id.gd1, R.id.gd2, R.id.gd3, R.id.gd4, R.id.gd5, R.id.gd6, R.id.gd7, R.id.gd8, R.id.gd9, R.id.gd10, R.id.gd11, R.id.gd12, R.id.gd13)
    private val GMARK = intArrayOf(R.id.gm0, R.id.gm1, R.id.gm2, R.id.gm3, R.id.gm4, R.id.gm5, R.id.gm6, R.id.gm7, R.id.gm8, R.id.gm9, R.id.gm10, R.id.gm11, R.id.gm12, R.id.gm13)
    private val GDOTS = intArrayOf(R.id.gt0, R.id.gt1, R.id.gt2, R.id.gt3, R.id.gt4, R.id.gt5, R.id.gt6, R.id.gt7, R.id.gt8, R.id.gt9, R.id.gt10, R.id.gt11, R.id.gt12, R.id.gt13)

    private fun data(ctx: Context): JSONObject? {
        val s = ctx.getSharedPreferences("kiri", Context.MODE_PRIVATE).getString("data", null) ?: return null
        return try {
            JSONObject(s).also { lang = it.optString("lang", "pt") }
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

    private fun pickFile(ctx: Context, vararg names: String): File? {
        for (n in names) {
            val f = File(ctx.filesDir, n)
            if (f.exists()) return f
        }
        return null
    }

    private fun mascot(ctx: Context, v: RemoteViews, id: Int, kind: String = "body") {
        val sl = sleepy()
        val f = when (kind) {
            "face" -> pickFile(ctx, "mascot_closeup.png", "mascot_face.png", "mascot.png")
            "pet" -> pickFile(ctx, "mascot_petimg.png") ?: (if (sl) pickFile(ctx, "mascot_sleep.png", "mascot.png") else pickFile(ctx, "mascot_sit.png", "mascot.png"))
            else -> if (sl) pickFile(ctx, "mascot_sleep.png", "mascot.png") else pickFile(ctx, "mascot.png")
        }
        if (f != null) {
            val b = BitmapFactory.decodeFile(f.path)
            if (b != null) {
                v.setImageViewBitmap(id, b)
                return
            }
        }
        val skin = data(ctx)?.optString("skin", "kiri20") ?: "kiri20"
        val res = when {
            skin == "kiri20" && kind == "face" -> when (data(ctx)?.optInt("face", 0) ?: 0) {
                1 -> R.drawable.kiri20_face2
                else -> R.drawable.kiri20_face
            }
            skin == "kiri20" && kind == "pet" && !sl -> R.drawable.kiri20_sit
            skin == "kiri20" -> if (sl) R.drawable.kiri20_sleep else R.drawable.kiri20
            skin == "kiri2" -> if (sl) R.drawable.kiri2_sleep else R.drawable.kiri2
            skin == "nino" -> if (sl) R.drawable.nino_sleep else R.drawable.nino
            else -> if (sl) R.drawable.kiri_sleep else R.drawable.kiri
        }
        v.setImageViewResource(id, res)
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
            sb.append(tl("Hoje livre", "Free today", "今天沒有安排"))
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
                sb.append(" ").append(ev.length().toString()).append(tl(" hoje", " today", " 件"))
            }
        }
        return sb
    }

    fun buildPet(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_kiri)
        mascot(ctx, v, R.id.img, "pet")
        openApp(ctx, v, R.id.root)
        return v
    }

    fun buildTalk(ctx: Context): RemoteViews {
        val d = data(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_talk)
        v.setTextViewText(R.id.line, if (sleepy()) tl("Zzz… boa noite.", "Zzz… good night.", "Zzz… 晚安。") else dailyLine(d, 1))
        openApp(ctx, v, R.id.root)
        return v
    }

    fun buildBubble(ctx: Context): RemoteViews {
        val d = data(ctx)
        val right = d?.optString("side", "esq") == "dir"
        val v = RemoteViews(ctx.packageName, if (right) R.layout.widget_bubble_r else R.layout.widget_bubble)
        background(v, R.id.root, clear(d, "med"))
        mascot(ctx, v, R.id.img, "face")
        v.setTextViewText(R.id.line, if (sleepy()) tl("Zzz… amanhã a gente continua.", "Zzz… let's continue tomorrow.", "Zzz… 明天再繼續吧。") else dailyLine(d, 0))
        v.setTextViewText(R.id.today, todaySummary(d, false))
        openApp(ctx, v, R.id.root)
        return v
    }

    private fun dayIndex(k: String): Long {
        val p = k.split("-")
        if (p.size != 3) return 0L
        val c = Calendar.getInstance()
        c.clear()
        c.set(p[0].toIntOrNull() ?: 2000, (p[1].toIntOrNull() ?: 1) - 1, p[2].toIntOrNull() ?: 1, 12, 0, 0)
        return Math.round(c.timeInMillis / 86400000.0)
    }

    fun buildCounter(ctx: Context): RemoteViews {
        val d = data(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_counter)
        val cw = d?.optJSONObject("cw")
        if (cw == null) {
            v.setTextViewText(R.id.name, tl("Contador de dias", "Day counter", "倒數日"))
            v.setTextViewText(R.id.big, "D-?")
            v.setTextViewText(R.id.sub, tl("Crie um na aba D-day", "Create one in the D-day tab", "請在倒數日分頁新增"))
        } else {
            val today = dayIndex(key(Calendar.getInstance()))
            val target = dayIndex(cw.optString("d"))
            val big: String
            val sub: String
            if (cw.optString("m") == "desde") {
                val n = (today - target + (if (cw.optBoolean("f", false)) 1 else 0)).toInt()
                if (n < 0) {
                    big = "D" + n
                    sub = tl("começa em " + days(-n), "starts in " + days(-n), days(-n) + "後開始")
                } else {
                    big = "D+" + n
                    sub = days(n)
                }
            } else {
                val n = (target - today).toInt()
                when {
                    n > 0 -> {
                        big = "D-" + n
                        sub = tl("faltam " + days(n), days(n) + " left", "還有 " + days(n))
                    }
                    n == 0 -> {
                        big = tl("Dia D!", "D-Day!", "就是今天！")
                        sub = tl("é hoje!", "it's today!", "就是今天！")
                    }
                    else -> {
                        big = "D+" + (-n)
                        sub = tl("passou há " + days(-n), days(-n) + " ago", "已過 " + days(-n))
                    }
                }
            }
            v.setTextViewText(R.id.name, cw.optString("n", tl("Contador", "Counter", "倒數日")))
            v.setTextViewText(R.id.big, big)
            v.setTextViewText(R.id.sub, sub)
            val fg = color(cw.optString("fg", ""), Color.WHITE)
            val num = color(cw.optString("num", ""), fg)
            v.setTextColor(R.id.name, fg)
            v.setTextColor(R.id.sub, fg)
            v.setTextColor(R.id.big, num)
        }
        val f = File(ctx.filesDir, "counter.jpg")
        val img = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
        if (img != null) {
            v.setImageViewBitmap(R.id.bg, img)
            val shade = cw?.optBoolean("shade", true) ?: true
            v.setViewVisibility(R.id.shade, if (shade) android.view.View.VISIBLE else android.view.View.GONE)
        } else {
            val base = color(cw?.optString("c"), Color.parseColor("#7C8CF0"))
            val chosen = color(cw?.optString("bg", ""), 0)
            val dark = if (chosen != 0) chosen else Color.rgb(
                (Color.red(base) * 0.55 + 16 * 0.45).toInt(),
                (Color.green(base) * 0.55 + 20 * 0.45).toInt(),
                (Color.blue(base) * 0.55 + 26 * 0.45).toInt()
            )
            val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(dark)
            v.setImageViewBitmap(R.id.bg, bmp)
            v.setViewVisibility(R.id.shade, if (chosen != 0) android.view.View.GONE else android.view.View.VISIBLE)
        }
        openApp(ctx, v, R.id.root)
        return v
    }

    private fun fillDays(v: RemoteViews, d: JSONObject?, count: Int, col: IntArray, name: IntArray, num: IntArray, mark: IntArray, dots: IntArray) {
        val holColor = color(d?.optString("hol"), Color.parseColor("#FF6B6B"))
        val today = key(Calendar.getInstance())
        val days = d?.optJSONObject("days")
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_MONTH, -((c.get(Calendar.DAY_OF_WEEK) + 5) % 7))
        for (i in 0 until count) {
            val k = key(c)
            val day = days?.optJSONObject(k)
            val dow = c.get(Calendar.DAY_OF_WEEK)
            val red = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY || holidayName(day) != null
            v.setTextViewText(name[i], (if (lang == "en") WD_EN else if (lang == "zh") WD_ZH else WD_PT)[dow - 1])
            v.setTextColor(name[i], if (red) holColor else 0xB3FFFFFF.toInt())
            v.setTextViewText(num[i], c.get(Calendar.DAY_OF_MONTH).toString())
            v.setTextColor(num[i], if (red) holColor else Color.WHITE)
            val m = day?.optJSONObject("m")
            if (m != null) {
                v.setTextViewText(mark[i], glyph(m.optString("s")))
                v.setTextColor(mark[i], color(m.optString("c"), Color.WHITE))
            } else v.setTextViewText(mark[i], " ")
            val sb = SpannableStringBuilder()
            val arr = day?.optJSONArray("d")
            if (arr != null) for (j in 0 until minOf(4, arr.length())) addColored(sb, "●", color(arr.optString(j), Color.WHITE))
            if (sb.isEmpty()) sb.append(" ")
            v.setTextViewText(dots[i], sb)
            val paint = color(day?.optString("p", ""), 0)
            val bg = when {
                paint != 0 -> (paint and 0x00FFFFFF) or (if (k == today) 0xAA000000.toInt() else 0x66000000)
                k == today -> 0x33FFFFFF
                else -> Color.TRANSPARENT
            }
            v.setInt(col[i], "setBackgroundColor", bg)
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun shortDate(): String = when (lang) {
        "en" -> SimpleDateFormat("MMM d", Locale.US)
        "zh" -> SimpleDateFormat("M月d日", Locale.TRADITIONAL_CHINESE)
        else -> SimpleDateFormat("d 'de' MMM", Locale("pt", "BR"))
    }.format(Date())

    fun buildBig(ctx: Context): RemoteViews {
        val d = data(ctx)
        val right = d?.optString("side", "esq") == "dir"
        val v = RemoteViews(ctx.packageName, if (right) R.layout.widget_big_r else R.layout.widget_big)
        background(v, R.id.root, clear(d, "big"))
        mascot(ctx, v, R.id.img, "face")
        v.setTextViewText(R.id.line, if (sleepy()) tl("Zzz… amanhã a gente continua.", "Zzz… let's continue tomorrow.", "Zzz… 明天再繼續吧。") else dailyLine(d, 0))
        v.setTextViewText(R.id.today, todaySummary(d, false))
        v.setTextViewText(R.id.wtitle, tl("Esta semana e a próxima", "This week and next", "本週與下週"))
        v.setTextViewText(R.id.date, shortDate())
        fillDays(v, d, 14, GCOL, GNAME, GNUM, GMARK, GDOTS)
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
            v.setTextViewText(NAME[i], (if (lang == "en") WD_EN else if (lang == "zh") WD_ZH else WD_PT)[dow - 1])
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
        v.setTextViewText(R.id.date, when (lang) {
            "en" -> SimpleDateFormat("MMM d", Locale.US)
            "zh" -> SimpleDateFormat("M月d日", Locale.TRADITIONAL_CHINESE)
            else -> SimpleDateFormat("d 'de' MMM", Locale("pt", "BR"))
        }.format(Date()))
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

class CounterWidget : KiriBaseWidget() {
    override fun build(ctx: Context): RemoteViews = Kiri.buildCounter(ctx)
}

class BigWidget : KiriBaseWidget() {
    override fun build(ctx: Context): RemoteViews = Kiri.buildBig(ctx)
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
            Pair(WeekWidget::class.java, { c: Context -> Kiri.buildWeek(c) }),
            Pair(CounterWidget::class.java, { c: Context -> Kiri.buildCounter(c) }),
            Pair(BigWidget::class.java, { c: Context -> Kiri.buildBig(c) })
        )
        for ((cls, build) in all) {
            val ids = m.getAppWidgetIds(ComponentName(ctx, cls))
            for (id in ids) m.updateAppWidget(id, build(ctx))
        }
    }
}
