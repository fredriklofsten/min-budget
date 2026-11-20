import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

val ks = Properties().apply { val f = rootProject.file("keystore.properties"); if (f.exists()) f.inputStream().use { load(it) } }

android {
    namespace = "se.minbudget.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "se.minbudget.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }
    signingConfigs {
        create("release") {
            if (ks.isNotEmpty()) {
                storeFile = rootProject.file(ks.getProperty("storeFile"))
                storePassword = ks.getProperty("storePassword")
                keyAlias = ks.getProperty("keyAlias")
                keyPassword = ks.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
}
