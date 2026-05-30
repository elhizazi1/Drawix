package xyz.siwane.drawix.pro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

// واجهة مخفية تماماً لطلب إذن تصوير الشاشة العميق
class ScreenCaptureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1001) {
            // إرسال الإذن والموافقة إلى الخدمة العائمة لتبدأ التصوير
            val serviceIntent = Intent(this, FloatingDrawService::class.java).apply {
                action = "ACTION_START_CAPTURE"
                putExtra("RESULT_CODE", resultCode)
                putExtra("DATA", data)
            }
            startService(serviceIntent)
        }
        finish() // إغلاق الواجهة المخفية فوراً
    }
}
