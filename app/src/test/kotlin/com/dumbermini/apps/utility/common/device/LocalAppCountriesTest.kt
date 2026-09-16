package com.dumbermini.apps.utility.common.device

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class LocalAppCountriesTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()

    private fun context(locales: String, telephony: TelephonyManager?): Context {
        val localeManager = mockk<LocaleManager>()
        every { localeManager.systemLocales } returns LocaleList.forLanguageTags(locales)
        return mockk<Context> {
            every { getSystemService(LocaleManager::class.java) } returns localeManager
            every { getSystemService(TelephonyManager::class.java) } returns telephony
        }
    }

    @Test
    fun `prefers both SIM countries over locale regions without phone permission`() {
        val telephony = app.getSystemService(TelephonyManager::class.java)
        val shadow = shadowOf(telephony)
        shadow.setSimCountryIso("se")
        shadow.setActiveModemCount(2)
        shadow.setReadPhoneStatePermission(false)
        val subscriptions = shadowOf(app.getSystemService(SubscriptionManager::class.java))
        subscriptions.setActiveSubscriptionInfos(
            SubscriptionInfoBuilder.newBuilder().setId(10).setSimSlotIndex(0).buildSubscriptionInfo(),
            SubscriptionInfoBuilder.newBuilder().setId(20).setSimSlotIndex(1).buildSubscriptionInfo(),
        )
        subscriptions.setReadPhoneStatePermission(false)
        shadow.setTelephonyManagerForSubscriptionId(10, mockk {
            every { simCountryIso } returns "se"
        })
        shadow.setTelephonyManagerForSubscriptionId(20, mockk {
            every { simCountryIso } returns "pl"
        })
        assertEquals(setOf("SE", "PL"), localAppCountries(context("en-GB,sv-SE", telephony)))
    }

    @Test
    fun `works without a SIM and does not infer a region from a language`() {
        assertEquals(setOf("SE", "PL"), localAppCountries(context("sv-SE,pl-PL,en", null)))
        assertEquals(emptySet(), localAppCountries(context("en", null)))
    }

    @Test
    fun `restricted telephony still allows locale countries`() {
        val telephony = mockk<TelephonyManager> {
            every { simCountryIso } throws SecurityException()
            every { activeModemCount } throws UnsupportedOperationException()
        }
        assertEquals(setOf("SE"), localAppCountries(context("sv-SE", telephony)))
    }
}
