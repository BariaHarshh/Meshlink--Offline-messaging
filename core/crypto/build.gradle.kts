plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.meshlink.app.crypto"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:domain"))

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Jetpack Security — EncryptedSharedPreferences wraps the private key
    // with an AES-256-GCM master key stored inside Android Keystore
    implementation(libs.androidx.security.crypto)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
}
