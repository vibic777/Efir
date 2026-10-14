package ru.efir.core.comms

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/** Буфер обмена на Desktop через AWT Toolkit. */
private class AwtLocalClipboard : LocalClipboard {

    override fun getText(): String? = runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return null
        clipboard.getData(DataFlavor.stringFlavor) as? String
    }.getOrNull()

    override fun setText(text: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
    }
}

actual fun createLocalClipboard(): LocalClipboard = AwtLocalClipboard()
