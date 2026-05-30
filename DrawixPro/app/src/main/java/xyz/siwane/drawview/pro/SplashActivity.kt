package xyz.siwane.drawix.pro

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.ActivityOptions // تمت إضافة هذا الاستيراد الهام
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView

class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // تفعيل ميزة Edge-to-Edge بكسر حدود الشفافية العلوية والسفلية
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        
        setContentView(R.layout.activity_splash)

        val ripple1 = findViewById<View>(R.id.ripple1)
        val ripple2 = findViewById<View>(R.id.ripple2)
        val logo = findViewById<ImageView>(R.id.iv_splash_logo)
        val title = findViewById<View>(R.id.tv_splash_title)

        // دخول الأيقونة والنص بحركة ارتدادية جميلة
        logo.scaleX = 0f; logo.scaleY = 0f
        logo.animate().scaleX(1f).scaleY(1f).setDuration(800)
            .setInterpolator(OvershootInterpolator(1.5f)).start()
            
        title.alpha = 0f; title.translationY = 50f
        title.animate().alpha(1f).translationY(0f).setDuration(800).setStartDelay(200)
            .setInterpolator(OvershootInterpolator(1.0f)).start()

        // تشغيل تأثيرات التموج اللانهائية
        startRippleAnimation(ripple1, 0)
        startRippleAnimation(ripple2, 700) 

        // الانتقال إلى الواجهة الرئيسية بعد 2.5 ثانية
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            
            // --- الحل الجذري للانتقال السلس (تغليف الحركة مع الأمر) ---
            val options = ActivityOptions.makeCustomAnimation(
                this, 
                android.R.anim.fade_in, 
                android.R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
            // ---------------------------------------------------------
            
            finish()
        }, 2500)
    }

    private fun startRippleAnimation(view: View, delay: Long) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 4.5f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 4.5f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0.6f, 0f) // يبدأ بشفافية 60% ويتلاشى

        scaleX.repeatCount = ObjectAnimator.INFINITE
        scaleY.repeatCount = ObjectAnimator.INFINITE
        alpha.repeatCount = ObjectAnimator.INFINITE

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.duration = 2000
        animatorSet.startDelay = delay
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.start()
    }
}
