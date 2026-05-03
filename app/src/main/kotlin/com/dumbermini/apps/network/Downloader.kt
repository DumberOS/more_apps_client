package com.dumbermini.apps.network

import com.dumbermini.apps.network.header.HeadersBuilder
import com.dumbermini.apps.network.validation.FileValidator
import java.io.File

interface Downloader {

    suspend fun headCall(
        url: String,
        headers: HeadersBuilder.() -> Unit = {},
    ): NetworkResponse

    suspend fun downloadToFile(
        url: String,
        target: File,
        validator: FileValidator? = null,
        headers: HeadersBuilder.() -> Unit = {},
        block: ProgressListener? = null,
    ): NetworkResponse
}

typealias ProgressListener = suspend (bytesReceived: DataSize, contentLength: DataSize?) -> Unit
