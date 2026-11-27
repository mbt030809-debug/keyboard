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

val fixedKeystoreFile = file("fixed-release.keystore")
if (!fixedKeystoreFile.exists()) {
    fixedKeystoreFile.writeBytes(
        Base64.getDecoder().decode(
            "MIIKjgIBAzCCCjgGCSqGSIb3DQEHAaCCCikEggolMIIKITCCBbgGCSqGSIb3DQEHAaCCBakEggWlMIIFoTCCBZ0GCyqGSIb3DQEMCgECoIIFQDCCBTwwZgYJKoZIhvcNAQUNMFkwOAYJKoZIhvcNAQUMMCsEFIH+lHCZZ++46d+QAiAJphg/GZzgAgInEAIBIDAMBggqhkiG9w0CCQUAMB0GCWCGSAFlAwQBKgQQ5KHmls8tPn6QZxRVm822WgSCBNDncDUq3s3K+BGRCT9T6qO5aTEyQWGW2VzsDta5OT8vYGsNLqzixYz9IOQIHBAM4TXM4a7d/vu53LE4DybDeiBYOe3k+9SHZyF/yQVHSbvuts9Wd49YYxtEOWg+C84LcGLniAIcIumVaewVDzzWKy76fWPfV4xq5hq5If/hJ/Ag2B22LVrfIKbOe5My9cueaPznLdEtaoalwdAaVaav6eM1XIl95JSnUcGGa4lbbJioCN6h6ZmW3/ws6MSsr853tMZ6XfCjpFgv6l5Ao8ER7t/2ZC59hhTcIhcKCkYvS4Jo3/DfoAl/s5fCOx/lKLYFgoYKg+cc4rQ4PGA0CWfLdGW3TJIjMw716ahNPwTaogkHGcbcFcALTxqfCzP84ZD+kPRw78QOhuSUF9hVTLP+rYaH+JTP/q42cs6s9MYyU7AeWviSOZha9l//agrha8Nnp97t8THmIFEls5xLLcp8IU+CxlpIEb2biW+VqgKV2v8NS3GvSOjTziCYg75+4wYxv9/OjCUQUK/0E8teitZe3rLdKXY1yL8ygTD7qlBgC69/RqrY8uCbX8GIXAE/ppYow3WCuHjM5GC7w1DI8WiindhG0wT6erWhAEb5xEr1H5Iljn18bV1MQnhYAg9NpPsSNCfjqnodrNfoTXfY9KHGcSE1iUrpO/sbNSivZS6NR6RlP9jHr4Y0B0aRqm9imC398sef7M/ssJUYa5kfy4HzpmeF+UvKgiEUdbFvRL5d7fmNiL8vGjMNrvMn+AM2dUYfuTALXa4x/lT8lKTPQkPWDsxL68xE+5bdpbMa3J3m4Z+xUDH16/lWGB6kiSDvmuQkOJ0ZSQSdy0f4s138UBikifI0P7NiBuS4gG8CkoXoAhP+bZ4ZFDtoLR5f9/BwC1fnAFoMTPdebawFRHZhIVyFY2tiOSpH1diZDgHIcIF1bgez7EHSOaCw0c6Fy7TpSxZ5vPwI5uH7NUa2pFvH8304BQQncJULnVJTVu2GBMw30BryxglbwJ+Ju6CnfoyD8Bf7YubPTu6sUMW7gckTGbGXCnsVNp0PlIzWVZl/OPZvCH7xo9m+JlzVGfJvaf9e6to/trrBB+E6abp50W+72XE2hRtHTRGNiF/Ltdn1VDwpZ+DtJ8+VhU+mlmUCXJhhqKqHzEI+2M+q4b3jmFlX3MxSHEBNIZBJibqSEpFLFgkmDOR/Oboq8SX5wQ6oG5EOXJaJnPsdWHgZRGRfuqXYLr7ojBye0C9hLLnPCkx1doVQ4PklBEsEKCtrQBWC9L8JCCX410z1XgvduUKfIPxbE3ojvaWtiGN8ARakTX0BgAGIdcp43RjDFvEmgemewodobChlo41PIPIH2vdmxeqzA47ZII0DHhe/UD3z+A2INfNSF++CozoTova6PDeesC8G2Tw3ohgMmfT97doH48XY5clnJt9pRPIHuBW7Rp5LTWdpZfY4rooPI4ZdtyMWKVu9dv2BFj5paWOcyCu3dJaKIvDgDi6y6P/g1mAeR0w2wuWdRgNJAGy8MwsDhGwH62hHCaJtQno/BuUmsq1GnheCVtBPVxZFPrHcq+rtv7wVZfSgxzqbZZpurQz7RVAGj76Po5Dkcn9bcTCc4wNIk7gzg8mOeOonA5s3ud1R7ze2diOzAiWNRTFKMCUGCSqGSIb3DQEJFDEYHhYAbQBiAHQAawBlAHkAYgBvAGEAcgBkMCEGCSqGSIb3DQEJFTEUBBJUaW1lIDE3OTU3OTc4MTQzNzEwggRhBgkqhkiG9w0BBwagggRSMIIETgIBADCCBEcGCSqGSIb3DQEHATBmBgkqhkiG9w0BBQ0wWTA4BgkqhkiG9w0BBQwwKwQUnq6ZY2yI8tgadEHBr41yg5Pxin8CAicQAgEgMAwGCCqGSIb3DQIJBQAwHQYJYIZIAWUDBAEqBBAj4tut2bNq1syXR7KLsUcEgIID0DQucJ+OOUeGtp9z8sBRdkFevnUvbt48JNJwMQwOj7UmgKFJyFpdiebOc2ZVWlsbR7jZ43ZSU58I7GwSIkPlYgMNorrcpmYG1VOpkVGQH1CR+HHmhwcAHO1GQekLIygpglvArXHJ9Hhl8FUzGNEbU/O5hfaVsYInF8/d/3KCv16TMJKFOxqiF+yP/0ryomiWI+nsbSGw0AMORkDzIb6WiPuoZYF0+g/XvnCWspL/0NLknmiuscjrRoyvoisMV5wWKTB4HX3xEjMOPqkm24m1C8XxI4vv62MLzC24weYhFIEheAYjHh8lguyuiaIw0E4mFGTcSAXnBraknCKupdkvgVyFNGnXWhi83VLDK1Qokz+8a7XqFkhcSQHzN82d44AHOGn3nq3KIMspoCm0EhuYk3cNjHcwE12tDzo0jIVgmO0uSGg8fzi16ffoFvbp7JtWFKfBUkyFwdar7WqsW+KLJxMhnG3pzOz1posUukehq0iIhMHqNxD8gR7bkhtkKw0yI6u5bkbn038mfqk+AWK+OdYhaTxZ2cJ6Dd4xNI2D6kkE56NyyYjF/ZbSV8VII3Ly0LCm0FkGurtxm5P6+WMLDrlvegt9Fz2GTBQ32R5XTrV1XrpZW7Gao4UA5fKpG1pTQez39ZjHRd8Zq0qG3Qjj0yND241yw0WyYuKVY3lFpyXtnbmwobEJZOJ6KvgPbXs8i4SVyjILRbeSCSDLI3Jzixt4PCj99kKMf6ZwDQH+YTCBOuAvboQ/51xzQCDmcDFHWcco8LMqjbq+OwZ07S/MvIx94Z5IPnLpgVFNDpEh26RbJjJveWpDbB0rahH9v/6IeDgf4qlKF61EpeSJufwNxRH3yqbO6JKK9kF0L3cB/VXU1dBAIOhjzG5fKk1Iget5PMKQo+Tz2EU23W7zL5NTGlamWPYiPHffabl+WkY39DVOtTb5GrMfpv0RAoi5X/IYgCUOKxwsTYQ9V/yMYm8Z7MiUMO5Qvne/JYtDyhs1y9lsYP8mHUoAuxGwPlBHgxSYruolhKRfZTfARcYLIDf1ORKa/JlyrtZs/CHHadaFLOje1a5cJ2f6vpieNge0lbQhY90uOjkDC1OX94RXJSR6lzJrbbdO3UmF9ORVJ8RkjKMFIQspNXyFxangMOClahqfxtfCVU3ZHwRie+S8A2TJMUm9xBVTzsNgm27UuwVZZSUdBengWiHWRoJsZSZQ6KVHdRKna8Wf79yWQwkE5buUWw58DpOGrLphUFdOUf6hYmQaGwr9xTcK16jluAoPyg+aNqIYZL1Yaj9PHaL0vqD7cgcwTTAxMA0GCWCGSAFlAwQCAQUABCC9VDWljb2p4jSkXpVvdKKrQKEh+WQV3qsyneCfYrUijwQUfHlY+TRHH/OgsohBJErPu29HkBcCAicQ"
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
            storePassword = "mbtkey2026"
            keyAlias = "mbtkeyboard"
            keyPassword = "mbtkey2026"
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
