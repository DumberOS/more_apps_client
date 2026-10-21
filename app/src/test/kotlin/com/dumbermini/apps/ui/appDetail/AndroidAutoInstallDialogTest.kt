package com.dumbermini.apps.ui.appDetail

import android.app.Application
import android.content.DialogInterface
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.dumbermini.apps.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AndroidAutoInstallDialogTest {
    class HostActivity : FragmentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_Main_Light)
            super.onCreate(savedInstanceState)
        }
    }

    @Test
    fun `confirm preserves selected repository and release after recreation`() {
        val controller = Robolectric.buildActivity(HostActivity::class.java).setup()
        AndroidAutoInstallDialog.newInstance(42, "123.hash")
            .showNow(controller.get().supportFragmentManager, AndroidAutoInstallDialog.REQUEST_KEY)
        controller.recreate()
        val activity = controller.get()
        var result: Bundle? = null
        activity.supportFragmentManager.setFragmentResultListener(
            AndroidAutoInstallDialog.REQUEST_KEY, activity,
        ) { _, bundle -> result = bundle }
        val dialog = activity.supportFragmentManager.findFragmentByTag(AndroidAutoInstallDialog.REQUEST_KEY)
            as AndroidAutoInstallDialog
        (dialog.requireDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val confirmed = requireNotNull(result)
        assertEquals(42L, confirmed.getLong(AndroidAutoInstallDialog.REPOSITORY_ID))
        assertEquals("123.hash", confirmed.getString(AndroidAutoInstallDialog.RELEASE_ID))
        controller.pause().stop().destroy()
    }

    @Test
    fun `cancel and dismissal never confirm installation`() {
        val controller = Robolectric.buildActivity(HostActivity::class.java).setup()
        val activity = controller.get()
        var result: Bundle? = null
        activity.supportFragmentManager.setFragmentResultListener(
            AndroidAutoInstallDialog.REQUEST_KEY, activity,
        ) { _, bundle -> result = bundle }
        val cancel = AndroidAutoInstallDialog.newInstance(null, null)
        cancel.showNow(activity.supportFragmentManager, "cancel")
        (cancel.requireDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(result)
        val dismiss = AndroidAutoInstallDialog.newInstance(null, null)
        dismiss.showNow(activity.supportFragmentManager, "dismiss")
        dismiss.requireDialog().cancel()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(result)
        controller.pause().stop().destroy()
    }
}
