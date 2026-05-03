package com.dumbermini.apps.sync.common

import androidx.test.platform.app.InstrumentationRegistry
import java.io.InputStream

fun assets(name: String): InputStream {
    return InstrumentationRegistry.getInstrumentation().context.assets.open(name)
}
