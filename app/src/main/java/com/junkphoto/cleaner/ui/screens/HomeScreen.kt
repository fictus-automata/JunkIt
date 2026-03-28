package com.junkphoto.cleaner.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.junkphoto.cleaner.db.JunkPhotoEntity
import com.junkphoto.cleaner.util.PreferenceManager
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isJunkModeActive: Boolean,
    activeCount: Int,
    deletedCount: Int,
    ttlMillis: Long,
    monitoredDir: String,
    junkPhotos: List<JunkPhotoEntity>,
    onToggleJunkMode: (Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    onUnjunk: (Long) -> Unit,
    onKeepPhotos: (List<Long>) -> Unit,
    onDeletePhotos: (List<JunkPhotoEntity>) -> Unit,
    onOpenRecycleBin: () -> Unit
) {
    // Fullscreen viewer state lifted to HomeScreen level
    var fullscreenPhoto by remember { mutableStateOf<JunkPhotoEntity?>(null) }
    
    // Selection state
    var selectionMode by remember { mutableStateOf(false) }
    val selectedPhotoIds = remember { mutableStateListOf<Long>() }

    if (selectionMode) {
        BackHandler {
            selectionMode = false
            selectedPhotoIds.clear()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "JunkIt",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            // Sort by ascending expiration time (soonest to delete first)
            val sorted = remember(junkPhotos) {
                junkPhotos.sortedBy { it.expiresAt }
            }

            // Group by TTL label
            val grouped = remember(sorted) {
                val ttlLabelMap = PreferenceManager.TTL_OPTIONS.entries
                    .associate { (label, millis) -> millis to label }

                sorted.groupBy { photo ->
                    ttlLabelMap[photo.ttlMillis] ?: formatTtl(photo.ttlMillis)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 2.dp),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Top section: toggle, stats, config
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))
                        JunkModeCard(
                            isActive = isJunkModeActive,
                            onToggle = onToggleJunkMode
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.PhotoLibrary,
                                label = "Tracked",
                                value = "$activeCount"
                            )
                            StatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Delete,
                                label = "Cleaned",
                                value = "$deletedCount",
                                onClick = onOpenRecycleBin
                            )
                        }

                        ConfigInfoCard(
                            ttlMillis = ttlMillis,
                            monitoredDir = monitoredDir
                        )
                    }
                }

                // Photo grid section — sorted by expiresAt, grouped by TTL
                if (junkPhotos.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectionMode) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        selectionMode = false
                                        selectedPhotoIds.clear()
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                                    }
                                    Text(
                                        "${selectedPhotoIds.size} selected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row {
                                    TextButton(
                                        onClick = {
                                            onKeepPhotos(selectedPhotoIds.toList())
                                            selectionMode = false
                                            selectedPhotoIds.clear()
                                        },
                                        enabled = selectedPhotoIds.isNotEmpty()
                                    ) {
                                        Text("Keep")
                                    }
                                    TextButton(
                                        onClick = {
                                            val photosToDelete = junkPhotos.filter { it.id in selectedPhotoIds }
                                            onDeletePhotos(photosToDelete)
                                            selectionMode = false
                                            selectedPhotoIds.clear()
                                        },
                                        enabled = selectedPhotoIds.isNotEmpty(),
                                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Delete")
                                    }
                                }
                            } else {
                                Text(
                                    "Junk Photos",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    grouped.forEach { (label, photos) ->
                        // Section header spanning full width
                        item(
                            key = "header_$label",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = 18.dp,
                                        bottom = 12.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${photos.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Photos in this group
                        items(photos, key = { it.id }) { photo ->
                            val isSelected = selectedPhotoIds.contains(photo.id)
                            JunkPhotoGridItem(
                                photo = photo,
                                selectionMode = selectionMode,
                                isSelected = isSelected,
                                onToggleSelect = {
                                    if (isSelected) {
                                        selectedPhotoIds.remove(photo.id)
                                        if (selectedPhotoIds.isEmpty()) selectionMode = false
                                    } else {
                                        selectedPhotoIds.add(photo.id)
                                    }
                                },
                                onEnterSelectionMode = {
                                    selectionMode = true
                                    selectedPhotoIds.add(photo.id)
                                },
                                onTap = { fullscreenPhoto = photo },
                                onUnjunk = { onUnjunk(photo.id) }
                            )
                        }
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyStateCard(
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }
        }

        // Fullscreen overlay — drawn on top of Scaffold
        fullscreenPhoto?.let { photo ->
            FullscreenPhotoViewer(
                photo = photo,
                onDismiss = { fullscreenPhoto = null },
                onUnjunk = {
                    onUnjunk(photo.id)
                    fullscreenPhoto = null
                }
            )
        }
    }
}

@Composable
private fun FullscreenPhotoViewer(
    photo: JunkPhotoEntity,
    onDismiss: () -> Unit,
    onUnjunk: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black)
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(photo.filePath))
                    .crossfade(true)
                    .build(),
                contentDescription = "Fullscreen photo",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )

            // Keep button at bottom-right of the image
            androidx.compose.material3.Button(
                onClick = onUnjunk,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1976D2), // Explicitly blue
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Keep",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun JunkModeCard(
    isActive: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (isActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Junk Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isActive) "Monitoring camera directory" else "Tap to start monitoring",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = if (onClick != null) modifier.clickable { onClick() } else modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConfigInfoCard(
    ttlMillis: Long,
    monitoredDir: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Auto-delete after: ${formatTtl(ttlMillis)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Watching: $monitoredDir",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun JunkPhotoGridItem(
    photo: JunkPhotoEntity,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onEnterSelectionMode: () -> Unit,
    onTap: () -> Unit,
    onUnjunk: () -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(false) }

    // Bottom sheet with details
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Thumbnail preview
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(photo.filePath))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = photo.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTimeRemaining(photo.expiresAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatFileSize(photo.fileSize),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = {
                        onUnjunk()
                        showBottomSheet = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Keep this photo", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Grid thumbnail tile
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (isSelected) Modifier.border(
                    BorderStroke(4.dp, Color(0xFF1976D2)),
                    RoundedCornerShape(4.dp)
                ) else Modifier
            )
            .combinedClickable(
                onClick = { 
                    if (selectionMode) {
                        onToggleSelect()
                    } else {
                        onTap() 
                    }
                },
                onLongClick = { 
                    if (!selectionMode) {
                        onEnterSelectionMode()
                    } else {
                        showBottomSheet = true 
                    }
                }
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(photo.filePath))
                .crossfade(true)
                .size(300)
                .build(),
            contentDescription = photo.fileName,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isSelected) Modifier.padding(4.dp).clip(RoundedCornerShape(2.dp)) else Modifier
                ),
            contentScale = ContentScale.Crop
        )

        // Selection Checkmark overlay
        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(Color(0xFF1976D2), CircleShape)
            )
        }

        // Gradient overlay at the bottom with time remaining
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = formatTimeRemaining(photo.expiresAt),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyStateCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No junk photos yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enable Junk Mode and take photos — they'll appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// --- Utility formatters ---

private fun formatTtl(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    return when {
        hours < 24 -> "$hours hour${if (hours != 1L) "s" else ""}"
        else -> {
            val days = hours / 24
            "$days day${if (days != 1L) "s" else ""}"
        }
    }
}

private fun formatTimeRemaining(expiresAt: Long): String {
    val remaining = expiresAt - System.currentTimeMillis()
    return if (remaining <= 0) {
        "Expired — pending deletion"
    } else {
        val hours = TimeUnit.MILLISECONDS.toHours(remaining)
        when {
            hours < 1 -> {
                val mins = TimeUnit.MILLISECONDS.toMinutes(remaining)
                "Deletes in ${mins}m"
            }

            hours < 24 -> "Deletes in ${hours}h"
            else -> {
                val days = hours / 24
                "Deletes in ${days}d"
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
