package xyz.siwane.drawix.pro

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import rikka.shizuku.Shizuku
import com.topjohnwu.superuser.Shell

object EngineManager {
    const val ENGINE_STANDARD = 0
    const val ENGINE_SHIZUKU = 1
    const val ENGINE_ROOT = 2
    const val ENGINE_LSPOSED = 3

    // ==========================================
    // دوال Shizuku
    // ==========================================
    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestShizukuPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==========================================
    // دوال Root
    // ==========================================
    fun requestRootAccess(): Boolean {
        return try {
            val builder = Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR)
            Shell.setDefaultBuilder(builder)
            val shell = Shell.getShell()
            if (shell.isRoot) return true
            val result = Shell.cmd("su -c id").exec()
            result.isSuccess && result.out.joinToString("").contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // السلاح السري: تفعيل Accessibility صامتاً
    // ==========================================
    fun enableAccessibilityServiceSilently(context: Context, engineType: Int) {
        val serviceName = "${context.packageName}/${context.packageName}.ScreenshotAccessibilityService"
        
        when (engineType) {
            ENGINE_SHIZUKU -> {
                if (hasShizukuPermission()) {
                    Shizuku.newProcess(arrayOf("sh", "-c", "settings put secure enabled_accessibility_services $serviceName"), null, null).waitFor()
                }
            }
            ENGINE_ROOT -> {
                Shell.cmd("settings put secure enabled_accessibility_services $serviceName").exec()
            }
        }
    }

    // ==========================================
    // دوال LSPosed
    // ==========================================
    fun isLSPosedActive(): Boolean {
        return false 
    }
}
