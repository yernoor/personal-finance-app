package dev.yernur.moneytracker.data.feedback

import java.net.URLEncoder

/** What a report is about; [label] is the GitHub issue label (created on first use by the repo owner, ignored for others). */
enum class FeedbackKind(val title: String, val label: String) {
    Bug("Bug", "bug"),
    Idea("Idea", "enhancement"),
    Crash("Crash", "crash"),
}

/** GitHub refuses very long query strings (about 8 KB), so the body is cut well below that. */
private const val MAX_BODY = 5000

/** Version and device lines appended to every report; the only facts about the phone that leave it, and no ledger data. */
fun deviceInfo(appVersion: String, androidRelease: String, sdk: Int, model: String) =
    "App $appVersion, Android $androidRelease (API $sdk), $model"

/** A "new issue" page of [repo] with the form already filled in; the person reviews it and presses Submit on GitHub. */
fun issueUrl(repo: String, kind: FeedbackKind, title: String, details: String, info: String): String {
    val body = "$details\n\n---\n$info".take(MAX_BODY)
    return "https://github.com/$repo/issues/new?labels=${encode(kind.label)}&title=${encode(title)}&body=${encode(body)}"
}

/** URLEncoder writes a space as "+", which GitHub's form would show literally in a title. */
private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
