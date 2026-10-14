import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.jbr.api)
}

// Версия — из version.properties в корне проекта.
val appVersion: String = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }.getProperty("version")

// Приложение упаковывается со средой JetBrains Runtime: только она даёт сделать верхнюю панель
// приложения заголовком окна (WindowChrome). Если её на машине нет, берётся JDK сборки —
// приложение работает так же, но с обычным системным заголовком.
val jbrHome: String? = runCatching {
    javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }.get().metadata.installationPath.asFile.absolutePath
}.getOrNull()

compose.desktop {
    application {
        if (jbrHome != null) javaHome = jbrHome
        // Skia загружает свою нативную библиотеку; JDK 24+ предупреждает, если доступ не разрешён.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        mainClass = "ru.efir.desktop.MainKt"

        nativeDistributions {
            // MSI — основной инсталлятор; EXE — автономный установщик.
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Efir"
            packageVersion = appVersion
            // WiX 3.11 (jpackage) не принимает не-ASCII символы в метаданных MSI,
            // поэтому описание/правообладатель — только ASCII.
            description = "Efir - AirDroid-like remote control of Android from Windows over LAN"
            vendor = "Efir"
            copyright = "(c) 2026 Efir"

            // Включённая JRE — установщик не требует установленной Java.
            includeAllModules = true

            windows {
                // Идентификатор продукта для обновлений/удаления (стабильный).
                upgradeUuid = "8f7c1e90-1d2b-4a52-9c3e-5b6a7c8d9e0f"
                // Ярлыки в меню «Пуск» и на рабочем столе.
                menuGroup = "Efir"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Значок установщика, ярлыков и exe — тот же знак, что в приложении.
                iconFile.set(project.file("icons/efir.ico"))
            }
        }
    }
}

// Переносимая сборка (папка с Efir.exe и встроенной JRE) — архивом в dist/.
val zipDistributable by tasks.registering(Zip::class) {
    // Файлы прежней версии из dist/ уходят: там лежит только текущий релиз.
    doFirst {
        project.delete(fileTree(rootProject.file("dist")) { include("Efir-*"); exclude("Efir-$appVersion*") })
    }
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveFileName.set("Efir-$appVersion-windows.zip")
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("dist"))
}
tasks.matching { it.name == "createDistributable" }.configureEach { finalizedBy(zipDistributable) }

// ---- Установщики MSI и EXE: галка «Запустить Efir» на последней странице, языки — английский и русский ----

/** Языки установщика: культура (имя файла в installer/loc) и номер языка Windows. Первый — основной. */
val installerLanguages = listOf("en-us" to 1033, "ru-ru" to 1049)

/** Запускает инструмент сборки; при ошибке падает с хвостом его вывода. */
fun runTool(workDir: File, log: File, vararg command: String) {
    log.parentFile.mkdirs()
    val process = ProcessBuilder(*command).directory(workDir).redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(log)).start()
    check(process.waitFor(20, TimeUnit.MINUTES)) { process.destroyForcibly(); "${command.first()} did not end" }
    check(process.exitValue() == 0) {
        "${File(command.first()).name} failed (${process.exitValue()}), see $log:\n" + log.readLines().takeLast(15).joinToString("\n")
    }
}

val powershell = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\WindowsPowerShell\\v1.0\\powershell.exe").absolutePath
// Шаблон installer/main.wxs взят из jpackage JDK 25 (JetBrains Runtime) — им же собраны папка приложения и установщик.

/**
 * Установщик, собранный jpackage из готовой папки приложения (`createDistributable`) со своими файлами WiX из `installer/`.
 * Задачи плагина `packageMsi` / `packageExe` свои файлы не принимают: они очищают папку ресурсов прямо перед jpackage.
 *
 * Языки: jpackage собирает установщик на английском. Задача MSI затем связывает тот же установщик на русском (WiX light),
 * берёт разницу как языковое преобразование (torch) и кладёт его в английский MSI (`embed-languages.ps1`): Windows сама
 * покажет русский на русской системе и английский на остальных. В EXE-установщике лежит MSI; он заменяется этим же
 * двуязычным (`replace-msi.ps1`), так что оба установщика одинаковы.
 */
