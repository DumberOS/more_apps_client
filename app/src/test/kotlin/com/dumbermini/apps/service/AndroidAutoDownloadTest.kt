package com.dumbermini.apps.service

import android.app.Application
import android.content.Intent
import android.os.Looper
import com.dumbermini.apps.datastore.Settings
import com.dumbermini.apps.datastore.SettingsRepository
import com.dumbermini.apps.datastore.model.InstallerType
import com.dumbermini.apps.installer.InstallManager
import com.dumbermini.apps.model.Release
import com.dumbermini.apps.model.Repository
import com.dumbermini.apps.network.Downloader
import com.dumbermini.apps.network.NetworkResponse
import com.dumbermini.apps.utility.common.cache.Cache
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AndroidAutoDownloadTest {
    @Test
    fun `confirmed cached APK installs once without a detail screen`() = checkInstall(cached = true, confirmed = true)

    @Test
    fun `confirmed downloaded APK installs once without a detail screen`() = checkInstall(cached = false, confirmed = true)

    @Test
    fun `ordinary fresh download retains existing installation behavior`() = checkInstall(cached = false, confirmed = false)

    private fun checkInstall(cached: Boolean, confirmed: Boolean) {
        // Attach a real service context without onCreate's Hilt injection; supply isolated dependencies.
        val service = Robolectric.buildService(DownloadService::class.java).get()
        val settings = mockk<SettingsRepository>()
        every { settings.data } returns flowOf(Settings(installerType = InstallerType.SESSION))
        service.settingsRepository = settings
        val installer = mockk<InstallManager>(relaxed = true)
        service.installer = installer
        service.downloader = mockk<Downloader>().also { downloader ->
            coEvery { downloader.downloadToFile(any(), any(), any(), any(), any()) } answers {
                secondArg<File>().writeText("downloaded APK")
                NetworkResponse.Success(200, null, null)
            }
        }
        val release = mockk<Release>(relaxed = true)
        val cacheFileName = "android-auto-test-$cached-$confirmed.apk"
        every { release.cacheFileName } returns cacheFileName
        every { release.getDownloadUrl(any()) } returns "https://example.com/app.apk"
        val repository = Repository.defaultRepository(
            address = "https://example.com", name = "Test", description = "", fingerprint = "",
        )
        if (cached) Cache.getReleaseFile(service, release.cacheFileName).writeText("cached APK")
        val binder = service.onBind(Intent())
        binder.enqueue(AndroidAutoInstall.PACKAGE_NAME, "Android Auto", repository, release, installAfterDownload = confirmed)
        shadowOf(Looper.getMainLooper()).idle()
        coVerify(exactly = if (confirmed) 1 else 0) {
            installer.install(match {
                it.packageName.name == AndroidAutoInstall.PACKAGE_NAME && it.installFileName == cacheFileName
            })
        }
        val success = binder.downloadState.value.currentItem as DownloadService.State.Success
        assertEquals(confirmed, success.installHandledByService)
        service.onDestroy()
    }
}
