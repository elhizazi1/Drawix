package xyz.siwane.drawix.pro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

object SmartNotificationHelper {

    private const val CHANNEL_ID = "DrawixSmartNotifications"
    private const val NOTIFY_ID_SCREENSHOT = 101
    private const val NOTIFY_ID_RETENTION = 102
    private const val NOTIFY_ID_PERMISSION = 103

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notif_channel_name)
            val descriptionText = context.getString(R.string.notif_channel_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 1. الإشعار الفوري بعد التقاط الشاشة
    fun showScreenshotSuccessNotification(context: Context) {
        createNotificationChannel(context)
        
        // التعديل هنا: استخدام الانتقال السلس للحفاظ على حالة التطبيق
        val galleryIntent = Intent(context, GalleryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, galleryIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gallery) 
            .setContentTitle(context.getString(R.string.notif_screenshot_title))
            .setContentText(context.getString(R.string.notif_screenshot_desc))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFY_ID_SCREENSHOT, builder.build())
    }

    // ==========================================
    // دوال الجدولة (WorkManager)
    // ==========================================

    fun scheduleRetentionReminder(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<RetentionWorker>()
            .setInitialDelay(3, TimeUnit.DAYS) // بعد 3 أيام
            .addTag("RETENTION_WORK")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "RetentionReminder", ExistingWorkPolicy.REPLACE, workRequest
        )
    }

    fun cancelRetentionReminder(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("RETENTION_WORK")
    }

    fun schedulePermissionReminder(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<PermissionWorker>()
            .setInitialDelay(4, TimeUnit.HOURS) // بعد 4 ساعات
            .addTag("PERMISSION_WORK")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "PermissionReminder", ExistingWorkPolicy.KEEP, workRequest
        )
    }
}

// ==========================================
// فئات العمال (Workers) التي تعمل في الخلفية
// ==========================================

class RetentionWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        SmartNotificationHelper.createNotificationChannel(applicationContext)
        
        // التعديل هنا أيضاً لانتقال سلس عند الضغط على إشعار العودة
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, "DrawixSmartNotifications")
            .setSmallIcon(R.drawable.ic_brush_2)
            .setContentTitle(applicationContext.getString(R.string.notif_retention_title))
            .setContentText(applicationContext.getString(R.string.notif_retention_desc))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(102, builder.build())
        return Result.success()
    }
}

class PermissionWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled(applicationContext)
        if (isAccessibilityEnabled) return Result.success() 

        SmartNotificationHelper.createNotificationChannel(applicationContext)
        
        // التعديل هنا أيضاً لانتقال سلس عند الضغط على إشعار الصلاحيات
        val intent = Intent(applicationContext, PermissionsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, "DrawixSmartNotifications")
            .setSmallIcon(R.drawable.ic_shield_security)
            .setContentTitle(applicationContext.getString(R.string.notif_permission_title))
            .setContentText(applicationContext.getString(R.string.notif_permission_desc))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(103, builder.build())
        return Result.success()
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }
        if (accessibilityEnabled == 1) {
            val services = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return services?.contains(context.packageName) == true
        }
        return false
    }
}
