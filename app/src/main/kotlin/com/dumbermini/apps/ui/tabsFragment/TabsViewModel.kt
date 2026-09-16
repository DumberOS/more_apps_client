package com.dumbermini.apps.ui.tabsFragment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dumbermini.apps.database.Database
import com.dumbermini.apps.datastore.SettingsRepository
import com.dumbermini.apps.datastore.get
import com.dumbermini.apps.datastore.model.SortOrder
import com.dumbermini.apps.model.ProductItem
import com.dumbermini.apps.ui.tabsFragment.TabsFragment.BackAction
import com.dumbermini.apps.utility.common.extension.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TabsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val savedStateHandle: SavedStateHandle,
    val countrySelection: com.dumbermini.apps.data.LocalCountrySelection,
) : ViewModel() {
    val availableCountries = Database.CountryAdapter.getAllStream().asStateFlow(emptySet())

    val currentSection =
        savedStateHandle.getStateFlow<ProductItem.Section>(STATE_SECTION, ProductItem.Section.All)

    val sortOrder = settingsRepository
        .get { sortOrder }
        .asStateFlow(SortOrder.POPULARITY)

    val allowHomeScreenSwiping = settingsRepository
        .get { homeScreenSwiping }
        .asStateFlow(false)

    val sections =
        combine(
            Database.CategoryAdapter.getAllStream(),
            Database.RepositoryAdapter.getEnabledStream(),
        ) { categories, repos ->
            val productCategories = allowedCategories
                .asSequence()
                .filter(categories::contains)
                .map(ProductItem.Section::Category)
                .toList()

            val enabledRepositories = repos
                .map { ProductItem.Section.Repository(it.id, it.name) }
            enabledRepositories.ifEmpty { setSection(ProductItem.Section.All) }
            buildList {
                add(ProductItem.Section.All)
                addAll(productCategories)
                addAll(enabledRepositories)
            }
        }
            .catch { it.printStackTrace() }
            .asStateFlow(emptyList())

    val isSearchActionItemExpanded = MutableStateFlow(false)

    val showSections = MutableStateFlow(false)

    val backAction = combine(
        currentSection,
        isSearchActionItemExpanded,
        showSections,
    ) { currentSection, isSearchActionItemExpanded, showSections ->
        when {
            currentSection != ProductItem.Section.All -> BackAction.ProductAll
            isSearchActionItemExpanded -> BackAction.CollapseSearchView
            showSections -> BackAction.HideSections
            else -> BackAction.None
        }
    }.asStateFlow(BackAction.None)

    fun setSection(section: ProductItem.Section) {
        savedStateHandle[STATE_SECTION] = section
    }

    fun setSortOrder(sortOrder: SortOrder) {
        viewModelScope.launch {
            settingsRepository.setSortOrder(sortOrder)
        }
    }

    private fun calcBackAction(
        currentSection: ProductItem.Section,
        isSearchActionItemExpanded: Boolean,
        showSections: Boolean,
    ): BackAction {
        return when {
            currentSection != ProductItem.Section.All -> {
                BackAction.ProductAll
            }

            isSearchActionItemExpanded -> {
                BackAction.CollapseSearchView
            }

            showSections -> {
                BackAction.HideSections
            }

            else -> {
                BackAction.None
            }
        }
    }

    companion object {
        private val allowedCategories = listOf(
            "2FA",
            "Ad blocker",
            "AI assistant",
            "App store",
            "Banking",
            "Banking & tap to pay",
            "Bikeshare",
            "Browser",
            "Calendar",
            "Car remote",
            "E-ID",
            "Email",
            "Dating",
            "File management",
            "Game",
            "Health",
            "Launcher",
            "Messaging",
            "Music & Podcasts",
            "Navigation",
            "Post & parcels",
            "Parking",
            "Password manager",
            "Rideshare",
            "Smart home",
            "Social network",
            "Terminal emulator",
            "Tickets & Wallets",
            "Transit & Transport",
            "VPN",
            "Weather",
            "Other",
        )

        private const val STATE_SECTION = "section"
    }
}
