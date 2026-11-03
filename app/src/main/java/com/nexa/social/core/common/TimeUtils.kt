package com.nexa.social.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Locale-neutral compact time formatting used across feeds and messages. */
object TimeUtils {

    fun relativeFromNow(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - timestamp).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1L -> "now"
            minutes < 60L -> "${minutes}m"
            hours < 24L -> "${hours}h"
            days < 7L -> "${days}d"
            days < 30L -> "${days / 7}w"
            else -> "${days / 30}mo"
        }
    }

    fun clockTime(timestamp: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(timestamp)
        val hours = ((totalMinutes / 60) % 24).toInt()
        val minutes = (totalMinutes % 60).toInt()
        return "%02d:%02d".format(hours, minutes)
    }

    fun minutesAgo(millis: Long): Long = System.currentTimeMillis() - millis

    /** Locale-aware date and time for gatherings and capsules. */
    fun dateTime(timestamp: Long): String =
        SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(Date(timestamp))

    fun date(timestamp: Long): String =
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
}
