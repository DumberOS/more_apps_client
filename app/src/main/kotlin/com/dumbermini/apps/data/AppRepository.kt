package com.dumbermini.apps.data

import com.dumbermini.apps.data.local.dao.AppDao
import com.dumbermini.apps.data.local.dao.RepoDao
import com.dumbermini.apps.data.local.model.toApp
import com.dumbermini.apps.data.model.App
import com.dumbermini.apps.data.model.AppMinimal
import com.dumbermini.apps.data.model.PackageName
import com.dumbermini.apps.datastore.SettingsRepository
import com.dumbermini.apps.datastore.get
import com.dumbermini.apps.datastore.model.SortOrder
import com.dumbermini.apps.sync.v2.model.DefaultName
import com.dumbermini.apps.sync.v2.model.Tag
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AppRepository @Inject constructor(
    private val appDao: AppDao,
    private val repoDao: RepoDao,
    private val settingsRepository: SettingsRepository,
) {

    private val localeStream = settingsRepository.get { language }

    suspend fun apps(
        sortOrder: SortOrder,
        searchQuery: String? = null,
        repoId: Int? = null,
        categoriesToInclude: List<DefaultName>? = null,
        categoriesToExclude: List<DefaultName>? = null,
        antiFeaturesToInclude: List<Tag>? = null,
        antiFeaturesToExclude: List<Tag>? = null,
    ): List<AppMinimal> = withContext(Dispatchers.Default) {
        val currentLocale = localeStream.first()
        appDao.query(
            sortOrder = sortOrder,
            searchQuery = searchQuery?.ifEmpty { null },
            repoId = repoId,
            categoriesToInclude = categoriesToInclude?.ifEmpty { null },
            categoriesToExclude = categoriesToExclude?.ifEmpty { null },
            antiFeaturesToInclude = antiFeaturesToInclude?.ifEmpty { null },
            antiFeaturesToExclude = antiFeaturesToExclude?.ifEmpty { null },
            locale = currentLocale,
        )
    }

    val categories: Flow<List<DefaultName>>
        get() = repoDao.categories().map { it.map { category -> category.defaultName } }

    fun getApp(packageName: PackageName): Flow<List<App>> = combine(
        appDao.queryAppEntity(packageName.name),
        localeStream,
    ) { appEntityRelations, locale ->
        appEntityRelations.map {
            val repo = repoDao.getRepo(it.app.repoId)!!
            it.toApp(locale, repo)
        }
    }

    suspend fun addToFavourite(packageName: PackageName): Boolean {
        val favourites = settingsRepository.get { favouriteApps }.first()
        val wasInFavourites = packageName.name in favourites
        settingsRepository.toggleFavourites(packageName.name)
        return !wasInFavourites
    }
}
