plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "xyz.siwane.drawix.pro"
    compileSdk = 35
    ndkVersion = "28.2.13676358"
    
    defaultConfig {
        applicationId = "xyz.siwane.drawix.pro"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        
        vectorDrawables { 
            useSupportLibrary = true
        }
        
        externalNativeBuild {
            ndkBuild {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86"))
            }
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true // لتقليل حجم التطبيق وحذف الموارد الميتة
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }
    
    buildFeatures {
        viewBinding = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))
    }
}

dependencies {
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // --- تم الرجوع للإصدار 12.2.0 لتشغيل أوامر النظام مباشرة ---
    implementation("dev.rikka.shizuku:api:12.2.0")
    implementation("dev.rikka.shizuku:provider:12.2.0")
    
        // --- مكتبة libsu الرسمية للتعامل مع أوامر الروت (Magisk / KernelSU) ---
    implementation("com.github.topjohnwu.libsu:core:5.2.2")
    
        // --- مكتبة Xposed/LSPosed API ---
    compileOnly("de.robv.android.xposed:api:82")

}

