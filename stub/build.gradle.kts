plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "stub"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt())
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        aidl = true
    }
}
