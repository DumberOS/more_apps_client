package com.dumbermini.apps.utility.common.device

import java.util.Locale

/** Accept the repository's English country names as well as ISO alpha-2 codes. */
object CountryCodes {
    private val codes = Locale.getISOCountries().toSet()
    private val names = codes.associateBy {
        Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.ENGLISH).lowercase(Locale.ROOT)
    } + mapOf("czechia" to "CZ", "czech republic" to "CZ")

    fun normalize(value: String): String? {
        val trimmed = value.trim()
        val code = trimmed.uppercase(Locale.ROOT)
        return code.takeIf { it in codes } ?: names[trimmed.lowercase(Locale.ROOT)]
    }
}
