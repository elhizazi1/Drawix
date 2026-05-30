package xyz.siwane.drawix.pro

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import xyz.siwane.drawix.pro.databinding.ActivityPermissionsBinding

class PermissionsActivity : BaseActivity() {

    private lateinit var binding: ActivityPermissionsBinding
    
    private val vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupBottomNav()
        setupPermissionButtons()
        
        setupAutoHidingBottomNav(binding.rootScrollView, binding.bottomNavBar)
    }

    override fun onResume() {
        super.onResume()
        updateBottomNavUI(binding.navBtnPermissions.id)
        updatePermissionStatuses() 
    }

    override fun onPause() {
        super.onPause()
        val hasAccessibility = ScreenshotAccessibilityService.instance != null
        if (!hasAccessibility) {
            SmartNotificationHelper.schedulePermissionReminder(this)
        }
    }

    private fun setupPermissionButtons() {
        binding.btnGrantAccessibility.setOnClickListener {
            triggerHapticFeedback(20)
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.btnGrantOverlay.setOnClickListener {
            triggerHapticFeedback(20)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }

        binding.btnGrantStorage.setOnClickListener {
            triggerHapticFeedback(20)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 101)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 101)
            }
        }

        binding.btnGrantNotifications.setOnClickListener {
            triggerHapticFeedback(20)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            }
        }

        // --- زر استثناء البطارية لمنع قتل التطبيق ---
        binding.btnGrantBattery.setOnClickListener {
            triggerHapticFeedback(20)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun updatePermissionStatuses() {
        val hasAccessibility = ScreenshotAccessibilityService.instance != null
        if (hasAccessibility) {
            binding.btnGrantAccessibility.text = getString(R.string.btn_permission_granted)
            binding.btnGrantAccessibility.setTextColor(Color.parseColor("#4CAF50"))
            binding.btnGrantAccessibility.isEnabled = false
        } else {
            binding.btnGrantAccessibility.text = getString(R.string.btn_grant_settings)
            binding.btnGrantAccessibility.setTextColor(Color.parseColor("#3B82F6"))
            binding.btnGrantAccessibility.isEnabled = true
        }

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        if (hasOverlay) {
            binding.btnGrantOverlay.text = getString(R.string.btn_permission_granted)
            binding.btnGrantOverlay.setTextColor(Color.parseColor("#4CAF50"))
            binding.btnGrantOverlay.isEnabled = false
        } else {
            binding.btnGrantOverlay.text = getString(R.string.btn_grant_permission)
            binding.btnGrantOverlay.setTextColor(Color.parseColor("#F59E0B"))
            binding.btnGrantOverlay.isEnabled = true
        }

        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        
        if (hasStorage) {
            binding.btnGrantStorage.text = getString(R.string.btn_permission_granted)
            binding.btnGrantStorage.setTextColor(Color.parseColor("#4CAF50"))
            binding.btnGrantStorage.isEnabled = false
        } else {
            binding.btnGrantStorage.text = getString(R.string.btn_grant_permission)
            binding.btnGrantStorage.setTextColor(Color.parseColor("#10B981"))
            binding.btnGrantStorage.isEnabled = true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            binding.layoutNotificationPermission.visibility = View.VISIBLE
            val hasNotifications = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            
            if (hasNotifications) {
                binding.btnGrantNotifications.text = getString(R.string.btn_permission_granted)
                binding.btnGrantNotifications.setTextColor(Color.parseColor("#4CAF50"))
                binding.btnGrantNotifications.isEnabled = false
            } else {
                binding.btnGrantNotifications.text = getString(R.string.btn_grant_permission)
                binding.btnGrantNotifications.setTextColor(Color.parseColor("#8B5CF6"))
                binding.btnGrantNotifications.isEnabled = true
            }
        } else {
            binding.layoutNotificationPermission.visibility = View.GONE
        }

        // --- تحديث حالة صلاحية استثناء البطارية ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(packageName)
            if (isIgnoringBattery) {
                binding.btnGrantBattery.text = getString(R.string.btn_permission_granted)
                binding.btnGrantBattery.setTextColor(Color.parseColor("#4CAF50"))
                binding.btnGrantBattery.isEnabled = false
            } else {
                binding.btnGrantBattery.text = getString(R.string.btn_grant_permission)
                binding.btnGrantBattery.setTextColor(Color.parseColor("#EF4444")) // لون أحمر للتنبيه بأهميتها
                binding.btnGrantBattery.isEnabled = true
            }
        } else {
            // في الأجهزة القديمة لا توجد هذه المشكلة غالباً
            binding.btnGrantBattery.text = getString(R.string.btn_permission_granted)
            binding.btnGrantBattery.setTextColor(Color.parseColor("#4CAF50"))
            binding.btnGrantBattery.isEnabled = false
        }
    }

    private fun setupBottomNav() {
        binding.navBtnHome.setOnClickListener {
            triggerHapticFeedback(20)
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startSmoothActivity(intent)
        }
        
        binding.navBtnGallery.setOnClickListener {
            triggerHapticFeedback(20)
            val intent = Intent(this, GalleryActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startSmoothActivity(intent)
        }

        binding.navBtnPermissions.setOnClickListener {
            triggerHapticFeedback(20)
        }
        
        binding.navBtnSettings.setOnClickListener {
            triggerHapticFeedback(20)
            val intent = Intent(this, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startSmoothActivity(intent)
        }
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

    private fun triggerHapticFeedback(duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}
