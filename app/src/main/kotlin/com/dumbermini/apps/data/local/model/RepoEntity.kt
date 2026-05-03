package com.dumbermini.apps.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dumbermini.apps.data.model.Authentication
import com.dumbermini.apps.data.model.FilePath
import com.dumbermini.apps.data.model.Fingerprint
import com.dumbermini.apps.data.model.Html
import com.dumbermini.apps.data.model.Repo
import com.dumbermini.apps.data.model.VersionInfo
import com.dumbermini.apps.sync.v2.model.RepoV2

/**
 * `enabled` flag will be kept in datastore and will be updated there only
 * `deleted` is not needed as we will delete all required data when deleting repo or disabling it
 * */
@Entity(tableName = "repository")
data class RepoEntity(
    val address: String,
    val webBaseUrl: String?,
    val fingerprint: Fingerprint,
    val timestamp: Long?,
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
)

fun RepoV2.repoEntity(
    id: Int,
    fingerprint: Fingerprint,
) = RepoEntity(
    id = id,
    address = address,
    timestamp = timestamp,
    fingerprint = fingerprint,
    webBaseUrl = webBaseUrl,
)

fun RepoEntity.toRepo(
    name: String,
    description: String,
    icon: String?,
    mirrors: List<String>,
    enabled: Boolean,
    authentication: Authentication?,
) = Repo(
    icon = FilePath(address, icon),
    name = name,
    description = Html(description),
    fingerprint = fingerprint,
    authentication = authentication,
    enabled = enabled,
    address = address,
    versionInfo = timestamp?.let { VersionInfo(timestamp = it, etag = null) },
    mirrors = mirrors,
    id = id,
)