fun registerInstaller(name: String, type: String) = tasks.register<Exec>(name) {
    group = "release"
    description = "Installer: Efir-<version>.$type with the Launch check box, English and Russian"
    dependsOn("createDistributable", ":unzipWix")
    val appImage = layout.buildDirectory.dir("compose/binaries/main/app/Efir")
    val out = layout.buildDirectory.dir("installer/$type")
    val res = layout.buildDirectory.dir("installer-res/$type")
    val temp = layout.buildDirectory.dir("installer-tmp/$type")
    val languageWork = layout.buildDirectory.dir("installer-languages")
    val wix = rootProject.layout.buildDirectory.dir("wix311")
    val msi = layout.buildDirectory.file("installer/msi/Efir-$appVersion.msi")
    inputs.dir(project.file("installer"))
    inputs.dir(appImage)
    outputs.dir(out)
    doFirst {
        out.get().asFile.deleteRecursively()
        out.get().asFile.mkdirs()
        // jpackage нужна временная папка, которой ещё нет.
        temp.get().asFile.deleteRecursively()
        temp.get().asFile.parentFile.mkdirs()
        val resDir = res.get().asFile.apply { deleteRecursively(); mkdirs() }
        project.file("installer/main.wxs").copyTo(File(resDir, "main.wxs"))
        // Основной язык — под именем английского файла самого jpackage, который он заменяет (один файл строк на культуру).
        project.file("installer/loc/${installerLanguages.first().first}.wxl").copyTo(File(resDir, "MsiInstallerStrings_en.wxl"))
        environment("PATH", wix.get().asFile.absolutePath + File.pathSeparator + System.getenv("PATH"))
        executable = File(checkNotNull(jbrHome) { "JetBrains Runtime 25 is needed to build the installer" }, "bin/jpackage.exe").absolutePath
    }
    executable = "jpackage"
    args(
        "--type", type,
        "--app-image", appImage.get().asFile.absolutePath,
        "--name", "Efir", "--app-version", appVersion, "--vendor", "Efir",
        "--description", "Efir - AirDroid-like remote control of Android from Windows over LAN",
        "--copyright", "(c) 2026 Efir",
        "--icon", project.file("icons/efir.ico").absolutePath,
        "--resource-dir", res.get().asFile.absolutePath,
        "--temp", temp.get().asFile.absolutePath,
        "--win-dir-chooser", "--win-per-user-install", "--win-shortcut", "--win-menu", "--win-menu-group", "Efir",
        // Постоянный: новая версия обновляет установленную, а не ставится рядом.
        "--win-upgrade-uuid", "8f7c1e90-1d2b-4a52-9c3e-5b6a7c8d9e0f",
        "--dest", out.get().asFile.absolutePath,
    )
    if (type == "msi") doLast {
        val wixDir = wix.get().asFile
        val tmp = temp.get().asFile
        val work = languageWork.get().asFile.apply { deleteRecursively(); mkdirs() }
        val log = File(work, "languages.log")
        val transforms = File(work, "mst").apply { mkdirs() }
        val objects = File(tmp, "wixobj").listFiles { f -> f.extension == "wixobj" }!!.map { it.absolutePath }.sorted()
        check(objects.isNotEmpty()) { "jpackage left no compiled installer in $tmp" }
        val base = msi.get().asFile
        installerLanguages.drop(1).forEach { (culture, number) ->
            val localized = File(work, "$culture.msi")
            // Так же, как light запускает сам jpackage, но на другом языке; -cc / -reusecab: файлы пакуются один раз.
            runTool(
                appImage.get().asFile, log,
                File(wixDir, "light.exe").absolutePath, "-nologo", "-spdb", "-sw1076", "-ext", "WixUtilExtension", "-ext", "WixUIExtension",
                "-b", File(tmp, "config").absolutePath, "-sice:ICE27", "-cc", File(work, "cab").absolutePath, "-reusecab",
                "-loc", project.file("installer/loc/$culture.wxl").absolutePath, "-cultures:$culture",
                "-out", localized.absolutePath, *objects.toTypedArray(),
            )
            runTool(
                work, log, File(wixDir, "torch.exe").absolutePath, "-nologo", "-p", "-t", "language",
                base.absolutePath, localized.absolutePath, "-out", File(transforms, "$number.mst").absolutePath,
            )
            localized.delete()
        }
        runTool(
            work, log, powershell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", project.file("installer/embed-languages.ps1").absolutePath,
            "-Msi", base.absolutePath, "-Transforms", transforms.absolutePath,
            "-Languages", installerLanguages.joinToString(",") { it.second.toString() },
        )
        logger.lifecycle("Installer languages: " + installerLanguages.joinToString(" ") { it.first })
        File(work, "cab").deleteRecursively()
    }
    if (type == "exe") doLast {
        val log = File(languageWork.get().asFile, "exe.log")
        runTool(
            out.get().asFile, log, powershell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", project.file("installer/replace-msi.ps1").absolutePath,
            "-Exe", File(out.get().asFile, "Efir-$appVersion.exe").absolutePath, "-Msi", msi.get().asFile.absolutePath,
        )
    }
    // Готовый установщик сразу копируется в dist/.
    finalizedBy(":collectDist")
}

val packageInstaller = registerInstaller("packageInstaller", "msi")
val packageInstallerExe = registerInstaller("packageInstallerExe", "exe")
// EXE берёт внутрь двуязычный MSI.
packageInstallerExe.configure { dependsOn(packageInstaller) }
