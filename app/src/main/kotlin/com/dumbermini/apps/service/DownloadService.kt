package com.dumbermini.apps.service

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.dumbermini.apps.BuildConfig
import com.dumbermini.apps.MainActivity
import com.dumbermini.apps.database.Database
import com.dumbermini.apps.data.model.toPackageName
import com.dumbermini.apps.datastore.SettingsRepository
import com.dumbermini.apps.datastore.get
import com.dumbermini.apps.datastore.model.InstallerType
import com.dumbermini.apps.installer.InstallManager
import com.dumbermini.apps.installer.model.InstallState
import com.dumbermini.apps.installer.model.installFrom
import com.dumbermini.apps.model.Release
import com.dumbermini.apps.model.Repository
import com.dumbermini.apps.network.DataSize
import com.dumbermini.apps.network.Downloader
import com.dumbermini.apps.network.NetworkResponse
import com.dumbermini.apps.network.percentBy
import com.dumbermini.apps.network.validation.ValidationException
import com.dumbermini.apps.utility.common.Constants
import com.dumbermini.apps.utility.common.Constants.NOTIFICATION_CHANNEL_INSTALL
import com.dumbermini.apps.utility.common.SdkCheck
import com.dumbermini.apps.utility.common.cache.Cache
import com.dumbermini.apps.utility.common.createNotificationChannel
import com.dumbermini.apps.utility.common.extension.notificationManager
import com.dumbermini.apps.utility.common.extension.stopForegroundCompat
import com.dumbermini.apps.utility.common.extension.toPendingIntent
import com.dumbermini.apps.utility.common.extension.updateAsMutable
import com.dumbermini.apps.utility.common.log
import com.dumbermini.apps.utility.notifications.createInstallNotification
import com.dumbermini.apps.utility.notifications.installNotification
import com.dumbermini.apps.utility.extension.DownloadSelection
import com.dumbermini.apps.utility.extension.selectDownload
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import com.dumbermini.apps.R.string as stringRes

@AndroidEntryPoint
class DownloadService : ConnectionService<DownloadService.Binder>() {
    companion object {
        private const val ACTION_CANCEL = "${BuildConfig.APPLICATION_ID}.intent.action.CANCEL"
    }

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var downloader: Downloader

    private val installerType
        get() = settingsRepository.get { installerType }

    @Inject
    lateinit var installer: InstallManager

    sealed class State(val packageName: String) {
        data object Idle : State("")
        data class Connecting(val name: String) : State(name)
        data class Downloading(val name: String, val read: DataSize, val total: DataSize?) : State(
            name,
        )

        data class Error(val name: String) : State(name)
        data class Cancel(val name: String) : State(name)
        data class Success(
            val name: String,
            val release: Release,
            val installHandledByService: Boolean = false,
        ) : State(name)
    }

    data class DownloadState(
        val currentItem: State = State.Idle,
        val queue: List<String> = emptyList(),
    ) {
        infix fun isDownloading(packageName: String): Boolean =
            currentItem.packageName == packageName && (
                    currentItem is State.Connecting || currentItem is State.Downloading
                    )

        infix fun isComplete(packageName: String): Boolean =
            currentItem.packageName == packageName && (
                    currentItem is State.Error ||
                            currentItem is State.Cancel ||
                            currentItem is State.Success ||
                            currentItem is State.Idle
                    )
    }

    private val _downloadState = MutableStateFlow(DownloadState())

    private class Task(
        val packageName: String,
        val name: String,
        val release: Release,
        val url: String,
        val authentication: String,
        val isUpdate: Boolean = false,
        val installAfterDownload: Boolean = false,
    ) {
        val notificationTag: String
            get() = "download-$packageName"
    }

    private data class CurrentTask(val task: Task, val lastState: State, val job: Job? = null)

    private var started = false
    private val tasks = mutableListOf<Task>()
    private var currentTask: CurrentTask? = null

    private val lock = Mutex()
    private var preparingAndroidAuto = false

    private fun isActive(packageName: String): Boolean =
        tasks.any { it.packageName == packageName } ||
            currentTask?.task?.packageName == packageName ||
            installer.state.value[packageName.toPackageName()] in
            setOf(InstallState.Pending, InstallState.Installing)

