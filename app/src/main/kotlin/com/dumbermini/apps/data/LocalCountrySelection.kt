package com.dumbermini.apps.data

import android.content.Context
import com.dumbermini.apps.utility.common.device.localAppCountries
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class LocalCountrySelection @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val preferences = context.getSharedPreferences("local_app_countries", Context.MODE_PRIVATE)
    private val selection = MutableStateFlow(readSelection())
    val countries = selection.asStateFlow()

    private fun readSelection(): Set<String> =
        preferences.getStringSet("selected", null)?.toSet() ?: localAppCountries(context)

    fun refresh() {
        selection.value = readSelection()
    }

    fun select(countries: Set<String>) {
        preferences.edit().putStringSet("selected", countries.toSet()).apply()
        selection.value = countries.toSet()
    }

    fun useAutomatic() {
        preferences.edit().remove("selected").apply()
        refresh()
    }
}
