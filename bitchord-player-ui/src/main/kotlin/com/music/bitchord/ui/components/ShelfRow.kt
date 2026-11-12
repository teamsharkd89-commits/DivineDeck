package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Wraps a horizontal shelf's [LazyRow] when the platform has chrome to add around one.
 */
typealias ShelfRowChrome = @Composable (
    state: LazyListState,
    modifier: Modifier,
    row: @Composable () -> Unit,
) -> Unit

val LocalShelfRowChrome = staticCompositionLocalOf<ShelfRowChrome> { { _, _, row -> row() } }

/**
 * [LazyRow] for a horizontal shelf, routed through [LocalShelfRowChrome].
 */
@Composable
fun ShelfRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: LazyListScope.() -> Unit,
) {
    LocalShelfRowChrome.current(state, modifier) {
        LazyRow(
            state = state,
            modifier = modifier,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content,
        )
    }
}
