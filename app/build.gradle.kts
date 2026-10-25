plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.signals.app"
    compileSdk = 34
    defaultConfig { applicationId = "com.signals.app"; minSdk = 26; targetSdk = 34; versionCode = 2; versionName = "2.0" }
    signingConfigs { create("release") { storeFile = file("signals.jks"); storePassword = "56fbbd14d2b876f2"; keyAlias = "signals"; keyPassword = "56fbbd14d2b876f2" } }
    buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("release"); isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
