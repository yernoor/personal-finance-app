package dev.yernur.moneytracker.walletlogger

import org.json.JSONObject

/** What is kept from one notification of an allow-listed app. Nothing else of the notification is read. */
data class LoggedNotification(
    val packageName: String,
    val postTime: Long,
    val notificationId: Int,
    val isGroupSummary: Boolean,
    val title: String?,
    val text: String?,
    val bigText: String?,
) {
    fun toJson(): String = JSONObject()
        .put("package", packageName)
        .put("postTime", postTime)
        .put("id", notificationId)
        .put("groupSummary", isGroupSummary)
        .put("title", title ?: JSONObject.NULL)
        .put("text", text ?: JSONObject.NULL)
        .put("bigText", bigText ?: JSONObject.NULL)
        .toString()

    companion object {
        fun fromJson(line: String): LoggedNotification {
            val o = JSONObject(line)
            return LoggedNotification(
                packageName = o.getString("package"),
                postTime = o.getLong("postTime"),
                notificationId = o.getInt("id"),
                isGroupSummary = o.getBoolean("groupSummary"),
                title = if (o.isNull("title")) null else o.getString("title"),
                text = if (o.isNull("text")) null else o.getString("text"),
                bigText = if (o.isNull("bigText")) null else o.getString("bigText"),
            )
        }
    }
}
