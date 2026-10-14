package ru.efir.core.files

/**
 * JVM-реализация фабрики файловой системы (Android и Desktop):
 * backend на `java.io.File` с ограничением доверенными корнями.
 */
actual object LocalFileSystemFactory {
    // Корни спрашиваются при каждой операции: папка, добавленная в «Настройках», доступна сразу.
    actual fun create(): FileSystem = LocalFileSystem(rootsProvider = { allowedFileRoots() })
}
