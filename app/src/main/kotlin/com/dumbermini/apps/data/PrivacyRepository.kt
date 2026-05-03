package com.dumbermini.apps.data

import com.dumbermini.apps.data.local.dao.DownloadStatsDao
import com.dumbermini.apps.data.local.dao.RBLogDao
import com.dumbermini.apps.data.local.model.DownloadStats
import com.dumbermini.apps.data.local.model.RBLogEntity
import com.dumbermini.apps.datastore.SettingsRepository
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

class PrivacyRepository(
    private val rbDao: RBLogDao,
    private val downloadStatsDao: DownloadStatsDao,
    private val settingsRepo: SettingsRepository,
) {
    private val cc = Dispatchers.IO

    fun getRBLogs(packageName: String): Flow<List<RBLogEntity>> = rbDao.getFlow(packageName)
        .flowOn(cc)

    fun getLatestDownloadStats(packageName: String): Flow<Long> =
        downloadStatsDao.total(packageName).flowOn(cc)

    suspend fun upsertRBLogs(lastModified: Date, logs: List<RBLogEntity>) {
        settingsRepo.setRbLogLastModified(lastModified)
        rbDao.upsert(logs)
    }

    suspend fun save(downloadStats: List<DownloadStats>) {
        downloadStatsDao.insert(downloadStats)
    }

    suspend fun clearDownloadStats() {
        downloadStatsDao.deleteAll()
        settingsRepo.clearDownloadStatsLastModified()
    }

    suspend fun clearRbLogs() {
        rbDao.deleteAll()
        settingsRepo.clearRbLogLastModified()
    }
}
