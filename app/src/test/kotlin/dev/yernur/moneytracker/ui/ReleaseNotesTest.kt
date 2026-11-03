package dev.yernur.moneytracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun githubGeneratedNotes_becomeHeadingBulletsAndParagraph() {
        val lines = parseNotes("## What's Changed\n* feat: a **bold** [link](https://x) by @me in https://x/pull/1\n\n**Full Changelog**: https://x/compare/a...b")
        assertEquals(listOf(NoteLine.Kind.Heading, NoteLine.Kind.Bullet, NoteLine.Kind.Paragraph), lines.map { it.kind })
        assertEquals("What's Changed", lines[0].text.text)
        assertEquals("feat: a bold link by @me in https://x/pull/1", lines[1].text.text)
        assertEquals("Full Changelog: https://x/compare/a...b", lines[2].text.text)
    }

    @Test
    fun emptyNotes_giveNoLines() = assertEquals(0, parseNotes("").size)
}
