package com.dumbermini.apps.utility.common.device

import android.annotation.SuppressLint

object SystemProperties {
    @SuppressLint("PrivateApi")
    fun get(key: String): String? {
        return try {
            Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("get", String::class.java)
                .invoke(null, key) as String
        } catch (_: Exception) {
            null
        }
    }
}
