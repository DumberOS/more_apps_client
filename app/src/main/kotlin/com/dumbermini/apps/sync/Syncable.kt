package com.dumbermini.apps.sync

import com.dumbermini.apps.data.model.Repo

interface Syncable<T> {
    suspend fun sync(repo: Repo, block: (SyncState) -> Unit)
}
