package com.dumbermini.apps.datastore.extension

import android.content.Context
import android.content.res.Configuration
import com.dumbermini.apps.datastore.model.SortOrder
import com.dumbermini.apps.datastore.model.Theme
import com.dumbermini.apps.utility.common.SdkCheck
import com.dumbermini.apps.R.string as stringRes
import com.dumbermini.apps.R.style as styleRes

fun Configuration.getThemeRes(theme: Theme, dynamicTheme: Boolean) = when (theme) {
    Theme.SYSTEM -> {
        if ((uiMode and Configuration.UI_MODE_NIGHT_YES) != 0) {
            if (SdkCheck.isSnowCake && dynamicTheme) {
                styleRes.Theme_Main_DynamicDark
            } else {
                styleRes.Theme_Main_Dark
            }
        } else {
            if (SdkCheck.isSnowCake && dynamicTheme) {
                styleRes.Theme_Main_DynamicLight
            } else {
                styleRes.Theme_Main_Light
            }
        }
    }

    Theme.SYSTEM_BLACK -> {
        if ((uiMode and Configuration.UI_MODE_NIGHT_YES) != 0) {
            if (SdkCheck.isSnowCake && dynamicTheme) {
                styleRes.Theme_Main_DynamicAmoled
            } else {
                styleRes.Theme_Main_Amoled
            }
        } else {
            if (SdkCheck.isSnowCake && dynamicTheme) {
                styleRes.Theme_Main_DynamicLight
            } else {
                styleRes.Theme_Main_Light
            }
        }
    }

    Theme.LIGHT -> if (SdkCheck.isSnowCake && dynamicTheme) {
        styleRes.Theme_Main_DynamicLight
    } else {
        styleRes.Theme_Main_Light
    }
    Theme.DARK -> if (SdkCheck.isSnowCake && dynamicTheme) {
        styleRes.Theme_Main_DynamicDark
    } else {
        styleRes.Theme_Main_Dark
    }
    Theme.AMOLED -> if (SdkCheck.isSnowCake && dynamicTheme) {
        styleRes.Theme_Main_DynamicAmoled
    } else {
        styleRes.Theme_Main_Amoled
    }
}

fun Context?.sortOrderName(sortOrder: SortOrder) = this?.let {
    when (sortOrder) {
        SortOrder.POPULARITY -> getString(stringRes.popularity)
        SortOrder.UPDATED -> getString(stringRes.recently_updated)
        SortOrder.ADDED -> getString(stringRes.whats_new)
        SortOrder.NAME -> getString(stringRes.name)
        SortOrder.SIZE -> getString(stringRes.size)
    }
} ?: ""
