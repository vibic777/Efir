package ru.efir.core.files

/**
 * Безопасная работа с путями: нормализация, проверка вложенности в доверенные
 * корни и валидация имён. Чистая логика (без I/O) — легко тестируется.
 *
 * Канонический разделитель — `/`. Обратные слэши (`\`) приводятся к `/`,
 * чтобы одинаково обрабатывать Windows- и Unix-пути.
 */
object PathSanitizer {

    /** Нельзя использовать в имени файла/каталога. */
    private val FORBIDDEN_NAME_CHARS = setOf('/', '\\', '\u0000')

    /**
     * Приводит путь к каноническому виду: единый разделитель `/`,
     * сворачивает `.` и `..`, убирает дублирующие и замыкающие слэши.
     *
     * Абсолютность (`/`/`C:/`) сохраняется. Для относительного пути
     * ведущие `..` сохраняются как есть (их некуда сворачивать).
     */
    fun normalize(path: String): String {
        val unified = path.replace('\\', '/')
        val isAbsolute = unified.startsWith("/") || ABSOLUTE_DRIVE.matches(unified)
        val parts = unified.split('/').filter { it.isNotEmpty() && it != "." }

        val stack = ArrayDeque<String>()
        for (part in parts) {
            if (part == "..") {
                if (stack.isNotEmpty() && stack.last() != "..") {
                    stack.removeLast()
                } else if (!isAbsolute) {
                    stack.addLast("..")
                }
                // Для абсолютного пути `..` выше корня игнорируется.
            } else {
                stack.addLast(part)
            }
        }

        val body = stack.joinToString("/")
        return when {
            isAbsolute && body.isEmpty() -> if (unified.startsWith("/")) "/" else drivePrefix(unified)
            isAbsolute -> {
                val prefix = if (unified.startsWith("/")) "/" else drivePrefix(unified)
                prefix + body
            }
            else -> body
        }
    }

    /** Лежит ли [path] внутри [root] (или совпадает с ним). */
    fun isWithin(root: String, path: String): Boolean {
        val r = normalize(root).trimEnd('/')
        val p = normalize(path).trimEnd('/')
        return p == r || p.startsWith("$r/")
    }

    /** Возвращает первый корень, которому принадлежит [path], либо `null`. */
    fun findRoot(roots: List<String>, path: String): String? =
        roots.firstOrNull { isWithin(it, path) }

    /**
     * Проверяет, что [path] содержится хотя бы в одном из [roots].
     * Бросает [IllegalArgumentException] иначе.
     */
    fun requireWithin(roots: List<String>, path: String): String {
        val normalized = normalize(path)
        require(findRoot(roots, normalized) != null) {
            "Путь вне разрешённых корней: $path"
        }
        return normalized
    }

    /** Строит путь к дочернему элементу [parent] с именем [name]. */
    fun child(parent: String, name: String): String {
        val base = normalize(parent)
        return if (base.isEmpty() || base == "/") "$base$name" else "$base/$name"
    }

    /** Имя родительского каталога (без замыкающего слэша). */
    fun parent(path: String): String {
        val normalized = normalize(path).trimEnd('/')
        val idx = normalized.lastIndexOf('/')
        return when {
            idx < 0 -> ""
            idx == 0 -> "/"
            else -> normalized.substring(0, idx)
        }
    }

    /** Последний сегмент пути (имя). */
    fun name(path: String): String = normalize(path).trimEnd('/').substringAfterLast('/')

    /** Валидно ли имя для переименования/создания (без разделителей и `.`/`..`). */
    fun isValidName(name: String): Boolean =
        name.isNotEmpty() &&
            name != "." &&
            name != ".." &&
            name.none { it in FORBIDDEN_NAME_CHARS }

    /** Проверяет имя и бросает [IllegalArgumentException], если оно некорректно. */
    fun requireValidName(name: String): String {
        require(isValidName(name)) { "Недопустимое имя: $name" }
        return name
    }

    private val ABSOLUTE_DRIVE = Regex("^[A-Za-z]:/")

    private fun drivePrefix(path: String): String = path.substring(0, 3) // "C:/"
}
