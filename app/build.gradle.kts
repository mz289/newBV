@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
}

android {
    namespace = AppConfiguration.appId
    compileSdk = AppConfiguration.compileSdk

    signingConfigs {
        // 本地和 CI 共用固定路径；缺失时由签名任务报错，禁止回退到临时密钥。
        create("distribution") {
            storeFile = rootProject.file(".signing/newbv.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = AppConfiguration.applicationId
        minSdk = AppConfiguration.minSdk
        targetSdk = AppConfiguration.targetSdk
        versionCode = AppConfiguration.versionCode
        versionName = AppConfiguration.versionName

        testInstrumentationRunner = "dev.frost819.newbv.app.CustomTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("distribution")
        }
        debug {
            signingConfig = signingConfigs.getByName("distribution")
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                    output.outputFileName.set(
                        "newBV_${AppConfiguration.versionCode}_${AppConfiguration.versionName}_${variant.name}.apk",
                    )
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            // Protobuf source files and desktop-only Jansi natives are not used at runtime on Android.
            excludes +=
                listOf(
                    "**/*.proto",
                    "/META-INF/native-image/jansi/**",
                    "/org/fusesource/jansi/internal/native/**",
                )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // === Project modules ===
    implementation(project(":bili-api"))
    implementation(project(":bili-subtitle"))
    implementation(project(":player"))
    implementation(project(":danmaku"))
    implementation(project(":core"))
    implementation(project(":data"))

    // === AndroidX Core ===
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.material)

    // === Lifecycle ===
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // === Navigation ===
    implementation(libs.androidx.navigation.compose)

    // === DataStore ===
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)

    // === Room ===
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // === Compose ===
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.util)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.tv.foundation)
    implementation(libs.androidx.compose.tv.material)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // === Hilt ===
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // === Media3 ===
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)

    // === Image loading (Coil 3) ===
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // === Networking ===
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.client.serialization.kotlinx)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

    // === Kotlin ===
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization)

    // === Other ===
    implementation(project(":danmaku-engine"))
    implementation(libs.androidsvg)
    implementation(libs.qrcode)
    debugImplementation(libs.leakcanary)

    // === Testing ===
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation("androidx.test:core:1.6.1")
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.androidx.room.testing)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
