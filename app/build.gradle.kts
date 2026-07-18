@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.ApkVariantOutput
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

val keystorePropertiesFile: File =
    rootProject.file("keystore.properties")


val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4
)


val baseVersionName = currentVersion.name
val currentVersionCode = currentVersion.code.toInt()


android {

    namespace = "com.junkfood.seal"

    compileSdk = 35


    // ============================
    // Signing
    // ============================

    if (keystorePropertiesFile.exists()) {

        val keystoreProperties = Properties()

        keystoreProperties.load(
            FileInputStream(keystorePropertiesFile)
        )


        signingConfigs {

            create("githubPublish") {

                keyAlias =
                    keystoreProperties["keyAlias"].toString()

                keyPassword =
                    keystoreProperties["keyPassword"].toString()

                storeFile =
                    file(
                        keystoreProperties["storeFile"]!!
                    )

                storePassword =
                    keystoreProperties["storePassword"].toString()
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


        versionCode = 200_000_150

        check(
            versionCode == currentVersionCode
        )


        versionName = baseVersionName


        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"


        vectorDrawables {

            useSupportLibrary = true
        }
    }


    // ============================
    // Split APK ABI
    // ============================

    splits {

        abi {

            isEnable = true

            reset()


            include(
                "armeabi-v7a",
                "arm64-v8a",
                "x86",
                "x86_64"
            )


            isUniversalApk = true
        }
    }


    // ============================
    // Room + KSP
    // ============================

    room {

        schemaDirectory(
            "$projectDir/schemas"
        )
    }


    ksp {

        arg(
            "room.incremental",
            "true"
        )
    }


    // ============================
    // Modern AGP 8.x API
    // لا يوجد applicationVariants
    // ============================

    androidComponents {

        onVariants { variant ->

            variant.outputs.forEach { output ->


                val abi =
                    output.filters
                        .firstOrNull {
                            it.filterType ==
                                FilterConfiguration.FilterType.ABI
                        }
                        ?.identifier


                val abiCode =
                    abiCodes[abi] ?: 0


                output.versionCode.set(
                    currentVersionCode + abiCode
                )


                if (output is ApkVariantOutput) {

                    output.outputFileName.set(
                        "V-Downloader-${baseVersionName}-${abi ?: "universal"}.apk"
                    )
                }
            }
        }
    }
    // ============================
    // Build Types
    // ============================

    buildTypes {

        release {

            isMinifyEnabled = true

            isShrinkResources = true


            proguardFiles(

                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),

                "proguard-rules.pro"
            )


            signingConfig =
                if (keystorePropertiesFile.exists()) {

                    signingConfigs.getByName(
                        "githubPublish"
                    )

                } else {

                    signingConfigs.getByName(
                        "debug"
                    )
                }
        }


        debug {

            if (keystorePropertiesFile.exists()) {

                signingConfig =
                    signingConfigs.getByName(
                        "githubPublish"
                    )
            }


            resValue(
                "string",
                "app_name",
                "V-Downloader"
            )
        }
    }


    // ============================
    // Product Flavors
    // ============================

    flavorDimensions += "publishChannel"


    productFlavors {


        create("generic") {

            dimension =
                "publishChannel"

            isDefault = true
        }


        create("githubPreview") {

            dimension =
                "publishChannel"


            resValue(
                "string",
                "app_name",
                "V-Downloader"
            )
        }


        create("fdroid") {

            dimension =
                "publishChannel"


            versionName =
                "$baseVersionName-(F-Droid)"
        }
    }


    // ============================
    // Lint
    // ============================

    lint {

        disable.addAll(

            listOf(

                "MissingTranslation",

                "ExtraTranslation",

                "MissingQuantity"
            )
        )
    }


    // ============================
    // Kotlin / Compose
    // ============================

    kotlinOptions {

        freeCompilerArgs +=
            "-opt-in=kotlin.RequiresOptIn"
    }


    packaging {

        resources {

            excludes +=
                "/META-INF/{AL2.0,LGPL2.1}"
        }


        jniLibs {

            useLegacyPackaging = true
        }
    }


    androidResources {

        generateLocaleConfig = true
    }
}


ktfmt {

    kotlinLangStyle()
}


kotlin {

    jvmToolchain(21)
}
dependencies {

    implementation(project(":color"))


    // Core
    implementation(
        libs.bundles.core
    )


    implementation(
        libs.androidx.lifecycle.runtimeCompose
    )


    // Compose
    implementation(
        platform(
            libs.androidx.compose.bom
        )
    )


    implementation(
        libs.bundles.androidxCompose
    )


    implementation(
        libs.bundles.accompanist
    )


    implementation(
        libs.androidx.compose.ui.tooling
    )


    // UI / Images
    implementation(
        libs.coil.kt.compose
    )


    // Serialization
    implementation(
        libs.kotlinx.serialization.json
    )


    // Dependency Injection
    implementation(
        libs.koin.android
    )


    implementation(
        libs.koin.compose
    )


    // Database
    implementation(
        libs.room.runtime
    )


    implementation(
        libs.room.ktx
    )


    ksp(
        libs.room.compiler
    )


    // Network
    implementation(
        libs.okhttp
    )


    // Downloader
    implementation(
        libs.bundles.youtubedlAndroid
    )


    // Storage
    implementation(
        libs.mmkv
    )


    // Background jobs
    implementation(
        libs.androidx.work.runtime.ktx
    )


    // Media
    implementation(
        libs.androidx.media3.exoplayer
    )


    implementation(
        libs.androidx.media3.ui
    )


    // Splash Screen
    implementation(
        "androidx.core:core-splashscreen:1.0.1"
    )


    // Tests
    testImplementation(
        libs.junit4
    )


    androidTestImplementation(
        libs.androidx.test.ext
    )


    androidTestImplementation(
        libs.androidx.test.espresso.core
    )
}