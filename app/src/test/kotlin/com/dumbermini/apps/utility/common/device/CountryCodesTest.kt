package com.dumbermini.apps.utility.common.device

import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class CountryCodesTest {
    @Test
    fun `matches staging country names and ISO codes independently of UI language`() {
        val countries = mapOf(
            "Australia" to "AU", "Belgium" to "BE", "Czechia" to "CZ",
            "Finland" to "FI", "France" to "FR", "Germany" to "DE",
            "Greece" to "GR", "Netherlands" to "NL", "Norway" to "NO",
            "Poland" to "PL", "Portugal" to "PT", "Spain" to "ES",
            "Sweden" to "SE", "Switzerland" to "CH", "United Kingdom" to "GB",
        )
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            countries.forEach { (name, code) ->
                assertEquals(code, CountryCodes.normalize(name))
                assertEquals(code, CountryCodes.normalize(" ${code.lowercase(Locale.ROOT)} "))
            }
            assertEquals("CZ", CountryCodes.normalize("Czech Republic"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `missing invalid and language-only values do not become countries`() {
        listOf("", " ", "Unknown", "en", "419", "Swed", "SE,PL").forEach {
            assertNull(CountryCodes.normalize(it))
        }
    }
}
