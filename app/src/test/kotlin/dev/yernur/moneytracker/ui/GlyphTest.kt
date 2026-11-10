package dev.yernur.moneytracker.ui

import androidx.compose.ui.graphics.vector.PathParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GlyphTest {
    @Test
    fun everyGlyphParsesIntoANonEmptyPath() {
        for (name in AppIcons.names) {
            val glyph = AppIcons.glyph(name)
            assertTrue("$name has no body", glyph.body.isNotBlank() || glyph.detail.isNotBlank())
            for (data in listOf(glyph.body, glyph.detail).filter { it.isNotBlank() }) {
                val nodes = PathParser().parsePathString(data).toNodes()
                assertTrue("$name: path data does not parse: $data", nodes.size > 1)
                assertTrue("$name: path is empty", !PathParser().parsePathString(data).toPath().isEmpty)
            }
        }
    }

    @Test
    fun theSetHasNinetyNineCategoryIcons_withTheNewOnesAddedAfterTheBackupOnes() {
        assertEquals(99, AppIcons.names.size)
        assertEquals("mustache", AppIcons.names[31])
        assertEquals("tenge", AppIcons.names[32])
        assertTrue(AppIcons.names.containsAll(listOf("coins", "salary", "fuel", "pharmacy", "calendar")))
    }

    @Test
    fun theSetCoversEveryNameTheBackupUses_andUnknownNamesFallBackToTheQuestionMark() {
        val backupNames = listOf(
            "prepaid", "purse", "present", "transport", "bank", "cafe", "other", "products", "return", "drinks", "girl", "family", "home",
            "heart", "haircut", "clothes", "camping", "mobile", "tax", "education", "sport", "travels", "mustache", "piggy_bank", "cash",
            "dollar", "deposit",
        )
        assertTrue("missing: ${backupNames - AppIcons.names.toSet()}", AppIcons.names.containsAll(backupNames))
        assertEquals(AppIcons.glyph("other").body, AppIcons.glyph("a_future_icon").body)
        assertEquals(AppIcons.names.size, AppIcons.names.toSet().size)
    }
}
