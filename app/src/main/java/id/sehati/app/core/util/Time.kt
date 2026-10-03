package id.sehati.app.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

fun interface Clock { fun now(): Long }

@Singleton
class SystemClock @Inject constructor() : Clock { override fun now() = System.currentTimeMillis() }

object Ids {
    fun uuid(): String = UUID.randomUUID().toString()
}

object TimeUtils {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val localeId = Locale("id", "ID")

    fun toLocalDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun dateIso(millis: Long): String = toLocalDate(millis).toString()
    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun startOfDay(millis: Long): Long = startOfDay(toLocalDate(millis))
    fun endOfDay(millis: Long): Long = startOfDay(toLocalDate(millis).plusDays(1))
    fun fromIso(iso: String): LocalDate = LocalDate.parse(iso)

    fun time(millis: Long): String = DateTimeFormatter.ofPattern("HH.mm", localeId).format(Instant.ofEpochMilli(millis).atZone(zone))
    fun date(millis: Long): String = DateTimeFormatter.ofPattern("d MMM yyyy", localeId).format(Instant.ofEpochMilli(millis).atZone(zone))
    fun dateTime(millis: Long): String = "${date(millis)} · ${time(millis)} WIB"
    fun shortDate(millis: Long): String = DateTimeFormatter.ofPattern("d MMM", localeId).format(Instant.ofEpochMilli(millis).atZone(zone))

    fun greeting(hour: Int): String = when (hour) {
        in 4..10 -> "Selamat pagi"
        in 11..14 -> "Selamat siang"
        in 15..17 -> "Selamat sore"
        else -> "Selamat malam"
    }
    fun hourOf(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(zone).hour

    fun durationLabel(totalMinutes: Int): String = when {
        totalMinutes <= 0 -> "0m"
        totalMinutes < 60 -> "${totalMinutes}m"
        else -> "${totalMinutes / 60}j ${totalMinutes % 60}m"
    }
}

object NumberFmt {
    fun thousands(n: Int): String = String.format(Locale("id", "ID"), "%,d", n)
}
