import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

// Версия приложения из version.properties попадает в код как ru.efir.core.BuildInfo.VERSION.
val appVersion: String = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }.getProperty("version")
val buildInfoDir = layout.buildDirectory.dir("generated/buildInfo")
val generateBuildInfo by tasks.registering {
    inputs.property("version", appVersion)
    outputs.dir(buildInfoDir)
    doLast {
        val file = buildInfoDir.get().file("ru/efir/core/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package ru.efir.core\n\n/** Сведения о сборке; файл создаётся из version.properties. */\n" +
                "object BuildInfo {\n    const val VERSION = \"$appVersion\"\n}\n",
        )
    }
}

kotlin {
    jvmToolchain(17)

    androidTarget()
    jvm("desktop")

    // expect/actual object находятся в Beta — подавляем предупреждение компилятора.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generateBuildInfo.map { buildInfoDir })
            dependencies {
                // Compose (общий UI-код)
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material3)
                api(compose.ui)

                // Асинхронность и сериализация
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.datetime)

                // Сеть (Ktor) — общие API транспорта и протокола
                api(libs.ktor.client.core)
                api(libs.ktor.client.websockets)
                api(libs.ktor.server.core)
                api(libs.ktor.server.websockets)
                api(libs.ktor.network)
                api(libs.ktor.network.tls)
            }
        }

        // Общий набор для JVM-таргетов (Android и Desktop):
        // здесь размещаются реализации транспорта на движке CIO.
        val jvmCommon by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.server.cio)
                // Генерация QR-кода для сопряжения (Android и Desktop).
                implementation(libs.zxing.core)
            }
        }

        val androidMain by getting {
            dependsOn(jvmCommon)
        }

        val desktopMain by getting {
            dependsOn(jvmCommon)
            // Шрифты лежат один раз — в ресурсах Android; на ПК они же идут в classpath (font/…).
            resources.srcDir("src/androidMain/res")
            dependencies {
                implementation(libs.kotlinx.coroutines.swing)
                // Чисто-JVM декодер H.264 для приёма видеопотока на ПК.
                implementation(libs.jcodec)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val desktopTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                // Отрисовка экранов в картинку (ImageComposeScene) нужна только тестам.
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "ru.efir.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
