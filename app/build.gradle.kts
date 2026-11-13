plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.divinedeck.launcher"
  // compileSdk 37: los módulos bitchord-* compilan contra 37 y el chequeo
  // de metadatos AAR exige que :app también compile contra 37.
  compileSdk = 37
  defaultConfig {
    applicationId = "com.divinedeck.launcher"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"
    // Número de build de GitHub Actions (el mismo que usa el tag
    // debug-06.11-<run_number>). En local es 0.
    buildConfigField("int", "GITHUB_RUN_NUMBER", (System.getenv("GITHUB_RUN_NUMBER") ?: "0"))
    // Solo arm64-v8a: es la ABI de la Galaxy Tab A11 y el AAR de
    // Sherpa-ONNX trae las 4 ABIs (~49 MB); sin este filtro el APK
    // cargaría con librerías nativas que la tablet nunca usa.
    ndk {
      abiFilters += "arm64-v8a"
    }
  }

  signingConfigs {
    // Firma debug persistente (Fase 06.11): si el entorno provee la ruta
    // DIVINEDECK_DEBUG_KEYSTORE, el APK debug se firma con esa llave para
    // que todos los APK debug tengan la misma firma y se instalen como
    // actualización normal. Sin esa variable, AGP usa su debug keystore
    // por defecto y cada máquina firma con una llave distinta (Android
    // rechaza entonces la instalación por "conflicto con el paquete").
    getByName("debug") {
      val keystorePath = System.getenv("DIVINEDECK_DEBUG_KEYSTORE")
      if (!keystorePath.isNullOrBlank()) {
        storeFile = file(keystorePath)
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Location & GPS (FusedLocationProviderClient - FuelSense Engine)
  implementation(libs.play.services.location)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)

  // BitChord Modular Architecture
  implementation(project(":bitchord-core"))
  implementation(project(":bitchord-data"))
  implementation(project(":bitchord-playback"))
  implementation(project(":bitchord-player-ui"))
  implementation(libs.coil3.compose)
  implementation(libs.coil3.network.okhttp)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Media3 (BitChord internal playback engine & MediaSession)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.datasource.okhttp)

  // InnerTubeX & NewPipe stream resolution engine
  implementation(libs.innertubex)
  implementation(libs.newpipe.extractor)
  implementation(libs.newpipe.nanojson)
  implementation(libs.jsoup)
  implementation(libs.mozilla.rhino)
  implementation(libs.mozilla.rhino.engine)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.kotlinx.serialization.json)

  // Sherpa-ONNX: reconocimiento de voz sin internet (offline, gratis,
  // sin clave ni cuenta). El modelo Whisper tiny en español (~104 MB) NO
  // va en el APK: se descarga una sola vez desde Hugging Face
  // (ver SherpaModelManager). Solo JitPack: la coordenada de Maven
  // Central con el mismo nombre es de un tercero sin relación.
  // Coordenada de 3 segmentos: JitPack publica el AAR precompilado
  // (jitpack.yml lo descarga del release) como
  // com.github.k2-fsa:sherpa-onnx:v2.0.1. Un cuarto segmento haría
  // que Gradle leyera "sherpa-onnx" como versión (run 37 falló por eso).
  // Sin dependencias adicionales: el AAR ya trae ONNX Runtime y JNI.
  // El POM del AAR declara además sherpa-onnx-jvm, pero el AAR ya
  // incluye esas mismas clases com.k2fsa.sherpa.onnx.*: sin excluirlo,
  // :app:checkDebugDuplicateClasses falla con ~190 clases duplicadas
  // (run 38). Se excluye solo ese módulo; el resto de transitivas queda.
  implementation("com.github.k2-fsa:sherpa-onnx:v2.0.1") {
    exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-jvm")
  }

}
