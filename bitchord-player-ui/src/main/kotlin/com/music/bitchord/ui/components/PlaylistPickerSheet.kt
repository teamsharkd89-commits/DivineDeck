package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.model.artworkAt

/**
 * Selector y creador de playlists de BitChord 1.8.1 (`PlaylistPickerSheet`)
 * adaptado para la pantalla automotriz horizontal (1340×800) de DIVINE DECK.
 *
 * Permite:
 * - Seleccionar una o varias playlists existentes donde añadir una canción.
 * - Crear una nueva playlist directamente (con o sin canciones iniciales).
 */
@Composable
fun PlaylistPickerSheet(
    playlists: List<UserPlaylist>,
    songToAdd: Song? = null,
    initialCreating: Boolean = false,
    onAddToPlaylists: (List<UserPlaylist>) -> Unit,
    onCreatePlaylist: (title: String, privacy: PlaylistPrivacy) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember(initialCreating) { mutableStateOf(initialCreating || playlists.isEmpty()) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF141824),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        ) {
            if (creating) {
                NewPlaylistForm(
                    songPreview = songToAdd,
                    onCreate = { title, privacy ->
                        onCreatePlaylist(title, privacy)
                    },
                    onBack = if (playlists.isNotEmpty() && !initialCreating) {
                        { creating = false }
                    } else null,
                    onDismiss = onDismiss,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Text(
                        text = if (songToAdd != null) "Añadir «${songToAdd.title}» a playlist" else "Seleccionar playlist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))

                    // Fila para crear nueva playlist e incluir la canción
                    Surface(
                        onClick = { creating = true },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Nueva playlist",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(playlists, key = { it.playlistId }) { playlist ->
                            val isSelected = playlist.playlistId in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.14f)
                                        else Color.White.copy(alpha = 0.04f)
                                    )
                                    .clickable {
                                        selectedIds = if (isSelected) {
                                            selectedIds - playlist.playlistId
                                        } else {
                                            selectedIds + playlist.playlistId
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E2436)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!playlist.thumbnailUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = playlist.thumbnailUrl.artworkAt(ROW_ART_PX),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.matchParentSize(),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.QueueMusic,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (playlist.subtitle.isNotBlank()) {
                                        Text(
                                            text = playlist.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.65f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Cancelar", color = Color.White)
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                val chosen = playlists.filter { it.playlistId in selectedIds }
                                if (chosen.isNotEmpty()) {
                                    onAddToPlaylists(chosen)
                                }
                            },
                            enabled = selectedIds.isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color.Black,
                            ),
                        ) {
                            Text(
                                text = if (selectedIds.size <= 1) "Añadir" else "Añadir (${selectedIds.size})",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formulario de creación de nueva playlist (`NewPlaylistForm` de BitChord 1.8.1).
 */
@Composable
fun NewPlaylistForm(
    songPreview: Song? = null,
    onCreate: (title: String, privacy: PlaylistPrivacy) -> Unit,
    onBack: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(PlaylistPrivacy.PRIVATE) }
    val canCreate = title.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(22.dp),
    ) {
        Text(
            text = "Nueva playlist",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (songPreview != null) {
                "Se creará la playlist y se añadirá «${songPreview.title}»."
            } else {
                "Introduce un nombre para tu nueva playlist personal."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(16.dp))

        PillTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = "Nombre de la playlist",
            container = Color(0xFF1E2436),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (canCreate) onCreate(title.trim(), privacy) },
            ),
        )

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = { (onBack ?: onDismiss)() },
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(if (onBack != null) "Volver" else "Cancelar", color = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { if (canCreate) onCreate(title.trim(), privacy) },
                enabled = canCreate,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color.Black,
                ),
            ) {
                Text("Crear", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Diálogo de renombrado de playlist (`RenamePlaylistForm` de BitChord 1.8.1).
 */
@Composable
fun RenamePlaylistForm(
    currentTitle: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(currentTitle) { mutableStateOf(currentTitle) }
    val trimmed = title.trim()
    val canSave = trimmed.isNotEmpty() && trimmed != currentTitle

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF141824),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
            ) {
                Text(
                    text = "Renombrar playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(14.dp))
                PillTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Nombre de la playlist",
                    container = Color(0xFF1E2436),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (canSave) onRename(trimmed) },
                    ),
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Cancelar", color = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { if (canSave) onRename(trimmed) },
                        enabled = canSave,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color.Black,
                        ),
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de confirmación para eliminar una playlist.
 */
@Composable
fun DeletePlaylistConfirmDialog(
    playlistTitle: String,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF141824),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
            ) {
                Text(
                    text = "Eliminar playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "¿Seguro que deseas eliminar la playlist «$playlistTitle»? Esta acción no se puede deshacer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Cancelar", color = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = onConfirmDelete,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5252),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Eliminar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Menú contextual de una tarjeta de Playlist o Colección al mantener pulsado en `LibraryScreen`.
 */
@Composable
fun PlaylistContextMenuDialog(
    title: String,
    isPinned: Boolean,
    isEditablePlaylist: Boolean,
    onPlay: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF141824),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.08f),
                )

                ContextMenuRow(
                    icon = Icons.Rounded.PlayArrow,
                    label = "Reproducir ahora",
                    tint = Color(0xFF00E5FF),
                    onClick = {
                        onDismiss()
                        onPlay()
                    },
                )

                ContextMenuRow(
                    icon = Icons.Rounded.PushPin,
                    label = if (isPinned) "Desfijar de Biblioteca" else "Fijar al inicio de Playlists",
                    tint = Color.White,
                    onClick = {
                        onDismiss()
                        onTogglePin()
                    },
                )

                if (isEditablePlaylist && onRename != null) {
                    ContextMenuRow(
                        icon = Icons.Rounded.Edit,
                        label = "Renombrar playlist",
                        tint = Color.White,
                        onClick = {
                            onDismiss()
                            onRename()
                        },
                    )
                }

                if (isEditablePlaylist && onDelete != null) {
                    ContextMenuRow(
                        icon = Icons.Rounded.Delete,
                        label = "Eliminar playlist",
                        tint = Color(0xFFFF5252),
                        onClick = {
                            onDismiss()
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}
