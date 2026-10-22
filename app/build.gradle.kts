plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.paperscan.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.paperscan.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    // Tesseract 5 OCR (tessdata_fast models need Tesseract 4+): Russian + English models are downloaded on first use.
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
}
