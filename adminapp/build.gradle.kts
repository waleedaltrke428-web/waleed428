plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.signals.admin"
    compileSdk = 34
    defaultConfig { applicationId = "com.signals.admin"; minSdk = 23; targetSdk = 34; versionCode = 1; versionName = "1.0" }
    signingConfigs { create("release") { storeFile = file("../app/signals.jks"); storePassword = "56fbbd14d2b876f2"; keyAlias = "signals"; keyPassword = "56fbbd14d2b876f2" } }
    buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("release"); isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
