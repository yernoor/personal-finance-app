package dev.yernur.moneytracker.data.update

import dev.yernur.moneytracker.data.sheets.HttpRequest
import dev.yernur.moneytracker.data.sheets.HttpTransport
import org.json.JSONObject

/** The APK attached to a release; [sha256] is GitHub's digest of it (null when the release has none). */
class ApkAsset(val name: String, val url: String, val size: Long, val sha256: String?)

/** [notes] is the release description GitHub holds, as Markdown (empty when there is none). */
class Release(val tag: String, val pageUrl: String, val apk: ApkAsset?, val notes: String = "")

class UpdateException(message: String) : Exception(message)

/** "v0.2.1" -> [0, 2, 1]; null for anything that is not dotted numbers (a name like "nightly"). */
fun parseVersion(text: String): List<Int>? =
    text.removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }

/** Numbers are compared one by one, so 0.10.0 is newer than 0.9.0 and 1 equals 1.0.0. */
fun isNewer(candidate: List<Int>, installed: List<Int>): Boolean {
    for (i in 0 until maxOf(candidate.size, installed.size)) {
        val a = candidate.getOrElse(i) { 0 }
        val b = installed.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

/**
 * Asks GitHub for the latest published release of the (public) repo, no token needed. The API allows 60 unauthenticated requests
 * per hour per IP (checked on the real repo: x-ratelimit-limit: 60); the app checks by hand or at most once a day.
 */
class UpdateChecker(private val transport: HttpTransport, private val installedVersion: String, private val repo: String = REPO) {
    /** Blocking. The latest release if it is newer than the installed version, else null. Throws [UpdateException] when GitHub cannot say. */
    fun check(): Release? {
        val request = HttpRequest("GET", "https://api.github.com/repos/$repo/releases/latest", mapOf("Accept" to "application/vnd.github+json"), null)
        val response = transport.execute(request)
        when (response.code) {
            200 -> Unit
            404 -> return null // no release published yet
            403, 429 -> throw UpdateException("GitHub is limiting requests right now. Try again in a while.")
            else -> throw UpdateException("GitHub answered with HTTP ${response.code}.")
        }
        val json = JSONObject(response.body)
        val tag = json.getString("tag_name")
        val latest = parseVersion(tag) ?: throw UpdateException("The latest release has an unexpected name: $tag.")
        val installed = parseVersion(installedVersion) ?: return null // an odd local build: never nag
        if (!isNewer(latest, installed)) return null
        return Release(tag, json.getString("html_url"), apkOf(json), (if (json.isNull("body")) "" else json.getString("body")).trim())
    }

    private fun apkOf(release: JSONObject): ApkAsset? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (!asset.getString("name").endsWith(".apk")) continue
            val digest = asset.optString("digest").removePrefix("sha256:")
            return ApkAsset(asset.getString("name"), asset.getString("browser_download_url"), asset.optLong("size"), digest.takeIf { it.length == 64 })
        }
        return null
    }

    companion object {
        const val REPO = "yernoor/personal-finance-app"
    }
}
