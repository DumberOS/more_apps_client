package com.dumbermini.apps.data

import com.dumbermini.apps.data.local.dao.InstalledDao
import com.dumbermini.apps.data.local.model.toDomain
import com.dumbermini.apps.data.local.model.toEntity
import com.dumbermini.apps.model.InstalledItem
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InstalledRepository @Inject constructor(
    private val installedDao: InstalledDao,
) {

    fun getStream(packageName: String): Flow<InstalledItem?> {
        return installedDao.stream(packageName).map { entity ->
            entity?.toDomain()
        }
    }

    fun getAllStream(): Flow<List<InstalledItem>> {
        return installedDao.streamAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun get(packageName: String): InstalledItem? {
        return installedDao.get(packageName)?.toDomain()
    }

    suspend fun put(installedItem: InstalledItem) {
        installedDao.insert(installedItem.toEntity())
    }

    suspend fun putAll(installedItems: List<InstalledItem>) {
        installedDao.replaceAll(installedItems.map { it.toEntity() })
    }

    suspend fun delete(packageName: String): Int {
        return installedDao.delete(packageName)
    }
}
