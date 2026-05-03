package com.dumbermini.apps.compose.repoList

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dumbermini.apps.data.RepoRepository
import com.dumbermini.apps.data.model.Repo
import com.dumbermini.apps.utility.common.extension.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class RepoListViewModel @Inject constructor(
    private val repository: RepoRepository,
) : ViewModel() {

    val stream = repository.repos
        .asStateFlow(emptyList())

    fun toggleRepo(repo: Repo) {
        viewModelScope.launch {
            repository.enableRepository(repo, !repo.enabled)
        }
    }

    fun deleteRepo(repoId: Int) {
        viewModelScope.launch {
            repository.deleteRepo(repoId)
        }
    }
}
