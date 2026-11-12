package com.divinedeck.launcher.feature.widgets.data

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup

/**
 * Contrato puente para desacoplar el AppWidgetHost nativo de Android
 * y permitir una gestión singleton segura, pruebas unitarias y manejo de fallos (Fase 05.2).
 */
interface AppWidgetHostBridge {
    val hostId: Int
    val isListening: Boolean
    val isOperational: Boolean get() = true
    val initializationError: Throwable? get() = null

    fun startListening()
    fun stopListening()
    fun allocateAppWidgetId(): Int
    fun deleteAppWidgetId(appWidgetId: Int)
    fun createView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo
    ): AppWidgetHostView?

    fun startAppWidgetConfigureActivityForResult(
        activity: Activity,
        appWidgetId: Int,
        intentFlags: Int,
        requestCode: Int,
        options: Bundle? = null
    )
}

/**
 * Fábrica y contenedor Singleton por proceso de AppWidgetHost.
 * Garantiza una única instancia del host con HOST_ID = 0xDD05 (56581).
 * En producción exige el host real de Android; ante un fallo de inicialización no
 * recurre a fallbacks simulados con IDs ficticios, sino que expone el estado no operativo.
 */
object DivineAppWidgetHostFactory {
    private const val TAG = "DivineAppWidgetHost"

    /** Identificador de host automotriz estable para DIVINE DECK (0xDD05 = 56581). */
    const val HOST_ID: Int = 0xDD05

    @Volatile
    private var instance: AppWidgetHostBridge? = null
    private val lock = Any()

    fun getOrCreateHost(
        context: Context,
        customFactory: ((Context, Int) -> AppWidgetHostBridge)? = null
    ): AppWidgetHostBridge {
        return instance ?: synchronized(lock) {
            instance ?: run {
                val appContext = context.applicationContext ?: context
                val created = try {
                    customFactory?.invoke(appContext, HOST_ID) ?: RealAppWidgetHostBridge(appContext, HOST_ID)
                } catch (e: Throwable) {
                    Log.e(TAG, "Error crítico: no se pudo inicializar RealAppWidgetHostBridge (0xDD05)", e)
                    UnoperationalAppWidgetHostBridge(HOST_ID, initializationError = e)
                }
                instance = created
                created
            }
        }
    }

    fun getInstance(): AppWidgetHostBridge? = instance

    fun resetForTesting(customInstance: AppWidgetHostBridge? = null) {
        synchronized(lock) {
            try {
                instance?.stopListening()
            } catch (_: Throwable) {}
            instance = customInstance
        }
    }
}

/**
 * Implementación real conectada al framework AppWidgetHost de Android.
 */
class RealAppWidgetHostBridge(
    context: Context,
    override val hostId: Int = DivineAppWidgetHostFactory.HOST_ID
) : AppWidgetHostBridge {
    private val host: AppWidgetHost = AppWidgetHost(context, hostId)
    private var _isListening: Boolean = false
    override val isListening: Boolean get() = _isListening
    override val isOperational: Boolean get() = true

    override fun startListening() {
        if (_isListening) return
        try {
            host.startListening()
            _isListening = true
        } catch (_: Throwable) {
            _isListening = false
        }
    }

    override fun stopListening() {
        if (!_isListening) return
        try {
            host.stopListening()
            _isListening = false
        } catch (_: Throwable) {
            _isListening = false
        }
    }

    override fun allocateAppWidgetId(): Int {
        return host.allocateAppWidgetId()
    }

    override fun deleteAppWidgetId(appWidgetId: Int) {
        try {
            host.deleteAppWidgetId(appWidgetId)
        } catch (_: Throwable) {}
    }

    override fun createView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo
    ): AppWidgetHostView? {
        return try {
            host.createView(context, appWidgetId, appWidget)
        } catch (_: Throwable) {
            null
        }
    }

    override fun startAppWidgetConfigureActivityForResult(
        activity: Activity,
        appWidgetId: Int,
        intentFlags: Int,
        requestCode: Int,
        options: Bundle?
    ) {
        try {
            host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, intentFlags, requestCode, options)
        } catch (e: Throwable) {
            Log.e("DivineAppWidgetHost", "Error al iniciar Activity de configuración para widgetId $appWidgetId", e)
        }
    }
}

