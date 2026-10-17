package com.dumbermini.apps.installer.installers.session

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class InstallConfirmationTest {
    private val installer = mockk<PackageInstaller>()
    private val packageManager = mockk<PackageManager>()
    private lateinit var context: Context
    private var active = false

    @Before
    fun setup() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        application.getSharedPreferences("pending_install_confirmations", Context.MODE_PRIVATE)
            .edit().clear().commit()
        every { packageManager.packageInstaller } returns installer
        val session = mockk<PackageInstaller.SessionInfo>()
        every { session.isActive } answers { active }
        every { installer.getSessionInfo(42) } returns session
        context = object : ContextWrapper(application) {
            override fun getPackageManager() = this@InstallConfirmationTest.packageManager
        }
    }

    private fun savePrompt() {
        InstallConfirmation.save(context, 42, Intent("android.content.pm.action.CONFIRM_INSTALL").apply {
            setPackage("com.android.packageinstaller")
            putExtra(PackageInstaller.EXTRA_SESSION_ID, 42)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    @Test
    fun `unanswered prompt remains available on subsequent foreground visits`() {
        savePrompt()
        val request = InstallConfirmation.next(context)!!
        assertEquals(42, request.sessionId)
        assertEquals(42, request.intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1))
        assertEquals("com.android.packageinstaller", request.intent.`package`)
        assertEquals(0, request.intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertNotNull(InstallConfirmation.next(context))
    }

    @Test
    fun `active install is not prompted while awaiting inactive callback`() {
        savePrompt()
        active = true
        assertNull(InstallConfirmation.next(context))
        active = false
        assertNotNull(InstallConfirmation.next(context))
    }

    @Test
    fun `answered or cancelled prompt cannot reappear`() {
        savePrompt()
        InstallConfirmation.remove(context, 42)
        assertNull(InstallConfirmation.next(context))
    }

    @Test
    fun `session removed by Android is discarded`() {
        savePrompt()
        every { installer.getSessionInfo(42) } returns null
        assertNull(InstallConfirmation.next(context))
        assertEquals(emptyMap<String, Any>(), context.getSharedPreferences(
            "pending_install_confirmations", Context.MODE_PRIVATE
        ).all)
    }
}
