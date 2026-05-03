package com.dumbermini.apps.data.local.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dumbermini.apps.data.model.Author
import com.dumbermini.apps.sync.v2.model.MetadataV2

@Entity(
    tableName = "author",
    indices = [Index("email", "name", "website", unique = true)],
)
data class AuthorEntity(
    val email: String?,
    val name: String?,
    val website: String?,
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
)

fun MetadataV2.authorEntity() = AuthorEntity(
    email = authorEmail,
    name = authorName,
    website = authorWebSite,
)

fun AuthorEntity.toAuthor() = Author(
    email = email,
    name = name,
    phone = null,
    web = website,
    id = id,
)
