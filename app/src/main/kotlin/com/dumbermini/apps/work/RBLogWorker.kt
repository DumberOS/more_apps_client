package com.dumbermini.apps.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dumbermini.apps.data.PrivacyRepository
import com.dumbermini.apps.data.local.model.RBData
import com.dumbermini.apps.data.local.model.toLogs
import com.dumbermini.apps.datastore.SettingsRepository
import com.dumbermini.apps.network.Downloader
import com.dumbermini.apps.network.NetworkResponse
import com.dumbermini.apps.sync.JsonParser
import com.dumbermini.apps.utility.common.Constants
import com.dumbermini.apps.utility.common.cache.Cache
import com.dumbermini.apps.utility.common.extension.exceptCancellation
import com.dumbermini.apps.utility.common.toForegroundInfo
import com.dumbermini.apps.utility.notifications.createRbNotification
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.*
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@HiltWorker
class RBLogWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val privacyRepository: PrivacyRepository,
    private val settingsRepository: SettingsRepository,
    private val downloader: Downloader,
) : CoroutineWorker(context, params) {

    @OptIn(ExperimentalTime::class)
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getInitial()
        if (!settings.rbLogsEnabled) {
            Log.i(TAG, "Reproducibility logs disabled, skipping")
            return@withContext Result.success()
        }
        val target = Cache.getTemporaryFile(context)
        try {
            val lastModified = settings.lastRbLogFetch
            val response = downloader.downloadToFile(
                url = BASE_URL,
                target = target,
                headers = {
                    if (lastModified != null) ifModifiedSince(Date(lastModified))
                }
            )
            if (response is NetworkResponse.Success && response.statusCode != 304) {
                setForegroundAsync(
                    context.createRbNotification()
                        .toForegroundInfo(Constants.NOTIFICATION_ID_RB_DOWNLOAD)
                )
                val logs = JsonParser.decodeFromString<Map<String, List<RBData>>>(target.readText())
                privacyRepository.upsertRBLogs(
                    lastModified = response.lastModified ?: Date(),
                    logs = logs.toLogs()
                )
                Log.i(TAG, "Fetched, parsed and saved RB Logs")
            }
            Result.success()
        } catch (e: Exception) {
            e.exceptCancellation()
            Log.e(TAG, "Failed to fetch logs", e)
            Result.failure()
        } finally {
            withContext(NonCancellable) {
                target.delete()
            }
        }
    }

    companion object {
        private const val TAG = "RBLogWorker"
        private const val BASE_URL =
            "https://codeberg.org/IzzyOnDroid/rbtlog/raw/branch/izzy/log/index.json"

        fun fetchRBLogs(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(
                "rb_index",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RBLogWorker>().build(),
            )
        }
    }
}
