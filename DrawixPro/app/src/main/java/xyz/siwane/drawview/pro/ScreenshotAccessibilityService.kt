package xyz.siwane.drawix.pro

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale

class ScreenshotAccessibilityService : AccessibilityService() {

    companion object {
        // نقطة اتصال ثابتة لكي تتمكن الخدمة العائمة من التحدث مع هذه الخدمة
        var instance: ScreenshotAccessibilityService? = null
    }

    // سياق مخصص يدعم اللغات للخدمة
    private lateinit var locContext: Context

    // دالة لتحديث لغة الخدمة بناءً على الإعدادات المحفوظة
    private fun getLocalizedContext(): Context {
        val prefs = getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("app_language", "ar") ?: "ar"
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        return createConfigurationContext(config)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        locContext = getLocalizedContext() // تهيئة سياق اللغة بمجرد اتصال الخدمة
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    // الدالة السحرية للتصوير الصامت (تعمل على أندرويد 11 فما فوق)
    fun takeSilentScreenshot(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                0, // الشاشة الافتراضية
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            
                            if (bitmap != null) {
                                // نسخ البكسلات لتحرير الذاكرة وتجنب أخطاء HardwareBuffer
                                val copiedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                hardwareBuffer.close()
                                
                                saveBitmapToGallery(copiedBitmap)
                                onSuccess()
                            } else {
                                onError(locContext.getString(R.string.error_extract_image))
                            }
                        } catch (e: Exception) {
                            onError(e.message ?: locContext.getString(R.string.error_unknown))
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        onError(locContext.getString(R.string.error_system_code, errorCode))
                    }
                }
            )
        } else {
            onError(locContext.getString(R.string.error_requires_android_11))
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        // قراءة تفضيلات المستخدم المحفوظة لتحديد الصيغة
        val prefs = getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
        val saveAsPng = prefs.getBoolean("save_as_png", true)

        // تحديد الامتداد، النوع، وصيغة الضغط بناءً على الإعدادات
        val extension = if (saveAsPng) "png" else "jpg"
        val mimeType = if (saveAsPng) "image/png" else "image/jpeg"
        val compressFormat = if (saveAsPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

        // تم تحديث اسم الصورة ليتوافق مع هوية التطبيق الجديدة والصيغة المختارة
        val fileName = "DrawixPro_${System.currentTimeMillis()}.$extension"
        var outputStream: OutputStream? = null
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DrawixPro")
            }
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                outputStream = resolver.openOutputStream(imageUri)
            }
        } else {
            @Suppress("DEPRECATION")
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "DrawixPro")
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, fileName)
            outputStream = FileOutputStream(file)
        }
        
        outputStream?.use { stream ->
            bitmap.compress(compressFormat, 100, stream)
            stream.flush()
        }
    }
}
