import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose.compiler)
}

// Ключи подписи лежат в папке private/ в корне проекта — она в .gitignore и в Git не попадает.
//  - private/signing/debug.keystore — ключ проекта: им подписаны все выдаваемые APK (и debug, и release),
//    поэтому новая сборка ставится поверх старой с любого компьютера, где лежит этот файл;
//  - private/keystore.properties — отдельный релизный ключ для публикации (необязателен; если он есть,
//    release подписывается им). Путь storeFile в нём — относительно папки private/.
// Без обоих файлов debug подписывается ключом этой машины, а release собирается неподписанным.
val privateDir = rootProject.file("private")
val projectKey = privateDir.resolve("signing/debug.keystore").takeIf { it.isFile }
val keystorePropertiesFile = privateDir.resolve("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKey = keystoreProperties.getProperty("storeFile") != null

// Версия — из version.properties в корне проекта.
val releaseInfo = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }

android {
    namespace = "ru.efir.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.efir.android"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseInfo.getProperty("android.code").toInt()
        versionName = releaseInfo.getProperty("version")
        // Имя APK: Efir-<версия>-<тип сборки>.apk
        setProperty("archivesBaseName", "Efir-$versionName")
    }

    signingConfigs {
        projectKey?.let { key -> getByName("debug") { storeFile = key } }
        if (hasReleaseKey) {
            create("release") {
                storeFile = privateDir.resolve(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8/обфускация: ужимаем код и ресурсы в релизе.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Релизный ключ, если он задан; иначе ключ проекта; без обоих — неподписанный APK.
            signingConfig = when {
                hasReleaseKey -> signingConfigs.getByName("release")
                projectKey != null -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}

// Релизный APK сразу попадает в dist/ в корне проекта.
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(":collectDist") }
