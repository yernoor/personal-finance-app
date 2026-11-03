package dev.yernur.moneytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** One block of a GitHub release description: a heading, a bullet or a paragraph line. */
class NoteLine(val kind: Kind, val text: AnnotatedString) {
    enum class Kind { Heading, Bullet, Paragraph }
}

private val INLINE = Regex("""\*\*(.+?)\*\*|`(.+?)`|\[(.+?)]\((.+?)\)""")

/** Bold, inline code and links (shown as their text) are the only Markdown that GitHub's generated notes and ours use. */
fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(at, m.range.first))
        when {
            m.groups[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            m.groups[2] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(m.groupValues[2]) }
            else -> append(m.groupValues[3])
        }
        at = m.range.last + 1
    }
    append(text.substring(at))
}

fun parseNotes(markdown: String): List<NoteLine> = markdown.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
    when {
        line.startsWith("#") -> NoteLine(NoteLine.Kind.Heading, inlineMarkdown(line.trimStart('#').trim()))
        line.startsWith("- ") || line.startsWith("* ") -> NoteLine(NoteLine.Kind.Bullet, inlineMarkdown(line.drop(2).trim()))
        else -> NoteLine(NoteLine.Kind.Paragraph, inlineMarkdown(line))
    }
}

@Composable
fun ReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (line in parseNotes(markdown)) when (line.kind) {
            NoteLine.Kind.Heading -> Text(line.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            NoteLine.Kind.Bullet -> Text(buildAnnotatedString { append("•  "); append(line.text) }, style = MaterialTheme.typography.bodyMedium)
            NoteLine.Kind.Paragraph -> Text(line.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
