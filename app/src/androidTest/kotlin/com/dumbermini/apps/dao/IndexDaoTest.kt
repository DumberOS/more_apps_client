package com.dumbermini.apps.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dumbermini.apps.data.local.DroidifyDatabase
import com.dumbermini.apps.data.local.dao.IndexDao
import com.dumbermini.apps.data.model.Fingerprint
import com.dumbermini.apps.sync.JsonParser
import com.dumbermini.apps.sync.common.Izzy
import com.dumbermini.apps.sync.common.assets
import com.dumbermini.apps.sync.common.benchmark
import com.dumbermini.apps.sync.v2.model.IndexV2
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class IndexDaoTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var dispatcher: CoroutineDispatcher

    @Inject
    lateinit var database: DroidifyDatabase

    @Inject
    lateinit var dao: IndexDao

    private lateinit var index: IndexV2
    private val fingerprint: Fingerprint = Izzy.fingerprint!!

    @OptIn(ExperimentalSerializationApi::class)
    @Before
    fun setUp() {
        hiltRule.inject()
        dispatcher = StandardTestDispatcher()
        index = JsonParser.decodeFromStream<IndexV2>(assets("izzy_index_v2.json"))
    }

    @Test
    fun benchmark_insert_full_new() = runTest(dispatcher, timeout = 5.minutes) {
        val output =
            benchmark(5, extraMessage = "IndexDao.insertIndex – empty DB (fresh insert)") {
                database.clearAllTables()
                measureTimeMillis {
                    dao.insertIndex(
                        fingerprint = fingerprint,
                        index = index,
                    )
                }
            }
        println(output)
    }
}