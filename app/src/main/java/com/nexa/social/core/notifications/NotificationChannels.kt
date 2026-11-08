package com.nexa.social.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.nexa.social.R

object NotificationChannels {

    const val GENERAL_CHANNEL_ID = "nexa_general"

    fun showLoginSuccess(context: Context) {
        create(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = Notification.Builder(context, GENERAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("تم تسجيل الدخول إلى Nexa")
            .setContentText("تم تسجيل الدخول إلى حسابك بنجاح.")
            .setCategory(Notification.CATEGORY_EVENT)
            .setAutoCancel(true)
            .build()
        manager.notify(4101, notification)
    }

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            GENERAL_CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }
}
