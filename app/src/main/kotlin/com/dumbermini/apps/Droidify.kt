package com.dumbermini.apps

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.NetworkType
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.crossfade
import com.dumbermini.apps.content.ProductPreferences
import com.dumbermini.apps.data.RepoRepository
import com.dumbermini.apps.database.Database
import com.dumbermini.apps.datastore.SettingsRepository
import com.dumbermini.apps.datastore.get
import com.dumbermini.apps.datastore.model.AutoSync
import com.dumbermini.apps.index.RepositoryUpdater
import com.dumbermini.apps.installer.InstallManager
import com.dumbermini.apps.model.Repository
import com.dumbermini.apps.network.Downloader
import com.dumbermini.apps.receivers.InstalledAppReceiver
import com.dumbermini.apps.service.Connection
import com.dumbermini.apps.service.SyncService
import com.dumbermini.apps.sync.SyncPreference
import com.dumbermini.apps.sync.toJobNetworkType
import com.dumbermini.apps.utility.common.Constants
import com.dumbermini.apps.utility.common.cache.Cache
import com.dumbermini.apps.utility.common.device.DumbdroidBuild
import com.dumbermini.apps.utility.common.extension.getDrawableCompat
import com.dumbermini.apps.utility.common.extension.getInstalledPackagesCompat
import com.dumbermini.apps.utility.common.extension.jobScheduler
import com.dumbermini.apps.utility.extension.toInstalledItem
import com.dumbermini.apps.work.CleanUpWorker
import dagger.hilt.android.HiltAndroidApp
import io.ktor.client.HttpClient
import javax.inject.Inject
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class Droidify : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    private val parentJob = SupervisorJob()
    private val appScope = CoroutineScope(Dispatchers.Default + parentJob)

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var repoRepository: RepoRepository

    @Inject
    lateinit var installer: InstallManager

    @Inject
    lateinit var downloader: Downloader

    @Inject
    lateinit var httpClient: HttpClient

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        val databaseInit = Database.init(this)
        runBlocking { disableMicrogRepoOnGapps() }
        ProductPreferences.init(this, appScope)
        RepositoryUpdater.init(appScope, downloader)
        listenApplications()
        checkLanguage()
        updatePreference()
        appScope.launch { installer() }

        if (databaseInit.created) {
            appScope.launch {
                forceSyncAll()
            }
        }

        if (databaseInit.changed) {
            appScope.launch {
                scheduleBootstrapSync(this@Droidify, settingsRepository.getInitial().autoSync)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel("Application Terminated")
        installer.close()
    }

    private fun listenApplications() {
        val installedItems = packageManager
            .getInstalledPackagesCompat()
            ?.mapNotNull { it.toInstalledItem() }
        if (installedItems != null) {
            Database.InstalledAdapter.putAll(installedItems)
        }
        appScope.launch {
            registerReceiver(
                InstalledAppReceiver(packageManager),
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addDataScheme("package")
                },
            )
        }
    }

    private fun checkLanguage() {
        appScope.launch {
            val lastSetLanguage = settingsRepository.getInitial().language
            val systemSetLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            if (systemSetLanguage != lastSetLanguage && lastSetLanguage != "system") {
                settingsRepository.setLanguage(systemSetLanguage)
            }
        }
    }

    private suspend fun disableMicrogRepoOnGapps() {
        if (!DumbdroidBuild.isGapps) {
            return
        }

        Database.RepositoryAdapter.getAll()
            .filter { it.address == Repository.MICROG_REPO_ADDRESS && it.enabled }
            .forEach { Database.RepositoryAdapter.put(it.enable(false)) }
        repoRepository.disableRepoByAddress(Repository.MICROG_REPO_ADDRESS)
    }

    private fun updatePreference() {
        appScope.launch {
            launch {
                settingsRepository.get { unstableUpdate }.drop(1).collect {
                    forceSyncAll()
                }
            }
            launch {
                settingsRepository.get { autoSync }.collectIndexed { index, syncMode ->
                    // Don't update sync job on initial collect
                    updateSyncJob(this@Droidify, index > 0, syncMode)
                }
            }
            launch {
                settingsRepository.get { cleanUpInterval }.drop(1).collect {
                    if (it == INFINITE) {
                        CleanUpWorker.removeAllSchedules(applicationContext)
                    } else {
                        CleanUpWorker.scheduleCleanup(applicationContext, it)
                    }
                }
            }
        }
    }

    private fun forceSyncAll() {
        Database.RepositoryAdapter.getAll().forEach {
            if (it.lastModified.isNotEmpty() || it.entityTag.isNotEmpty()) {
                Database.RepositoryAdapter.put(it.copy(lastModified = "", entityTag = ""))
            }
        }
        Connection(
            SyncService::class.java,
            onBind = { connection, binder ->
                binder.sync(SyncService.SyncRequest.FORCE)
                connection.unbind(this)
            },
        ).bind(this)
    }

    companion object {
        @SuppressLint("ScheduleExactAlarm")
        fun updateSyncJob(context: Context, force: Boolean, autoSync: AutoSync) {
            if (autoSync == AutoSync.NEVER) {
                context.jobScheduler?.cancel(Constants.JOB_ID_SYNC)
                context.jobScheduler?.cancel(Constants.JOB_ID_SYNC_BOOTSTRAP)
                return
            }
            val jobScheduler = context.jobScheduler
            val syncConditions = when (autoSync) {
                AutoSync.ALWAYS -> SyncPreference(NetworkType.CONNECTED)
                AutoSync.WIFI_ONLY -> SyncPreference(NetworkType.UNMETERED)
                AutoSync.WIFI_PLUGGED_IN ->
                    SyncPreference(NetworkType.UNMETERED, pluggedIn = true)
            }
            val period = SyncService.Job.minPeriodMillis()
            val networkType = syncConditions.toJobNetworkType()
            val existingJob = jobScheduler?.allPendingJobs
                ?.firstOrNull { it.id == Constants.JOB_ID_SYNC }
            val jobNeedsUpdate = existingJob?.let {
                it.intervalMillis != period ||
                    it.networkType != networkType ||
                    it.isRequireCharging != syncConditions.pluggedIn ||
                    (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            it.isRequireBatteryNotLow != syncConditions.batteryNotLow
                        )
            } ?: true
            if (force || jobNeedsUpdate) {
                val job = SyncService.Job.create(
                    context = context,
                    periodMillis = period,
                    networkType = networkType,
                    isCharging = syncConditions.pluggedIn,
                    isBatteryLow = syncConditions.batteryNotLow,
                )
                jobScheduler?.schedule(job)
            }
        }

        fun scheduleBootstrapSync(context: Context, autoSync: AutoSync) {
            if (autoSync == AutoSync.NEVER) return

            val jobScheduler = context.jobScheduler ?: return
            val syncConditions = when (autoSync) {
                AutoSync.ALWAYS -> SyncPreference(NetworkType.CONNECTED)
                AutoSync.WIFI_ONLY -> SyncPreference(NetworkType.UNMETERED)
                AutoSync.WIFI_PLUGGED_IN ->
                    SyncPreference(NetworkType.UNMETERED, pluggedIn = true)
            }
            val existingJob = jobScheduler.allPendingJobs
                .firstOrNull { it.id == Constants.JOB_ID_SYNC_BOOTSTRAP }
            if (existingJob != null) return

            val job = SyncService.Job.createOneShot(
                context = context,
                jobId = Constants.JOB_ID_SYNC_BOOTSTRAP,
                networkType = syncConditions.toJobNetworkType(),
                isCharging = syncConditions.pluggedIn,
                isBatteryLow = syncConditions.batteryNotLow,
                minLatencyMillis = 30.seconds.inWholeMilliseconds,
            )
            jobScheduler.schedule(job)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val memoryCache = MemoryCache.Builder()
            .maxSizePercent(context, 0.25)
            .build()

        val diskCache = DiskCache.Builder()
            .directory(Cache.getImagesDir(this))
            .maxSizePercent(0.05)
            .build()

        return ImageLoader.Builder(this)
            .memoryCache(memoryCache)
            .diskCache(diskCache)
            .error(getDrawableCompat(R.drawable.ic_cannot_load).asImage())
            .crossfade(350)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = { httpClient }))
                add(FallbackIconInterceptor())
            }
            .build()
    }
}

private class FallbackIconInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val result = chain.proceed()

        if (result is SuccessResult) return result

        val fallbackIconUrl = request.newBuilder()
            .data((request.data as String).replaceAfterLast('/', "icon.png"))
            .build()
        return chain.withRequest(fallbackIconUrl).proceed()
    }
}
