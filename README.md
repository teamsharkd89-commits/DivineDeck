# DIVINE DECK

Launcher automotriz Android para el **Chevrolet Chevy C2 2009** (gris metálico,
2 puertas, 1.6L MPFI, 100 HP) en una **Samsung Galaxy Tab A11 SM-X133**
(1340×800, horizontal). Kotlin + Jetpack Compose, Gradle multi-módulo.

No es un launcher de accesos directos: es un sistema de infoentretenimiento
integrado — Cockpit con Chevy 3D, velocímetro GPS, Widget Center, música
BitChord 1.8.1 integrada (sin abrir app externa), App Center y Nav Center.

## Módulos

- `:app` — launcher, Cockpit, GPS, widgets, ajustes
- `:bitchord-core`, `:bitchord-data`, `:bitchord-playback`, `:bitchord-player-ui`
  — motor de música BitChord (reproducción local y streaming, descargas
  offline, letras sincronizadas, EQ/DSP, cola con gestos)

## Temas (Fase 06.11)

10 temas seleccionables en **Ajustes > Personalización**, se guardan y se
restauran al iniciar. Tema por defecto: **Minimalista**.

| Tema | Descripción |
|---|---|
| Minimalista | Principal. Claro, blanco y gris, máxima legibilidad. |
| Rojo Sport | Fibra de carbono y rojo racing. |
| Espectro | Arcoíris sobre negro. |
| Vaporwave | Rosa neón, cian y sol retro. |
| Ácido Lima | Negro con verde lima fosforescente. |
| Obsidian, Arctic, Monochrome, Bronze, Personalizado | Los 5 temas clásicos. |

El layout Panorámico es el mismo en todos los temas; solo cambia la paleta.

## Compilar

Android Studio, JDK 17, compileSdk 37, minSdk 26, targetSdk 35.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --tests "*ThemeTest" --tests "*SettingsRepositoryTest"
```

El APK debug sale en `app/build/outputs/apk/debug/app-debug.apk`.

## Estado

- Último APK validado: **Phase 06.9.1** (SHA-256
  `85D474C5B353A580E105D6ACB66C18797AEAF5DA77CDD72BFB576B842AB5BA36`).
- Phase 06.10 en curso: M0 y M1 terminados; M2 (letras y artwork offline)
  con 5 tests en fallo conocidos; M3 (widget Cockpit) y M4 (regresión) pendientes.
- **Nota sobre assets binarios:** el modelo 3D `app/src/main/assets/models/chevy_c2_2009.glb`,
  los iconos del launcher (`res/mipmap-*/ic_launcher*.webp`), el sonido
  `chime_test.wav` y `gradle/wrapper/gradle-wrapper.jar` son archivos binarios
  y van en `assets-bin/` codificados en base64 (el conector de subida solo
  transporta texto UTF-8). El workflow `.github/workflows/build-apk.yml` los
  decodifica antes de compilar; en un clon local, ver `assets-bin/README.md`.

## Licencia / uso

Proyecto personal de Daniel Hernández. No publicar APKs ni releases sin su
aprobación expresa.
