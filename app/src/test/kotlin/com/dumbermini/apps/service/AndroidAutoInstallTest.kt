package com.dumbermini.apps.service

import com.dumbermini.apps.model.Product
import com.dumbermini.apps.model.Release
import com.dumbermini.apps.model.Repository
import com.dumbermini.apps.utility.extension.selectDownload
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AndroidAutoInstallTest {
    private val packages = AndroidAutoInstall.dependencies.keys.toList()

    private fun available() = packages.associateWith {
        listOf(product(it, 1, "signature") to repository()).selectDownload(null)!!
    }

    @Test
    fun `missing dependencies are queued in the requested order`() {
        val plan = AndroidAutoInstall.planDependencies(emptySet(), emptySet(), available(), 100)
        assertEquals(packages, plan.downloads.keys.toList())
        assertTrue(plan.unavailable.isEmpty())
    }

    @Test
    fun `installed and active dependencies are skipped without errors`() {
        val plan = AndroidAutoInstall.planDependencies(setOf(packages[0]), setOf(packages[1]), available(), 100)
        assertEquals(listOf(packages[2]), plan.downloads.keys.toList())
        assertTrue(plan.unavailable.isEmpty())
        val allInstalled = AndroidAutoInstall.planDependencies(packages.toSet(), emptySet(), emptyMap(), 0)
        assertTrue(allInstalled.downloads.isEmpty())
        assertTrue(allInstalled.unavailable.isEmpty())
    }

    @Test
    fun `missing and incompatible apps are reported while other apps proceed`() {
        val available = available().toMutableMap()
        available.remove(packages[0])
        val incompatible = available.getValue(packages[1])
        available[packages[1]] = incompatible.copy(release = incompatible.release.copy(
            incompatibilities = listOf(Release.Incompatibility.Platform),
        ))
        val plan = AndroidAutoInstall.planDependencies(emptySet(), emptySet(), available, 100)
        assertEquals(listOf(packages[2]), plan.downloads.keys.toList())
        assertEquals(listOf("Google Maps", "Google Searchbox"), plan.unavailable)
    }

    @Test
    fun `insufficient space is reported for affected dependencies`() {
        val available = available().toMutableMap()
        val large = available.getValue(packages[0])
        available[packages[0]] = large.copy(release = large.release.copy(size = 101))
        val plan = AndroidAutoInstall.planDependencies(emptySet(), emptySet(), available, 100)
        assertEquals(packages.drop(1), plan.downloads.keys.toList())
        assertEquals(listOf("Google Maps"), plan.unavailable)
    }

    @Test
    fun `shared selection uses newest suggested product and rejects missing releases`() {
        val old = product(packages[0], 1, "signature")
        val newer = product(packages[0], 2, "signature")
        assertEquals(2L, listOf(old to repository(), newer to repository()).selectDownload(null)!!.release.versionCode)
        assertNull(listOf(old.copy(releases = emptyList()) to repository()).selectDownload(null))
        assertNull(emptyList<Pair<Product, Repository>>().selectDownload(null))
    }

    private fun product(
        packageName: String,
        versionCode: Long,
        signature: String,
    ): Product {
        val release = Release(
            selected = true,
            version = versionCode.toString(),
            versionCode = versionCode,
            added = 0L,
            size = 1L,
            minSdkVersion = 23,
            targetSdkVersion = 36,
            maxSdkVersion = 0,
            source = "",
            release = "$packageName-$versionCode.apk",
            hash = "hash-$versionCode",
            hashType = "sha256",
            signature = signature,
            obbMain = "",
            obbMainHash = "",
            obbMainHashType = "",
            obbPatch = "",
            obbPatchHash = "",
            obbPatchHashType = "",
            permissions = emptyList(),
            features = emptyList(),
            platforms = listOf("android"),
            incompatibilities = emptyList(),
        )
        return Product(
            repositoryId = 1L,
            packageName = packageName,
            name = packageName,
            summary = "",
            description = "",
            whatsNew = "",
            icon = "",
            metadataIcon = "",
            author = Product.Author("", "", ""),
            source = "",
            changelog = "",
            web = "",
            tracker = "",
            added = 0L,
            updated = 0L,
            popularity = 0L,
            suggestedVersionCode = versionCode,
            categories = emptyList(),
            antiFeatures = emptyList(),
            licenses = emptyList(),
            donates = emptyList(),
            screenshots = emptyList(),
            releases = listOf(release),
        )
    }

    private fun repository(): Repository {
        return Repository.defaultRepository(
            address = "https://example.com/fdroid/repo",
            name = "Example",
            description = "",
            enabled = true,
            fingerprint = "fingerprint",
        )
    }
}
