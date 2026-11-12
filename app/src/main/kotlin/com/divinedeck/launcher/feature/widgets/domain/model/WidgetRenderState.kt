package com.divinedeck.launcher.feature.widgets.domain.model

/**
 * Estados de presentación visual de un widget nativo dentro del Cockpit (Fase 05.3).
 */
enum class WidgetRenderState {
    /** Ranura sin widget asignado o en estado inicial. */
    EMPTY,

    /** Widget en proceso de inicialización o medición dimensional. */
    LOADING,

    /** Widget Android real renderizado mediante AndroidView y AppWidgetHostView. */
    READY,

    /** Aplicación proveedora o componente desinstalado o no encontrado. */
    PROVIDER_UNAVAILABLE,

    /** Error al inflar o renderizar la vista nativa. */
    RENDER_ERROR
}
