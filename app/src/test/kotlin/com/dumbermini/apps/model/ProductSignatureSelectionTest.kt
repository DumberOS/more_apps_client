package com.dumbermini.apps.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProductSignatureSelectionTest {

    @Test
    fun `canUpdate allows unknown installed signature`() {
        val installedItem = InstalledItem(
            packageName = "com.google.android.gms",
            version = "1.0",
            versionCode = 1,
            signature = "",
        )
        val product = product(
            packageName = installedItem.packageName,
            versionCode = 2,
            signature = "repo-signature",
        )

        assertTrue(product.canUpdate(installedItem))
    }

    @Test
    fun `canUpdate rejects known signature mismatch`() {
        val installedItem = InstalledItem(
            packageName = "com.google.android.gms",
            version = "1.0",
            versionCode = 1,
            signature = "installed-signature",
        )
        val product = product(
            packageName = installedItem.packageName,
            versionCode = 2,
            signature = "repo-signature",
        )

        assertFalse(product.canUpdate(installedItem))
    }

    @Test
    fun `findSuggested keeps matching signature preference when known`() {
        val installedItem = InstalledItem(
            packageName = "com.google.android.gms",
            version = "1.0",
            versionCode = 1,
            signature = "matching-signature",
        )
        val matchingProduct = product(
            packageName = installedItem.packageName,
            versionCode = 2,
            signature = "matching-signature",
        )
        val mismatchingProduct = product(
            packageName = installedItem.packageName,
            versionCode = 3,
            signature = "other-signature",
        )

        val suggested = listOf(
            matchingProduct to repository(),
            mismatchingProduct to repository(),
        ).findSuggested(installedItem)

        assertNotNull(suggested)
        assertEquals(matchingProduct.versionCode, suggested!!.first.versionCode)
    }

    @Test
    fun `findSuggested does not exclude releases when installed signature is unknown`() {
        val installedItem = InstalledItem(
            packageName = "com.google.android.gms",
            version = "1.0",
            versionCode = 1,
            signature = "",
        )
        val product = product(
            packageName = installedItem.packageName,
            versionCode = 2,
            signature = "repo-signature",
        )

        val suggested = listOf(product to repository()).findSuggested(installedItem)

        assertNotNull(suggested)
        assertEquals(product.versionCode, suggested!!.first.versionCode)
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
