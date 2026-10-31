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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Sync
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.femi.gallery.R
import com.femi.gallery.data.work.BackgroundScans
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
            // Fix 5: MaterialTheme.shapes.large replaces hard-coded RoundedCornerShape(24.dp)
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
                    // Fix 9: ExposedDropdownMenuBox replaces OutlinedTextField + overlay-Box hack.
                    // Correct accessibility semantics (role = combobox), no click intercept needed.
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
                            currentThemeMode = option.mode
                            themeExpanded = false
                            ThemePreferences.setThemeMode(context, option.mode)
                        },
                    )
                }
            }

            // Auto-play Setting Card
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

            // Mute by Default Setting Card
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
                                    .clickable {
                                        scanOnlyCharging = !scanOnlyCharging
                                        ThemePreferences.setScanOnlyWhileCharging(context, scanOnlyCharging)
                                        BackgroundScans.update(context, settingsChanged = true)
                                    }
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
    }
}

/**
 * Fix 9: ExposedDropdownMenuBox-based picker replacing the old OutlinedTextField + overlay Box hack.
 * [ExposedDropdownMenu] is a scope extension on [ExposedDropdownMenuBoxScope], so it must be called
 * directly inside the [ExposedDropdownMenuBox] content lambda (not nested inside other lambdas).
 */
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

/** Icon + title + summary row reused inside setting cards. */
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
        // Fix 5: MaterialTheme.shapes.medium replaces hard-coded RoundedCornerShape(14.dp)
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

/** A settings card with an icon, a title, a line of explanation and a switch; the whole card is tappable. */
@Composable
private fun SettingSwitchCard(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // Fix 5: MaterialTheme.shapes.large replaces hard-coded RoundedCornerShape(24.dp)
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
