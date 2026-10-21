package com.dumbermini.apps.ui.appDetail

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.dumbermini.apps.R.string as stringRes

class AndroidAutoInstallDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?) =
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(stringRes.android_auto_install_title)
            .setMessage(stringRes.android_auto_install_message)
            .setNegativeButton(stringRes.cancel, null)
            .setPositiveButton(stringRes.install) { _, _ ->
                parentFragmentManager.setFragmentResult(REQUEST_KEY, arguments ?: Bundle())
            }
            .create()

    companion object {
        const val REQUEST_KEY = "android_auto_install"
        const val REPOSITORY_ID = "repository_id"
        const val RELEASE_ID = "release_id"

        fun newInstance(repositoryId: Long?, releaseIdentifier: String?) = AndroidAutoInstallDialog().apply {
            arguments = bundleOf(RELEASE_ID to releaseIdentifier)
            repositoryId?.let { requireArguments().putLong(REPOSITORY_ID, it) }
        }
    }
}
