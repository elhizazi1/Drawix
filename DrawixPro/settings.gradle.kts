pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    jcenter() // <-- خادم احتياطي ضروري لجلب مكتبات Xposed
    maven { url = uri("https://jitpack.io") } 
    maven { url = uri("https://api.xposed.info/") } // <-- خادم Xposed الرسمي
  }
}

rootProject.name = "Drawix Pro"

include(":app")
