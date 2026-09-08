package com.dumbermini.apps.ui.appList

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dumbermini.apps.database.CursorOwner.Request.Available
import com.dumbermini.apps.database.CursorOwner.Request.Installed
import com.dumbermini.apps.database.CursorOwner.Request.Updates
import com.dumbermini.apps.database.Database
import com.dumbermini.apps.datastore.SettingsRepository
import com.dumbermini.apps.datastore.get
import com.dumbermini.apps.datastore.model.SortOrder
import com.dumbermini.apps.model.ProductItem
import com.dumbermini.apps.model.ProductItem.Section.All
import com.dumbermini.apps.service.Connection
import com.dumbermini.apps.service.SyncService
import com.dumbermini.apps.utility.common.extension.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class AppListViewModel
@Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val skipSignatureStream = settingsRepository
        .get { ignoreSignature }
        .asStateFlow(false)

    private val sortOrderFlow = settingsRepository
        .get { sortOrder }
        .asStateFlow(SortOrder.POPULARITY)

    private val sections = MutableStateFlow<ProductItem.Section>(All)

    val state = combine(
        skipSignatureStream,
        sortOrderFlow,
        sections,
    ) { skipSignature, sortOrder, section ->
        AppListState(
            sections = section,
            sortOrder = sortOrder,
            skipSignatureCheck = skipSignature,
        )
    }.asStateFlow(
        initialValue = AppListState(),
        started = SharingStarted.Eagerly,
    )

    val reposStream = Database.RepositoryAdapter
        .getAllStream()
        .asStateFlow(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val showUpdateAllButton = skipSignatureStream.flatMapLatest { skip ->
        Database.ProductAdapter
            .getUpdatesStream(skip)
            .map { it.isNotEmpty() }
    }.asStateFlow(false)

    val syncConnection = Connection(SyncService::class.java)

    fun updateAll() {
        viewModelScope.launch {
            syncConnection.binder?.updateAllApps()
        }
    }

    fun setSection(newSection: ProductItem.Section) {
        sections.value = newSection
    }
}

data class AppListState(
    val sections: ProductItem.Section = All,
    val sortOrder: SortOrder = SortOrder.POPULARITY,
    val skipSignatureCheck: Boolean = false,
) {
    fun toRequest(source: AppListFragment.Source, searchQuery: String) = when (source) {
        AppListFragment.Source.AVAILABLE -> Available(
            searchQuery = searchQuery,
            section = sections,
            order = sortOrder,
            skipSignatureCheck = skipSignatureCheck,
        )

        AppListFragment.Source.INSTALLED -> Installed(
            searchQuery = searchQuery,
            order = sortOrder,
            skipSignatureCheck = skipSignatureCheck,
        )

        AppListFragment.Source.UPDATES -> Updates(
            searchQuery = searchQuery,
            order = sortOrder,
            skipSignatureCheck = skipSignatureCheck,
        )
    }
}
