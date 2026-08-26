plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.kapt")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.silverwest.dayli"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.silverwest.dayli"
        minSdk = 24
        targetSdk = 36
        versionCode = 17
        versionName = "1.1.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // GEMINI_API_KEY buildConfigField 제거됨 — Firebase AI Logic 이전으로 APK에 키 미포함
        // (2026-08 프로젝트 정지 인시던트: APK 내장 키 유출·도용 재발 방지)
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    sourceSets {
        // Room schema JSON을 androidTest assets로 노출해 MigrationTestHelper가 사용 가능하게 함
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

// Room schema export 위치 (마이그레이션 검증 기반)
kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.compose.material:material")

    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended:1.6.4")

    // Reorderable (드래그 순서 변경)
    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")

    // Lifecycle ViewModel & LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")              // ✅ LiveData 지원
    implementation("androidx.compose.runtime:runtime-livedata:1.6.4")              // ✅ observeAsState 지원

    // SplashScreen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Google AdMob
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // ML Kit Text Recognition (한국어 OCR)
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")

    // Firebase AI Logic (Gemini) — APK에 API 키를 내장하지 않는 구조
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-ai")
    // App Check — 정품 앱만 AI 호출 가능 (키 도용 원천 차단)
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    // Java 8+ API 지원 (LocalDateTime 등)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Test
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")  // 단위 테스트에서 JSONObject/Array 실제 구현 사용
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation("androidx.room:room-testing:2.6.1")  // MigrationTestHelper
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
