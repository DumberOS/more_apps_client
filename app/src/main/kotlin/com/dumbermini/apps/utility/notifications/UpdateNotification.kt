package com.dumbermini.apps.utility.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat
import com.dumbermini.apps.MainActivity
import com.dumbermini.apps.R
import com.dumbermini.apps.model.ProductItem
import com.dumbermini.apps.utility.common.Constants
import com.dumbermini.apps.utility.common.SdkCheck
import com.dumbermini.apps.utility.common.extension.getColorFromAttr
import android.R as AndroidR

private const val MAX_UPDATE_NOTIFICATION = 5

fun updatesAvailableNotification(
    context: Context,
    productItems: List<ProductItem>,
) = NotificationCompat
    .Builder(context, Constants.NOTIFICATION_CHANNEL_UPDATES)
    .setSmallIcon(R.drawable.ic_new_releases)
    .setContentTitle(context.getString(R.string.new_updates_available))
    .setContentText(
        context.resources.getQuantityString(
            R.plurals.new_updates_DESC_FORMAT,
            productItems.size,
            productItems.size,
        ),
    )
    .setColor(
        ContextThemeWrapper(context, R.style.Theme_Main_Light)
            .getColorFromAttr(AndroidR.attr.colorPrimary).defaultColor,
    )
    .setContentIntent(
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_UPDATES),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    )
    .setStyle(
        NotificationCompat.InboxStyle().also {
            for (productItem in productItems.take(MAX_UPDATE_NOTIFICATION)) {
                it.addLine("${productItem.name} ${productItem.version}")
            }
            if (productItems.size > MAX_UPDATE_NOTIFICATION) {
                val summary =
                    context.getString(
                        R.string.plus_more_FORMAT,
                        productItems.size - MAX_UPDATE_NOTIFICATION,
                    )
                if (SdkCheck.isNougat) {
                    it.addLine(summary)
                } else {
                    it.setSummaryText(summary)
                }
            }
        },
    )
    .build()