package com.femi.gallery.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExposedDropdownMenu
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.femi.gallery.R
import com.femi.gallery.data.work.BackgroundScans
import com.femi.gallery.domain.mirror.AlbumSortOption
import com.femi.gallery.presentation.common.HugeTopAppBar

private data class LanguageOption(
    val tag: String,
    val nameRes: Int,
)

private val languageOptions = listOf(
    LanguageOption("", R.string.language_system),
    LanguageOption("en", R.string.language_en),
    LanguageOption("yo", R.string.language_yo),
    LanguageOption("ig", R.string.language_ig),
    LanguageOption("ha", R.string.language_ha),
    LanguageOption("pcm", R.string.language_pcm),
)

private data class ThemeOption(
    val mode: Int,
    val nameRes: Int,
)

private val themeOptions = listOf(
    ThemeOption(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, R.string.theme_system),
    ThemeOption(AppCompatDelegate.MODE_NIGHT_NO, R.string.theme_light),
    ThemeOption(AppCompatDelegate.MODE_NIGHT_YES, R.string.theme_dark),
)

private data class SortOption(
    val option: AlbumSortOption,
    val nameRes: Int,
)

private val sortOptions = listOf(
    SortOption(AlbumSortOption.Recency, R.string.settings_album_sort_recency),
    SortOption(AlbumSortOption.Name, R.string.settings_album_sort_name),
    SortOption(AlbumSortOption.Count, R.string.settings_album_sort_count),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocales.isEmpty) "" else currentLocales.toLanguageTags()

    var selectedTag by remember(currentTag) { mutableStateOf(currentTag) }
    var languageExpanded by remember { mutableStateOf(false) }

    val currentThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var themeExpanded by remember { mutableStateOf(false) }

    val autoplayEnabled by viewModel.autoplayVideos.collectAsStateWithLifecycle()
    val muteEnabled by viewModel.muteVideos.collectAsStateWithLifecycle()
    val backgroundScan by viewModel.backgroundScan.collectAsStateWithLifecycle()
    val scanOnlyCharging by viewModel.scanOnlyWhileCharging.collectAsStateWithLifecycle()

    val currentSortOption by viewModel.albumSortOption.collectAsStateWithLifecycle()
    var sortExpanded by remember { mutableStateOf(false) }

    val albums by viewModel.albums.collectAsStateWithLifecycle()
    var showHideAlbumsDialog by remember { mutableStateOf(false) }
    val hiddenAlbums by viewModel.hiddenAlbums.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val toggleBackgroundScan: (Boolean) -> Unit = { enabled ->
        viewModel.setBackgroundScan(enabled)
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val selectedLanguageName = stringResource(
        languageOptions.find { it.tag == selectedTag }?.nameRes ?: R.string.language_system
    )

    val selectedThemeName = stringResource(
        themeOptions.find { it.mode == currentThemeMode }?.nameRes ?: R.string.theme_system
    )

    val selectedSortName = stringResource(
        sortOptions.find { it.option == currentSortOption }?.nameRes ?: R.string.settings_album_sort_recency
    )

    val deletedCount by viewModel.deletedEmptyAlbumsCount.collectAsStateWithLifecycle()
    LaunchedEffect(deletedCount) {
        deletedCount?.let { count ->
            val message = if (count > 0) {
                context.getString(R.string.settings_delete_empty_albums_deleted, count)
            } else {
                context.getString(R.string.settings_delete_empty_albums_none)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearDeletedCount()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HugeTopAppBar(
                title = stringResource(R.string.menu_settings),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsGroup(title = stringResource(R.string.settings_group_general)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Language Setting Card
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SettingCardHeader(
                                icon = Icons.Outlined.Language,
                                title = stringResource(R.string.settings_language),
                                summary = stringResource(R.string.settings_language_summary),
                            )
                            SettingDropdown(
                                selectedName = selectedLanguageName,
                                expanded = languageExpanded,
                                onExpandedChange = { languageExpanded = it },
                                items = languageOptions.map { stringResource(it.nameRes) },
                                onItemSelected = { index ->
                                    val option = languageOptions[index]
                                    selectedTag = option.tag
                                    languageExpanded = false
                                    val locales = if (option.tag.isEmpty()) {
                                        LocaleListCompat.getEmptyLocaleList()
                                    } else {
                                        LocaleListCompat.forLanguageTags(option.tag)
                                    }
                                    AppCompatDelegate.setApplicationLocales(locales)
                                },
                            )
                        }
                    }

                    // Theme Setting Card
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SettingCardHeader(
                                icon = Icons.Outlined.Brightness6,
                                title = stringResource(R.string.settings_theme),
                                summary = stringResource(R.string.settings_theme_summary),
                            )
                            SettingDropdown(
                                selectedName = selectedThemeName,
                                expanded = themeExpanded,
                                onExpandedChange = { themeExpanded = it },
                                items = themeOptions.map { stringResource(it.nameRes) },
                                onItemSelected = { index ->
                                    val option = themeOptions[index]
                                    themeExpanded = false
                                    viewModel.setThemeMode(option.mode)
                                },
                            )
                        }
                    }
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_group_albums)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Sort Setting Card
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SettingCardHeader(
                                icon = Icons.AutoMirrored.Outlined.Sort,
                                title = stringResource(R.string.settings_album_sort),
                                summary = stringResource(R.string.settings_album_sort_summary),
                            )
                            SettingDropdown(
                                selectedName = selectedSortName,
                                expanded = sortExpanded,
                                onExpandedChange = { sortExpanded = it },
                                items = sortOptions.map { stringResource(it.nameRes) },
                                onItemSelected = { index ->
                                    val option = sortOptions[index]
                                    sortExpanded = false
                                    viewModel.setAlbumSortOption(option.option)
                                },
                            )
                        }
                    }

                    // Hide Albums Card
                    SettingActionCard(
                        icon = Icons.Outlined.FolderOff,
                        title = stringResource(R.string.settings_hide_albums),
                        summary = stringResource(R.string.settings_hide_albums_summary),
                        onClick = { showHideAlbumsDialog = true },
                    )

                    // Delete Empty Albums Card
                    SettingActionCard(
                        icon = Icons.Outlined.DeleteOutline,
                        title = stringResource(R.string.settings_delete_empty_albums),
                        summary = stringResource(R.string.settings_delete_empty_albums_summary),
                        onClick = { viewModel.deleteEmptyAlbums() },
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_group_video)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Auto-play Setting Card
                    SettingSwitchCard(
                        icon = Icons.Outlined.PlayArrow,
                        title = stringResource(R.string.settings_autoplay_videos),
                        summary = stringResource(R.string.settings_autoplay_videos_summary),
                        checked = autoplayEnabled,
                        onCheckedChange = { viewModel.setAutoplayVideos(it) },
                    )

                    // Mute by Default Setting Card
                    SettingSwitchCard(
                        icon = Icons.AutoMirrored.Outlined.VolumeOff,
                        title = stringResource(R.string.settings_mute_videos),
                        summary = stringResource(R.string.settings_mute_videos_summary),
                        checked = muteEnabled,
                        onCheckedChange = { viewModel.setMuteVideos(it) },
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_group_indexing)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Library scans in the background
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggleBackgroundScan(!backgroundScan) }
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                SettingCardHeader(
                                    icon = Icons.Outlined.Sync,
                                    title = stringResource(R.string.settings_background_scan),
                                    summary = stringResource(R.string.settings_background_scan_summary),
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = backgroundScan,
                                    onCheckedChange = { toggleBackgroundScan(it) },
                                )
                            }

                            AnimatedVisibility(
                                visible = backgroundScan,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.setScanOnlyWhileCharging(!scanOnlyCharging) }
                                            .padding(20.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        SettingCardHeader(
                                            icon = Icons.Outlined.BatteryChargingFull,
                                            title = stringResource(R.string.settings_scan_charging),
                                            summary = stringResource(R.string.settings_scan_charging_summary),
                                            modifier = Modifier.weight(1f),
                                        )
                                        Switch(
                                            checked = scanOnlyCharging,
                                            onCheckedChange = { viewModel.setScanOnlyWhileCharging(it) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ClipModelCard()
        }

        if (showHideAlbumsDialog) {
            AlertDialog(
                onDismissRequest = { showHideAlbumsDialog = false },
                title = { Text(stringResource(R.string.settings_hide_albums_dialog_title)) },
                text = {
                    LazyColumn {
                        items(albums, key = { it.bucketId }) { album ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newHidden = hiddenAlbums.toMutableSet()
                                        if (newHidden.contains(album.bucketId)) {
                                            newHidden.remove(album.bucketId)
                                        } else {
                                            newHidden.add(album.bucketId)
                                        }
                                        viewModel.setHiddenAlbums(newHidden)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = hiddenAlbums.contains(album.bucketId),
                                    onCheckedChange = null
                                )
                                Text(
                                    text = album.name,
                                    modifier = Modifier.padding(start = 16.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHideAlbumsDialog = false }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingDropdown(
    selectedName: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<String>,
    onItemSelected: (index: Int) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            items.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onItemSelected(index) },
                )
            }
        }
    }
}

@Composable
private fun SettingCardHeader(
    icon: ImageVector,
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.height(48.dp),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingSwitchCard(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingCardHeader(
                icon = icon,
                title = title,
                summary = summary,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingActionCard(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingCardHeader(
                icon = icon,
                title = title,
                summary = summary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