    inner class Binder : android.os.Binder() {
        val downloadState = _downloadState.asStateFlow()

        fun installAndroidAuto(
            repositoryId: Long?,
            releaseIdentifier: String?,
            onUnavailable: (List<String>) -> Unit,
        ) {
            if (preparingAndroidAuto || isActive(AndroidAutoInstall.PACKAGE_NAME)) return
            preparingAndroidAuto = true
            lifecycleScope.launch {
                try {
                    val (installed, available) = withContext(Dispatchers.IO) {
                        val repositories = Database.RepositoryAdapter.getAll().associateBy { it.id }
                        val packages = listOf(AndroidAutoInstall.PACKAGE_NAME) + AndroidAutoInstall.dependencies.keys
                        val installed = packages.filter {
                            Database.InstalledAdapter.get(it, null) != null
                        }.toSet()
                        val available = packages.mapNotNull { packageName ->
                            val products = Database.ProductAdapter.get(packageName, null).mapNotNull { product ->
                                repositories[product.repositoryId]?.let { product to it }
                            }
                            val selection = if (packageName == AndroidAutoInstall.PACKAGE_NAME && releaseIdentifier != null) {
                                products.firstOrNull { it.second.id == repositoryId }?.let { (product, repository) ->
                                    product.releases.find { it.identifier == releaseIdentifier }?.let {
                                        DownloadSelection(product.name, repository, it)
                                    }
                                }
                            } else products.selectDownload(null)
                            selection?.let { packageName to it }
                        }.toMap()
                        installed to available
                    }
                    val packageName = AndroidAutoInstall.PACKAGE_NAME
                    if (packageName in installed || isActive(packageName)) return@launch
                    val selection = available[packageName]
                    val freeSpace = Cache.getEmptySpace(this@DownloadService)
                    if (selection == null || !selection.release.compatible || selection.release.size > freeSpace) {
                        onUnavailable(listOf("Android Auto"))
                        return@launch
                    }
                    val plan = AndroidAutoInstall.planDependencies(
                        installed,
                        AndroidAutoInstall.dependencies.keys.filter(::isActive).toSet(),
                        available,
                        freeSpace,
                    )
                    enqueue(packageName, selection.name, selection.repository, selection.release, installAfterDownload = true)
                    plan.downloads.forEach { (dependency, download) ->
                        enqueue(dependency, download.name, download.repository, download.release, installAfterDownload = true)
                    }
                    if (plan.unavailable.isNotEmpty()) onUnavailable(plan.unavailable)
                } finally {
                    preparingAndroidAuto = false
                }
            }
        }

        fun enqueue(
            packageName: String,
            name: String,
            repository: Repository,
            release: Release,
            isUpdate: Boolean = false,
            installAfterDownload: Boolean = false,
        ) {
            val task = Task(
                packageName = packageName,
                name = name,
                release = release,
                url = release.getDownloadUrl(repository),
                authentication = repository.authentication,
                isUpdate = isUpdate,
                installAfterDownload = installAfterDownload,
            )
            if (Cache.getReleaseFile(this@DownloadService, release.cacheFileName).exists()) {
                lifecycleScope.launch { publishSuccess(task) }
                return
            }
            cancelTasks(packageName)
            cancelCurrentTask(packageName)
            notificationManager?.cancel(
                task.notificationTag,
                Constants.NOTIFICATION_ID_DOWNLOADING,
            )
            tasks += task
            if (currentTask == null) {
                handleDownload()
            } else {
                updateCurrentQueue { add(packageName) }
            }
        }

        fun cancel(packageName: String) {
            cancelTasks(packageName)
            cancelCurrentTask(packageName)
        }
    }

    private val binder = Binder()
    override fun onBind(intent: Intent): Binder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(
            id = Constants.NOTIFICATION_CHANNEL_DOWNLOADING,
            name = getString(stringRes.downloading),
        )
        createNotificationChannel(
            id = NOTIFICATION_CHANNEL_INSTALL,
            name = getString(stringRes.install),
        )

