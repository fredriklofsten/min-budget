package se.minbudget.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import java.util.Calendar

/** Påminnelser på lönedagen: en notis kl. 08:00 som frågar hur mycket lönen blev. */
object PayReminders {
    private const val PREFS = "pay"
    private const val KEY = "list"
    const val CHANNEL = "lonedag"
    const val ACTION_ALARM = "se.minbudget.app.PAY_ALARM"
    const val EXTRA_PAY = "pay"
    private const val HOUR = 8

    data class Item(val id: String, val name: String, val day: Int)

    fun save(ctx: Context, json: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
        scheduleNext(ctx)
    }

    fun items(ctx: Context): List<Item> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Item(o.optString("id"), o.optString("name", "Lön"), o.optInt("day", 25).coerceIn(1, 31))
            }
        } catch (e: Exception) { emptyList() }
    }

    /** Datum (kl. 08) i given månad för en lönedag, med sista dagen i korta månader. */
    private fun at(year: Int, month: Int, day: Int): Calendar {
        val c = Calendar.getInstance()
        c.clear(); c.set(year, month, 1, HOUR, 0, 0)
        c.set(Calendar.DAY_OF_MONTH, minOf(day, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
        return c
    }

    private fun nextFor(day: Int, now: Calendar): Calendar {
        var c = at(now.get(Calendar.YEAR), now.get(Calendar.MONTH), day)
        if (!c.after(now)) {
            val n = now.clone() as Calendar
            n.set(Calendar.DAY_OF_MONTH, 1); n.add(Calendar.MONTH, 1)
            c = at(n.get(Calendar.YEAR), n.get(Calendar.MONTH), day)
        }
        return c
    }

    private fun alarmIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, PayReceiver::class.java).setAction(ACTION_ALARM)
        return PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun scheduleNext(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(ctx)
        val list = items(ctx)
        am.cancel(pi)
        if (list.isEmpty()) return
        val now = Calendar.getInstance()
        val next = list.map { nextFor(it.day, now) }.minByOrNull { it.timeInMillis } ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
    }

    /** Lönerna som ska komma idag. */
    fun dueToday(ctx: Context): List<Item> {
        val now = Calendar.getInstance()
        val today = now.get(Calendar.DAY_OF_MONTH)
        val last = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        return items(ctx).filter { minOf(it.day, last) == today }
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(CHANNEL, "Lönedag", NotificationManager.IMPORTANCE_DEFAULT)
                ch.description = "Frågar hur mycket lönen blev på lönedagen"
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun notify(ctx: Context, due: List<Item>) {
        if (due.isEmpty()) return
        ensureChannel(ctx)
        val open = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_PAY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(ctx, 2, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val names = due.joinToString(" och ") { it.name.lowercase() }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFF1F7A5C.toInt())
            .setContentTitle("Lönedag! 💰")
            .setContentText("Hur mycket blev din $names den här gången? Tryck för att fylla i.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Hur mycket blev din $names den här gången? Tryck för att fylla i, så räknar budgeten om allt till nästa lön."))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(25, n) } catch (e: SecurityException) { }
    }
}

class PayReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == PayReminders.ACTION_ALARM) PayReminders.notify(ctx, PayReminders.dueToday(ctx))
        PayReminders.scheduleNext(ctx)
    }
}
