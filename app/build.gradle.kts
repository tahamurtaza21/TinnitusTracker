plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aiish.tinnitus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aiish.tinnitus"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10" // ✅ must match Compose 1.8.3
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ✅ Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx.v281)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom.v20240300))
    implementation(libs.androidx.compiler)


    // ✅ Compose UI
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose.v277)
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.work.runtime.ktx.v290)

    // ✅ Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.google.firebase.firestore.ktx)

    // ✅ Charting
    implementation(libs.mpandroidchart)

    // ✅ PDF Generator
    implementation(libs.itext7.core.v725)

    // ✅ Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.firebase.functions.ktx)
    implementation(libs.androidx.room.common.jvm)
    implementation(libs.google.firebase.storage.ktx)

    // ✅ Testing
    testImplementation(libs.junit)
    testImplementation(libs.testng)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core.v351)
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)

}
