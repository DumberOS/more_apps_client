package com.dumbermini.apps.sync.common

import com.dumbermini.apps.sync.v1.model.AppV1
import com.dumbermini.apps.sync.v1.model.IndexV1
import com.dumbermini.apps.sync.v1.model.RepoV1
import kotlin.test.Test
import kotlin.test.assertEquals

class IndexConverterPopularityTest {

    @Test
    fun `preserves popularity while converting a v1 index`() {
        val index = IndexV1(
            repo = RepoV1(
                address = "https://example.com/fdroid/repo",
                icon = "icon.png",
                name = "Test",
                description = "",
                timestamp = 1L,
                version = 1,
            ),
            apps = listOf(
                AppV1(
                    packageName = "com.example.app",
                    license = "Unknown",
                    popularity = 5_000_000L,
                ),
            ),
        )

        assertEquals(
            5_000_000L,
            index.toV2().packages.getValue("com.example.app").metadata.popularity,
        )
    }
}
