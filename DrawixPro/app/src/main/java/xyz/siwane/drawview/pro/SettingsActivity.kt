package xyz.siwane.drawix.pro

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import rikka.shizuku.Shizuku 
import xyz.siwane.drawix.pro.databinding.ActivitySettingsBinding

class SettingsActivity : BaseActivity() { 

    private lateinit var binding: ActivitySettingsBinding
    private var isExportingEnglish = true

    // ==========================================
    // محركات رفع وتنزيل ملفات الترجمة (JSON)
    // ==========================================
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            try {
                val jsonStr = TranslationEngine.generateTemplateJson(this, isExportingEnglish)
                contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(jsonStr.toByteArray())
                }
                Toast.makeText(this, getString(R.string.toast_export_success), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.toast_export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { showImportDetailsDialog(it) }
    }

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "laser_comet_mode") {
            val isComet = prefs.getBoolean(key, true)
            if (binding.switchLaserMode.isChecked != isComet) {
                binding.switchLaserMode.isChecked = isComet
            }
        }
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1001 && grantResult == PackageManager.PERMISSION_GRANTED) {
            runOnUiThread {
                val prefs = getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
                prefs.edit().putInt("core_engine_type", EngineManager.ENGINE_SHIZUKU).apply()
                binding.radioShizuku.isChecked = true
                binding.radioStandard.isChecked = false
                binding.radioRoot.isChecked = false
                binding.radioLsposed.isChecked = false
                Toast.makeText(this, getString(R.string.toast_shizuku_success), Toast.LENGTH_SHORT).show()
                
                Thread { EngineManager.enableAccessibilityServiceSilently(this, EngineManager.ENGINE_SHIZUKU) }.start()
            }
        } else {
            runOnUiThread {
                Toast.makeText(this, getString(R.string.toast_shizuku_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupBottomNav()
        setupSettingsControls()
        loadSavedPreferences()
        setupEngineSelection() 
        setupCommunityTranslations() 
        
        setupAutoHidingBottomNav(binding.rootScrollView, binding.bottomNavBar)
    }

    override fun onResume() {
        super.onResume()
        updateBottomNavUI(binding.navBtnSettings.id)
        
        val prefs = getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        binding.switchLaserMode.isChecked = prefs.getBoolean("laser_comet_mode", true)
    }

    override fun onPause() {
        super.onPause()
        val prefs = getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    // ==========================================
    // تهيئة أزرار الترجمة (التصدير والاستيراد)
    // ==========================================
    private fun setupCommunityTranslations() {
        binding.btnExportTemplate.setOnClickListener {
            val options = arrayOf(getString(R.string.export_option_en), getString(R.string.export_option_ar))
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_export_title))
                .setItems(options) { _, which ->
                    isExportingEnglish = (which == 0)
                    val fileName = "drawix_template_${if(isExportingEnglish) "en" else "ar"}.json"
                    exportLauncher.launch(fileName)
                }.show()
        }

        binding.btnImportLanguage.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }
    }

    private fun showImportDetailsDialog(uri: Uri) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }

        val etName = EditText(this).apply { hint = getString(R.string.hint_lang_name) }
        val etCode = EditText(this).apply { hint = getString(R.string.hint_lang_code) }
        val cbRtl = CheckBox(this).apply {
            text = getString(R.string.checkbox_rtl)
            setTextColor(Color.parseColor("#E65100")) 
        }

        layout.addView(etName)
        layout.addView(etCode)
        layout.addView(cbRtl)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_import_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_confirm_lang)) { _, _ ->
                val name = etName.text.toString().trim()
                val code = etCode.text.toString().trim().lowercase()
                
                if (name.isEmpty() || code.isEmpty()) {
                    Toast.makeText(this, getString(R.string.toast_import_empty), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val finalDirectionCode = (if (cbRtl.isChecked) "rtl_" else "ltr_") + code
                val isSuccess = TranslationEngine.importTranslation(this, uri, name, finalDirectionCode)
                
                if (isSuccess) {
                    Toast.makeText(this, getString(R.string.toast_import_success), Toast.LENGTH_LONG).show()
                    val customLangId = "custom_$finalDirectionCode"
                    
                    getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
                        .edit().putString("app_language", customLangId).apply()
                    setAppLocale(customLangId)
                } else {
                    Toast.makeText(this, getString(R.string.toast_import_failed), Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    // ==========================================
    // تهيئة باقي الواجهة
    // ==========================================
    private fun setupSettingsControls() {
        val prefs = getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)

        binding.btnChangeLanguage.setOnClickListener {
            showLanguageDialog()
        }

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode_enabled", isChecked).apply()
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        binding.switchHideIcon.setOnCheckedChangeListener { _, isChecked ->
            toggleAppIconVisibility(isChecked)
            if (isChecked) {
                Toast.makeText(this, getString(R.string.toast_icon_hidden), Toast.LENGTH_LONG).show()
            }
        }

        binding.seekButtonSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvButtonSizeValue.text = "${progress + 50}%"
                val scale = 0.5f + (progress / 100f) 
                prefs.edit().putInt("button_scale_progress", progress).putFloat("button_scale", scale).apply()
                FloatingDrawService.instance?.applyButtonScale(scale)
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        binding.seekPanelSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvPanelSizeValue.text = "${progress + 50}%"
                val scale = 0.5f + (progress / 100f) 
                prefs.edit().putInt("ui_scale_progress", progress).putFloat("panel_scale", scale).apply()
                FloatingDrawService.instance?.applyPanelScale(scale)
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        binding.seekBtnOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val visiblePercentage = 100 - progress
                binding.tvOpacityValue.text = "$visiblePercentage%"
                val alphaValue = (visiblePercentage / 100f).coerceAtLeast(0.2f)
                prefs.edit().putInt("btn_opacity_progress", progress).putFloat("btn_alpha_value", alphaValue).apply()
                FloatingDrawService.instance?.applyButtonOpacity(alphaValue)
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        binding.switchLaserMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("laser_comet_mode", isChecked).apply()
            binding.seekLaserDuration.isEnabled = !isChecked
            binding.layoutLaserDuration.alpha = if (isChecked) 0.5f else 1.0f
            
            FloatingDrawService.instance?.syncLaserModeUI()
        }

        binding.seekLaserDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actualSeconds = progress + 1
                binding.tvLaserValue.text = "$actualSeconds ${getString(R.string.settings_seconds_unit)}"
                prefs.edit().putInt("laser_duration_progress", progress).putInt("laser_duration_seconds", actualSeconds).apply()
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        binding.switchImageFormat.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("save_as_png", isChecked).apply()
        }

        binding.switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("haptic_enabled", isChecked).apply()
        }
    }

    private fun loadSavedPreferences() {
        val prefs = getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
        val currentLangCode = prefs.getString("app_language", "ar") ?: "ar"

        val isDarkMode = prefs.getBoolean("dark_mode_enabled", true)
        binding.switchDarkMode.isChecked = isDarkMode
        
        if (isDarkMode) {
            binding.tvThemeTitle.text = getString(R.string.settings_dark_mode)
            binding.tvThemeDesc.text = getString(R.string.settings_dark_mode_desc)
            binding.ivThemeIcon.setImageResource(R.drawable.ic_moon)
            binding.ivThemeIcon.setColorFilter(ContextCompat.getColor(this, R.color.pui_text_dim))
        } else {
            binding.tvThemeTitle.text = getString(R.string.settings_light_mode)
            binding.tvThemeDesc.text = getString(R.string.settings_light_mode_desc)
            binding.ivThemeIcon.setImageResource(R.drawable.ic_sun_1)
            binding.ivThemeIcon.setColorFilter(ContextCompat.getColor(this, R.color.status_warning))
        }

        binding.tvCurrentLanguage.text = getLanguageName(currentLangCode)

        binding.switchHideIcon.isChecked = prefs.getBoolean("hide_app_icon", false)

        val btnSize = prefs.getInt("button_scale_progress", 50)
        binding.seekButtonSize.progress = btnSize
        binding.tvButtonSizeValue.text = "${btnSize + 50}%"

        val uiSize = prefs.getInt("ui_scale_progress", 50)
        binding.seekPanelSize.progress = uiSize
        binding.tvPanelSizeValue.text = "${uiSize + 50}%"

        val opacityProgress = prefs.getInt("btn_opacity_progress", 0)
        binding.seekBtnOpacity.progress = opacityProgress
        binding.tvOpacityValue.text = "${100 - opacityProgress}%"

        val isComet = prefs.getBoolean("laser_comet_mode", true)
        binding.switchLaserMode.isChecked = isComet
        binding.seekLaserDuration.isEnabled = !isComet
        binding.layoutLaserDuration.alpha = if (isComet) 0.5f else 1.0f

        val laserProgress = prefs.getInt("laser_duration_progress", 1)
        binding.seekLaserDuration.progress = laserProgress
        binding.tvLaserValue.text = "$laserProgress ${getString(R.string.settings_seconds_unit)}"

        binding.switchImageFormat.isChecked = prefs.getBoolean("save_as_png", true)
        binding.switchHaptic.isChecked = prefs.getBoolean("haptic_enabled", true)
    }

    private fun setupEngineSelection() {
        val prefs = getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
        val currentEngine = prefs.getInt("core_engine_type", EngineManager.ENGINE_STANDARD)

        fun updateEngineUI(engineType: Int) {
            binding.radioStandard.isChecked = (engineType == EngineManager.ENGINE_STANDARD)
            binding.radioShizuku.isChecked = (engineType == EngineManager.ENGINE_SHIZUKU)
            binding.radioRoot.isChecked = (engineType == EngineManager.ENGINE_ROOT)
            binding.radioLsposed.isChecked = (engineType == EngineManager.ENGINE_LSPOSED)
        }

        updateEngineUI(currentEngine)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        binding.btnEngineStandard.setOnClickListener {
            prefs.edit().putInt("core_engine_type", EngineManager.ENGINE_STANDARD).apply()
            updateEngineUI(EngineManager.ENGINE_STANDARD)
            Toast.makeText(this, getString(R.string.toast_engine_standard), Toast.LENGTH_SHORT).show()
        }

        binding.btnEngineShizuku.setOnClickListener {
            if (!EngineManager.isShizukuRunning()) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_shizuku_missing_title))
                    .setMessage(getString(R.string.dialog_shizuku_missing_desc))
                    .setPositiveButton(getString(R.string.dialog_delete_confirm), null)
                    .show()
                return@setOnClickListener
            }

            if (EngineManager.hasShizukuPermission()) {
                prefs.edit().putInt("core_engine_type", EngineManager.ENGINE_SHIZUKU).apply()
                updateEngineUI(EngineManager.ENGINE_SHIZUKU)
                Toast.makeText(this, getString(R.string.toast_shizuku_success), Toast.LENGTH_SHORT).show()
                
                Thread { EngineManager.enableAccessibilityServiceSilently(this, EngineManager.ENGINE_SHIZUKU) }.start()
            } else {
                EngineManager.requestShizukuPermission(1001)
            }
        }

        binding.btnEngineRoot.setOnClickListener {
            Toast.makeText(this, getString(R.string.toast_requesting_root), Toast.LENGTH_SHORT).show()
            
            Thread {
                val isRooted = EngineManager.requestRootAccess()
                runOnUiThread {
                    if (isRooted) {
                        prefs.edit().putInt("core_engine_type", EngineManager.ENGINE_ROOT).apply()
                        updateEngineUI(EngineManager.ENGINE_ROOT)
                        Toast.makeText(this@SettingsActivity, getString(R.string.toast_root_success), Toast.LENGTH_SHORT).show()
                        
                        Thread { EngineManager.enableAccessibilityServiceSilently(this@SettingsActivity, EngineManager.ENGINE_ROOT) }.start()
                    } else {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle(getString(R.string.dialog_root_missing_title))
                            .setMessage(getString(R.string.dialog_root_missing_desc))
                            .setPositiveButton(getString(R.string.dialog_delete_confirm), null)
                            .show()
                    }
                }
            }.start()
        }

        binding.btnEngineLsposed.setOnClickListener {
            if (EngineManager.isLSPosedActive()) {
                prefs.edit().putInt("core_engine_type", EngineManager.ENGINE_LSPOSED).apply()
                updateEngineUI(EngineManager.ENGINE_LSPOSED)
                Toast.makeText(this, getString(R.string.toast_lsposed_success), Toast.LENGTH_SHORT).show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_lsposed_missing_title))
                    .setMessage(getString(R.string.dialog_lsposed_missing_desc))
                    .setPositiveButton(getString(R.string.dialog_delete_confirm), null)
                    .show()
            }
        }
    }

    private fun toggleAppIconVisibility(hideIcon: Boolean) {
        val prefs = getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("hide_app_icon", hideIcon).apply()

        val packageManager = packageManager
        val componentName = ComponentName(this, SplashActivity::class.java) 
        
        val state = if (hideIcon) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        
        packageManager.setComponentEnabledSetting(
            componentName,
            state,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun showLanguageDialog() {
        val baseLanguages = mutableListOf("العربية", "English", "Français", "Español", "Português", "Русский", "हिन्दी", "中文")
        val baseCodes = mutableListOf("ar", "en", "fr", "es", "pt", "ru", "hi", "zh")

        val prefs = getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
        
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("custom_lang_name_") && value is String) {
                baseLanguages.add("$value (Community)")
                baseCodes.add(key.replace("custom_lang_name_", "")) 
            }
        }

        val currentLang = prefs.getString("app_language", "ar") ?: "ar"
        val checkedItem = baseCodes.indexOf(currentLang).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_language)) 
            .setSingleChoiceItems(baseLanguages.toTypedArray(), checkedItem) { dialog, which ->
                val selectedCode = baseCodes[which]
                if (selectedCode != currentLang) {
                    prefs.edit().putString("app_language", selectedCode).apply()
                    setAppLocale(selectedCode)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun getLanguageName(code: String): String {
        if (code.startsWith("custom_")) {
            val prefs = getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
            return prefs.getString("custom_lang_name_$code", getString(R.string.default_custom_lang)) ?: getString(R.string.default_custom_lang)
        }

        return when(code) {
            "en" -> "English"
            "fr" -> "Français"
            "es" -> "Español"
            "pt" -> "Português"
            "ru" -> "Русский"
            "hi" -> "हिन्दी"
            "zh" -> "中文"
            else -> "العربية"
        }
    }

    private fun setAppLocale(languageCode: String) {
        val intent = Intent(this, SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startSmoothActivity(intent)
        finishAffinity() 
    }

    private fun setupBottomNav() {
        binding.navBtnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startSmoothActivity(intent)
        }
        
        binding.navBtnGallery.setOnClickListener {
            val intent = Intent(this, GalleryActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startSmoothActivity(intent)
        }

        binding.navBtnPermissions.setOnClickListener {
            val intent = Intent(this, PermissionsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startSmoothActivity(intent)
        }
        
        binding.navBtnSettings.setOnClickListener {}
    }

    private fun updateBottomNavUI(activeTabId: Int) {
        val activeColor = Color.parseColor("#299E94") 
        val inactiveColor = ContextCompat.getColor(this, R.color.pui_text_dim)

        binding.navBtnHome.setBackgroundResource(R.drawable.bg_nav_inactive)
        binding.navBtnHome.setColorFilter(inactiveColor)
        
        binding.navBtnGallery.setBackgroundResource(R.drawable.bg_nav_inactive)
        binding.navBtnGallery.setColorFilter(inactiveColor)

        binding.navBtnPermissions.setBackgroundResource(R.drawable.bg_nav_inactive)
        binding.navBtnPermissions.setColorFilter(inactiveColor)
        
        binding.navBtnSettings.setBackgroundResource(R.drawable.bg_nav_inactive)
        binding.navBtnSettings.setColorFilter(inactiveColor)

        when (activeTabId) {
            binding.navBtnHome.id -> {
                binding.navBtnHome.setBackgroundResource(R.drawable.bg_nav_active)
                binding.navBtnHome.setColorFilter(activeColor)
            }
            binding.navBtnGallery.id -> {
                binding.navBtnGallery.setBackgroundResource(R.drawable.bg_nav_active)
                binding.navBtnGallery.setColorFilter(activeColor)
            }
            binding.navBtnPermissions.id -> {
                binding.navBtnPermissions.setBackgroundResource(R.drawable.bg_nav_active)
                binding.navBtnPermissions.setColorFilter(activeColor)
            }
            binding.navBtnSettings.id -> {
                binding.navBtnSettings.setBackgroundResource(R.drawable.bg_nav_active)
                binding.navBtnSettings.setColorFilter(activeColor)
            }
        }
    }
}
