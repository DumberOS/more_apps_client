package com.dumbermini.apps.compose.appDetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.dumbermini.apps.data.AppRepository
import com.dumbermini.apps.data.RepoRepository
import com.dumbermini.apps.data.model.App
import com.dumbermini.apps.data.model.Package
import com.dumbermini.apps.data.model.PackageName
import com.dumbermini.apps.data.model.Repo
import com.dumbermini.apps.datastore.CustomButtonRepository
import com.dumbermini.apps.datastore.model.CustomButton
import com.dumbermini.apps.utility.common.extension.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val repoRepository: RepoRepository,
    private val customButtonRepository: CustomButtonRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val packageName: String = requireNotNull(savedStateHandle["packageName"]) {
        "Required argument 'packageName' was not found in SavedStateHandle"
    }

    val customButtons: StateFlow<List<CustomButton>> = customButtonRepository.buttons
        .asStateFlow(emptyList())

    val state: StateFlow<AppDetailState> = appRepository
        .getApp(PackageName(packageName))
        .map { apps ->
            when {
                apps.isEmpty() -> AppDetailState.Error("No app found for $packageName")
                else -> AppDetailState.Success(
                    app = apps.first(),
                    packages = apps.flatMap {
                        val repo = repoRepository.getRepo(it.repoId.toInt())
                        if (repo != null && it.packages != null) {
                            it.packages.map { pkg -> pkg to repo }
                        } else {
                            emptyList()
                        }
                    }.sortedByDescending { (pkg, _) -> pkg.manifest.versionCode }
                )
            }
        }
        .onStart { emit(AppDetailState.Loading) }
        .asStateFlow(AppDetailState.Loading)
}

sealed interface AppDetailState {
    object Loading : AppDetailState
    data class Error(val message: String) : AppDetailState
    data class Success(
        val app: App,
        val packages: List<Pair<Package, Repo>>,
    ) : AppDetailState
}
