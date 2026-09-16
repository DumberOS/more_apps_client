package com.dumbermini.apps.utility.common.device

import android.app.LocaleManager
import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/** Country hints stay on-device. App language overrides and roaming networks are ignored. */
fun localAppCountries(context: Context): Set<String> {
    val simCountries = simCountries(context.getSystemService(TelephonyManager::class.java))
    if (simCountries.isNotEmpty()) return simCountries

    val locales = context.getSystemService(LocaleManager::class.java).systemLocales
    return buildSet {
        for (index in 0 until locales.size()) {
            locales[index]?.country?.let(CountryCodes::normalize)?.let(::add)
        }
    }
}

private fun simCountries(telephony: TelephonyManager?): Set<String> = buildSet {
    if (telephony == null) return@buildSet
    fun addSimCountry(manager: TelephonyManager) {
        try {
            CountryCodes.normalize(manager.simCountryIso.orEmpty())?.let(::add)
        } catch (_: SecurityException) {
            // Some devices restrict telephony access; locale regions still work.
        } catch (_: UnsupportedOperationException) {
            // Telephony services can exist on devices without SIM support.
        }
    }
    addSimCountry(telephony)

    try {
        for (slot in 0 until telephony.activeModemCount) {
            val subscriptionId = SubscriptionManager.getSubscriptionId(slot)
            if (SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
                addSimCountry(telephony.createForSubscriptionId(subscriptionId))
            }
        }
    } catch (_: SecurityException) {
        // Keep the default SIM country if slot lookup is restricted.
    } catch (_: UnsupportedOperationException) {
        // No subscription support on this device.
    }
}
