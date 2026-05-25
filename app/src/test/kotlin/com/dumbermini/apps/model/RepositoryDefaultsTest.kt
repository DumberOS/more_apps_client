package com.dumbermini.apps.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RepositoryDefaultsTest {

    @Test
    fun `microg repository is enabled by default on non-gapps builds`() {
        val microgRepo = Repository.defaultRepositories(microgEnabled = true)
            .find { it.address == Repository.MICROG_REPO_ADDRESS }

        assertNotNull(microgRepo)
        assertTrue(microgRepo!!.enabled)
    }

    @Test
    fun `microg repository is disabled by default on gapps builds`() {
        val microgRepo = Repository.defaultRepositories(microgEnabled = false)
            .find { it.address == Repository.MICROG_REPO_ADDRESS }

        assertNotNull(microgRepo)
        assertFalse(microgRepo!!.enabled)
    }
}
