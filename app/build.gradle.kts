plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hiktv.viewer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hiktv.viewer"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"

        ndk {
            // libvlc-all ships arm64-v8a/armeabi-v7a/x86/x86_64; TV hardware only
            // needs the ARM ABIs, so drop the other two rather than double the APK.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt"
            )
        }
        jniLibs {
            // Compressed .so storage: smaller APK to sideload from a USB stick,
            // at the cost of extraction at install time rather than mmap-from-APK.
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
            resources.srcDirs("src/test/resources")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Coroutines - previously only resolved transitively, now pinned explicitly
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Compose-stable immutable collections, for Device/Channel lists
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // TV-specific Compose
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0-alpha10")

    // Networking + digest auth
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.github.rburgst:okhttp-digest:3.1.1")

    // Image loading (OkHttp is plugged in via ImageLoader.Builder.okHttpClient)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Encrypted storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Device list persisted in EncryptedSharedPreferences as JSON via the
    // platform's built-in org.json (android.jar) - no dependency needed.

    // XML parsing for ISAPI/SADP responses. A real, portable implementation
    // rather than android.util.Xml - that ties Isapi.kt's parsing to the
    // Android framework stub, which throws (by design) in plain JVM unit
    // tests instead of parsing, since it's unmocked there.
    implementation("net.sf.kxml:kxml2:2.3.0")

    // Video + audio playback. Pinned to 3.7.2: 3.7.3+ depends on kotlin-stdlib
    // 2.2.10, which conflicts with this project's Kotlin 1.9.22 plugin version.
    implementation("org.videolan.android:libvlc-all:3.7.2")


    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}
