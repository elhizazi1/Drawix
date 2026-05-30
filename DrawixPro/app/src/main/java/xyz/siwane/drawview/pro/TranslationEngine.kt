package xyz.siwane.drawix.pro

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader

object TranslationEngine {

    // ذاكرة التخزين المؤقت للنصوص (سريعة جداً ولا تستهلك المعالج)
    private val customTranslations = HashMap<String, String>()
    var isCustomLanguageActive = false
        private set

    // =======================================================
    // 1. تهيئة المحرك عند فتح التطبيق (تُستدعى في Splash/BaseActivity)
    // =======================================================
    fun init(context: Context) {
        val prefs = context.getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("app_language", "ar") ?: "ar"

        // إذا كانت اللغة تبدأ بـ "custom_"، فهذا يعني أن المستخدم يستعمل لغته الخاصة
        if (langCode.startsWith("custom_")) {
            loadCustomLanguage(context, langCode)
        } else {
            isCustomLanguageActive = false
            customTranslations.clear()
        }
    }

    private fun loadCustomLanguage(context: Context, langCode: String) {
        try {
            val file = File(context.filesDir, "custom_langs/$langCode.json")
            if (file.exists()) {
                val jsonString = file.readText()
                val jsonObject = JSONObject(jsonString)
                
                customTranslations.clear()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    // تجاهل مفاتيح التعليمات
                    if (!key.startsWith("_INSTRUCTION_")) {
                        customTranslations[key] = jsonObject.getString(key)
                    }
                }
                isCustomLanguageActive = true
            } else {
                isCustomLanguageActive = false // خط الرجعة: الملف غير موجود
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isCustomLanguageActive = false // خط الرجعة: خطأ في بنية JSON
        }
    }

    // =======================================================
    // 2. استخراج النص (مع حماية من الانهيار)
    // =======================================================
    fun getCustomString(context: Context, resId: Int, defaultString: String): String {
        if (!isCustomLanguageActive) return defaultString

        try {
            // تحويل الرقم (ID) إلى اسم المفتاح (مثال: app_name)
            val resourceEntryName = context.resources.getResourceEntryName(resId)
            
            // إذا كان المفتاح موجوداً في JSON المرفوع، نعيده. وإلا نعيد النص الأصلي!
            return customTranslations[resourceEntryName] ?: defaultString
        } catch (e: Exception) {
            // حماية مطلقة: إذا حدث أي خطأ (مثلاً ID للنظام وليس للتطبيق)، نعيد النص الأصلي
            return defaultString
        }
    }

    // =======================================================
    // 3. توليد قالب JSON (للتصدير)
    // =======================================================
    fun generateTemplateJson(context: Context, baseIsEnglish: Boolean): String {
        val json = JSONObject()
        
        // --- إضافة التعليمات للمستخدم (كأنها مفاتيح JSON) ---
        json.put("_INSTRUCTION_1", "⚠️ WARNING: DO NOT CHANGE THE KEYS ON THE LEFT SIDE!")
        json.put("_INSTRUCTION_2", "⚠️ تحذير: لا تقم بتغيير المفاتيح الموجودة على اليسار أبداً!")
        json.put("_INSTRUCTION_3", "ONLY translate the text on the right side. | قم بترجمة النصوص الموجودة على اليمين فقط.")
        json.put("_INSTRUCTION_4", "If the app does not show your translation, check if you accidentally deleted a quote (\") or comma (,).")
        
        // قائمة ببعض المفاتيح الأساسية (يمكننا جلبها ديناميكياً، ولكن هذا مثال حي)
        val keysToExport = listOf(
            "app_name", "app_subtitle", "tip_title", "tip_desc", "btn_apply",
            "nav_home", "nav_gallery", "nav_settings", "nav_permissions"
            // (في النسخة النهائية سنجلب جميع نصوص التطبيق برمجياً هنا)
        )

        // جلب النصوص الأصلية ووضعها في الـ JSON
        val config = context.resources.configuration
        config.setLocale(java.util.Locale(if (baseIsEnglish) "en" else "ar"))
        val localizedContext = context.createConfigurationContext(config)

        keysToExport.forEach { key ->
            try {
                val resId = context.resources.getIdentifier(key, "string", context.packageName)
                if (resId != 0) {
                    json.put(key, localizedContext.getString(resId))
                }
            } catch (e: Exception) { }
        }

        return json.toString(4) // رقم 4 لترتيب الـ JSON وجعله جميلاً ومقروءاً للمستخدم
    }

    // =======================================================
    // 4. استيراد ملف الترجمة الجديد وحفظه
    // =======================================================
    fun importTranslation(context: Context, uri: Uri, langNameNative: String, langCodeStr: String): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val reader = InputStreamReader(inputStream)
            val jsonString = reader.readText()
            reader.close()

            // التحقق من أن الملف JSON سليم قبل اعتماده (خط دفاعي)
            JSONObject(jsonString) 

            // إنشاء مجلد الترجمات إذا لم يكن موجوداً
            val dir = File(context.filesDir, "custom_langs")
            if (!dir.exists()) dir.mkdirs()

            // حفظ الملف باسم الكود الجديد (مثال: custom_tr.json)
            val customLangId = "custom_${langCodeStr.lowercase()}"
            val file = File(dir, "$customLangId.json")
            file.writeText(jsonString)

            // تحديث الإعدادات لتعرف أن هناك لغة جديدة
            val prefs = context.getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("custom_lang_name_$customLangId", langNameNative) // حفظ اسم اللغة (مثل: Türkçe)
                .apply()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false // الاستيراد فشل (غالباً الملف تالف)
        }
    }
}
