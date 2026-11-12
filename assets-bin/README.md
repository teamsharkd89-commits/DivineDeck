# Archivos binarios (base64)

El conector de subida a GitHub de Muse solo transporta archivos de texto
UTF-8, así que los archivos binarios del proyecto van aquí codificados en
base64, con la misma ruta relativa que deben tener en el proyecto y la
extensión `.b64`.

El workflow `.github/workflows/build-apk.yml` los decodifica a su lugar
antes de compilar. En un clon local, decodifícalos con:

```bash
find assets-bin -name '*.b64' | while read f; do
  dest="${f#assets-bin/}"
  dest="${dest%.b64}"
  mkdir -p "$(dirname "$dest")"
  base64 -d "$f" > "$dest"
done
```

Contenido:

- `app/src/main/assets/models/chevy_c2_2009.glb` — modelo 3D del Chevy C2
  (aprobado visualmente, no reemplazar sin motivo importante).
- `app/src/main/assets/chime_test.wav` — sonido de prueba.
- `gradle/wrapper/gradle-wrapper.jar` — necesario para `./gradlew`.
- `app/src/main/res/mipmap-*/ic_launcher*.webp` — iconos del launcher.

No borres estos archivos sin subir primero los binarios reales por otro
medio (por ejemplo, la interfaz web de GitHub).
