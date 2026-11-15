plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The GeckoView version. CI looks up the newest stable build on Mozilla maven and passes it as -PgeckoVersion, so this fallback is only
// for a local build. After the first green build the exact version is pinned in gradle.properties.
val geckoVersion = (findProperty("geckoVersion") as String?) ?: "160.0.+"

// The real app is arm64 only. CI also builds the same code for an x86_64 emulator (-PtestAbi=x86_64) to smoke-test it.
val abi = (findProperty("testAbi") as String?) ?: "arm64-v8a"

// Release signing comes from the environment (CI stores the key as a secret). Without it the APK stays unsigned, so nothing signed with a
// public key can ever ship by accident.
val ksPath: String? = System.getenv("SHEER_KEYSTORE")

android {
    namespace = "com.killswitch.sheer"
    // GeckoView 160 is built against Android 37.2 and refuses anything older. The runner image already has that platform.
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.killswitch.sheer"
        minSdk = 26
        targetSdk = 34
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = 100 + (run ?: 0)
        versionName = "0.1." + (run ?: "dev")
        manifestPlaceholders["appLabel"] = "Sheer"
        // Only CI's second emulator run changes this (-PtestTop=true), to check the layout with the address bar at the top.
        resValue("bool", "default_address_top", (findProperty("testTop") as String?) ?: "false")
        // The Galaxy A52 is 64-bit ARM. One engine build keeps the APK smaller.
        ndk { abiFilters += abi }
    }

    compileOptions {
        // GeckoView uses some Java 17 APIs.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (ksPath != null) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("SHEER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SHEER_KEY_ALIAS") ?: "sheer"
                keyPassword = System.getenv("SHEER_KEY_PASSWORD") ?: System.getenv("SHEER_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (ksPath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true }

    packaging {
        // Firefox itself ships its native libraries this way.
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += arrayOf("DebugProbesKt.bin", "kotlin-tooling-metadata.json", "kotlin/**", "META-INF/*.version", "META-INF/**/LICENSE.txt")
        }
    }
    lint { checkReleaseBuilds = false }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview-$abi:$geckoVersion")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.12.0")
    implementation("org.jetbrains.compose.ui:ui:1.12.0")
    implementation("org.jetbrains.compose.ui:ui-graphics:1.12.0")
    implementation("org.jetbrains.compose.material:material-ripple:1.12.0")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
}