/**
 * Implementación no operativa utilizada cuando falla la creación del AppWidgetHost real en producción
 * o cuando no se dispone de un contexto válido.
 * NO genera IDs ficticios y mantiene isOperational = false.
 */
class UnoperationalAppWidgetHostBridge(
    override val hostId: Int = DivineAppWidgetHostFactory.HOST_ID,
    override val initializationError: Throwable? = null
) : AppWidgetHostBridge {
    override val isListening: Boolean get() = false
    override val isOperational: Boolean get() = false

    override fun startListening() {
        // No-op seguro
    }

    override fun stopListening() {
        // No-op seguro
    }

    override fun allocateAppWidgetId(): Int {
        return -1
    }

    override fun deleteAppWidgetId(appWidgetId: Int) {
        // No-op seguro
    }

    override fun createView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo
    ): AppWidgetHostView? = null

    override fun startAppWidgetConfigureActivityForResult(
        activity: Activity,
        appWidgetId: Int,
        intentFlags: Int,
        requestCode: Int,
        options: Bundle?
    ) {
        // No-op seguro
    }
}

/**
 * Doble de prueba controlable explícitamente para tests unitarios.
 * Nunca se activa automáticamente en producción.
 */
class FakeAppWidgetHostBridge(
    override val hostId: Int = DivineAppWidgetHostFactory.HOST_ID,
    override val isOperational: Boolean = true,
    override val initializationError: Throwable? = null,
    val throwOnStartListening: Boolean = false,
    val throwOnAllocate: Boolean = false
) : AppWidgetHostBridge {
    var startListeningCallCount = 0
    var stopListeningCallCount = 0
    var allocateCallCount = 0
    var configureCallCount = 0
    val deletedIds = mutableListOf<Int>()
    private var _isListening = false

    override val isListening: Boolean get() = _isListening

    override fun startListening() {
        startListeningCallCount++
        if (throwOnStartListening) throw RuntimeException("Simulated host start failure")
        _isListening = true
    }

    override fun stopListening() {
        stopListeningCallCount++
        _isListening = false
    }

    override fun allocateAppWidgetId(): Int {
        allocateCallCount++
        if (throwOnAllocate) throw RuntimeException("Simulated allocation failure")
        return 1000 + allocateCallCount
    }

    override fun deleteAppWidgetId(appWidgetId: Int) {
        deletedIds.add(appWidgetId)
    }

    override fun createView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo
    ): AppWidgetHostView? = null

    override fun startAppWidgetConfigureActivityForResult(
        activity: Activity,
        appWidgetId: Int,
        intentFlags: Int,
        requestCode: Int,
        options: Bundle?
    ) {
        configureCallCount++
    }
}

/**
 * Caché de vistas de widgets (AppWidgetHostView) indexada por appWidgetId (Fase 05.1.1 / 05.2).
 */
class AppWidgetViewCache(
    private val viewDetacher: (View) -> Unit = { view ->
        (view.parent as? ViewGroup)?.removeView(view)
    }
) {
    private val cache = mutableMapOf<Int, AppWidgetHostView>()

    fun get(appWidgetId: Int): AppWidgetHostView? {
        return cache[appWidgetId]
    }

    fun put(appWidgetId: Int, view: AppWidgetHostView) {
        val existing = cache[appWidgetId]
        if (existing != null && existing !== view) {
            viewDetacher(existing)
        }
        cache[appWidgetId] = view
    }

    fun prepareForTransfer(appWidgetId: Int): AppWidgetHostView? {
        val view = cache[appWidgetId] ?: return null
        viewDetacher(view)
        return view
    }

    fun remove(appWidgetId: Int): AppWidgetHostView? {
        val view = cache.remove(appWidgetId)
        if (view != null) {
            viewDetacher(view)
        }
        return view
    }

    fun clear() {
        cache.values.forEach { view ->
            viewDetacher(view)
        }
        cache.clear()
    }

    fun contains(appWidgetId: Int): Boolean = cache.containsKey(appWidgetId)

    fun size(): Int = cache.size
}
