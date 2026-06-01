import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "skelterjohn.mixedmeter"
    // Required for extractReleaseNativeSymbolTables (Play native crash symbolication).
    ndkVersion = "27.0.12077973"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "skelterjohn.mixedmeter"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "1.3.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                // Packaged into the AAB for Play Console native crash symbolication.
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

val releaseBundleOutput = layout.projectDirectory.dir("release")

afterEvaluate {
    tasks.named("bundleRelease").configure {
        doLast {
            releaseBundleOutput.asFile.mkdirs()
            val bundle = layout.buildDirectory.file("outputs/bundle/release/app-release.aab").get().asFile
            bundle.copyTo(releaseBundleOutput.file("app-release.aab").asFile, overwrite = true)
            val mapping = layout.buildDirectory.file("outputs/mapping/release/mapping.txt").get().asFile
            if (mapping.exists()) {
                mapping.copyTo(releaseBundleOutput.file("mapping.txt").asFile, overwrite = true)
            }
            val nativeSymbols = layout.buildDirectory
                .file("outputs/native-debug-symbols/release/native-debug-symbols.zip")
                .get()
                .asFile
            if (nativeSymbols.exists()) {
                nativeSymbols.copyTo(
                    releaseBundleOutput.file("native-debug-symbols.zip").asFile,
                    overwrite = true,
                )
            } else {
                logger.warn(
                    "Native debug symbols were not generated. Install the NDK (Android Studio → " +
                        "SDK Manager → SDK Tools → NDK (Side by side), version ${android.ndkVersion}) and rebuild. " +
                        "Without it, Play Console will warn that native debug symbols are missing.",
                )
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.reorderable)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

gradle.taskGraph.whenReady {
    val needsReleaseSigning = allTasks.any {
        it.path == ":app:bundleRelease" || it.path == ":app:assembleRelease"
    }
    if (needsReleaseSigning) {
        check(keystorePropertiesFile.exists()) {
            "Release signing requires keystore.properties at the project root. " +
                "Copy keystore.properties.example and fill in your upload key."
        }
        val storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
        check(storeFile.exists()) {
            "Keystore not found at ${storeFile.absolutePath} (storeFile in keystore.properties)."
        }
    }
}