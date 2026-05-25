package com.dumbermini.apps.utility.common.device

object DumbdroidBuild {
    private const val BRANCH_PROPERTY = "ro.dumbdroid.branch"
    private const val GAPPS_BRANCH = "gapps"

    val branch: String
        get() = SystemProperties.get(BRANCH_PROPERTY).orEmpty()

    val isGapps: Boolean
        get() = branch == GAPPS_BRANCH
}
