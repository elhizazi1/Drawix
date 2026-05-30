package xyz.siwane.drawix.pro

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import xyz.siwane.drawix.pro.databinding.ActivityGalleryBinding
import java.io.File

class GalleryActivity : BaseActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private val imageFiles = mutableListOf<File>()
    
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
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { 
            triggerHapticFeedback(15)
            finish()
        }

        setupBottomNav()
        setupRecyclerView()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.rvGallery.setOnScrollChangeListener { _, _, _, _, _ ->
                val canScrollUp = binding.rvGallery.canScrollVertically(-1)
                val isScrollingDown = binding.rvGallery.computeVerticalScrollOffset() > 10

                if (isScrollingDown && binding.bottomNavBar.translationY == 0f) {
                    binding.bottomNavBar.animate().translationY(250f).setDuration(250).start()
                } else if (!canScrollUp && binding.bottomNavBar.translationY > 0f) {
                    binding.bottomNavBar.animate().translationY(0f).setDuration(250).start()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadImages()
        updateBottomNavUI(binding.navBtnGallery.id)
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

    private fun setupRecyclerView() {
        binding.rvGallery.layoutManager = GridLayoutManager(this, 2)
        binding.rvGallery.adapter = GalleryAdapter()
        binding.rvGallery.isNestedScrollingEnabled = true 
    }

    private fun loadImages() {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "DrawixPro")
        imageFiles.clear()
        
        if (directory.exists()) {
            val files = directory.listFiles { file -> 
                file.extension.equals("png", ignoreCase = true) || file.extension.equals("jpg", ignoreCase = true) 
            }
            if (files != null && files.isNotEmpty()) {
                imageFiles.addAll(files.sortedByDescending { it.lastModified() })
                binding.layoutEmptyState.visibility = View.GONE
            } else {
                binding.layoutEmptyState.visibility = View.VISIBLE
            }
        } else {
            binding.layoutEmptyState.visibility = View.VISIBLE
        }
        
        binding.rvGallery.adapter?.notifyDataSetChanged()
    }

    private fun showFullScreenImage(file: File) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            setOnClickListener { dialog.dismiss() }
        }
        dialog.setContentView(imageView)
        dialog.show()
    }

    private fun confirmAndDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_delete_title))
            .setMessage(getString(R.string.dialog_delete_message))
            .setPositiveButton(getString(R.string.dialog_delete_confirm)) { _, _ ->
                
                val filePath = file.absolutePath
                var isDeleted = file.delete() // 1. المحاولة العادية
                
                // 2. إذا فشل الحذف (غالباً بسبب ملكية الروت للملف)
                if (!isDeleted) {
                    try {
                        val prefs = getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
                        val currentEngine = prefs.getInt("core_engine_type", EngineManager.ENGINE_STANDARD)
                        
                        // استخدام القوة الجبرية بناءً على المحرك النشط
                        if (currentEngine == EngineManager.ENGINE_ROOT || currentEngine == EngineManager.ENGINE_LSPOSED) {
                            val result = com.topjohnwu.superuser.Shell.cmd("rm -f \"$filePath\"").exec()
                            isDeleted = result.isSuccess && !file.exists()
                        } else if (currentEngine == EngineManager.ENGINE_SHIZUKU && EngineManager.hasShizukuPermission()) {
                            val process = rikka.shizuku.Shizuku.newProcess(arrayOf("sh", "-c", "rm -f \"$filePath\""), null, null)
                            process.waitFor()
                            isDeleted = !file.exists()
                        } else {
                            // محاولة أخيرة بالروت كخيار طوارئ
                            val result = com.topjohnwu.superuser.Shell.cmd("su -c rm -f \"$filePath\"").exec()
                            isDeleted = result.isSuccess && !file.exists()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (isDeleted || !file.exists()) {
                    Toast.makeText(this, getString(R.string.toast_delete_success), Toast.LENGTH_SHORT).show()
                    loadImages() 
                } else {
                    Toast.makeText(this, getString(R.string.toast_delete_error), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.dialog_delete_cancel), null)
            .show()
    }

    private fun shareImage(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.toast_share_error), Toast.LENGTH_SHORT).show()
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

    inner class GalleryAdapter : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivScreenshot: ImageView = view.findViewById(R.id.iv_screenshot)
            val btnDelete: ImageView = view.findViewById(R.id.btn_delete)
            val btnShare: ImageView = view.findViewById(R.id.btn_share)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = imageFiles[position]
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            holder.ivScreenshot.setImageBitmap(bitmap)

            holder.ivScreenshot.setOnClickListener { 
                triggerHapticFeedback(10)
                showFullScreenImage(file) 
            }
            holder.btnDelete.setOnClickListener { 
                triggerHapticFeedback(15)
                confirmAndDelete(file) 
            }
            holder.btnShare.setOnClickListener { 
                triggerHapticFeedback(15)
                shareImage(file) 
            }
        }

        override fun getItemCount(): Int = imageFiles.size
    }
}
