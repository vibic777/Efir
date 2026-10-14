package ru.efir.core.files

import android.os.Environment
import ru.efir.core.media.EfirAndroidEnvironment

actual fun defaultFileRoots(): List<String> {
    val roots = mutableListOf<String>()

    runCatching {
        Environment.getExternalStorageDirectory()?.absolutePath?.let { roots.add(it) }
    }
    // Внутренняя папка приложения корнем не становится никогда: в ней токены доверия.
    EfirAndroidEnvironment.appContext?.let { context ->
        context.getExternalFilesDir(null)?.absolutePath?.let { roots.add(it) }
    }
    return roots.distinct()
}

actual fun protectedFilePaths(): List<String> {
    val context = EfirAndroidEnvironment.appContext ?: return emptyList()
    return listOfNotNull(
        context.applicationInfo?.dataDir,
        context.filesDir?.parentFile?.absolutePath,
    ).distinct()
}

actual fun localBrowseRoots(): List<String> {
    val roots = mutableListOf<String>()
    runCatching { Environment.getExternalStorageDirectory()?.absolutePath?.let { roots.add(it) } }
    // Карта памяти и другие тома: их корень — часть пути папки приложения до «/Android/».
    EfirAndroidEnvironment.appContext?.let { context ->
        runCatching {
            context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
                val path = dir.absolutePath
                val at = path.indexOf("/Android/")
                if (at > 0) roots.add(path.substring(0, at))
            }
        }
    }
    return roots.distinct()
}
