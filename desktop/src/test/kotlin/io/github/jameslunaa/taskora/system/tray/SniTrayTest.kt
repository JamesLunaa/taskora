package io.github.jameslunaa.taskora.system.tray

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.image.BufferedImage

class SniTrayTest {
    @Test
    fun menuLayout_numbersItemsFromOne() {
        val layout = SniTray.menuLayout(
            listOf(
                TrayItem.Action("open", "Open Taskora"),
                TrayItem.Toggle("note", "Sticky note", checked = true),
                TrayItem.Separator,
            ),
        )
        assertEquals(0, layout.id)
        assertEquals("submenu", layout.properties["children-display"]?.value)
        val children = layout.children.map { it.value as MenuNode }
        assertEquals(listOf(1, 2, 3), children.map { it.id })
        assertEquals("Open Taskora", children[0].properties["label"]?.value)
        assertEquals("checkmark", children[1].properties["toggle-type"]?.value)
        assertEquals(1, children[1].properties["toggle-state"]?.value)
        assertEquals("separator", children[2].properties["type"]?.value)
        assertEquals(MenuNode.SIGNATURE, layout.children[0].sig)
    }

    @Test
    fun pixmap_isArgbInNetworkOrder() {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, 0xFF112233.toInt()) }
        val pixmap = SniTray.pixmap(image, 1)
        assertEquals(1, pixmap.width)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x11, 0x22, 0x33), pixmap.data)
    }
}
