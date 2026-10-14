package ru.efir.core.ui

import ru.efir.core.ui.i18n.EnStrings
import ru.efir.core.ui.i18n.Lang
import ru.efir.core.ui.i18n.RuStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Справка: у каждого раздела есть текст на каждом языке, а поиск находит по словам и показывает окружение. */
class HelpTest {

    @Test
    fun everyTopicHasTitleAndBodyInEveryLanguage() {
        for (lang in Lang.entries) {
            val s = lang.strings
            for (topic in HelpTopic.entries) {
                assertTrue(s.helpTexts[topic.name] != null, "${lang.code}: нет текста раздела ${topic.name}")
                assertTrue(topic.title(s).isNotBlank(), "${lang.code}: пустой заголовок ${topic.name}")
                assertTrue(topic.body(s).length > 80, "${lang.code}: слишком короткий текст ${topic.name}")
                topic.icons.forEach { assertTrue(it.label(s).isNotBlank(), "${lang.code}: значок без слов в ${topic.name}") }
            }
            assertEquals(HelpTopic.entries.size, s.helpTexts.size, "${lang.code}: в текстах есть раздел, которого нет в справке")
        }
    }

    @Test
    fun englishHelpIsNotRussian() {
        for (topic in HelpTopic.entries) {
            assertTrue(topic.body(EnStrings).none { it in 'а'..'я' }, "В английском тексте ${topic.name} остался русский")
        }
    }

    @Test
    fun everyGroupHasSections() {
        HelpGroup.entries.forEach { group ->
            assertTrue(HelpTopic.entries.any { it.group == group }, "У вкладки ${group.name} нет разделов")
        }
    }

    @Test
    fun searchFindsWordWithItsSurroundings() {
        val hits = searchHelp("папка загрузок", RuStrings)
        assertTrue(hits.isNotEmpty())
        // Раздел, в названии которого есть искомое, идёт первым.
        assertEquals(HelpTopic.DOWNLOADS, hits.first().topic)
        val hit = hits.first { it.from >= 0 }
        assertEquals("папка загрузок", hit.snippet.substring(hit.from, hit.to).lowercase())
    }

    @Test
    fun searchFindsAllWordsWhenThePhraseIsAbsent() {
        // Этих слов рядом нет ни в одном разделе, но оба есть в разделе о версии.
        val hits = searchHelp("версия трее", RuStrings)
        assertTrue(hits.any { it.topic == HelpTopic.ABOUT })
    }

    @Test
    fun searchIgnoresCaseAndShortQueries() {
        assertTrue(searchHelp("я", RuStrings).isEmpty())
        assertTrue(searchHelp("ВЕРСИЯ", RuStrings).any { it.topic == HelpTopic.ABOUT })
        assertTrue(searchHelp("version", EnStrings).any { it.topic == HelpTopic.ABOUT })
        assertTrue(searchHelp("qwertyzzz", RuStrings).isEmpty())
    }
}