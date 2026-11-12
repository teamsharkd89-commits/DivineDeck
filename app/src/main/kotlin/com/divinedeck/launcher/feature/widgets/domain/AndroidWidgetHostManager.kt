package com.divinedeck.launcher.feature.widgets.domain

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.divinedeck.launcher.feature.widgets.domain.model.DiscoveredWidgetProvider
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetCandidateType
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetSlot
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato para gestión universal de los dos espacios de widgets en DIVINE DECK
 * (WIDGET SUPERIOR y WIDGET INFERIOR), descubrimiento genérico y vinculación dinámica (Fase 05.2.1).
 */
interface AndroidWidgetHostManager {
    val configuredSlots: StateFlow<List<WidgetSlot>>
    val isListening: StateFlow<Boolean>
    val isOperational: StateFlow<Boolean>
    val allocatedWidgetIds: StateFlow<Set<Int>>
    val initializationError: Throwable?

    fun getHostId(): Int
    fun startListening()
    fun stopListening()

    fun allocateAppWidgetId(): Int
    fun deleteAppWidgetId(appWidgetId: Int)

    // Descubrimiento universal de proveedores (Fase 05.2.1)
    fun getInstalledWidgetProviders(): List<AppWidgetProviderInfo>
    fun discoverProviders(): List<DiscoveredWidgetProvider>
    fun getDiscoveredProvider(component: ComponentName): DiscoveredWidgetProvider?
    fun getDiscoveredProviderByComponent(packageName: String, className: String): DiscoveredWidgetProvider?
    fun getDiscoveredProviderForSlot(slotId: Int): DiscoveredWidgetProvider?
    fun getProviderInfoForSlot(slotId: Int): AppWidgetProviderInfo?
    fun getAppWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo?

    // Flujo de vinculación dinámica y transaccional (Fase 05.2.1)
    fun startBindingFlow(activity: Activity, slotId: Int, provider: DiscoveredWidgetProvider): Boolean
    fun startBindingFlow(activity: Activity, slotId: Int): Boolean
    fun handleActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?): Boolean
    fun unbindSlot(slotId: Int)

    // Reasignación y traslado seguro entre ranuras (Fase 05.2.1)
    fun swapSlots(): Boolean
    fun moveWidget(fromSlotId: Int, toSlotId: Int): Boolean

    // Compatibilidad y mantenimiento
    fun bindWidgetToSlot(slotId: Int, appWidgetId: Int)
    fun removeWidgetFromSlot(slotId: Int)
    fun assignCandidateToSlot(slotId: Int, candidate: WidgetCandidateType)
    fun clearSlot(slotId: Int)
    fun refreshAndRestoreBindings()

    // Gestión segura de caché de vistas (Fase 05.1.1)
    fun getOrCreateHostView(
        context: Context,
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo
    ): AppWidgetHostView?

    fun prepareHostViewForTransfer(appWidgetId: Int): AppWidgetHostView?
    fun releaseHostView(appWidgetId: Int)
    fun clearHostViews()
}
