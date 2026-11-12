package com.music.bitchord.ui.graphics

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.palette.graphics.Palette
import coil3.Image
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

/**
 * Pixel access and Palette swatches for Android.
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
fun ImageRequest.Builder.forPixelAccess(): ImageRequest.Builder = allowHardware(false)

fun Image.toImageBitmap(): ImageBitmap = toBitmap().asImageBitmap()

fun imageBitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

fun ImageBitmap.argbPixels(): IntArray =
    IntArray(width * height).also { readPixels(it) }

class Swatch(val rgb: Int, val population: Int)

fun paletteSwatches(image: ImageBitmap, maxColors: Int, clearFilters: Boolean): List<Swatch> {
    val builder = Palette.from(image.asAndroidBitmap()).maximumColorCount(maxColors)
    if (clearFilters) builder.clearFilters()
    return builder.generate().swatches.map { Swatch(it.rgb, it.population) }
}
