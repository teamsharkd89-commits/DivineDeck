package com.divinedeck.launcher.feature.widgets.domain.model

import android.content.ComponentName
import android.graphics.drawable.Drawable

/**
 * Estados del ciclo de vinculación de un widget en una ranura de DIVINE DECK.
 */
enum class WidgetBindingStatus(val label: String) {
    UNINSTALLED("No instalada"),
    PROVIDER_NOT_FOUND("Proveedor no encontrado"),
    AVAILABLE_TO_BIND("Disponible para vincular"),
    AWAITING_AUTHORIZATION("Esperando autorización del sistema"),
    REQUIRES_CONFIGURATION("Requiere configuración"),
    BOUND("Vinculado"),
    BINDING_ERROR("Error de vinculación"),
    HOST_NOT_OPERATIONAL("Host no operativo")
}

/**
 * Metadatos y capacidades universales de un proveedor de AppWidget descubierto en el sistema Android (Fase 05.2.1).
 * Modelo genérico totalmente independiente de nombres de aplicación o paquetes específicos.
 */
data class DiscoveredWidgetProvider(
    val provider: ComponentName?,
    val appName: String = "",
    val label: String = "",
    val description: String? = null,
    val appIcon: Drawable? = null,
    val previewImage: Int = 0,
    val icon: Int = 0,
    val minWidth: Int = 0,
    val minHeight: Int = 0,
    val minResizeWidth: Int = 0,
    val minResizeHeight: Int = 0,
    val maxResizeWidth: Int = 0,
    val maxResizeHeight: Int = 0,
    val resizeMode: Int = 0,
    val widgetCategory: Int = 0,
    val initialLayout: Int = 0,
    val configure: ComponentName? = null,
    val isResizeSupported: Boolean = resizeMode != 0,
    val packageNameOverride: String? = null,
    val classNameOverride: String? = null
) {
    val packageName: String
        get() = packageNameOverride ?: (try { provider?.packageName } catch (_: Throwable) { null }) ?: ""

    val className: String
        get() = classNameOverride ?: (try { provider?.className } catch (_: Throwable) { null }) ?: ""

    val componentShortString: String
        get() = provider?.flattenToShortString() ?: "$packageName/$className"

    val shortLabel: String
        get() = label.ifBlank { (try { provider?.shortClassName } catch (_: Throwable) { null }) ?: className.substringAfterLast('.') }

    val resolvedAppName: String
        get() = appName.ifBlank { shortLabel }

    val formattedDimensions: String
        get() = "${minWidth} × ${minHeight} dp"
}
