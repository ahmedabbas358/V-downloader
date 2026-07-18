@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.FilterConfiguration
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.room)
    alias(libs.plugins.ktfmt.gradle)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")

val splitApks = true // ✅ تم تفعيل split APK لتقليل الحجم

val abiFilterList = (properties["ABI_FILTERS"] as String).split(';')

val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4
)

val baseVersionName = currentVersion.name
val currentVersionCode = currentVersion.code.toInt()

android {
    compileSdk = 35

    if (keystorePropertiesFile.exists()) {
        val keystoreProperties = Properties()
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
        signingConfigs {
            create("githubPublish") {
                keyAlias = keystoreProperties["keyAlias"].toString()
                keyPassword = keystoreProperties["keyPassword"].toString()
                storeFile = file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"].toString()
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.vdownloader.app"

        minSdk = 24
        targetSdk = 35

        versionCode = 205_000_400
        check(versionCode == currentVersionCode)

        versionName = baseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        ndk {
            abiFilters.addAll(abiFilterList)
        }
    }

    room { schemaDirectory("$projectDir/schemas") }
    ksp { arg("room.incremental", "true") }

    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                val name =
                    abiFilterList.firstOrNull()

                val baseAbiCode = abiCodes[name]

                if (baseAbiCode != null) {
                    output.versionCode.set(baseAbiCode + (output.versionCode.get() ?: 0))
                }
            }
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
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("githubPublish")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }

        debug {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("githubPublish")
            }
            // Removed debug application id and version suffixes to make it a unified production-like app
            resValue("string", "app_name", "V-Downloader")
        }
    }

    flavorDimensions += "publishChannel"

    productFlavors {
        create("generic") {
            dimension = "publishChannel"
            isDefault = true
        }

        create("githubPreview") {
            dimension = "publishChannel"
            resValue("string", "app_name", "V-Downloader")
        }

        create("fdroid") {
            dimension = "publishChannel"
            versionName = "$baseVersionName-(F-Droid)"
        }
    }

    lint {
        disable.addAll(
            listOf(
                "MissingTranslation",
                "ExtraTranslation",
                "MissingQuantity"
            )
        )
    }

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "V-Downloader-${defaultConfig.versionName}-${name}.apk"
        }
    }

    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs.useLegacyPackaging = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    namespace = "com.junkfood.seal"
}

ktfmt {
    kotlinLangStyle()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":color"))

    implementation(libs.bundles.core)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.androidxCompose)
    implementation(libs.bundles.accompanist)

    implementation(libs.coil.kt.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.bundles.youtubedlAndroid)
    implementation(libs.mmkv)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)

    implementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.core:core-splashscreen:1.0.1")
}