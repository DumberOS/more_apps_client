package com.dumbermini.apps.utility.extension

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class PackageInfoTest {
    private fun aa(version: Long = 1, flags: Int = ApplicationInfo.FLAG_SYSTEM) =
        PackageInfo().apply {
            packageName = "com.google.android.projection.gearhead"
            longVersionCode = version
            versionName = if (version == 1L) "1.0-placeholder" else "17.7.663624-release"
            applicationInfo = ApplicationInfo().apply { this.flags = flags }
        }

    @Test
    fun `factory placeholder is not an installed app`() {
        assertNull(aa().toInstalledItem())
    }

    @Test
    fun `real AA update remains installed`() {
        val item = aa(177663624, ApplicationInfo.FLAG_SYSTEM or
            ApplicationInfo.FLAG_UPDATED_SYSTEM_APP or ApplicationInfo.FLAG_HAS_CODE)
            .toInstalledItem()!!
        assertEquals("com.google.android.projection.gearhead", item.packageName)
        assertEquals(177663624L, item.versionCode)
    }

    @Test
    fun `version one alone does not identify a placeholder`() {
        assertNotNull(aa(flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_HAS_CODE)
            .toInstalledItem())
        assertNotNull(aa(flags = 0).toInstalledItem())
        assertNotNull(aa(flags = ApplicationInfo.FLAG_SYSTEM or
            ApplicationInfo.FLAG_UPDATED_SYSTEM_APP).toInstalledItem())
        assertNotNull(aa(version = 2).toInstalledItem())
    }

    @Test
    fun `other code-free system apps remain installed`() {
        val other = aa().apply { packageName = "com.example.other" }
        assertNotNull(other.toInstalledItem())
    }

    @Test
    fun `missing application info does not hide the app`() {
        assertNotNull(aa().apply { applicationInfo = null }.toInstalledItem())
    }

    @Test
    fun `startup inventory excludes placeholder but includes real AA after install`() {
        val other = aa().apply { packageName = "com.example.other" }
        assertEquals(listOf("com.example.other"),
            listOf(other, aa()).mapNotNull { it.toInstalledItem() }.map { it.packageName })
        val real = aa(177663624, ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_HAS_CODE)
        assertEquals(2, listOf(other, real).mapNotNull { it.toInstalledItem() }.size)
        assertNull(aa().toInstalledItem()) // Restoring the placeholder removes its database row.
    }
}
