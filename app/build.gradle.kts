import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 빌드 번호는 app/version.properties 에서 읽는다
val buildNumber = Properties()
    .apply { file("version.properties").inputStream().use { load(it) } }
    .getProperty("build").toInt()

val fixedKeystoreFile = file("fixed-debug.keystore")
if (!fixedKeystoreFile.exists()) {
    fixedKeystoreFile.writeBytes(
        Base64.getDecoder().decode(
            "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"
        )
    )
}

android {
    namespace = "com.mbt.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mbt.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = fixedKeystoreFile
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = false
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // 로컬 환경에서 Maven Central 직접 다운로드가 제한될 때를 대비한 미러
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
            it.systemProperty("robolectric.dependency.repo.id", "gcs-mirror")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
