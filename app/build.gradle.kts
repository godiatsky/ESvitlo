plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ua.serg.svitlo"
    compileSdk = 34
    defaultConfig {
        applicationId = "ua.serg.svitlo"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "10.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("androidx.work:work-runtime-ktx:2.9.1") }
