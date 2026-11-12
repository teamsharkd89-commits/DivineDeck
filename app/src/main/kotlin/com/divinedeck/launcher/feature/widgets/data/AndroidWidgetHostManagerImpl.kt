package com.divinedeck.launcher.feature.widgets.data

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.divinedeck.launcher.feature.widgets.domain.AndroidWidgetHostManager
import com.divinedeck.launcher.feature.widgets.domain.model.DiscoveredWidgetProvider
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetBindingStatus
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetCandidateType
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Implementación universal del gestor de widgets nativos de Android para DIVINE DECK (Fase 05.2.1).
 * 
 * Capacidades:
 * 1. Descubrimiento universal de cualquier proveedor de widgets instalado en Android (categoría HOME_SCREEN).
 * 2. Asignación dinámica a cualquiera de las dos ranuras del Cockpit (Superior / Inferior).
 * 3. Flujo transaccional de autorización con reversión segura ante cancelaciones.
 * 4. Persistencia en SharedPreferences y migración transparente de asociaciones existentes.
 * 5. Reasignación, traslado e intercambio seguro de widgets entre ranuras.
 */
class AndroidWidgetHostManagerImpl(
    private val context: Context? = null,
    hostBridge: AppWidgetHostBridge? = null,
    private val appWidgetManager: AppWidgetManager? = null,
    private val viewCache: AppWidgetViewCache = AppWidgetViewCache(),
    private val bindIfAllowedHelper: ((Int, ComponentName) -> Boolean)? = null,
    private val installedProvidersProvider: (() -> List<AppWidgetProviderInfo>)? = null
) : AndroidWidgetHostManager {

    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Resolución de host: inyección explícita para pruebas o instancia singleton real para producción
    private val hostBridge: AppWidgetHostBridge = hostBridge ?: run {
        if (context != null) {
            DivineAppWidgetHostFactory.getOrCreateHost(context)
        } else {
            UnoperationalAppWidgetHostBridge(
                DivineAppWidgetHostFactory.HOST_ID,
                initializationError = IllegalStateException("Contexto nulo: host no operativo en entorno sin contexto")
            )
        }
    }

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isOperational = MutableStateFlow(this.hostBridge.isOperational)
    override val isOperational: StateFlow<Boolean> = _isOperational.asStateFlow()

    private val _allocatedWidgetIds = MutableStateFlow<Set<Int>>(emptySet())
    override val allocatedWidgetIds: StateFlow<Set<Int>> = _allocatedWidgetIds.asStateFlow()

    override val initializationError: Throwable?
        get() = hostBridge.initializationError

    private val _configuredSlots = MutableStateFlow<List<WidgetSlot>>(
        listOf(
            WidgetSlot(slotId = 1, positionLabel = "WIDGET SUPERIOR"),
            WidgetSlot(slotId = 2, positionLabel = "WIDGET INFERIOR")
        )
    )
    override val configuredSlots: StateFlow<List<WidgetSlot>> = _configuredSlots.asStateFlow()

    // Estado en memoria para transacciones de vinculación pendientes con reversión segura
    private data class PendingBinding(
        val slotId: Int,
        val newAppWidgetId: Int,
        val targetProvider: DiscoveredWidgetProvider,
        val previousSlotState: WidgetSlot,
        val isConfiguring: Boolean = false
    )
    private var pendingBinding: PendingBinding? = null

    init {
        refreshAndRestoreBindings()
    }

    override fun getHostId(): Int = hostBridge.hostId

    override fun startListening() {
        if (!_isOperational.value || _isListening.value) return
        try {
            hostBridge.startListening()
            _isListening.value = hostBridge.isListening
        } catch (_: Throwable) {
            _isListening.value = false
        }
    }

    override fun stopListening() {
        if (!_isListening.value) return
        try {
            hostBridge.stopListening()
            _isListening.value = hostBridge.isListening
        } catch (_: Throwable) {
            _isListening.value = false
        }
    }

    override fun allocateAppWidgetId(): Int {
        if (!_isOperational.value) {
            return -1
        }
        val id = try {
            hostBridge.allocateAppWidgetId()
        } catch (_: Throwable) {
            -1
        }
        if (id > 0) {
            _allocatedWidgetIds.update { it + id }
        }
        return id
    }

    override fun deleteAppWidgetId(appWidgetId: Int) {
        if (appWidgetId <= 0) return
        releaseHostView(appWidgetId)
        _allocatedWidgetIds.update { it - appWidgetId }
        try {
            hostBridge.deleteAppWidgetId(appWidgetId)
        } catch (_: Throwable) {}
    }

    // =========================================================================
    // 1. Descubrimiento Universal de Proveedores (Fase 05.2.1)
    // =========================================================================

    override fun getInstalledWidgetProviders(): List<AppWidgetProviderInfo> {
        return try {
            installedProvidersProvider?.invoke()
                ?: (appWidgetManager ?: context?.let { AppWidgetManager.getInstance(it) })?.installedProviders
                ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override fun discoverProviders(): List<DiscoveredWidgetProvider> {
        val all = getInstalledWidgetProviders()
        return all
            .filter { info ->
                // Considerar widgets compatibles con HOME_SCREEN (widgetCategory == 0 o contiene HOME_SCREEN)
                val cat = try { info.widgetCategory } catch (_: Throwable) { 0 }
                cat == 0 || (cat and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0
            }
            .map { toDiscoveredProvider(it) }
    }

    private fun getPackageAndClass(info: AppWidgetProviderInfo): Pair<String, String> {
        testProviderRegistry[info]?.let { return Pair(it.packageName, it.className) }
        val pkg = try { info.provider?.packageName ?: "" } catch (_: Throwable) { "" }
        val cls = try { info.provider?.className ?: "" } catch (_: Throwable) { "" }
        return Pair(pkg, cls)
    }

    private fun toDiscoveredProvider(info: AppWidgetProviderInfo): DiscoveredWidgetProvider {
        val (pkg, cls) = getPackageAndClass(info)
        val testData = testProviderRegistry[info]

        // 1. Resolver nombre de aplicación
        val appName = try {
            testData?.appName?.takeIf { it.isNotBlank() }
                ?: context?.packageManager?.let { pm ->
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                }
        } catch (_: Throwable) { null } ?: testData?.appName ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }

        // 2. Resolver etiqueta del widget
        val label = try {
            testData?.label?.takeIf { it.isNotBlank() }
                ?: context?.packageManager?.let { pm ->
                    info.loadLabel(pm)?.toString()
                }
        } catch (_: Throwable) { null } ?: testData?.label ?: cls.substringAfterLast('.').ifEmpty { "Widget" }

        // 3. Resolver descripción
        val desc = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context?.let { info.loadDescription(it)?.toString() }
            } else null
        } catch (_: Throwable) { null }

        // 4. Resolver icono
        val iconDrawable = try {
            context?.packageManager?.getApplicationIcon(pkg)
        } catch (_: Throwable) { null }

        val maxW = try { info.maxResizeWidth } catch (_: Throwable) { 0 }
        val maxH = try { info.maxResizeHeight } catch (_: Throwable) { 0 }
        val minW = try { info.minWidth } catch (_: Throwable) { 0 }
        val minH = try { info.minHeight } catch (_: Throwable) { 0 }
        val minRW = try { info.minResizeWidth } catch (_: Throwable) { 0 }
        val minRH = try { info.minResizeHeight } catch (_: Throwable) { 0 }
        val resizeMode = try { info.resizeMode } catch (_: Throwable) { 0 }
        val category = try { info.widgetCategory } catch (_: Throwable) { 0 }
        val layout = try { info.initialLayout } catch (_: Throwable) { 0 }
        val preview = try { info.previewImage } catch (_: Throwable) { 0 }
        val iconRes = try { info.icon } catch (_: Throwable) { 0 }

        return DiscoveredWidgetProvider(
            provider = info.provider,
            appName = appName,
            label = label,
            description = desc,
            appIcon = iconDrawable,
            previewImage = preview,
            icon = iconRes,
            minWidth = minW,
            minHeight = minH,
            minResizeWidth = minRW,
            minResizeHeight = minRH,
            maxResizeWidth = maxW,
            maxResizeHeight = maxH,
            resizeMode = resizeMode,
            widgetCategory = category,
            initialLayout = layout,
            configure = info.configure,
            isResizeSupported = resizeMode != 0,
            packageNameOverride = if (info.provider?.packageName.isNullOrEmpty()) pkg else null,
            classNameOverride = if (info.provider?.className.isNullOrEmpty()) cls else null
        )
    }

    override fun getDiscoveredProvider(component: ComponentName): DiscoveredWidgetProvider? {
        val pkg = try { component.packageName } catch (_: Throwable) { "" }
        val cls = try { component.className } catch (_: Throwable) { "" }
        return getDiscoveredProviderByComponent(pkg, cls)
    }

    override fun getDiscoveredProviderByComponent(packageName: String, className: String): DiscoveredWidgetProvider? {
        return discoverProviders().find {
            it.packageName.equals(packageName, ignoreCase = true) &&
            it.className.equals(className, ignoreCase = true)
        }
    }

    override fun getDiscoveredProviderForSlot(slotId: Int): DiscoveredWidgetProvider? {
        val slot = _configuredSlots.value.find { it.slotId == slotId } ?: return null
        val pkg = slot.providerPackage ?: return null
        val cls = slot.providerClass ?: return null
        return getDiscoveredProviderByComponent(pkg, cls)
    }

    override fun getAppWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo? {
        if (appWidgetId <= 0) return null
        return try {
            (appWidgetManager ?: context?.let { AppWidgetManager.getInstance(it) })?.getAppWidgetInfo(appWidgetId)
        } catch (_: Throwable) {
            null
        }
    }

    override fun getProviderInfoForSlot(slotId: Int): AppWidgetProviderInfo? {
        val slot = _configuredSlots.value.find { it.slotId == slotId } ?: return null
        val id = slot.appWidgetId
        if (id != null && id > 0) {
            val fromManager = getAppWidgetInfo(id)
            if (fromManager != null) return fromManager
        }
        val pkg = slot.providerPackage ?: return null
        val cls = slot.providerClass ?: return null
        return getInstalledWidgetProviders().find {
            val (p, c) = getPackageAndClass(it)
            p.equals(pkg, ignoreCase = true) && c.equals(cls, ignoreCase = true)
        }
    }

    // =========================================================================
    // 2. Flujo de Vinculación Dinámico y Transaccional (Fase 05.2.1)
    // =========================================================================

    override fun startBindingFlow(activity: Activity, slotId: Int): Boolean {
        val currentSlot = _configuredSlots.value.find { it.slotId == slotId } ?: return false
        val provider = currentSlot.discoveredProvider ?: getDiscoveredProviderForSlot(slotId)
        if (provider == null) {
            _configuredSlots.update { list ->
                list.map { if (it.slotId == slotId) it.copy(bindingStatus = WidgetBindingStatus.PROVIDER_NOT_FOUND) else it }
            }
            return false
        }
        return startBindingFlow(activity, slotId, provider)
    }

    override fun startBindingFlow(
        activity: Activity,
        slotId: Int,
        provider: DiscoveredWidgetProvider
    ): Boolean {
        if (!_isOperational.value) {
            _configuredSlots.update { list ->
                list.map { if (it.slotId == slotId) it.copy(bindingStatus = WidgetBindingStatus.HOST_NOT_OPERATIONAL) else it }
            }
            return false
        }

        if (slotId != 1 && slotId != 2) {
            return false
        }

        val previousSlot = _configuredSlots.value.find { it.slotId == slotId } ?: WidgetSlot(
            slotId = slotId,
            positionLabel = if (slotId == 1) "WIDGET SUPERIOR" else "WIDGET INFERIOR"
        )

        // 1. Asignar nuevo ID provisional
        val newWidgetId = allocateAppWidgetId()
        if (newWidgetId <= 0) {
            _configuredSlots.update { list ->
                list.map { if (it.slotId == slotId) it.copy(bindingStatus = WidgetBindingStatus.BINDING_ERROR) else it }
            }
            return false
        }

        // Registrar transacción pendiente con estado previo para reversión segura
        pendingBinding = PendingBinding(
            slotId = slotId,
            newAppWidgetId = newWidgetId,
            targetProvider = provider,
            previousSlotState = previousSlot,
            isConfiguring = false
        )

        // 2. Comprobar autorización con el sistema operativo
        val component = provider.provider ?: ComponentName(provider.packageName, provider.className)
        val isAllowed = try {
            bindIfAllowedHelper?.invoke(newWidgetId, component)
                ?: (appWidgetManager ?: AppWidgetManager.getInstance(activity)).bindAppWidgetIdIfAllowed(newWidgetId, component)
        } catch (_: Throwable) {
            false
        }

        if (isAllowed) {
            // Permiso ya concedido por el SO
            if (provider.configure != null) {
                pendingBinding = pendingBinding?.copy(isConfiguring = true)
                _configuredSlots.update { list ->
                    list.map {
                        if (it.slotId == slotId) {
                            it.copy(
                                bindingStatus = WidgetBindingStatus.REQUIRES_CONFIGURATION,
                                discoveredProvider = provider
                            )
                        } else it
                    }
                }
                hostBridge.startAppWidgetConfigureActivityForResult(
                    activity,
                    newWidgetId,
                    0,
                    REQUEST_CONFIGURE_APPWIDGET,
                    null
                )
                return true
            } else {
                completeBinding(slotId, newWidgetId, provider, previousSlot)
                pendingBinding = null
                return true
            }
        } else {
            // Se requiere autorización del usuario: despachar ACTION_APPWIDGET_BIND
            _configuredSlots.update { list ->
                list.map {
                    if (it.slotId == slotId) {
                        it.copy(
                            bindingStatus = WidgetBindingStatus.AWAITING_AUTHORIZATION,
                            discoveredProvider = provider
                        )
                    } else it
                }
            }
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component)
            }
            try {
                activity.startActivityForResult(intent, REQUEST_BIND_APPWIDGET)
                return true
            } catch (_: Throwable) {
                deleteAppWidgetId(newWidgetId)
                _configuredSlots.update { list ->
                    list.map { if (it.slotId == slotId) previousSlot else it }
                }
                pendingBinding = null
                return false
            }
        }
    }

    override fun handleActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        when (requestCode) {
            REQUEST_BIND_APPWIDGET -> {
                val pending = pendingBinding ?: return false
                if (resultCode == Activity.RESULT_OK) {
                    if (pending.targetProvider.configure != null) {
                        pendingBinding = pending.copy(isConfiguring = true)
                        _configuredSlots.update { list ->
                            list.map {
                                if (it.slotId == pending.slotId) {
                                    it.copy(
                                        bindingStatus = WidgetBindingStatus.REQUIRES_CONFIGURATION,
                                        discoveredProvider = pending.targetProvider
                                    )
                                } else it
                            }
                        }
                        hostBridge.startAppWidgetConfigureActivityForResult(
                            activity,
                            pending.newAppWidgetId,
                            0,
                            REQUEST_CONFIGURE_APPWIDGET,
                            null
                        )
                    } else {
                        completeBinding(pending.slotId, pending.newAppWidgetId, pending.targetProvider, pending.previousSlotState)
                        pendingBinding = null
                    }
                } else {
                    // Usuario canceló la autorización: reversión limpia preservando el widget anterior
                    deleteAppWidgetId(pending.newAppWidgetId)
                    _configuredSlots.update { list ->
                        list.map { if (it.slotId == pending.slotId) pending.previousSlotState else it }
                    }
                    pendingBinding = null
                }
                return true
            }
            REQUEST_CONFIGURE_APPWIDGET -> {
                val pending = pendingBinding ?: return false
                if (resultCode == Activity.RESULT_OK) {
                    completeBinding(pending.slotId, pending.newAppWidgetId, pending.targetProvider, pending.previousSlotState)
                    pendingBinding = null
                } else {
                    // Configuración cancelada: reversión limpia preservando el widget anterior
                    deleteAppWidgetId(pending.newAppWidgetId)
                    _configuredSlots.update { list ->
                        list.map { if (it.slotId == pending.slotId) pending.previousSlotState else it }
                    }
                    pendingBinding = null
                }
                return true
            }
            else -> return false
        }
    }

    private fun completeBinding(
        slotId: Int,
        newAppWidgetId: Int,
        provider: DiscoveredWidgetProvider,
        previousSlot: WidgetSlot?
    ) {
        // Liberar ID anterior si la ranura ya tenía un widget diferente
        val oldId = previousSlot?.appWidgetId
        if (oldId != null && oldId > 0 && oldId != newAppWidgetId) {
            val otherSlot = _configuredSlots.value.find { it.slotId != slotId }
            if (otherSlot?.appWidgetId != oldId) {
                deleteAppWidgetId(oldId)
            }
        }

        _allocatedWidgetIds.update { it + newAppWidgetId }

        val newSlot = WidgetSlot(
            slotId = slotId,
            positionLabel = if (slotId == 1) "WIDGET SUPERIOR" else "WIDGET INFERIOR",
            assignedCandidate = WidgetCandidateType.GENERIC_WIDGET,
            appWidgetId = newAppWidgetId,
            providerPackage = provider.packageName,
            providerClass = provider.className,
            bindingStatus = WidgetBindingStatus.BOUND,
            discoveredProvider = provider,
            title = provider.label.ifBlank { provider.shortLabel },
            appName = provider.appName
        )

        _configuredSlots.update { list ->
            list.map { if (it.slotId == slotId) newSlot else it }
        }

        saveSlotBinding(newSlot)
    }

    override fun unbindSlot(slotId: Int) {
        clearSlot(slotId)
    }

    // =========================================================================
    // 3. Reasignación y Traslado Seguro entre Ranuras (Fase 05.2.1)
    // =========================================================================

    override fun swapSlots(): Boolean {
        val slots = _configuredSlots.value
        val slot1 = slots.find { it.slotId == 1 } ?: return false
        val slot2 = slots.find { it.slotId == 2 } ?: return false

        val newSlot1 = slot2.copy(slotId = 1, positionLabel = "WIDGET SUPERIOR")
        val newSlot2 = slot1.copy(slotId = 2, positionLabel = "WIDGET INFERIOR")

        _configuredSlots.value = listOf(newSlot1, newSlot2)
        saveSlotBinding(newSlot1)
        saveSlotBinding(newSlot2)
        return true
    }

    override fun moveWidget(fromSlotId: Int, toSlotId: Int): Boolean {
        if (fromSlotId == toSlotId) return true
        val slots = _configuredSlots.value
        val fromSlot = slots.find { it.slotId == fromSlotId } ?: return false
        val toSlot = slots.find { it.slotId == toSlotId } ?: return false

        if (toSlot.isEmpty) {
            val newToSlot = fromSlot.copy(
                slotId = toSlotId,
                positionLabel = if (toSlotId == 1) "WIDGET SUPERIOR" else "WIDGET INFERIOR"
            )
            val newFromSlot = WidgetSlot(
                slotId = fromSlotId,
                positionLabel = if (fromSlotId == 1) "WIDGET SUPERIOR" else "WIDGET INFERIOR",
                bindingStatus = WidgetBindingStatus.AVAILABLE_TO_BIND
            )
            _configuredSlots.value = listOf(
                if (newFromSlot.slotId == 1) newFromSlot else newToSlot,
                if (newFromSlot.slotId == 2) newFromSlot else newToSlot
            )
            saveSlotBinding(newToSlot)
            clearSlotPersistence(fromSlotId)
            return true
        } else {
            return swapSlots()
        }
    }

    // =========================================================================
    // 4. Persistencia, Migración y Consistencia (Fase 05.2.1)
    // =========================================================================

    private fun saveSlotBinding(slot: WidgetSlot) {
        val slotId = slot.slotId
        val pKey = if (slotId == 1) KEY_SLOT_1_PROVIDER_PACKAGE else KEY_SLOT_2_PROVIDER_PACKAGE
        val cKey = if (slotId == 1) KEY_SLOT_1_PROVIDER_CLASS else KEY_SLOT_2_PROVIDER_CLASS
        val idKey = if (slotId == 1) KEY_SLOT_1_APPWIDGET_ID else KEY_SLOT_2_APPWIDGET_ID
        val bKey = if (slotId == 1) KEY_SLOT_1_BOUND else KEY_SLOT_2_BOUND
        val candKey = if (slotId == 1) KEY_SLOT_1_CANDIDATE else KEY_SLOT_2_CANDIDATE
        val titleKey = if (slotId == 1) KEY_SLOT_1_TITLE else KEY_SLOT_2_TITLE
        val appNameKey = if (slotId == 1) KEY_SLOT_1_APP_NAME else KEY_SLOT_2_APP_NAME

        val editor = prefs?.edit() ?: return
        if (slot.providerPackage != null) editor.putString(pKey, slot.providerPackage) else editor.remove(pKey)
        if (slot.providerClass != null) editor.putString(cKey, slot.providerClass) else editor.remove(cKey)
        if (slot.appWidgetId != null) editor.putInt(idKey, slot.appWidgetId) else editor.remove(idKey)
        editor.putBoolean(bKey, slot.isBound)
        editor.putString(candKey, slot.assignedCandidate.id)
        if (slot.title.isNotBlank()) editor.putString(titleKey, slot.title) else editor.remove(titleKey)
        if (slot.appName != null) editor.putString(appNameKey, slot.appName) else editor.remove(appNameKey)
        editor.apply()
    }

    private fun clearSlotPersistence(slotId: Int) {
        val pKey = if (slotId == 1) KEY_SLOT_1_PROVIDER_PACKAGE else KEY_SLOT_2_PROVIDER_PACKAGE
        val cKey = if (slotId == 1) KEY_SLOT_1_PROVIDER_CLASS else KEY_SLOT_2_PROVIDER_CLASS
        val idKey = if (slotId == 1) KEY_SLOT_1_APPWIDGET_ID else KEY_SLOT_2_APPWIDGET_ID
        val bKey = if (slotId == 1) KEY_SLOT_1_BOUND else KEY_SLOT_2_BOUND
        val candKey = if (slotId == 1) KEY_SLOT_1_CANDIDATE else KEY_SLOT_2_CANDIDATE
        val titleKey = if (slotId == 1) KEY_SLOT_1_TITLE else KEY_SLOT_2_TITLE
        val appNameKey = if (slotId == 1) KEY_SLOT_1_APP_NAME else KEY_SLOT_2_APP_NAME

        prefs?.edit()
            ?.remove(pKey)
            ?.remove(cKey)
            ?.remove(idKey)
            ?.remove(bKey)
            ?.remove(candKey)
            ?.remove(titleKey)
            ?.remove(appNameKey)
            ?.apply()
    }

    override fun refreshAndRestoreBindings() {
        val allDiscovered = discoverProviders()
        _configuredSlots.update {
            listOf(
                restoreSlot(1, "WIDGET SUPERIOR", allDiscovered),
                restoreSlot(2, "WIDGET INFERIOR", allDiscovered)
            )
        }
    }

    private fun restoreSlot(slotId: Int, label: String, allDiscovered: List<DiscoveredWidgetProvider>): WidgetSlot {
        val idKey = if (slotId == 1) KEY_SLOT_1_APPWIDGET_ID else KEY_SLOT_2_APPWIDGET_ID
        val pKey = if (slotId == 1) KEY_SLOT_1_PROVIDER_PACKAGE else KEY_SLOT_2_PROVIDER_PACKAGE
        val cKey = if (slotId == 1) KEY_SLOT_1_PROVIDER_CLASS else KEY_SLOT_2_PROVIDER_CLASS
        val bKey = if (slotId == 1) KEY_SLOT_1_BOUND else KEY_SLOT_2_BOUND
        val candKey = if (slotId == 1) KEY_SLOT_1_CANDIDATE else KEY_SLOT_2_CANDIDATE
        val titleKey = if (slotId == 1) KEY_SLOT_1_TITLE else KEY_SLOT_2_TITLE
        val appNameKey = if (slotId == 1) KEY_SLOT_1_APP_NAME else KEY_SLOT_2_APP_NAME

        val storedId = prefs?.getInt(idKey, -1) ?: -1
        var storedPackage = prefs?.getString(pKey, null)
        var storedClass = prefs?.getString(cKey, null)
        val isBound = prefs?.getBoolean(bKey, false) ?: false
        val storedCandidate = prefs?.getString(candKey, null)
        val storedTitle = prefs?.getString(titleKey, null)
        val storedAppName = prefs?.getString(appNameKey, null)

        // 1. MIGRACIÓN RETROCOMPATIBLE (Fase 5.2 -> 5.2.1):
        // Si no hay package/class explícito pero existe un candidate histórico:
        if (storedPackage == null && storedCandidate != null) {
            val matchingCandidate = when {
                storedCandidate.contains("bitchord", ignoreCase = true) ->
                    allDiscovered.find { it.packageName.contains("bitchord", ignoreCase = true) }
                storedCandidate.contains("fuel", ignoreCase = true) ->
                    allDiscovered.find { it.packageName.contains("fuel", ignoreCase = true) }
                else -> null
            }
            if (matchingCandidate != null) {
                storedPackage = matchingCandidate.packageName
                storedClass = matchingCandidate.className
            }
        }

        if (!_isOperational.value) {
            return WidgetSlot(
                slotId = slotId,
                positionLabel = label,
                bindingStatus = WidgetBindingStatus.HOST_NOT_OPERATIONAL
            )
        }

        if (isBound && storedId > 0 && storedPackage != null && storedClass != null) {
            // 2. Verificar si el proveedor específico continúa instalado
            val discovered = allDiscovered.find {
                it.packageName.equals(storedPackage, ignoreCase = true) &&
                it.className.equals(storedClass, ignoreCase = true)
            }

            if (discovered != null) {
                _allocatedWidgetIds.update { it + storedId }
                return WidgetSlot(
                    slotId = slotId,
                    positionLabel = label,
                    assignedCandidate = WidgetCandidateType.GENERIC_WIDGET,
                    appWidgetId = storedId,
                    providerPackage = storedPackage,
                    providerClass = storedClass,
                    bindingStatus = WidgetBindingStatus.BOUND,
                    discoveredProvider = discovered,
                    title = storedTitle ?: discovered.label.ifBlank { discovered.shortLabel },
                    appName = storedAppName ?: discovered.appName
                )
            } else {
                // Proveedor desinstalado: purgar claves huérfanas
                clearSlotPersistence(slotId)
                return WidgetSlot(
                    slotId = slotId,
                    positionLabel = label,
                    bindingStatus = WidgetBindingStatus.UNINSTALLED
                )
            }
        } else {
            return WidgetSlot(
                slotId = slotId,
                positionLabel = label,
                bindingStatus = WidgetBindingStatus.AVAILABLE_TO_BIND
            )
        }
    }

    // =========================================================================
    // 5. Gestión y Compatibilidad
    // =========================================================================

    override fun bindWidgetToSlot(slotId: Int, appWidgetId: Int) {
        _allocatedWidgetIds.update { it + appWidgetId }
        val provider = getDiscoveredProviderForSlot(slotId)
        _configuredSlots.update { list ->
            list.map { slot ->
                if (slot.slotId == slotId) {
                    slot.copy(
                        appWidgetId = appWidgetId,
                        bindingStatus = WidgetBindingStatus.BOUND,
                        discoveredProvider = provider
                    )
                } else slot
            }
        }
        val targetSlot = _configuredSlots.value.find { it.slotId == slotId }
        if (targetSlot != null) saveSlotBinding(targetSlot)
    }

    override fun removeWidgetFromSlot(slotId: Int) {
        clearSlot(slotId)
    }

    override fun assignCandidateToSlot(slotId: Int, candidate: WidgetCandidateType) {
        _configuredSlots.update { list ->
            list.map { slot ->
                if (slot.slotId == slotId) {
                    slot.copy(
                        assignedCandidate = candidate,
                        appWidgetId = null,
                        bindingStatus = WidgetBindingStatus.AVAILABLE_TO_BIND
                    )
                } else slot
            }
        }
    }

    override fun clearSlot(slotId: Int) {
        val currentSlot = _configuredSlots.value.find { it.slotId == slotId } ?: return
        val associatedWidgetId = currentSlot.appWidgetId

        // 1. Limpiar estado de la ranura
        _configuredSlots.update { list ->
            list.map { slot ->
                if (slot.slotId == slotId) {
                    WidgetSlot(
                        slotId = slot.slotId,
                        positionLabel = slot.positionLabel,
                        bindingStatus = WidgetBindingStatus.AVAILABLE_TO_BIND
                    )
                } else slot
            }
        }

        // 2. Limpiar persistencia
        clearSlotPersistence(slotId)

        // 3. Liberar ID si otra ranura no lo está compartiendo
        if (associatedWidgetId != null && associatedWidgetId > 0) {
            val isReferencedByOtherSlot = _configuredSlots.value.any {
                it.slotId != slotId && it.appWidgetId == associatedWidgetId
            }
            if (!isReferencedByOtherSlot) {
                releaseHostView(associatedWidgetId)
                _allocatedWidgetIds.update { it - associatedWidgetId }
                try {
                    hostBridge.deleteAppWidgetId(associatedWidgetId)
                } catch (_: Throwable) {}
            }
        }
    }

    // =========================================================================
    // 6. Caché de Vistas (Fase 05.1.1)
    // =========================================================================

    override fun getOrCreateHostView(
        context: Context,
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo
    ): AppWidgetHostView? {
        if (!_isOperational.value || appWidgetId <= 0) {
            return null
        }
        val cached = viewCache.get(appWidgetId)
        if (cached != null) {
            return cached
        }
        return try {
            val newView = hostBridge.createView(context, appWidgetId, providerInfo)
            if (newView != null) {
                viewCache.put(appWidgetId, newView)
            }
            newView
        } catch (_: Throwable) {
            null
        }
    }

    override fun prepareHostViewForTransfer(appWidgetId: Int): AppWidgetHostView? {
        return viewCache.prepareForTransfer(appWidgetId)
    }

    override fun releaseHostView(appWidgetId: Int) {
        viewCache.remove(appWidgetId)
    }

    override fun clearHostViews() {
        viewCache.clear()
    }

    companion object {
        const val REQUEST_BIND_APPWIDGET = 0xDD51
        const val REQUEST_CONFIGURE_APPWIDGET = 0xDD52

        // Registro de pruebas en JVM para resolver package/class/appName en stubs de android.jar
        data class TestProviderData(
            val appName: String,
            val packageName: String,
            val className: String,
            val label: String = className
        )
        internal val testProviderRegistry = mutableMapOf<AppWidgetProviderInfo, TestProviderData>()

        const val PREFS_NAME = "divine_deck_widgets_prefs"
        const val KEY_SLOT_1_CANDIDATE = "key_slot_1_candidate"
        const val KEY_SLOT_2_CANDIDATE = "key_slot_2_candidate"
        const val KEY_SLOT_1_APPWIDGET_ID = "key_slot_1_appwidget_id"
        const val KEY_SLOT_2_APPWIDGET_ID = "key_slot_2_appwidget_id"
        const val KEY_SLOT_1_PROVIDER_PACKAGE = "key_slot_1_provider_package"
        const val KEY_SLOT_2_PROVIDER_PACKAGE = "key_slot_2_provider_package"
        const val KEY_SLOT_1_PROVIDER_CLASS = "key_slot_1_provider_class"
        const val KEY_SLOT_2_PROVIDER_CLASS = "key_slot_2_provider_class"
        const val KEY_SLOT_1_BOUND = "key_slot_1_bound"
        const val KEY_SLOT_2_BOUND = "key_slot_2_bound"
        const val KEY_SLOT_1_TITLE = "key_slot_1_title"
        const val KEY_SLOT_2_TITLE = "key_slot_2_title"
        const val KEY_SLOT_1_APP_NAME = "key_slot_1_app_name"
        const val KEY_SLOT_2_APP_NAME = "key_slot_2_app_name"
    }
}
