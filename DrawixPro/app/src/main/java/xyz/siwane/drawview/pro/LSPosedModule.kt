package xyz.siwane.drawix.pro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.view.WindowManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

class LSPosedModule : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        
        // ========================================================
        // 1. التطبيق الخاص بنا: تأكيد تفعيل الموديول
        // ========================================================
        if (lpparam.packageName == "xyz.siwane.drawix.pro") {
            try {
                XposedHelpers.findAndHookMethod(
                    "xyz.siwane.drawix.pro.EngineManager",
                    lpparam.classLoader,
                    "isLSPosedActive",
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Any {
                            // نجعل الدالة تعيد true بدلاً من false
                            return true
                        }
                    }
                )
            } catch (e: Exception) {
                XposedBridge.log("DrawixPro: Hooking self failed - ${e.message}")
            }
        }

        // ========================================================
        // 2. إطار النظام الأساسي (Android): كسر القواعد الأمنية!
        // ========================================================
        if (lpparam.packageName == "android") {
            
            // أ. تجاوز الشاشة السوداء (FLAG_SECURE) لجميع التطبيقات في الهاتف
            try {
                val secureHook = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val attrs = param.args[1] as? WindowManager.LayoutParams
                        if (attrs != null) {
                            // نزع علم الحماية السري! (FLAG_SECURE.inv() تعكس القيمة وتعطل الحماية)
                            attrs.flags = attrs.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                        }
                    }
                }
                
                val wmsClass = XposedHelpers.findClass("com.android.server.wm.WindowManagerService", lpparam.classLoader)
                // حقن أثناء إضافة نافذة جديدة
                XposedHelpers.findAndHookMethod(wmsClass, "addView", android.view.View::class.java, WindowManager.LayoutParams::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, secureHook)
                // حقن أثناء تحديث نافذة موجودة
                XposedHelpers.findAndHookMethod(wmsClass, "updateViewLayout", android.view.View::class.java, WindowManager.LayoutParams::class.java, secureHook)
                
                XposedBridge.log("DrawixPro: FLAG_SECURE successfully bypassed!")
            } catch (e: Exception) {
                XposedBridge.log("DrawixPro: WMS Hook failed - ${e.message}")
            }

            // ب. زرع مستمع خفي (Receiver) داخل عقل النظام لتنفيذ أمر التصوير
            try {
                val amsClass = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", lpparam.classLoader)
                XposedHelpers.findAndHookMethod(amsClass, "systemReady", Runnable::class.java, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as Context
                        
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(c: Context?, intent: Intent?) {
                                if (intent?.action == "xyz.siwane.drawix.pro.ACTION_LSPOSED_CAPTURE") {
                                    takeScreenshotFromSystemRoot(context)
                                }
                            }
                        }
                        
                        val filter = IntentFilter("xyz.siwane.drawix.pro.ACTION_LSPOSED_CAPTURE")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                        } else {
                            context.registerReceiver(receiver, filter)
                        }
                        XposedBridge.log("DrawixPro: System listener injected successfully!")
                    }
                })
            } catch (e: Exception) {
                XposedBridge.log("DrawixPro: Failed to inject receiver - ${e.message}")
            }
        }
    }

    // ========================================================
    // دالة تنفيذ التصوير بصلاحية قلب النظام (System Server)
    // ========================================================
    private fun takeScreenshotFromSystemRoot(context: Context) {
        try {
            // بما أننا داخل النظام، نحن أسياد الجهاز، نأمر النظام بالتقاط الشاشة دون أي قيود
            val fileName = "DrawixPro_LSPosed_${System.currentTimeMillis()}.png"
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "DrawixPro")
            if (!dir.exists()) dir.mkdirs()
            
            val file = File(dir, fileName)
            
            // أمر screencap يعمل هنا بكفاءة مرعبة لأنه يُنفذ من النظام نفسه (uid 1000)
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "screencap -p > \"${file.absolutePath}\"")).waitFor()
            
            // إجبار تطبيق المعرض على رؤية الصورة فوراً
            val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            scanIntent.data = android.net.Uri.fromFile(file)
            context.sendBroadcast(scanIntent)
            
            XposedBridge.log("DrawixPro: Screenshot captured like a boss! -> ${file.absolutePath}")
        } catch (e: Exception) {
            XposedBridge.log("DrawixPro: Screenshot Error: ${e.message}")
        }
    }
}