        lifecycleScope.launch {
            _downloadState
                .filter { currentTask != null }
                .sample(400)
                .collectLatest {
                    publishForegroundState(false, it.currentItem)
                }
        }
    }

    override fun onTimeout(startId: Int) {
        super.onTimeout(startId)
        onDestroy()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTasks(null)
        cancelCurrentTask(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            currentTask?.let { binder.cancel(it.task.packageName) }
            stopSelf()
        } else {
            startForeground(
                Constants.NOTIFICATION_ID_DOWNLOADING,
                stateNotificationBuilder
                    .setContentTitle(getString(stringRes.downloading_FORMAT, ""))
                    .setContentText(getString(stringRes.connecting))
                    .setProgress(1, 0, true)
                    .build(),
            )
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun cancelTasks(packageName: String?) {
        tasks.removeAll {
            (packageName == null || it.packageName == packageName) && run {
                updateCurrentState(State.Cancel(it.packageName))
                true
            }
        }
    }

    private fun cancelCurrentTask(packageName: String?) {
        currentTask?.let {
            if (packageName == null || it.task.packageName == packageName) {
                it.job?.cancel()
                currentTask = null
                updateCurrentState(State.Cancel(it.task.packageName))
            }
        }
    }

    private fun showNotificationError(task: Task, error: NetworkResponse.Error) {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData("package:${task.packageName}".toUri())
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .toPendingIntent(this)
        notificationManager?.notify(
            task.notificationTag,
            Constants.NOTIFICATION_ID_DOWNLOADING,
            NotificationCompat
                .Builder(this, Constants.NOTIFICATION_CHANNEL_DOWNLOADING)
                .setAutoCancel(true)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setColor(Color.GREEN)
                .setOnlyAlertOnce(true)
                .setContentIntent(intent)
                .errorNotificationContent(task, error)
                .build(),
        )
    }

    private fun NotificationCompat.Builder.errorNotificationContent(
        task: Task,
        error: NetworkResponse.Error,
    ): NotificationCompat.Builder {
        val title = if (error is NetworkResponse.Error.Validation) {
            stringRes.could_not_validate_FORMAT
        } else {
            stringRes.could_not_download_FORMAT
        }
        val description = when (error) {
            is NetworkResponse.Error.ConnectionTimeout -> getString(stringRes.connection_error_DESC)
            is NetworkResponse.Error.Http -> getString(stringRes.http_error_DESC)
            is NetworkResponse.Error.IO -> getString(stringRes.io_error_DESC)
            is NetworkResponse.Error.SocketTimeout -> getString(stringRes.socket_error_DESC)
            is NetworkResponse.Error.Validation -> error.exception.message
            is NetworkResponse.Error.Unknown -> getString(stringRes.unknown_error_DESC)
        }
        setContentTitle(getString(title, task.name))
        return setContentText(description)
    }

    private fun showNotificationInstall(task: Task) {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_INSTALL)
            .setData("package:${task.packageName}".toUri())
            .putExtra(MainActivity.EXTRA_CACHE_FILE_NAME, task.release.cacheFileName)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .toPendingIntent(this)
        val notification = createInstallNotification(
            appName = task.name,
            state = InstallState.Pending,
            autoCancel = true,
        ) {
            setContentIntent(intent)
        }
        notificationManager?.installNotification(
            packageName = task.packageName,
            notification = notification,
        )
    }

    private suspend fun publishSuccess(task: Task) {
        val currentInstaller = installerType.first()
        updateCurrentQueue { add("") }
        updateCurrentState(State.Success(task.packageName, task.release, task.installAfterDownload))
        val hasSilentInstallPermission =
            packageManager.checkPermission(
                Manifest.permission.INSTALL_PACKAGES,
                packageName,
            ) == PackageManager.PERMISSION_GRANTED
        val autoInstallWithSessionInstaller =
            currentInstaller == InstallerType.SESSION &&
                task.isUpdate &&
                (
                    hasSilentInstallPermission ||
                        SdkCheck.canAutoInstall(task.release.targetSdkVersion)
                    )

        showNotificationInstall(task)
        if (task.installAfterDownload || currentInstaller == InstallerType.ROOT ||
            currentInstaller == InstallerType.SHIZUKU ||
            autoInstallWithSessionInstaller
        ) {
            val installItem = task.packageName installFrom task.release.cacheFileName
            installer install installItem
        }
    }

    private val stateNotificationBuilder by lazy {
        NotificationCompat
            .Builder(this, Constants.NOTIFICATION_CHANNEL_DOWNLOADING)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setColor(Color.GREEN)
            .addAction(
                0,
                getString(stringRes.cancel),
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, this::class.java).setAction(ACTION_CANCEL),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
    }

    private fun publishForegroundState(force: Boolean, state: State) {
        if (!force && currentTask == null) return
        currentTask = currentTask!!.copy(lastState = state)
        stateNotificationBuilder.downloadingNotificationContent(state)
            ?.let { notification ->
                startForeground(
                    Constants.NOTIFICATION_ID_DOWNLOADING,
                    notification.build(),
                )
            } ?: run {
            log("Invalid Download State: $state", "DownloadService", Log.ERROR)
        }
    }

    private fun NotificationCompat.Builder.downloadingNotificationContent(
        state: State,
    ): NotificationCompat.Builder? {
        return when (state) {
            is State.Connecting -> {
                setContentTitle(getString(stringRes.downloading_FORMAT, currentTask!!.task.name))
                setContentText(getString(stringRes.connecting))
                setProgress(1, 0, true)
            }

            is State.Downloading -> {
                setContentTitle(getString(stringRes.downloading_FORMAT, currentTask!!.task.name))
                if (state.total != null) {
                    setContentText("${state.read} / ${state.total}")
                    setProgress(100, state.read.value percentBy state.total.value, false)
                } else {
                    setContentText(state.read.toString())
                    setProgress(0, 0, true)
                }
            }

            else -> null
        }
    }

    private fun handleDownload() {
        if (currentTask != null) return
        if (!started) {
            started = true
        }
        if (tasks.isEmpty()) {
            if (started) started = false
            stopForegroundCompat()
            return
        }
        val task = tasks.removeAt(0)
        val connectionState = State.Connecting(task.packageName)
        currentTask = CurrentTask(task, connectionState)
        with(stateNotificationBuilder) {
            setWhen(System.currentTimeMillis())
            setContentIntent(createNotificationIntent(task.packageName))
        }
        publishForegroundState(true, connectionState)
        val partialReleaseFile = Cache.getPartialReleaseFile(this, task.release.cacheFileName)
        val job = lifecycleScope.downloadFile(task, partialReleaseFile)
        currentTask = currentTask?.copy(job = job)
        updateCurrentState(State.Connecting(task.packageName))
    }

    private fun createNotificationIntent(packageName: String): PendingIntent? =
        Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData("package:$packageName".toUri())
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .toPendingIntent(this)

    private fun CoroutineScope.downloadFile(
        task: Task,
        target: File,
    ) = launch {
        try {
            val releaseValidator = ReleaseFileValidator(
                context = this@DownloadService,
                packageName = task.packageName,
                release = task.release,
            )
            val response = downloader.downloadToFile(
                url = task.url,
                target = target,
                validator = releaseValidator,
                headers = { if (task.authentication.isNotEmpty()) authentication(task.authentication) },
            ) { read, total ->
                yield()
                updateCurrentState(State.Downloading(task.packageName, read, total))
            }

            when (response) {
                is NetworkResponse.Success -> {
                    val releaseFile = Cache.getReleaseFile(
                        this@DownloadService,
                        task.release.cacheFileName,
                    )
                    target.renameTo(releaseFile)
                    publishSuccess(task)
                }

                is NetworkResponse.Error -> {
                    updateCurrentState(State.Error(task.packageName))
                    showNotificationError(task, response)
                }
            }
        } finally {
            lock.withLock { currentTask = null }
            handleDownload()
        }
    }

    private fun updateCurrentState(state: State) {
        _downloadState.update {
            val newQueue =
                if (state.packageName in it.queue) {
                    it.queue.updateAsMutable {
                        removeAll { name -> name == "" }
                        remove(state.packageName)
                    }
                } else {
                    it.queue
                }
            it.copy(currentItem = state, queue = newQueue)
        }
    }

    private fun updateCurrentQueue(block: MutableList<String>.() -> Unit) {
        _downloadState.update { state ->
            state.copy(queue = state.queue.updateAsMutable(block))
        }
    }
}
