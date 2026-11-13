plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Version aus dem Git-Tag (Release-Workflow setzt VERSION_NAME): Datum des Releases Jahr.Monat.Tag, z. B. v26.11.12
// (zweites Release am selben Tag: v26.11.12.1); sonst die hier eingetragene.
val versionAusTag: String? = System.getenv("VERSION_NAME")?.removePrefix("v")?.takeIf { it.matches(Regex("\\d+\\.\\d+\\.\\d+(\\.\\d+)?")) }
val versionName0 = versionAusTag ?: "26.11.13"
val versionCode0 = versionName0.split(".").map { it.toInt() }.let { p -> p[0] * 1_000_000 + p[1] * 10_000 + p[2] * 100 + p.getOrElse(3) { 0 } }

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
            buildConfigField("String", "UNSPLASH_API", "\"http://127.0.0.1:8766/unsplash\"")
            buildConfigField("String", "COMMONS_API", "\"http://127.0.0.1:8766/commons/api.php\"")
        }
        release {
            buildConfigField("String", "UPDATE_API", "\"https://api.github.com/repos/Teyro/DammLauncher/releases/latest\"")
            buildConfigField("String", "UNSPLASH_API", "\"https://api.unsplash.com\"")
            buildConfigField("String", "COMMONS_API", "\"https://commons.wikimedia.org/w/api.php\"")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
        // QR-Muster für die Gegenprobe mit einem fremden Decoder ausgeben (siehe QrCodeTest)
        unitTests.all { t -> System.getenv("QR_AUSGABE")?.let { t.systemProperty("qrAusgabe", it) } }
    }
    packaging { resources.excludes += listOf("META-INF/*.version", "kotlin/**", "DebugProbesKt.bin") }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
