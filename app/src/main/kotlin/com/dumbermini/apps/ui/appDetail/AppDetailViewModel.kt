package com.dumbermini.apps.ui.appDetail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dumbermini.apps.BuildConfig
import com.dumbermini.apps.data.PrivacyRepository
import com.dumbermini.apps.data.local.model.RBLogEntity
import com.dumbermini.apps.data.model.toPackageName
import com.dumbermini.apps.database.Database
import com.dumbermini.apps.datastore.CustomButtonRepository
import com.dumbermini.apps.datastore.SettingsRepository
import com.dumbermini.apps.datastore.model.CustomButton
import com.dumbermini.apps.datastore.model.InstallerType
import com.dumbermini.apps.installer.InstallManager
import com.dumbermini.apps.installer.installers.isShizukuAlive
import com.dumbermini.apps.installer.installers.isShizukuGranted
import com.dumbermini.apps.installer.installers.isShizukuInstalled
import com.dumbermini.apps.installer.installers.isSuiAvailable
import com.dumbermini.apps.installer.installers.requestPermissionListener
import com.dumbermini.apps.installer.model.InstallState
import com.dumbermini.apps.installer.model.installFrom
import com.dumbermini.apps.model.InstalledItem
import com.dumbermini.apps.model.Product
import com.dumbermini.apps.model.Repository
import com.dumbermini.apps.utility.common.extension.asStateFlow
import com.dumbermini.apps.utility.extension.combine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val installer: InstallManager,
    private val settingsRepository: SettingsRepository,
    private val customButtonRepository: CustomButtonRepository,
    privacyRepository: PrivacyRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val installationProblems = savedStateHandle.getStateFlow("installation_problems", arrayListOf<String>())

    fun reportInstallationProblems(names: List<String>) {
        savedStateHandle["installation_problems"] = ArrayList(names)
    }

    val packageName: String = requireNotNull(savedStateHandle[ARG_PACKAGE_NAME])

    private val repoAddress: StateFlow<String?> =
        savedStateHandle.getStateFlow(ARG_REPO_ADDRESS, null)

    val installerState: StateFlow<InstallState?> =
        installer.state.mapNotNull { stateMap ->
            stateMap[packageName.toPackageName()]
        }.asStateFlow(null)

    val customButtons: StateFlow<List<CustomButton>> = customButtonRepository.buttons
        .asStateFlow(emptyList())

    val state =
        combine(
            Database.ProductAdapter.getStream(packageName),
            Database.RepositoryAdapter.getAllStream(),
            Database.InstalledAdapter.getStream(packageName),
            privacyRepository.getRBLogs(packageName),
            privacyRepository.getLatestDownloadStats(packageName),
            repoAddress,
            settingsRepository.data
        ) { products, repositories, installedItem, rblogs, downloads, suggestedAddress, settings ->
            val idAndRepos = repositories.associateBy { it.id }
            val filteredProducts = products.filter { product ->
                idAndRepos[product.repositoryId] != null
            }
            AppDetailUiState(
                products = filteredProducts,
                repos = repositories,
                rblogs = rblogs,
                downloads = downloads,
                installedItem = installedItem,
                isFavourite = packageName in settings.favouriteApps,
                allowIncompatibleVersions = settings.incompatibleVersions,
                isSelf = packageName == BuildConfig.APPLICATION_ID,
                addressIfUnavailable = suggestedAddress,
            )
        }.asStateFlow(AppDetailUiState())

    fun shizukuState(context: Context): ShizukuState? {
        val isSelected =
            runBlocking { settingsRepository.getInitial().installerType == InstallerType.SHIZUKU }
        if (!isSelected) return null
        val isAlive = isShizukuAlive()
        val isSuiAvailable = isSuiAvailable()
        if (isSuiAvailable) return null

        val isGranted = if (isAlive) {
            if (isShizukuGranted()) {
                true
            } else {
                runBlocking { requestPermissionListener() }
            }
        } else false
        return ShizukuState(
            isNotInstalled = !isShizukuInstalled(context),
            isNotGranted = !isGranted,
            isNotAlive = !isAlive,
        )
    }

    fun setDefaultInstaller() {
        viewModelScope.launch {
            settingsRepository.setInstallerType(InstallerType.Default)
        }
    }

    fun setFavouriteState() {
        viewModelScope.launch {
            settingsRepository.toggleFavourites(packageName)
        }
    }

    fun installPackage(packageName: String, fileName: String) {
        viewModelScope.launch {
            installer install (packageName installFrom fileName)
        }
    }

    fun uninstallPackage() {
        viewModelScope.launch {
            installer uninstall packageName.toPackageName()
        }
    }

    fun removeQueue() {
        viewModelScope.launch {
            installer remove packageName.toPackageName()
        }
    }

    companion object {
        const val ARG_PACKAGE_NAME = "package_name"
        const val ARG_REPO_ADDRESS = "repo_address"
    }
}

data class ShizukuState(
    val isNotInstalled: Boolean,
    val isNotGranted: Boolean,
    val isNotAlive: Boolean,
) {
    val check: Boolean
        get() = isNotInstalled || isNotAlive || isNotGranted
}

data class AppDetailUiState(
    val products: List<Product> = emptyList(),
    val repos: List<Repository> = emptyList(),
    val rblogs: List<RBLogEntity> = emptyList(),
    val downloads: Long = -1,
    val installedItem: InstalledItem? = null,
    val isSelf: Boolean = false,
    val isFavourite: Boolean = false,
    val allowIncompatibleVersions: Boolean = false,
    val addressIfUnavailable: String? = null,
)
