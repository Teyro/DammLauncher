plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Version aus dem Git-Tag (Release-Workflow setzt VERSION_NAME, z. B. 1.2.0); sonst die hier eingetragene.
val versionAusTag: String? = System.getenv("VERSION_NAME")?.removePrefix("v")?.takeIf { it.matches(Regex("\\d+\\.\\d+\\.\\d+")) }
val versionName0 = versionAusTag ?: "1.0.0"
val versionCode0 = versionName0.split(".").let { (a, b, c) -> a.toInt() * 10000 + b.toInt() * 100 + c.toInt() }

android {
    namespace = "de.oejendorferdamm.dammlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.oejendorferdamm.dammlauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = versionCode0
        versionName = versionName0
        // nur Deutsch – spart die Übersetzungen aller Systembibliotheken
        resourceConfigurations += listOf("de")
    }

    // Wird nur in CI über Umgebungsvariablen gesetzt (siehe .github/workflows/release.yml).
    val releaseKeystorePfad = System.getenv("RELEASE_KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystorePfad != null) {
            create("release") {
                storeFile = file(releaseKeystorePfad)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            // Testversion fragt den Test-Server im Emulator statt GitHub (siehe scripts/testserver.py)
            buildConfigField("String", "UPDATE_API", "\"http://127.0.0.1:8766/api/releases/latest\"")
        }
        release {
            buildConfigField("String", "UPDATE_API", "\"https://api.github.com/repos/Teyro/DammLauncher/releases/latest\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystorePfad != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += listOf("META-INF/*.version", "kotlin/**", "DebugProbesKt.bin") }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
