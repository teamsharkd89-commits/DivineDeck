package com.divinedeck.launcher

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.divinedeck.launcher.feature.voice.data.CrashCatcher
import com.music.bitchord.data.Http
import com.music.bitchord.data.innertube.AndroidStreamHooks
import com.music.bitchord.data.settings.AppSettings

/**
 * Aplicación principal de DIVINE DECK.
 * Punto central de inicialización para servicios automotrices,
 * ciclo de vida global y configuración del launcher.
 */
class DivineDeckApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        // Primero que nada: el atrapador de crashes guarda el último
        // error en filesDir/crash-ultimo-error.txt (Ajustes > DIVERSIÓN).
        CrashCatcher.install(this)
        super.onCreate()
        // Inicialización temprana de subsistemas y telemetría de BitChord
        AndroidStreamHooks.installEarly()
        AppSettings.init(this)
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { Http.client }))
            }
            .crossfade(true)
            .build()
    }
}
