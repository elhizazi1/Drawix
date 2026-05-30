package xyz.siwane.drawix.pro

import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.util.Locale

class DrawTileService : TileService() {

    // دالة لفرض وقراءة اللغة المحفوظة داخل التطبيق
    private fun getLocalizedContext(): Context {
        val prefs = getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("app_language", "ar") ?: "ar"
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        return createConfigurationContext(config)
    }

    // عندما يفتح المستخدم شريط الإشعارات العلوي
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    // عندما ينقر المستخدم على الأيقونة من البار العلوي
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, FloatingDrawService::class.java)
        
        if (FloatingDrawService.isRunning) {
            // إذا كانت الخدمة تعمل، قم بإيقافها
            stopService(intent)
        } else {
            // إذا كانت متوقفة، قم بتشغيلها
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        updateTileState()
    }

    // تحديث شكل ولون الأيقونة (مفعلة / معطلة) باللغة الصحيحة
    private fun updateTileState() {
        val tile = qsTile ?: return
        val locContext = getLocalizedContext() // استدعاء سياق اللغة

        if (FloatingDrawService.isRunning) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = locContext.getString(R.string.tile_active_label)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = locContext.getString(R.string.tile_inactive_label)
        }
        tile.updateTile()
    }
}
