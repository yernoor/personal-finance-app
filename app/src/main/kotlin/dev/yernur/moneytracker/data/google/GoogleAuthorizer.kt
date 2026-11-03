package dev.yernur.moneytracker.data.google

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** What the app needs from Google's authorization: a token, or a consent screen to show first. */
class AuthResult(val accessToken: String?, val consent: IntentSender?)

interface Authorizer {
    /** Asks for access; the result carries either a token (already granted) or the consent screen to launch. */
    suspend fun authorize(): AuthResult

    /** The result of the consent screen. */
    fun resultFrom(data: Intent?): AuthResult

    /** A new token after the API rejected [rejected]. */
    suspend fun freshToken(rejected: String): String
}

/**
 * Google Identity Services, Authorization API (docs: developer.android.com/identity/authorization). The device only ever holds a
 * one-hour access token; asking again is silent as long as the owner has not removed the permission. The Google Cloud client is
 * found by package name + signing certificate, so no client id or secret lives in the app.
 */
class GoogleAuthorizer(context: Context) : Authorizer {
    private val client = Identity.getAuthorizationClient(context)
    private val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE))).build()

    override suspend fun authorize(): AuthResult = client.authorize(request).await().toAuthResult()

    override fun resultFrom(data: Intent?): AuthResult = client.getAuthorizationResultFromIntent(data).toAuthResult()

    override suspend fun freshToken(rejected: String): String {
        client.clearToken(ClearTokenRequest.builder().setToken(rejected).build()).await()
        return authorize().accessToken ?: error("Google asked for consent again; connect once more")
    }

    private fun AuthorizationResult.toAuthResult() =
        AuthResult(accessToken, if (hasResolution()) pendingIntent?.intentSender else null)

    companion object {
        /** Non-sensitive scope: the app reaches only files it created itself. */
        const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
