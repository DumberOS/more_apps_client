package com.dumbermini.apps.data.model

import com.dumbermini.apps.network.DataSize

interface DataFile {
    val name: String
    val hash: String
    val size: DataSize
}
