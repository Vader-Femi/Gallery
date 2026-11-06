package com.femi.gallery.presentation.people

import android.content.ContentUris
import android.provider.MediaStore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.femi.gallery.R
import com.femi.gallery.data.db.PersonRow
import com.femi.gallery.domain.face.FaceScanStatus
import com.femi.gallery.presentation.common.HugeTopAppBar
import com.femi.gallery.presentation.selection.NameDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PeopleScreen(
    onBack: () -> Unit,
    onPersonClick: (personId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PeopleViewModel = hiltViewModel(),
) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val faceRecognitionOn by viewModel.enabled.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var renamingPerson by remember { mutableStateOf<PersonRow?>(null) }
    renamingPerson?.let { person ->
        NameDialog(
            title = stringResource(R.string.people_rename_title),
            confirmLabel = stringResource(R.string.people_rename_confirm),
            initialName = if (person.isNamed) person.name else "",
            onConfirm = { name ->
                viewModel.renamePerson(person.id, name)
                renamingPerson = null
            },
            onDismiss = { renamingPerson = null },
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HugeTopAppBar(
                title = stringResource(R.string.people_title),
                onBack = onBack,
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        IconButton(
                            onClick = { showMenu = true },
                            enabled = faceRecognitionOn && scanState.status != FaceScanStatus.Scanning,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = null,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.people_scan_button)) },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.startScan()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.people_rescan_all)) },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.rescan()
                            },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (scanState.status == FaceScanStatus.Scanning) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Face,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.people_scan_button),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.people_scan_progress, scanState.scanned, scanState.total),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val progress = if (scanState.total > 0) {
                            scanState.scanned.toFloat() / scanState.total.toFloat()
                        } else 0f
                        LinearWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (!faceRecognitionOn) {
                PeopleOff(onTurnOn = viewModel::turnOn)
            } else if (people.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(80.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Face,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = stringResource(R.string.people_empty_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.people_empty_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.startScan() },
                                enabled = scanState.status != FaceScanStatus.Scanning,
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.people_scan_button),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            } else {
                val namedPeople = remember(people) { people.filter { it.isNamed } }
                val unnamedPeople = remember(people) { people.filter { !it.isNamed } }
                val showSections = namedPeople.isNotEmpty() && unnamedPeople.isNotEmpty()

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(145.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (showSections) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(
                                title = stringResource(R.string.people_section_named),
                                count = namedPeople.size,
                            )
                        }
                        items(namedPeople, key = { it.id }) { person ->
                            PersonCard(
                                person = person,
                                onClick = { onPersonClick(person.id) },
                                onRename = { renamingPerson = person },
                                onRemove = { viewModel.removePerson(person.id) },
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(
                                title = stringResource(R.string.people_section_unnamed),
                                count = unnamedPeople.size,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        items(unnamedPeople, key = { it.id }) { person ->
                            PersonCard(
                                person = person,
                                onClick = { onPersonClick(person.id) },
                                onRename = { renamingPerson = person },
                                onRemove = { viewModel.removePerson(person.id) },
                            )
                        }
                    } else {
                        items(people, key = { it.id }) { person ->
                            PersonCard(
                                person = person,
                                onClick = { onPersonClick(person.id) },
                                onRename = { renamingPerson = person },
                                onRemove = { viewModel.removePerson(person.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PersonCard(
    person: PersonRow,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = if (person.isNamed) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        tonalElevation = if (person.isNamed) 2.dp else 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (person.isNamed) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.people_rename_title)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.people_delete_confirm)) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PersonAvatar(
                    coverMediaId = person.coverMediaId,
                    coverCrop = person.coverCrop,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (person.isNamed) 2.5.dp else 1.5.dp,
                            color = if (person.isNamed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (person.isNamed) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(6.dp))
                if (person.isNamed) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = stringResource(R.string.people_photos_count, person.photoCount),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = stringResource(R.string.people_photos_count, person.photoCount),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onRename() },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = stringResource(R.string.people_add_name),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersonAvatar(
    coverMediaId: Long,
    coverCrop: String,
    modifier: Modifier = Modifier,
) {
    val uri = remember(coverMediaId) {
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, coverMediaId).toString()
    }
    val parts = remember(coverCrop) {
        coverCrop.split(',').mapNotNull { it.toFloatOrNull() }
    }
    val (cx, cy, zoom) = remember(parts) {
        if (parts.size == 4) {
            val l = parts[0]
            val t = parts[1]
            val r = parts[2]
            val b = parts[3]
            val midX = (l + r) / 2f
            val midY = (t + b) / 2f
            val faceW = (r - l).coerceAtLeast(0.05f)
            val faceH = (b - t).coerceAtLeast(0.05f)
            val span = maxOf(faceW, faceH) * 1.5f
            val scale = (1f / span).coerceIn(1f, 5f)
            Triple(midX, midY, scale)
        } else {
            Triple(0.5f, 0.5f, 1f)
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment((cx - 0.5f) * 2f, (cy - 0.5f) * 2f),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = TransformOrigin(cx, cy)
                },
        )
    }
}


/** Shown instead of the people while face recognition is off in Settings. */
@Composable
private fun PeopleOff(onTurnOn: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.people_off_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.people_off_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onTurnOn) { Text(stringResource(R.string.people_off_button)) }
        }
    }
}
