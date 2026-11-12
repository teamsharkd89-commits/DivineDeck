package com.divinedeck.launcher.feature.widgets

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import com.divinedeck.launcher.feature.widgets.data.AndroidWidgetHostManagerImpl
import com.divinedeck.launcher.feature.widgets.data.AppWidgetHostBridge
import com.divinedeck.launcher.feature.widgets.data.AppWidgetViewCache
import com.divinedeck.launcher.feature.widgets.data.DivineAppWidgetHostFactory
import com.divinedeck.launcher.feature.widgets.domain.model.DiscoveredWidgetProvider
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetBindingStatus
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetCandidateType
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetSlot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WidgetHostManagerTest {

    private class InMemorySharedPreferences : SharedPreferences {
        val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = data[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class Editor : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val toRemove = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor { temp[key!!] = value; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { temp[key!!] = values; return this }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { temp[key!!] = value; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { temp[key!!] = value; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { temp[key!!] = value; return this }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { temp[key!!] = value; return this }
            override fun remove(key: String?): SharedPreferences.Editor { toRemove.add(key!!); return this }
            override fun clear(): SharedPreferences.Editor { clear = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) data.clear()
                toRemove.forEach { data.remove(it) }
                data.putAll(temp)
            }
        }
    }

    private class TestContext(
        val inMemoryPrefs: SharedPreferences = InMemorySharedPreferences()
    ) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = inMemoryPrefs
    }

    private class TestActivity(
        val testContext: TestContext
    ) : Activity() {
        var lastStartedIntent: Intent? = null
        var lastRequestCode: Int = -1

        override fun startActivityForResult(intent: Intent?, requestCode: Int) {
            lastStartedIntent = intent
            lastRequestCode = requestCode
        }

        override fun getApplicationContext(): Context = testContext
    }

    private fun createFakeProviderInfo(
        packageName: String,
        className: String,
        minWidth: Int = 250,
        minHeight: Int = 110,
        configureClass: String? = null,
        appName: String = packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() },
        label: String = className.substringAfterLast('.'),
        category: Int = 1 // WIDGET_CATEGORY_HOME_SCREEN
    ): AppWidgetProviderInfo {
        val info = AppWidgetProviderInfo()
        info.provider = ComponentName(packageName, className)
        info.minWidth = minWidth
        info.minHeight = minHeight
        info.resizeMode = 3
        info.widgetCategory = category
        if (configureClass != null) {
            info.configure = ComponentName(packageName, configureClass)
        }
        AndroidWidgetHostManagerImpl.testProviderRegistry[info] = AndroidWidgetHostManagerImpl.Companion.TestProviderData(
            appName = appName,
            packageName = packageName,
            className = className,
            label = label
        )
        return info
    }

    @Before
    fun setUp() {
        DivineAppWidgetHostFactory.resetForTesting()
        AndroidWidgetHostManagerImpl.testProviderRegistry.clear()
    }

    @After
    fun tearDown() {
        DivineAppWidgetHostFactory.resetForTesting()
        AndroidWidgetHostManagerImpl.testProviderRegistry.clear()
    }

    // =========================================================================
    // 1. Pruebas de Fase 02.1 / 04.5A (Compatibilidad de Ranuras)
    // =========================================================================

    @Test
    fun `initial widget slots are two balanced spaces in empty state`() {
        val manager = AndroidWidgetHostManagerImpl()
        val slots = manager.configuredSlots.value
        assertEquals(2, slots.size)
        assertEquals("WIDGET SUPERIOR", slots[0].positionLabel)
        assertEquals("WIDGET INFERIOR", slots[1].positionLabel)
        assertTrue(slots[0].isEmpty)
        assertTrue(slots[1].isEmpty)
    }

    @Test
    fun `assigning candidate to upper and lower slots updates configuration`() {
        val manager = AndroidWidgetHostManagerImpl()

        manager.assignCandidateToSlot(1, WidgetCandidateType.BITCHORD_MUSIC)
        manager.assignCandidateToSlot(2, WidgetCandidateType.FUEL_SENSE)

        val slots = manager.configuredSlots.value
        assertEquals(WidgetCandidateType.BITCHORD_MUSIC, slots[0].assignedCandidate)
        assertEquals(WidgetCandidateType.FUEL_SENSE, slots[1].assignedCandidate)
    }

    @Test
    fun `clearSlot removes candidate and restores empty slot`() {
        val manager = AndroidWidgetHostManagerImpl()
        manager.assignCandidateToSlot(1, WidgetCandidateType.BITCHORD_MUSIC)
        assertFalse(manager.configuredSlots.value[0].assignedCandidate == WidgetCandidateType.NONE)

        manager.clearSlot(1)
        assertEquals(WidgetCandidateType.NONE, manager.configuredSlots.value[0].assignedCandidate)
        assertTrue(manager.configuredSlots.value[0].isEmpty)
    }

    @Test
    fun `isFuelSenseCandidate helper identifies fuel sense slot`() {
        val slot1 = WidgetSlot(slotId = 1, assignedCandidate = WidgetCandidateType.BITCHORD_MUSIC)
        val slot2 = WidgetSlot(slotId = 2, assignedCandidate = WidgetCandidateType.FUEL_SENSE)

        assertFalse(slot1.isFuelSenseCandidate)
        assertTrue(slot2.isFuelSenseCandidate)
    }

    // =========================================================================
    // 2. Pruebas de Fase 05.1 y 05.1.1 (Infraestructura de Host y Caché de Vistas)
    // =========================================================================

    private class FakeAppWidgetHostBridge(
        override val hostId: Int = 0xDD05,
        override var isListening: Boolean = false,
        override val isOperational: Boolean = true,
        override val initializationError: Throwable? = null
    ) : AppWidgetHostBridge {
        private var nextId = 100
        val allocatedIds = mutableListOf<Int>()
        val deletedIds = mutableListOf<Int>()
        val createdViews = mutableMapOf<Int, AppWidgetHostView>()
        var lastConfigureRequestCode = -1

        override fun startListening() { isListening = true }
        override fun stopListening() { isListening = false }
        override fun allocateAppWidgetId(): Int {
            val id = nextId++
            allocatedIds.add(id)
            return id
        }
        override fun deleteAppWidgetId(appWidgetId: Int) { deletedIds.add(appWidgetId) }
        override fun createView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo): AppWidgetHostView? {
            val view = object : AppWidgetHostView(context) {}
            createdViews[appWidgetId] = view
            return view
        }
        override fun startAppWidgetConfigureActivityForResult(
            activity: Activity,
            appWidgetId: Int,
            intentFlags: Int,
            requestCode: Int,
            options: Bundle?
        ) {
            lastConfigureRequestCode = requestCode
        }
    }

    private class UnoperationalAppWidgetHostBridge(
        override val hostId: Int = 0xDD05,
        override val isListening: Boolean = false,
        override val isOperational: Boolean = false,
        override val initializationError: Throwable? = IllegalStateException("Host simulado inoperativo")
    ) : AppWidgetHostBridge {
        override fun startListening() {}
        override fun stopListening() {}
        override fun allocateAppWidgetId(): Int = -1
        override fun deleteAppWidgetId(appWidgetId: Int) {}
        override fun createView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo): AppWidgetHostView? = null
        override fun startAppWidgetConfigureActivityForResult(activity: Activity, appWidgetId: Int, intentFlags: Int, requestCode: Int, options: Bundle?) {}
    }

    @Test
    fun `DivineAppWidgetHostFactory returns singleton instance for same hostId`() {
        val testContext = TestContext()
        val bridge1 = DivineAppWidgetHostFactory.getOrCreateHost(testContext)
        val bridge2 = DivineAppWidgetHostFactory.getOrCreateHost(testContext)
        assertSame(bridge1, bridge2)
        assertEquals(0xDD05, bridge1.hostId)
    }

    @Test
    fun `startListening and stopListening control host listening state`() {
        val fakeBridge = FakeAppWidgetHostBridge()
        val manager = AndroidWidgetHostManagerImpl(hostBridge = fakeBridge)

        assertFalse(manager.isListening.value)
        manager.startListening()
        assertTrue(manager.isListening.value)
        assertTrue(fakeBridge.isListening)

        manager.stopListening()
        assertFalse(manager.isListening.value)
        assertFalse(fakeBridge.isListening)
    }

    @Test
    fun `allocateAppWidgetId increments and tracks unique IDs`() {
        val fakeBridge = FakeAppWidgetHostBridge()
        val manager = AndroidWidgetHostManagerImpl(hostBridge = fakeBridge)

        val id1 = manager.allocateAppWidgetId()
        val id2 = manager.allocateAppWidgetId()

        assertNotEquals(id1, id2)
        assertTrue(manager.allocatedWidgetIds.value.contains(id1))
        assertTrue(manager.allocatedWidgetIds.value.contains(id2))
    }

    @Test
    fun `deleteAppWidgetId removes ID from tracking`() {
        val fakeBridge = FakeAppWidgetHostBridge()
        val manager = AndroidWidgetHostManagerImpl(hostBridge = fakeBridge)

        val id = manager.allocateAppWidgetId()
        assertTrue(manager.allocatedWidgetIds.value.contains(id))

        manager.deleteAppWidgetId(id)
        assertFalse(manager.allocatedWidgetIds.value.contains(id))
        assertTrue(fakeBridge.deletedIds.contains(id))
    }

    @Test
    fun `viewCache stores and retrieves AppWidgetHostView`() {
        val testContext = TestContext()
        val cache = AppWidgetViewCache()
        val view = object : AppWidgetHostView(testContext) {}

        cache.put(42, view)
        assertSame(view, cache.get(42))
        assertNull(cache.get(999))
    }

    @Test
    fun `prepareForTransfer removes view from parent without detaching`() {
        val testContext = TestContext()
        val cache = AppWidgetViewCache()
        val view = object : AppWidgetHostView(testContext) {}

        cache.put(42, view)
        val transferred = cache.prepareForTransfer(42)
        assertSame(view, transferred)
        assertSame(view, cache.get(42))
    }

    @Test
    fun `viewCache clear removes all cached views`() {
        val testContext = TestContext()
        val cache = AppWidgetViewCache()
        cache.put(1, object : AppWidgetHostView(testContext) {})
        cache.put(2, object : AppWidgetHostView(testContext) {})

        cache.clear()
        assertNull(cache.get(1))
        assertNull(cache.get(2))
    }

    @Test
    fun `clearSlot preserves id if another slot still references it`() {
        val testContext = TestContext()
        val fakeBridge = FakeAppWidgetHostBridge()
        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge
        )

        val idShared = manager.allocateAppWidgetId()
        manager.bindWidgetToSlot(1, idShared)
        manager.bindWidgetToSlot(2, idShared)

        manager.clearSlot(1)

        val slots = manager.configuredSlots.value
        assertNull(slots[0].appWidgetId)
        assertEquals(idShared, slots[1].appWidgetId)
        assertTrue(manager.allocatedWidgetIds.value.contains(idShared))
        assertFalse(fakeBridge.deletedIds.contains(idShared))
    }

    // =========================================================================
    // 3. Pruebas de Fase 05.2.1: Descubrimiento Universal y Asignación Dinámica
    // =========================================================================

    @Test
    fun `discoverProviders finds multiple distinct applications without hardcoded filtering`() {
        val spotifyInfo = createFakeProviderInfo("com.spotify.music", "SpotifyWidget", appName = "Spotify", label = "Reproductor")
        val wazeInfo = createFakeProviderInfo("com.waze", "WazeWidget", appName = "Waze", label = "Ruta")
        val bitchordInfo = createFakeProviderInfo("com.bitchord.music", "BitchordWidget", appName = "Bitchord", label = "Control Audio")
        val fuelSenseInfo = createFakeProviderInfo("com.fuelsense.car", "FuelSenseWidget", appName = "Fuel Sense", label = "Telemetría")
        val clockInfo = createFakeProviderInfo("com.google.android.deskclock", "DigitalClockWidget", appName = "Reloj", label = "Reloj 2x1")

        val manager = AndroidWidgetHostManagerImpl(
            installedProvidersProvider = { listOf(spotifyInfo, wazeInfo, bitchordInfo, fuelSenseInfo, clockInfo) }
        )

        val discovered = manager.discoverProviders()
        assertEquals(5, discovered.size)
        assertTrue(discovered.any { it.packageName == "com.spotify.music" })
        assertTrue(discovered.any { it.packageName == "com.waze" })
        assertTrue(discovered.any { it.packageName == "com.bitchord.music" })
        assertTrue(discovered.any { it.packageName == "com.fuelsense.car" })
        assertTrue(discovered.any { it.packageName == "com.google.android.deskclock" })
    }

    @Test
    fun `discoverProviders exposes multiple providers of the same application independently`() {
        val spotifySmall = createFakeProviderInfo("com.spotify.music", "SpotifyMiniWidget", minWidth = 140, minHeight = 70, label = "Mini")
        val spotifyLarge = createFakeProviderInfo("com.spotify.music", "SpotifyLargeWidget", minWidth = 280, minHeight = 140, label = "Grande 4x2")

        val manager = AndroidWidgetHostManagerImpl(
            installedProvidersProvider = { listOf(spotifySmall, spotifyLarge) }
        )

        val discovered = manager.discoverProviders()
        assertEquals(2, discovered.size)
        assertEquals("SpotifyMiniWidget", discovered[0].className)
        assertEquals("SpotifyLargeWidget", discovered[1].className)
    }

    @Test
    fun `discoverProviders includes completely unknown applications`() {
        val unknownInfo = createFakeProviderInfo("com.custom.thirdparty.telemetry", "CustomGaugesWidget", appName = "Custom Tools")

        val manager = AndroidWidgetHostManagerImpl(
            installedProvidersProvider = { listOf(unknownInfo) }
        )

        val discovered = manager.discoverProviders()
        assertEquals(1, discovered.size)
        assertEquals("com.custom.thirdparty.telemetry", discovered[0].packageName)
        assertEquals("Custom Tools", discovered[0].resolvedAppName)
    }

    @Test
    fun `providers are uniquely identified by ComponentName`() {
        val info1 = createFakeProviderInfo("com.example.app", "WidgetA")
        val info2 = createFakeProviderInfo("com.example.app", "WidgetB")

        val manager = AndroidWidgetHostManagerImpl(
            installedProvidersProvider = { listOf(info1, info2) }
        )

        val foundA = manager.getDiscoveredProviderByComponent("com.example.app", "WidgetA")
        val foundB = manager.getDiscoveredProviderByComponent("com.example.app", "WidgetB")
        val foundNone = manager.getDiscoveredProviderByComponent("com.example.app", "WidgetNonExistent")

        assertNotNull(foundA)
        assertNotNull(foundB)
        assertNull(foundNone)
        assertEquals("WidgetA", foundA?.className)
        assertEquals("WidgetB", foundB?.className)
    }

    @Test
    fun `handles incomplete metadata gracefully`() {
        val barebonesInfo = AppWidgetProviderInfo().apply {
            provider = ComponentName("com.barebones.app", "BareWidget")
            minWidth = 0
            minHeight = 0
            widgetCategory = 1
        }
        AndroidWidgetHostManagerImpl.testProviderRegistry[barebonesInfo] = AndroidWidgetHostManagerImpl.Companion.TestProviderData(
            appName = "",
            packageName = "com.barebones.app",
            className = "BareWidget",
            label = ""
        )

        val manager = AndroidWidgetHostManagerImpl(
            installedProvidersProvider = { listOf(barebonesInfo) }
        )

        val discovered = manager.discoverProviders()
        assertEquals(1, discovered.size)
        assertEquals("com.barebones.app", discovered[0].packageName)
        assertEquals("BareWidget", discovered[0].className)
        assertEquals("BareWidget", discovered[0].resolvedAppName)
        assertEquals("0 × 0 dp", discovered[0].formattedDimensions)
    }

    @Test
    fun `allows assigning any arbitrary provider to Slot 1`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val mapsInfo = createFakeProviderInfo("com.google.android.apps.maps", "TrafficWidget", appName = "Google Maps")

        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(mapsInfo) },
            bindIfAllowedHelper = { _, _ -> true }
        )

        val provider = manager.discoverProviders().first()
        val success = manager.startBindingFlow(activity, 1, provider)
        assertTrue(success)

        val slot1 = manager.configuredSlots.value[0]
        assertEquals(WidgetBindingStatus.BOUND, slot1.bindingStatus)
        assertEquals("com.google.android.apps.maps", slot1.providerPackage)
        assertEquals("TrafficWidget", slot1.providerClass)
        assertTrue(slot1.isBound)
    }

    @Test
    fun `allows assigning any arbitrary provider to Slot 2`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val spotifyInfo = createFakeProviderInfo("com.spotify.music", "PlayerWidget", appName = "Spotify")

        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(spotifyInfo) },
            bindIfAllowedHelper = { _, _ -> true }
        )

        val provider = manager.discoverProviders().first()
        val success = manager.startBindingFlow(activity, 2, provider)
        assertTrue(success)

        val slot2 = manager.configuredSlots.value[1]
        assertEquals(WidgetBindingStatus.BOUND, slot2.bindingStatus)
        assertEquals("com.spotify.music", slot2.providerPackage)
        assertEquals("PlayerWidget", slot2.providerClass)
        assertTrue(slot2.isBound)
    }

    @Test
    fun `safely replaces an existing widget in a slot and cleans old widget id`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val appA = createFakeProviderInfo("com.app.a", "WidgetA")
        val appB = createFakeProviderInfo("com.app.b", "WidgetB")

        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appA, appB) },
            bindIfAllowedHelper = { _, _ -> true }
        )

        // 1. Vincular App A a Ranura 1
        val provA = manager.getDiscoveredProviderByComponent("com.app.a", "WidgetA")!!
        manager.startBindingFlow(activity, 1, provA)
        val oldId = manager.configuredSlots.value[0].appWidgetId!!

        // 2. Reemplazar Ranura 1 con App B
        val provB = manager.getDiscoveredProviderByComponent("com.app.b", "WidgetB")!!
        manager.startBindingFlow(activity, 1, provB)
        val newId = manager.configuredSlots.value[0].appWidgetId!!

        assertNotEquals(oldId, newId)
        assertEquals("com.app.b", manager.configuredSlots.value[0].providerPackage)
        assertTrue("El ID anterior debe ser liberado", fakeBridge.deletedIds.contains(oldId))
        assertFalse("El nuevo ID debe permanecer activo", fakeBridge.deletedIds.contains(newId))
    }

    @Test
    fun `reverts cleanly and preserves previous widget when binding is canceled`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val appA = createFakeProviderInfo("com.app.a", "WidgetA")
        val appB = createFakeProviderInfo("com.app.b", "WidgetB")

        var allowBind = true
        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appA, appB) },
            bindIfAllowedHelper = { _, _ -> allowBind }
        )

        // 1. Vincular App A con éxito
        val provA = manager.getDiscoveredProviderByComponent("com.app.a", "WidgetA")!!
        manager.startBindingFlow(activity, 1, provA)
        val originalSlot1 = manager.configuredSlots.value[0]
        assertEquals(WidgetBindingStatus.BOUND, originalSlot1.bindingStatus)
        assertEquals("com.app.a", originalSlot1.providerPackage)

        // 2. Intentar reemplazar con App B, pero requiere diálogo del SO
        allowBind = false
        val provB = manager.getDiscoveredProviderByComponent("com.app.b", "WidgetB")!!
        manager.startBindingFlow(activity, 1, provB)
        assertEquals(WidgetBindingStatus.AWAITING_AUTHORIZATION, manager.configuredSlots.value[0].bindingStatus)

        // 3. Usuario cancela la vinculación en el diálogo del SO
        manager.handleActivityResult(activity, AndroidWidgetHostManagerImpl.REQUEST_BIND_APPWIDGET, Activity.RESULT_CANCELED, null)

        // 4. Ranura 1 debe haber revertido exactamente al estado previo de App A
        val restoredSlot1 = manager.configuredSlots.value[0]
        assertEquals(WidgetBindingStatus.BOUND, restoredSlot1.bindingStatus)
        assertEquals("com.app.a", restoredSlot1.providerPackage)
        assertEquals(originalSlot1.appWidgetId, restoredSlot1.appWidgetId)
    }

    @Test
    fun `reverts cleanly and preserves previous widget when configure activity is canceled`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val appA = createFakeProviderInfo("com.app.a", "WidgetA")
        val appBWithConfig = createFakeProviderInfo("com.app.b", "WidgetB", configureClass = "ConfigActivity")

        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appA, appBWithConfig) },
            bindIfAllowedHelper = { _, _ -> true }
        )

        // 1. Vincular App A
        val provA = manager.getDiscoveredProviderByComponent("com.app.a", "WidgetA")!!
        manager.startBindingFlow(activity, 1, provA)
        val originalSlot1 = manager.configuredSlots.value[0]

        // 2. Iniciar vinculación de App B (entra a REQUIRES_CONFIGURATION)
        val provB = manager.getDiscoveredProviderByComponent("com.app.b", "WidgetB")!!
        manager.startBindingFlow(activity, 1, provB)
        assertEquals(WidgetBindingStatus.REQUIRES_CONFIGURATION, manager.configuredSlots.value[0].bindingStatus)

        // 3. Usuario cancela la Activity de configuración
        manager.handleActivityResult(activity, AndroidWidgetHostManagerImpl.REQUEST_CONFIGURE_APPWIDGET, Activity.RESULT_CANCELED, null)

        // 4. Ranura 1 conserva intacto el widget anterior
        val restoredSlot1 = manager.configuredSlots.value[0]
        assertEquals(WidgetBindingStatus.BOUND, restoredSlot1.bindingStatus)
        assertEquals("com.app.a", restoredSlot1.providerPackage)
        assertEquals(originalSlot1.appWidgetId, restoredSlot1.appWidgetId)
    }

    @Test
    fun `unbindSlot clears slot and deletes widget id`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val appA = createFakeProviderInfo("com.app.a", "WidgetA")

        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appA) },
            bindIfAllowedHelper = { _, _ -> true }
        )

        val provA = manager.discoverProviders().first()
        manager.startBindingFlow(activity, 1, provA)
        val boundId = manager.configuredSlots.value[0].appWidgetId!!

        manager.unbindSlot(1)
        val slot1 = manager.configuredSlots.value[0]
        assertEquals(WidgetBindingStatus.AVAILABLE_TO_BIND, slot1.bindingStatus)
        assertNull(slot1.appWidgetId)
        assertTrue(fakeBridge.deletedIds.contains(boundId))
    }

    @Test
    fun `swapSlots swaps widgets between Slot 1 and Slot 2 safely`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val appA = createFakeProviderInfo("com.app.a", "WidgetA")
        val appB = createFakeProviderInfo("com.app.b", "WidgetB")

        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appA, appB) },
            bindIfAllowedHelper = { _, _ -> true }
        )

        manager.startBindingFlow(activity, 1, manager.getDiscoveredProviderByComponent("com.app.a", "WidgetA")!!)
        manager.startBindingFlow(activity, 2, manager.getDiscoveredProviderByComponent("com.app.b", "WidgetB")!!)

        val idA = manager.configuredSlots.value[0].appWidgetId!!
        val idB = manager.configuredSlots.value[1].appWidgetId!!

        val swapped = manager.swapSlots()
        assertTrue(swapped)

        val newSlot1 = manager.configuredSlots.value[0]
        val newSlot2 = manager.configuredSlots.value[1]

        assertEquals("com.app.b", newSlot1.providerPackage)
        assertEquals(idB, newSlot1.appWidgetId)

        assertEquals("com.app.a", newSlot2.providerPackage)
        assertEquals(idA, newSlot2.appWidgetId)
    }

    @Test
    fun `moveWidget transfers widget to empty slot`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val appA = createFakeProviderInfo("com.app.a", "WidgetA")

        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appA) },
            bindIfAllowedHelper = { _, _ -> true }
        )

        manager.startBindingFlow(activity, 1, manager.getDiscoveredProviderByComponent("com.app.a", "WidgetA")!!)
        val idA = manager.configuredSlots.value[0].appWidgetId!!

        val moved = manager.moveWidget(1, 2)
        assertTrue(moved)

        val slot1 = manager.configuredSlots.value[0]
        val slot2 = manager.configuredSlots.value[1]

        assertTrue(slot1.isEmpty)
        assertEquals("com.app.a", slot2.providerPackage)
        assertEquals(idA, slot2.appWidgetId)
    }

    @Test
    fun `prevents duplicate widget ids between slots`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val appA = createFakeProviderInfo("com.app.a", "WidgetA")
        val appB = createFakeProviderInfo("com.app.b", "WidgetB")

        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appA, appB) },
            bindIfAllowedHelper = { _, _ -> true }
        )

        manager.startBindingFlow(activity, 1, manager.getDiscoveredProviderByComponent("com.app.a", "WidgetA")!!)
        manager.startBindingFlow(activity, 2, manager.getDiscoveredProviderByComponent("com.app.b", "WidgetB")!!)

        val id1 = manager.configuredSlots.value[0].appWidgetId
        val id2 = manager.configuredSlots.value[1].appWidgetId

        assertNotNull(id1)
        assertNotNull(id2)
        assertNotEquals(id1, id2)
    }

    @Test
    fun `persists and restores arbitrary widget bindings across reboot`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val appX = createFakeProviderInfo("com.app.x", "WidgetX", appName = "App X")
        val appY = createFakeProviderInfo("com.app.y", "WidgetY", appName = "App Y")

        // Sesión 1: Vincular App X en Ranura 1 y App Y en Ranura 2
        val manager1 = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appX, appY) },
            bindIfAllowedHelper = { _, _ -> true }
        )
        manager1.startBindingFlow(activity, 1, manager1.getDiscoveredProviderByComponent("com.app.x", "WidgetX")!!)
        manager1.startBindingFlow(activity, 2, manager1.getDiscoveredProviderByComponent("com.app.y", "WidgetY")!!)

        val idX = manager1.configuredSlots.value[0].appWidgetId!!
        val idY = manager1.configuredSlots.value[1].appWidgetId!!

        // Sesión 2: Simular reinicio creando nueva instancia de manager con los mismos SharedPreferences
        val manager2 = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appX, appY) }
        )

        val slot1 = manager2.configuredSlots.value[0]
        val slot2 = manager2.configuredSlots.value[1]

        assertEquals(WidgetBindingStatus.BOUND, slot1.bindingStatus)
        assertEquals("com.app.x", slot1.providerPackage)
        assertEquals(idX, slot1.appWidgetId)

        assertEquals(WidgetBindingStatus.BOUND, slot2.bindingStatus)
        assertEquals("com.app.y", slot2.providerPackage)
        assertEquals(idY, slot2.appWidgetId)
    }

    @Test
    fun `migrates legacy candidates transparently on reboot`() {
        val testContext = TestContext()
        val fakeBridge = FakeAppWidgetHostBridge()
        val bitchordInfo = createFakeProviderInfo("com.bitchord.music", "BitchordAppWidgetProvider")

        // Simular SharedPreferences de una versión antigua donde solo se guardaba el candidate id "bitchord_music"
        testContext.inMemoryPrefs.edit()
            .putInt(AndroidWidgetHostManagerImpl.KEY_SLOT_1_APPWIDGET_ID, 555)
            .putBoolean(AndroidWidgetHostManagerImpl.KEY_SLOT_1_BOUND, true)
            .putString(AndroidWidgetHostManagerImpl.KEY_SLOT_1_CANDIDATE, "bitchord_music")
            .apply()

        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(bitchordInfo) }
        )

        val slot1 = manager.configuredSlots.value[0]
        assertEquals(WidgetBindingStatus.BOUND, slot1.bindingStatus)
        assertEquals("com.bitchord.music", slot1.providerPackage)
        assertEquals(555, slot1.appWidgetId)
    }

    @Test
    fun `detects uninstalled application and cleans orphan association`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val appA = createFakeProviderInfo("com.app.a", "WidgetA")
        val appB = createFakeProviderInfo("com.app.b", "WidgetB")

        // 1. Vincular ambas apps
        val manager1 = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appA, appB) },
            bindIfAllowedHelper = { _, _ -> true }
        )
        manager1.startBindingFlow(activity, 1, manager1.getDiscoveredProviderByComponent("com.app.a", "WidgetA")!!)
        manager1.startBindingFlow(activity, 2, manager1.getDiscoveredProviderByComponent("com.app.b", "WidgetB")!!)

        // 2. Simular que App A fue desinstalada
        val manager2 = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appB) }
        )

        val slot1 = manager2.configuredSlots.value[0]
        val slot2 = manager2.configuredSlots.value[1]

        assertEquals(WidgetBindingStatus.UNINSTALLED, slot1.bindingStatus)
        assertNull(slot1.appWidgetId)

        assertEquals(WidgetBindingStatus.BOUND, slot2.bindingStatus)
        assertEquals("com.app.b", slot2.providerPackage)
        assertNotNull(slot2.appWidgetId)
    }

    @Test
    fun `startBindingFlow fails safely when host is not operational`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val unoperationalBridge = UnoperationalAppWidgetHostBridge()
        val appA = createFakeProviderInfo("com.app.a", "WidgetA")

        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = unoperationalBridge,
            installedProvidersProvider = { listOf(appA) }
        )

        val provA = manager.discoverProviders().first()
        val success = manager.startBindingFlow(activity, 1, provA)

        assertFalse(success)
        assertEquals(WidgetBindingStatus.HOST_NOT_OPERATIONAL, manager.configuredSlots.value[0].bindingStatus)
    }

    // =========================================================================
    // 5. Pruebas de Fase 05.3 (Renderizado Nativo, Proveedor para Ranuras y Ciclo de Vistas)
    // =========================================================================

    @Test
    fun `getProviderInfoForSlot resolves AppWidgetProviderInfo for bound slot with installed provider`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val appInfo = createFakeProviderInfo("com.music.player", "MusicWidgetProvider")

        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appInfo) },
            bindIfAllowedHelper = { _, _ -> true }
        )

        val discovered = manager.getDiscoveredProviderByComponent("com.music.player", "MusicWidgetProvider")!!
        manager.startBindingFlow(activity, 1, discovered)

        val resolved = manager.getProviderInfoForSlot(1)
        assertNotNull(resolved)
        assertSame(appInfo, resolved)
    }

    @Test
    fun `getProviderInfoForSlot returns null for empty or invalid slot`() {
        val testContext = TestContext()
        val fakeBridge = FakeAppWidgetHostBridge()
        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge
        )

        assertNull(manager.getProviderInfoForSlot(1))
        assertNull(manager.getProviderInfoForSlot(2))
        assertNull(manager.getProviderInfoForSlot(99))
    }

    @Test
    fun `getProviderInfoForSlot returns null when provider is uninstalled or unknown`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val appInfo = createFakeProviderInfo("com.weather.temp", "WeatherWidget")

        val manager1 = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appInfo) },
            bindIfAllowedHelper = { _, _ -> true }
        )
        val discovered = manager1.getDiscoveredProviderByComponent("com.weather.temp", "WeatherWidget")!!
        manager1.startBindingFlow(activity, 1, discovered)

        // Sesión donde la app fue desinstalada
        val manager2 = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { emptyList() }
        )

        assertNull(manager2.getProviderInfoForSlot(1))
    }

    @Test
    fun `getAppWidgetInfo returns null for zero or negative widget ID`() {
        val testContext = TestContext()
        val fakeBridge = FakeAppWidgetHostBridge()
        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge
        )

        assertNull(manager.getAppWidgetInfo(0))
        assertNull(manager.getAppWidgetInfo(-1))
        assertNull(manager.getAppWidgetInfo(-42))
    }

    @Test
    fun `getOrCreateHostView instantiates view and reuses cached instance`() {
        val testContext = TestContext()
        val fakeBridge = FakeAppWidgetHostBridge()
        val appInfo = createFakeProviderInfo("com.test.widget", "TestWidget")
        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge
        )

        val view1 = manager.getOrCreateHostView(testContext, 201, appInfo)
        assertNotNull(view1)

        val view2 = manager.getOrCreateHostView(testContext, 201, appInfo)
        assertSame(view1, view2)
    }

    @Test
    fun `prepareHostViewForTransfer detaches view and preserves cached instance`() {
        val testContext = TestContext()
        val fakeBridge = FakeAppWidgetHostBridge()
        val appInfo = createFakeProviderInfo("com.test.widget", "TestWidget")
        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge
        )

        val view = manager.getOrCreateHostView(testContext, 301, appInfo)
        assertNotNull(view)

        val transferred = manager.prepareHostViewForTransfer(301)
        assertSame(view, transferred)

        val viewAfter = manager.getOrCreateHostView(testContext, 301, appInfo)
        assertSame(view, viewAfter)
    }

    @Test
    fun `releaseHostView removes view from cache and subsequent getOrCreate creates new view`() {
        val testContext = TestContext()
        val fakeBridge = FakeAppWidgetHostBridge()
        val appInfo = createFakeProviderInfo("com.test.widget", "TestWidget")
        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge
        )

        val view1 = manager.getOrCreateHostView(testContext, 401, appInfo)
        assertNotNull(view1)

        manager.releaseHostView(401)

        val view2 = manager.getOrCreateHostView(testContext, 401, appInfo)
        assertNotNull(view2)
        assertNotEquals(System.identityHashCode(view1), System.identityHashCode(view2))
    }

    @Test
    fun `clearSlot releases cached view and deletes widget ID from hostBridge`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val appInfo = createFakeProviderInfo("com.test.widget", "TestWidget")
        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appInfo) },
            bindIfAllowedHelper = { _, _ -> true }
        )

        val discovered = manager.getDiscoveredProviderByComponent("com.test.widget", "TestWidget")!!
        manager.startBindingFlow(activity, 1, discovered)
        val widgetId = manager.configuredSlots.value[0].appWidgetId!!

        val view = manager.getOrCreateHostView(testContext, widgetId, appInfo)
        assertNotNull(view)

        manager.clearSlot(1)

        assertTrue(fakeBridge.deletedIds.contains(widgetId))
        assertTrue(manager.configuredSlots.value[0].isEmpty)
        assertNull(manager.getProviderInfoForSlot(1))
    }

    @Test
    fun `getOrCreateHostView returns null when host is not operational`() {
        val testContext = TestContext()
        val unoperationalBridge = UnoperationalAppWidgetHostBridge()
        val appInfo = createFakeProviderInfo("com.test.widget", "TestWidget")
        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = unoperationalBridge
        )

        val view = manager.getOrCreateHostView(testContext, 501, appInfo)
        assertNull(view)
    }

    @Test
    fun `clearHostViews empties view cache`() {
        val testContext = TestContext()
        val fakeBridge = FakeAppWidgetHostBridge()
        val appInfo = createFakeProviderInfo("com.test.widget", "TestWidget")
        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge
        )

        val view1 = manager.getOrCreateHostView(testContext, 601, appInfo)
        assertNotNull(view1)

        manager.clearHostViews()

        val view2 = manager.getOrCreateHostView(testContext, 601, appInfo)
        assertNotNull(view2)
        assertNotEquals(System.identityHashCode(view1), System.identityHashCode(view2))
    }

    @Test
    fun `moveWidget shifts provider info and slot configuration`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val appInfo = createFakeProviderInfo("com.music.player", "MusicWidgetProvider")

        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appInfo) },
            bindIfAllowedHelper = { _, _ -> true }
        )

        val discovered = manager.getDiscoveredProviderByComponent("com.music.player", "MusicWidgetProvider")!!
        manager.startBindingFlow(activity, 1, discovered)

        assertNotNull(manager.getProviderInfoForSlot(1))
        assertNull(manager.getProviderInfoForSlot(2))

        val moved = manager.moveWidget(fromSlotId = 1, toSlotId = 2)
        assertTrue(moved)

        assertNull(manager.getProviderInfoForSlot(1))
        assertNotNull(manager.getProviderInfoForSlot(2))
        assertSame(appInfo, manager.getProviderInfoForSlot(2))
    }

    @Test
    fun `swapSlots swaps provider info between slots`() {
        val testContext = TestContext()
        val activity = TestActivity(testContext)
        val fakeBridge = FakeAppWidgetHostBridge()
        val appA = createFakeProviderInfo("com.app.a", "WidgetA")
        val appB = createFakeProviderInfo("com.app.b", "WidgetB")

        val manager = AndroidWidgetHostManagerImpl(
            context = testContext,
            hostBridge = fakeBridge,
            installedProvidersProvider = { listOf(appA, appB) },
            bindIfAllowedHelper = { _, _ -> true }
        )

        manager.startBindingFlow(activity, 1, manager.getDiscoveredProviderByComponent("com.app.a", "WidgetA")!!)
        manager.startBindingFlow(activity, 2, manager.getDiscoveredProviderByComponent("com.app.b", "WidgetB")!!)

        assertSame(appA, manager.getProviderInfoForSlot(1))
        assertSame(appB, manager.getProviderInfoForSlot(2))

        val swapped = manager.swapSlots()
        assertTrue(swapped)

        assertSame(appB, manager.getProviderInfoForSlot(1))
        assertSame(appA, manager.getProviderInfoForSlot(2))
    }
}
