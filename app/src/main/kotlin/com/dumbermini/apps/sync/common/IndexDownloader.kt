package com.dumbermini.apps.sync.common

import android.content.Context
import com.dumbermini.apps.data.model.Repo
import com.dumbermini.apps.network.Downloader
import com.dumbermini.apps.network.ProgressListener
import com.dumbermini.apps.utility.common.cache.Cache

import java.io.File
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun Downloader.downloadIndex(
    context: Context,
    repo: Repo,
    fileName: String,
    url: String,
    diff: Boolean = false,
    onProgress: ProgressListener? = null,
): File = withContext(Dispatchers.IO) {
    val indexFile = Cache.getIndexFile(context, "repo_${repo.id}_$fileName")
    downloadToFile(
        url = url,
        target = indexFile,
        block = onProgress,
        headers = {
            if (repo.shouldAuthenticate) {
                with(requireNotNull(repo.authentication)) {
                    authentication(
                        username = username,
                        password = password,
                    )
                }
            }
            if (repo.versionInfo != null && repo.versionInfo.timestamp > 0L && !diff) {
                ifModifiedSince(Date(repo.versionInfo.timestamp))
            }
        },
    )
    indexFile
}

const val INDEX_V1_NAME = "index-v1.jar"
const val ENTRY_V2_NAME = "entry.jar"
const val INDEX_V2_NAME = "index-v2.json"
