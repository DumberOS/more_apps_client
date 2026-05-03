package com.dumbermini.apps.di

import com.dumbermini.apps.BuildConfig.BUILD_TYPE
import com.dumbermini.apps.BuildConfig.VERSION_NAME
import com.dumbermini.apps.datastore.SettingsRepository
import com.dumbermini.apps.datastore.model.ProxyPreference
import com.dumbermini.apps.datastore.model.ProxyType
import com.dumbermini.apps.network.Downloader
import com.dumbermini.apps.network.KtorDownloader
import com.dumbermini.apps.utility.common.log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import java.net.InetSocketAddress
import java.net.Proxy
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Singleton
    @Provides
    fun provideHttpClient(settingsRepository: SettingsRepository): HttpClient {
        val proxyPreference = runBlocking { settingsRepository.getInitial().proxy }
        val engine = OkHttp.create { proxy = proxyPreference.toProxy() }
        return HttpClient(engine) {
            install(UserAgent) {
                agent = "More Apps/${VERSION_NAME}-${BUILD_TYPE}"
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000L
                socketTimeoutMillis = 15_000L
            }
        }
    }

    @Singleton
    @Provides
    fun provideDownloader(
        httpClient: HttpClient,
        @IoDispatcher
        dispatcher: CoroutineDispatcher,
    ): Downloader = KtorDownloader(
        client = httpClient,
        dispatcher = dispatcher,
    )
}

private fun ProxyPreference.toProxy(): Proxy {
    val socketAddress = when (type) {
        ProxyType.DIRECT -> null
        ProxyType.HTTP, ProxyType.SOCKS -> {
            try {
                InetSocketAddress.createUnresolved(host, port)
            } catch (e: IllegalArgumentException) {
                log(e)
                null
            }
        }
    }
    val androidProxyType = when (type) {
        ProxyType.DIRECT -> Proxy.Type.DIRECT
        ProxyType.HTTP -> Proxy.Type.HTTP
        ProxyType.SOCKS -> Proxy.Type.SOCKS
    }
    return socketAddress?.let { Proxy(androidProxyType, it) } ?: Proxy.NO_PROXY
}
