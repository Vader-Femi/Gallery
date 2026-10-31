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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import android.Manifest
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
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

    var currentThemeMode by remember { mutableIntStateOf(ThemePreferences.getThemeMode(context)) }
    var themeExpanded by remember { mutableStateOf(false) }

    var autoplayEnabled by remember { mutableStateOf(ThemePreferences.getAutoPlayVideos(context)) }
    var muteEnabled by remember { mutableStateOf(ThemePreferences.getMuteVideos(context)) }
    var backgroundScan by remember { mutableStateOf(ThemePreferences.getBackgroundScan(context)) }
    var scanOnlyCharging by remember { mutableStateOf(ThemePreferences.getScanOnlyWhileCharging(context)) }
    
    var currentSortOption by remember { mutableStateOf(ThemePreferences.getAlbumSortOption(context)) }
    var sortExpanded by remember { mutableStateOf(false) }

    val albums by viewModel.albums.collectAsStateWithLifecycle()
    var showHideAlbumsDialog by remember { mutableStateOf(false) }
    var hiddenAlbums by remember { mutableStateOf(ThemePreferences.getHiddenAlbums(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val toggleBackgroundScan: (Boolean) -> Unit = { enabled ->
        backgroundScan = enabled
        ThemePreferences.setBackgroundScan(context, enabled)
        BackgroundScans.update(context, settingsChanged = true)
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
            // Expressive Language Setting Card
            Surface(
                shape = RoundedCornerShape(24.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.height(48.dp),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_language),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_language_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Material Expressive Dropdown for Language
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = selectedLanguageName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { languageExpanded = !languageExpanded }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                    )
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { languageExpanded = !languageExpanded },
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { languageExpanded = !languageExpanded }
                        ) {}

                        DropdownMenu(
                            expanded = languageExpanded,
                            onDismissRequest = { languageExpanded = false },
                        ) {
                            languageOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.nameRes)) },
                                    onClick = {
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
                    }
                }
            }

            // Expressive Theme Setting Card
            Surface(
                shape = RoundedCornerShape(24.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.height(48.dp),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Brightness6,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_theme),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_theme_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Material Expressive Dropdown for Theme
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = selectedThemeName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { themeExpanded = !themeExpanded }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                    )
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { themeExpanded = !themeExpanded },
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { themeExpanded = !themeExpanded }
                        ) {}

                        DropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false },
                        ) {
                            themeOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.nameRes)) },
                                    onClick = {
                                        currentThemeMode = option.mode
                                        themeExpanded = false
                                        ThemePreferences.setThemeMode(context, option.mode)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Expressive Sort Setting Card
            Surface(
                shape = RoundedCornerShape(24.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.height(48.dp),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_album_sort),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_album_sort_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Material Expressive Dropdown for Sort
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = selectedSortName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { sortExpanded = !sortExpanded }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                    )
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sortExpanded = !sortExpanded },
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { sortExpanded = !sortExpanded }
                        ) {}

                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false },
                        ) {
                            sortOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.nameRes)) },
                                    onClick = {
                                        currentSortOption = option.option
                                        sortExpanded = false
                                        ThemePreferences.setAlbumSortOption(context, option.option)
                                    },
                                )
                            }
                        }
                    }
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

            // Expressive Auto-play Setting Card
            SettingSwitchCard(
                icon = Icons.Outlined.PlayArrow,
                title = stringResource(R.string.settings_autoplay_videos),
                summary = stringResource(R.string.settings_autoplay_videos_summary),
                checked = autoplayEnabled,
                onCheckedChange = {
                    autoplayEnabled = it
                    ThemePreferences.setAutoPlayVideos(context, it)
                },
            )

            // Expressive Mute by Default Setting Card
            SettingSwitchCard(
                icon = Icons.AutoMirrored.Outlined.VolumeOff,
                title = stringResource(R.string.settings_mute_videos),
                summary = stringResource(R.string.settings_mute_videos_summary),
                checked = muteEnabled,
                onCheckedChange = {
                    muteEnabled = it
                    ThemePreferences.setMuteVideos(context, it)
                },
            )

            // Library scans in the background
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                toggleBackgroundScan(!backgroundScan)
                            }
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.height(48.dp),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Sync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_background_scan),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_background_scan_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = backgroundScan,
                            onCheckedChange = {
                                toggleBackgroundScan(it)
                            },
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
                                    .clickable {
                                        scanOnlyCharging = !scanOnlyCharging
                                        ThemePreferences.setScanOnlyWhileCharging(context, scanOnlyCharging)
                                        BackgroundScans.update(context, settingsChanged = true)
                                    }
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.height(48.dp),
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.BatteryChargingFull,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_scan_charging),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.settings_scan_charging_summary),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = scanOnlyCharging,
                                    onCheckedChange = {
                                        scanOnlyCharging = it
                                        ThemePreferences.setScanOnlyWhileCharging(context, it)
                                        BackgroundScans.update(context, settingsChanged = true)
                                    },
                                )
                            }
                        }
                    }
                }
            }
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
                                        hiddenAlbums = newHidden
                                        ThemePreferences.setHiddenAlbums(context, newHidden)
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

/** A settings card with an icon, a title, a line of explanation, acts as a button. */
@Composable
private fun SettingActionCard(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
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
}
/** A settings card with an icon, a title, a line of explanation and a switch; the whole card is tappable. */
@Composable
private fun SettingSwitchCard(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
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
            Surface(
                shape = RoundedCornerShape(14.dp),
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
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
