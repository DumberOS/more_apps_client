package com.dumbermini.apps.index

import com.dumbermini.apps.model.Product
import com.dumbermini.apps.model.Release
import com.dumbermini.apps.utility.common.extension.Json
import com.dumbermini.apps.utility.common.extension.writeDictionary
import com.dumbermini.apps.utility.serialization.product
import com.dumbermini.apps.utility.serialization.serialize
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class IndexV1ParserPopularityTest {

    @Test
    fun `parses popularity and defaults missing values to zero`() {
        val products = mutableListOf<Product>()
        val index =
            """
            {
              "repo": {"name": "Test"},
              "apps": [
                {"packageName": "com.example.popular", "name": "Popular", "popularity": 5000000, "country": "Sweden"},
                {"packageName": "com.example.unknown", "name": "Unknown"}
              ]
            }
            """.trimIndent()

        IndexV1Parser.parse(
            repositoryId = 7L,
            inputStream = index.byteInputStream(),
            callback = object : IndexV1Parser.Callback {
                override fun onRepository(
                    mirrors: List<String>,
                    name: String,
                    description: String,
                    version: Int,
                    timestamp: Long,
                ) = Unit

                override fun onProduct(product: Product) {
                    products += product
                }

                override fun onReleases(packageName: String, releases: List<Release>) = Unit
            },
        )

        assertEquals(5_000_000L, products[0].popularity)
        assertEquals(0L, products[1].popularity)
        assertEquals("Sweden", products[0].country)
        assertEquals("", products[1].country)

        val output = ByteArrayOutputStream()
        Json.factory.createGenerator(output).use { generator ->
            generator.writeDictionary(products[0]::serialize)
        }
        val restored = Json.factory.createParser(output.toByteArray()).use { parser ->
            parser.nextToken()
            parser.product()
        }
        assertEquals(5_000_000L, restored.popularity)
        assertEquals("Sweden", restored.country)
    }
}
