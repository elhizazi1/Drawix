package xyz.siwane.drawix.pro

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

class DrawApplication : Application() {
    override fun attachBaseContext(base: Context) {
        val prefs = base.getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("app_language", "ar") ?: "ar"
        
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        
        super.attachBaseContext(base.createConfigurationContext(config))
    }
}
