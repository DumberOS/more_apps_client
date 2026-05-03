package com.dumbermini.apps.model

data class InstalledItem(
    val packageName: String,
    val version: String,
    val versionCode: Long,
    val signature: String
)
