package ru.efir.core.sync

import ru.efir.core.protocol.SyncRuleConfig

/**
 * Фильтр файлов/каталогов для правила синхронизации.
 *
 * Поддерживает маски включения/исключения в стиле glob (`*`, `?`, `**`) и
 * ограничения по размеру. Сопоставление ведётся по относительному пути с
 * разделителем `/`. Несколько сегментов матчатся через `**` (любая глубина),
 * `*` — в пределах одного сегмента.
 *
 * Правила применения:
 *  - если задан хотя бы один include-шаблон, путь должен совпасть с любым из них;
 *  - если путь совпал с исключающим шаблоном — он отбрасывается;
 *  - файлы вне [minFileSize]..[maxFileSize] отбрасываются (каталоги не проверяются).
 *
 * Также всегда игнорируются временные/системные файлы (`.efirpart`, `*.tmp`,
 * `~$*`, `.DS_Store`, `Thumbs.db`).
 */
class PathFilter private constructor(
    private val include: List<Glob>,
    private val exclude: List<Glob>,
    private val minFileSize: Long?,
    private val maxFileSize: Long?,
) {

    /** Совпадает ли относительный путь [relativePath] с фильтром. */
    fun accepts(relativePath: String, directory: Boolean = false, size: Long = 0): Boolean {
        if (relativePath.isBlank()) return false
        if (isTemporary(relativePath)) return false
        if (exclude.any { it.matches(relativePath) }) return false
        if (include.isNotEmpty() && !include.any { it.matches(relativePath) }) {
            // Каталоги не отбрасываем по include — иначе не обойти дерево.
            if (!directory) return false
        }
        if (!directory) {
            if (minFileSize != null && size < minFileSize) return false
            if (maxFileSize != null && size > maxFileSize) return false
        }
        return true
    }

    /** Отбрасывать ли каталог целиком (быстрая проверка без обхода детей). */
    fun excludesDirectory(relativePath: String): Boolean {
        if (relativePath.isBlank()) return false
        if (isTemporary(relativePath)) return true
        return exclude.any { it.matches(relativePath) }
    }

    /** Временный/системный файл, который никогда не синхронизируется. */
    private fun isTemporary(relativePath: String): Boolean {
        val name = relativePath.substringAfterLast('/')
        return name.endsWith(".efirpart") ||
            name.endsWith(".tmp") ||
            name.startsWith("~$") ||
            name == ".DS_Store" ||
            name == "Thumbs.db"
    }

    companion object {
        /** Строит фильтр из конфигурации правила. */
        fun from(rule: SyncRuleConfig): PathFilter = PathFilter(
            include = rule.includePatterns.mapNotNull { Glob.compile(it) },
            exclude = rule.excludePatterns.mapNotNull { Glob.compile(it) },
            minFileSize = rule.minFileSize,
            maxFileSize = rule.maxFileSize,
        )

        /** Фильтр, пропускающий всё (кроме временных файлов). */
        val ACCEPT_ALL: PathFilter = PathFilter(emptyList(), emptyList(), null, null)
    }
}

/**
 * Скомпилированный glob-шаблон.
 *
 * `**` — любое число сегментов (включая ноль), `*` — любое число символов
 * в пределах сегмента, `?` — один символ. Разделитель — `/`.
 */
internal class Glob private constructor(private val regex: Regex) {

    fun matches(path: String): Boolean = regex.matches(path)

    companion object {
        /** Компилирует шаблон или возвращает `null`, если он пуст. */
        fun compile(pattern: String): Glob? {
            val trimmed = pattern.trim().replace('\\', '/')
            if (trimmed.isEmpty()) return null
            return Glob(Regex(buildRegex(trimmed)))
        }

        /**
         * Преобразует glob в регулярное выражение (посегментно, разделитель `/`).
         *
         * Обработка сегмента `**` (двойная звёздочка):
         *  - единственный сегмент — любые символы (`.*`);
         *  - в начале — ноль или более каталогов (`**\/` не требует слэша после);
         *  - в конце — сам путь и его содержимое;
         *  - в середине — необязательные промежуточные каталоги.
         *
         * Внутри обычного сегмента `*` — любые символы, кроме `/`, `?` — один символ.
         */
        private fun buildRegex(pattern: String): String {
            val segments = pattern.split('/')
            val builder = StringBuilder("^")
            var skipSlash = false

            for (k in segments.indices) {
                val segment = segments[k]
                val isFirst = k == 0
                val isLast = k == segments.lastIndex

                if (segment == "**") {
                    when {
                        isFirst && isLast -> builder.append(".*")
                        isFirst -> {
                            builder.append("(?:.*/)?")
                            skipSlash = true
                        }

                        isLast -> builder.append("(?:/.*)?")

                        else -> {
                            builder.append("/(?:.*/)?")
                            skipSlash = true
                        }
                    }
                    continue
                }

                if (!isFirst && !skipSlash) builder.append('/')
                skipSlash = false
                builder.append(translateSegment(segment))
            }

            builder.append("$")
            return builder.toString()
        }

        /** Переводит один сегмент (без `/`) в часть регулярного выражения. */
        private fun translateSegment(segment: String): String {
            val builder = StringBuilder()
            for (c in segment) {
                when {
                    c == '*' -> builder.append("[^/]*")
                    c == '?' -> builder.append("[^/]")
                    c in "\\.^$|()[]{}+" -> builder.append('\\').append(c)
                    else -> builder.append(c)
                }
            }
            return builder.toString()
        }
    }
}
