// Корневой build-файл: только объявление плагинов без применения
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// Готовые релизы копируются в dist/ — чтобы не искать их по папкам сборки.
// Задача запускается сама после сборки релизного APK и установщиков Windows.
tasks.register<Copy>("collectDist") {
    val version = java.util.Properties().apply { file("version.properties").inputStream().use { load(it) } }.getProperty("version")
    // Файлы прежней версии из dist/ уходят: там лежит только текущий релиз. Из папок сборки берётся тоже только он.
    doFirst {
        delete(fileTree("dist") { include("Efir-*"); exclude("Efir-$version*") })
    }
    include("Efir-$version*")
    from("androidApp/build/outputs/apk/release") { include("*.apk") }
    from("desktopApp/build/installer/msi") { include("*.msi") }
    from("desktopApp/build/installer/exe") { include("*.exe") }
    into("dist")
}
