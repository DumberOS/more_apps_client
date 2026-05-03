package com.dumbermini.apps.sync

import android.content.Context
import com.dumbermini.apps.data.model.Repo
import com.dumbermini.apps.sync.v2.model.IndexV2
import com.dumbermini.apps.utility.common.cache.Cache

class LocalSyncable(
    private val context: Context,
) : Syncable<IndexV2> {
    override suspend fun sync(repo: Repo, block: (SyncState) -> Unit) {
        try {
            val file = Cache.getTemporaryFile(context).apply {
                outputStream().use {
                    block(SyncState.IndexDownload.Success(repo.id))
                    if (repo.id == 5) {
                        it.write(context.assets.open("izzy_index_v2.json").readBytes())
                    } else {
                        it.write(context.assets.open("fdroid_index_v2.json").readBytes())
                    }
                }
            }
            val fingerprint = requireNotNull(repo.fingerprint) {
                "Fingerprint should not be null if index v2 is being fetched"
            }
            val index = JsonParser.decodeFromString<IndexV2>(file.readBytes().decodeToString())
            block(SyncState.JsonParsing.Success(repo.id, fingerprint, index))
        } catch (t: Throwable) {
            block(SyncState.JsonParsing.Failure(repo.id, t))
        }
    }
}
