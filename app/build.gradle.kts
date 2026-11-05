plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "dev.andrew.plinth"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.andrew.plinth"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The default debug keystore lives under ~/.android, which is read-only in this
    // sandbox; AGP then refuses to validate it. Use a workspace copy when present.
    val localDebugKeystore = rootProject.file(".keystore/debug.keystore")
    signingConfigs {
        if (localDebugKeystore.isFile) {
            getByName("debug") {
                storeFile = localDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Robolectric keeps a download lock under user.home, which is read-only on
            // this machine. Give the test JVM a writable home, and when the SDK jar is
            // already in the real Maven cache tell Robolectric to use it offline so a
            // test run never needs the network.
            val realHome = System.getProperty("user.home")
            val cachedSdk = File(realHome, ".m2/repository/org/robolectric/android-all-instrumented")
                .listFiles()
                ?.firstOrNull { it.isDirectory }
            test.systemProperty(
                "user.home",
                layout.buildDirectory.dir("test-home").get().asFile.apply { mkdirs() }.absolutePath,
            )
            if (cachedSdk != null) {
                test.systemProperty("robolectric.offline", "true")
                test.systemProperty("robolectric.dependency.dir", cachedSdk.absolutePath)
            }
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
