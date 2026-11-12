# DIVINE DECK — FASE 3.1.1: HEADER ALIGNMENT & COCKPIT MICRO-REFINEMENT

## 1. Resumen del Ajuste Realizado

Siguiendo estrictamente las restricciones de la **Fase 3.1.1**, se ha modificado **únicamente** el archivo [`VehicleShowcaseCard.kt`](file:///C:/DivineDeck/app/src/main/kotlin/com/divinedeck/launcher/core/designsystem/components/VehicleShowcaseCard.kt) para reubicar y estabilizar la barra superior del visor 3D sin alterar el tamaño del visor, la escala del Chevrolet Chevy C2, la geometría `.glb`, la cámara, los widgets laterales ni el dock inferior.

### Causa Raíz Detectada en la Samsung Galaxy Tab A11 (`1340 × 800`) y Solución Aplicada

1. **Reubicación junto al borde superior del visor (`top = 6.dp`, `start = 10.dp`, `end = 10.dp`)**:
   - Anteriormente, la fila superior tenía `.padding(horizontal = 16.dp, vertical = 10.dp)`, desplazándola hacia abajo dentro del recuadro del visor.
   - Se redujo el margen superior a **`top = 6.dp`** con márgenes laterales uniformes de **`10.dp`** ([`VehicleShowcaseCard.kt`](file:///C:/DivineDeck/app/src/main/kotlin/com/divinedeck/launcher/core/designsystem/components/VehicleShowcaseCard.kt#L96-L102)), situando toda la barra pegada limpiamente cerca del límite superior del recuadro sin tocar las esquinas redondeadas.
2. **Bloqueo de salto de línea y altura uniforme (`height = 24.dp`, `maxLines = 1`, `softWrap = false`)**:
   - En la densidad física de la Samsung Galaxy Tab A11, al cambiar a etiquetas como `VISTA: LATERAL` / `VISTA: TRASERA` junto con `MANUAL`, la ausencia de `weight(1f, fill = false)` y `softWrap = false` provocaba que el texto `CHEVROLET CHEVY C2 • 2009 • 2P GRIS METÁLICO` pudiera envolverse en varias líneas verticales, aumentando la altura de la fila y empujando visualmente los controles hacia el techo del modelo 3D.
   - Se fijó `.weight(1f, fill = false).padding(end = 8.dp).height(24.dp)` en la cápsula de identidad ([`VehicleShowcaseCard.kt`](file:///C:/DivineDeck/app/src/main/kotlin/com/divinedeck/launcher/core/designsystem/components/VehicleShowcaseCard.kt#L105-L152)) y `.height(24.dp)` con `maxLines = 1, softWrap = false` en [`StudioChipButton`](file:///C:/DivineDeck/app/src/main/kotlin/com/divinedeck/launcher/core/designsystem/components/VehicleShowcaseCard.kt#L256-L302), garantizando que nombre y controles permanezcan alineados en una única línea horizontal de `24.dp` sin superponerse ni invadir el espacio del vehículo.

---

## 2. Comparativa Visual 1340 × 800 (Fase 3.1.1 vs. Fase 3.1)

````carousel
![Fase 3.1.1 (Actual): Barra superior reubicada junto al límite superior del visor (top = 6.dp, height = 24.dp), aumentando la separación libre sobre el Chevrolet Chevy C2](C:/Users/Administrator/.gemini/antigravity/brain/15eab831-3149-4572-8743-e9a3b1ec9598/cockpit_v311_preview.png)
<!-- slide -->
![Fase 3.1 (Anterior): Barra superior más baja y más próxima al techo del vehículo](C:/Users/Administrator/.gemini/antigravity/brain/15eab831-3149-4572-8743-e9a3b1ec9598/cockpit_v31_preview.png)
````

---

## 3. Verificación de Restricciones Absolutas

| Elemento / Restricción | Estado en Fase 3.1.1 |
| :--- | :--- |
| **Archivo modificado** | Únicamente [`VehicleShowcaseCard.kt`](file:///C:/DivineDeck/app/src/main/kotlin/com/divinedeck/launcher/core/designsystem/components/VehicleShowcaseCard.kt) |
| **Tamaño del visor 3D (`ChevyStudioViewport`)** | **Sin cambios** (`Modifier.fillMaxSize()`) |
| **Geometría `.glb` y escala del modelo** | **Sin cambios** (`chevy_c2_2009.glb` intacto) |
| **Cámara, FOV, zoom y distancia (`ChevyGlbStudioView.kt`)** | **Sin cambios** (`FOV 28°`, `distance 3.52m`, `camTargetY 0.60m`) |
| **Widgets laterales (`CockpitWidgetColumn.kt`)** | **Sin cambios** |
| **Dock inferior (`AutomotiveBottomDock.kt`)** | **Sin cambios** |
| **Temas, navegación, GPS y modo inmersivo** | **Sin cambios** |
