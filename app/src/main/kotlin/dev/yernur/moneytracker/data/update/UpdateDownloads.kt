package dev.yernur.moneytracker.data.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Running(val progress: Float) : DownloadStatus
    data class Done(val file: File) : DownloadStatus
    data class Failed(val message: String) : DownloadStatus
}

/** Downloads that outlive the screen: the screen only starts one and watches it. */
interface UpdateDownloads {
    fun start(apk: ApkAsset)

    fun status(apk: ApkAsset): Flow<DownloadStatus>
}

private fun workName(apk: ApkAsset) = "update-download-${apk.name}"

/** WorkManager keeps the download going when the screen is left or the phone is locked, and resumes it after a lost connection (see [ApkInstaller]). */
class WorkDownloads(private val context: Context) : UpdateDownloads {
    override fun start(apk: ApkAsset) {
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setInputData(Data.Builder().putString(URL, apk.url).putString(NAME, apk.name).putLong(SIZE, apk.size).putString(SHA256, apk.sha256).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        // KEEP: a download already running or finished for this file is not restarted; a failed or cancelled one is replaced.
        WorkManager.getInstance(context).enqueueUniqueWork(workName(apk), ExistingWorkPolicy.KEEP, request)
    }

    override fun status(apk: ApkAsset): Flow<DownloadStatus> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(workName(apk)).map { infos ->
            val info = infos.lastOrNull() ?: return@map DownloadStatus.Idle
            when (info.state) {
                WorkInfo.State.SUCCEEDED -> info.outputData.getString(PATH)?.let { DownloadStatus.Done(File(it)) } ?: DownloadStatus.Idle
                WorkInfo.State.FAILED -> DownloadStatus.Failed(info.outputData.getString(ERROR) ?: "The download failed.")
                WorkInfo.State.CANCELLED -> DownloadStatus.Idle
                else -> DownloadStatus.Running(info.progress.getFloat(PROGRESS, 0f))
            }
        }

    companion object {
        const val URL = "url"
        const val NAME = "name"
        const val SIZE = "size"
        const val SHA256 = "sha256"
        const val PATH = "path"
        const val ERROR = "error"
        const val PROGRESS = "progress"
    }
}

/** Runs in a foreground service (notification with progress) so Android does not stop it when the phone is locked. */
class UpdateDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val apk = ApkAsset(
            inputData.getString(WorkDownloads.NAME).orEmpty(), inputData.getString(WorkDownloads.URL).orEmpty(),
            inputData.getLong(WorkDownloads.SIZE, 0), inputData.getString(WorkDownloads.SHA256),
        )
        runCatching { setForeground(foregroundInfo()) } // best effort: without it the download still runs while the app is open
        var lastPercent = -1
        return try {
            val file = withContext(Dispatchers.IO) {
                ApkInstaller(applicationContext).download(apk) { fraction ->
                    val percent = (fraction * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        setProgressAsync(Data.Builder().putFloat(WorkDownloads.PROGRESS, fraction).build())
                    }
                }
            }
            Result.success(Data.Builder().putString(WorkDownloads.PATH, file.path).build())
        } catch (e: IOException) {
            // Cut off (connection lost, phone asleep): the partial file stays and the retry continues from it.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else failure("No internet connection.")
        } catch (e: Exception) {
            failure(e.message ?: "The download failed.")
        }
    }

    private fun failure(message: String) = Result.failure(Data.Builder().putString(WorkDownloads.ERROR, message).build())

    private fun foregroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Downloading the update")
            .setProgress(0, 0, true).setOngoing(true).build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private companion object {
        const val CHANNEL = "app_update"
        const val NOTIFICATION_ID = 7002
        const val MAX_ATTEMPTS = 5
    }
}
