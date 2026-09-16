package com.dumbermini.apps.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.dumbermini.apps.utility.common.device.localAppCountries
import com.dumbermini.apps.database.CursorOwner
import com.dumbermini.apps.model.ProductItem
import com.dumbermini.apps.ui.appList.AppListFragment
import com.dumbermini.apps.ui.appList.AppListState

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class LocalCountrySelectionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("local_app_countries", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `manual countries survive refresh and recreation including an empty selection`() {
        val selection = LocalCountrySelection(context)
        selection.select(setOf("SE", "PL"))
        selection.refresh()
        assertEquals(setOf("SE", "PL"), selection.countries.value)
        assertEquals(setOf("SE", "PL"), LocalCountrySelection(context).countries.value)
        selection.select(emptySet())
        assertEquals(emptySet(), LocalCountrySelection(context).countries.value)
        selection.useAutomatic()
        assertEquals(localAppCountries(context), selection.countries.value)
        assertEquals(localAppCountries(context), LocalCountrySelection(context).countries.value)
    }

    @Test
    fun `local country filtering ignores Explore category while preserving search and countries`() {
        val category = ProductItem.Section.Category("Navigation")
        val state = AppListState(sections = category, countries = setOf("SE", "PL"))
        val local = state.toRequest(AppListFragment.Source.LOCAL, "train") as CursorOwner.Request.Available
        assertEquals(ProductItem.Section.All, local.section)
        assertEquals(setOf("SE", "PL"), local.countries)
        assertEquals("train", local.searchQuery)
        val explore = state.toRequest(AppListFragment.Source.AVAILABLE, "train") as CursorOwner.Request.Available
        assertEquals(category, explore.section)
        assertEquals(null, explore.countries)
    }
}
