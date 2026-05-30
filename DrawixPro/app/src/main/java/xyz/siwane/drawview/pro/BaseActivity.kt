package xyz.siwane.drawix.pro

import android.app.ActivityOptions
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.NestedScrollView
import java.util.Locale

// =====================================================================
// غلاف السياق الذكي (ContextWrapper) الذي يعترض النصوص ويجلبها من JSON
// =====================================================================
class DrawixContextWrapper(base: Context) : ContextWrapper(base) {
    private var customRes: Resources? = null

    override fun getResources(): Resources {
        if (customRes == null) {
            val origRes = super.getResources()
            @Suppress("DEPRECATION")
            customRes = object : Resources(origRes.assets, origRes.displayMetrics, origRes.configuration) {
                @Throws(NotFoundException::class)
                override fun getString(id: Int): String {
                    val defaultStr = super.getString(id)
                    return TranslationEngine.getCustomString(this@DrawixContextWrapper, id, defaultStr)
                }

                @Throws(NotFoundException::class)
                override fun getText(id: Int): CharSequence {
                    val defaultStr = super.getString(id)
                    return TranslationEngine.getCustomString(this@DrawixContextWrapper, id, defaultStr)
                }
            }
        }
        return customRes!!
    }
}

// =====================================================================
// Base Activity
// =====================================================================
open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("app_language", "ar") ?: "ar"

        // 1. تهيئة محرك الترجمة ليعرف ما إذا كان هناك ملف مخصص
        TranslationEngine.init(newBase)

        // 2. تحديث لغة النظام
        var context = updateLocale(newBase, langCode)

        // 3. السحر! إذا كانت اللغة المخصصة مفعلة، نغلف الـ Context لاعتراض النصوص
        if (TranslationEngine.isCustomLanguageActive) {
            context = DrawixContextWrapper(context)
        }

        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode_enabled", true)
        
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = resources.configuration.layoutDirection
    }

    // --- هندسة الانتقال السلس الجديدة ---
    fun startSmoothActivity(intent: Intent) {
        val options = ActivityOptions.makeCustomAnimation(
            this, android.R.anim.fade_in, android.R.anim.fade_out
        ).toBundle()
        startActivity(intent, options)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyFadeTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN)
    }

    override fun finish() {
        super.finish()
        applyFadeTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE)
    }

    private fun applyFadeTransition(transitionType: Int) {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                transitionType, 
                android.R.anim.fade_in, 
                android.R.anim.fade_out
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun updateLocale(context: Context, language: String): Context {
        // فحص ذكي للاتجاه: إذا كانت اللغة مخصصة بـ rtl نجعل قاعدتها العربية (اليمين لليسار)، وإلا الإنجليزية
        val actualLang = when {
            language.startsWith("custom_rtl_") -> "ar" 
            language.startsWith("custom_ltr_") -> "en" 
            language.startsWith("custom_") -> "en" // افتراضي إذا لم يتم التحديد
            else -> language
        }
        
        val locale = Locale(actualLang)
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale) 
        
        return context.createConfigurationContext(config)
    }

    // --- هندسة الحركة الذكية للشريط السفلي ---
    fun setupAutoHidingBottomNav(scrollView: NestedScrollView, bottomNav: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val isScrollingDown = scrollY > oldScrollY + 15
                val isScrollingUp = scrollY < oldScrollY - 15

                if (isScrollingDown && bottomNav.translationY == 0f) {
                    bottomNav.animate()
                        .translationY(250f)
                        .setDuration(250)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                } else if (isScrollingUp && bottomNav.translationY > 0f) {
                    bottomNav.animate()
                        .translationY(0f)
                        .setDuration(250)
                        .setInterpolator(OvershootInterpolator(1.2f)) 
                        .start()
                }
            }
        }
    }
}
