package xyz.siwane.drawix.pro

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.View
import android.view.WindowInsetsController
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import xyz.siwane.drawix.pro.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    
    private val vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    
    private var isDarkTheme = true
    private var colorActiveBg = 0

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        if (checkOverlayPermission()) {
            startDrawingService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { 
            val permissionsToRequest = mutableListOf<String>()
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (permissionsToRequest.isNotEmpty()) {
                requestPermissions(permissionsToRequest.toTypedArray(), 100)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { 
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE, 
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), 100)
            }
        }

        initializeThemeColors()
        playEntranceAnimations()
        startBreathingAnimation(binding.headerIconContainer)

        setupServiceToggle()
        setupBottomNav()
        setupAccordion() 
        setupAboutLinks()
        setupSupportLinks() // دالة الدعم الجديدة
        
        setupAutoHidingBottomNav(binding.rootScrollView, binding.bottomNavBar)
        
        updateUI(FloatingDrawService.isRunning, animate = false)
    }

    override fun onResume() {
        super.onResume()
        updateUI(FloatingDrawService.isRunning, animate = true)
        updateBottomNavUI(binding.navBtnHome.id)
    }

    private fun initializeThemeColors() {
        val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isDarkTheme = mode == Configuration.UI_MODE_NIGHT_YES

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (isDarkTheme) {
                controller?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
            } else {
                controller?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (isDarkTheme) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        colorActiveBg = if (isDarkTheme) Color.parseColor("#2600B693") else Color.parseColor("#1A00B693")
    }

    private fun setupServiceToggle() {
        binding.switchDrawService.setOnCheckedChangeListener { _, isChecked ->
            triggerHapticFeedback(30)
            if (isChecked) {
                if (!checkOverlayPermission()) {
                    binding.switchDrawService.isChecked = false
                    requestOverlayPermission()
                } else {
                    startDrawingService()
                }
            } else {
                stopDrawingService()
            }
        }
    }
    
    private fun setupBottomNav() {
        binding.navBtnHome.setOnClickListener {
            triggerHapticFeedback(20)
        }
        
        binding.navBtnGallery.setOnClickListener {
            triggerHapticFeedback(20)
            val intent = Intent(this, GalleryActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startSmoothActivity(intent)
        }

        binding.navBtnPermissions.setOnClickListener {
            triggerHapticFeedback(20)
            val intent = Intent(this, PermissionsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startSmoothActivity(intent)
        }
        
        binding.navBtnSettings.setOnClickListener {
            triggerHapticFeedback(20)
            val intent = Intent(this, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startSmoothActivity(intent)
        }
    }

    private fun setupAccordion() {
        binding.layoutFaq1Header.setOnClickListener {
            triggerHapticFeedback(15)
            toggleFaqSection(binding.layoutFaq1Body, binding.ivFaq1Arrow)
        }
        
        binding.layoutFaq2Header.setOnClickListener {
            triggerHapticFeedback(15)
            toggleFaqSection(binding.layoutFaq2Body, binding.ivFaq2Arrow)
        }
        
        binding.layoutFaq3Header.setOnClickListener {
            triggerHapticFeedback(15)
            toggleFaqSection(binding.layoutFaq3Body, binding.ivFaq3Arrow)
        }
    }

    private fun toggleFaqSection(bodyLayout: View, arrowIcon: ImageView) {
        if (bodyLayout.visibility == View.VISIBLE) {
            bodyLayout.visibility = View.GONE
            arrowIcon.animate().rotation(0f).setDuration(250)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        } else {
            bodyLayout.visibility = View.VISIBLE
            arrowIcon.animate().rotation(180f).setDuration(250)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
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

    private fun setupAboutLinks() {
        binding.btnPrivacy.setOnClickListener {
            triggerHapticFeedback(15)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://drawix.siwane.xyz/privacy"))
            startActivity(intent)
        }
        
        binding.btnTerms.setOnClickListener {
            triggerHapticFeedback(15)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://drawix.siwane.xyz/terms"))
            startActivity(intent)
        }
        
        binding.btnDevWebsite.setOnClickListener {
            triggerHapticFeedback(15)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://jamal.elhizazi.me/"))
            startActivity(intent)
        }
        
        binding.btnSupportSite.setOnClickListener {
            triggerHapticFeedback(15)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.siwane.xyz/"))
            startActivity(intent)
        }
        
        binding.btnContactEmail.setOnClickListener {
            triggerHapticFeedback(15)
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:jamal@elhizazi.me")
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.support_email_subject))
            }
            startActivity(Intent.createChooser(intent, getString(R.string.support_email_chooser)))
        }
    }

    // دالة مخصصة لربط أزرار الدعم (سيتم تفعيل محتواها بعد تحديث الواجهة XML)
    private fun setupSupportLinks() {
        binding.btnSupportPaypal.setOnClickListener {
            triggerHapticFeedback(15)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/Elhizazi"))
            startActivity(intent)
        }
        
        binding.btnSupportCoffee.setOnClickListener {
            triggerHapticFeedback(15)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/elhizazi1"))
            startActivity(intent)
        }
    }


    private fun startDrawingService() {
        val intent = Intent(this, FloatingDrawService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        SmartNotificationHelper.cancelRetentionReminder(this)
        updateUI(isRunning = true, animate = true)
    }

    private fun stopDrawingService() {
        stopService(Intent(this, FloatingDrawService::class.java))
        SmartNotificationHelper.scheduleRetentionReminder(this)
        updateUI(isRunning = false, animate = true)
    }

    private fun updateUI(isRunning: Boolean, animate: Boolean) {
        binding.switchDrawService.isChecked = isRunning
        val activeColor = ContextCompat.getColor(this, R.color.colorPrimary)
        val inactiveColor = ContextCompat.getColor(this, R.color.status_error)
        val textColor = ContextCompat.getColor(this, R.color.pui_text)

        if (isRunning) {
            binding.tvStatusDesc.text = getString(R.string.service_running)
            binding.tvStatusDesc.setTextColor(activeColor)
            binding.ivMainIcon.setColorFilter(activeColor)
            
            if (animate) {
                animateCardColor(binding.cardStatus, ContextCompat.getColor(this, R.color.pui_card), colorActiveBg, false)
                pulseAnimation(binding.headerIconContainer)
            } else {
                binding.cardStatus.backgroundTintList = ColorStateList.valueOf(colorActiveBg)
            }
        } else {
            binding.tvStatusDesc.text = getString(R.string.service_stopped)
            binding.tvStatusDesc.setTextColor(inactiveColor)
            binding.ivMainIcon.setColorFilter(textColor)
            
            if (animate) {
                animateCardColor(binding.cardStatus, colorActiveBg, ContextCompat.getColor(this, R.color.pui_card), true)
            } else {
                binding.cardStatus.backgroundTintList = null
            }
        }
    }

    private fun playEntranceAnimations() {
        val viewsToAnimate = listOf(
            binding.headerIconContainer, binding.cardStatus, 
            binding.layoutStatsRow, binding.panelTips,
            binding.cardNoteInfo, binding.cardNoteWarning,
            binding.cardNoteError, binding.cardNoteTrust,
            binding.tvGuideTitle, binding.cardGuide, binding.bottomNavBar
        )
        
        viewsToAnimate.forEach { 
            it.alpha = 0f
            it.translationY = 60f 
        }

        var delay = 0L
        viewsToAnimate.forEach { view ->
            view.animate()
                .alpha(1f).translationY(0f)
                .setDuration(400)
                .setStartDelay(delay)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()
            delay += 40L
        }
    }

    private fun startBreathingAnimation(view: View) {
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.04f, 1f)
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.04f, 1f)
        scaleY.repeatCount = ObjectAnimator.INFINITE
        scaleX.repeatCount = ObjectAnimator.INFINITE
        scaleY.duration = 2200
        scaleX.duration = 2200
        scaleY.interpolator = AccelerateDecelerateInterpolator()
        scaleX.interpolator = AccelerateDecelerateInterpolator()
        scaleY.start()
        scaleX.start()
    }

    private fun pulseAnimation(view: View) {
        view.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
    }

    private fun animateCardColor(view: View, fromColor: Int, toColor: Int, removeTintOnEnd: Boolean) {
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)
        colorAnimation.duration = 350
        colorAnimation.addUpdateListener { animator ->
            view.backgroundTintList = ColorStateList.valueOf(animator.animatedValue as Int)
        }
        if (removeTintOnEnd) {
            colorAnimation.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    view.backgroundTintList = null
                }
            })
        }
        colorAnimation.start()
    }

    private fun triggerHapticFeedback(duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
            Toast.makeText(this, getString(R.string.permission_request), Toast.LENGTH_LONG).show()
        }
    }
}
