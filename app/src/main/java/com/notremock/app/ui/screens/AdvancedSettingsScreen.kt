package com.notremock.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notremock.app.R
import com.notremock.app.core.privacy.ShizukuStatus
import com.notremock.app.core.privacy.TimeZoneController
import com.notremock.app.data.prefs.PanicAction
import com.notremock.app.data.prefs.TimeZoneMode
import com.notremock.app.ui.theme.AmberWarning
import com.notremock.app.ui.theme.EmeraldPrimary
import com.notremock.app.ui.theme.RoseDanger
import com.notremock.app.ui.viewmodel.MainViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val shizukuStatus by viewModel.shizukuStatus.collectAsState()
    val isXposedActive by viewModel.isXposedActive.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Location Realism
            item {
                SectionCard(title = stringResource(R.string.section_realism)) {
                    Text(stringResource(R.string.adv_jitter, settings.jitterMeters), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.jitterMeters.toFloat(),
                        onValueChange = {
                            viewModel.updateRealism(it.roundToInt(), settings.cityRadiusKm, settings.updateIntervalMs, settings.altitudeMeters)
                        },
                        valueRange = 0f..15f
                    )

                    Text(stringResource(R.string.adv_city_radius, settings.cityRadiusKm), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.cityRadiusKm,
                        onValueChange = {
                            viewModel.updateRealism(settings.jitterMeters, (it * 10).roundToInt() / 10f, settings.updateIntervalMs, settings.altitudeMeters)
                        },
                        valueRange = 0f..5f
                    )

                    Text(stringResource(R.string.adv_interval, settings.updateIntervalMs), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.updateIntervalMs.toFloat(),
                        onValueChange = {
                            val snapped = ((it / 250f).roundToInt() * 250).coerceIn(500, 5000)
                            viewModel.updateRealism(settings.jitterMeters, settings.cityRadiusKm, snapped, settings.altitudeMeters)
                        },
                        valueRange = 500f..5000f
                    )

                    Text(stringResource(R.string.adv_altitude, settings.altitudeMeters), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.altitudeMeters.toFloat(),
                        onValueChange = {
                            viewModel.updateRealism(settings.jitterMeters, settings.cityRadiusKm, settings.updateIntervalMs, it.roundToInt())
                        },
                        valueRange = 0f..1500f
                    )
                }
            }

            // Kill Switch & Security
            item {
                SectionCard(title = stringResource(R.string.section_kill_switch)) {
                    SwitchRow(
                        title = stringResource(R.string.adv_watchdog),
                        subtitle = stringResource(R.string.adv_watchdog_desc),
                        checked = settings.watchdogEnabled,
                        onCheckedChange = {
                            viewModel.updateKillSwitch(it, settings.stealthNotification, settings.panicAction, settings.fallbackCityId)
                        }
                    )
                    SwitchRow(
                        title = stringResource(R.string.adv_stealth_notif),
                        subtitle = stringResource(R.string.adv_stealth_notif_desc),
                        checked = settings.stealthNotification,
                        onCheckedChange = {
                            viewModel.updateKillSwitch(settings.watchdogEnabled, it, settings.panicAction, settings.fallbackCityId)
                        }
                    )
                    SwitchRow(
                        title = stringResource(R.string.adv_biometric_lock),
                        subtitle = stringResource(R.string.adv_biometric_lock_desc),
                        checked = settings.biometricLock,
                        onCheckedChange = { viewModel.setBiometricLock(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.adv_panic_action), style = MaterialTheme.typography.labelLarge)
                    RadioRow(
                        label = stringResource(R.string.panic_stop),
                        selected = settings.panicAction == PanicAction.STOP_MOCKING,
                        onClick = {
                            viewModel.updateKillSwitch(settings.watchdogEnabled, settings.stealthNotification, PanicAction.STOP_MOCKING, settings.fallbackCityId)
                        }
                    )
                    RadioRow(
                        label = stringResource(R.string.panic_fallback),
                        selected = settings.panicAction == PanicAction.JUMP_FALLBACK_CITY,
                        onClick = {
                            viewModel.updateKillSwitch(settings.watchdogEnabled, settings.stealthNotification, PanicAction.JUMP_FALLBACK_CITY, settings.selectedCityId)
                        }
                    )
                }
            }

            // TimeZone Engine
            item {
                SectionCard(title = stringResource(R.string.section_timezone_engine)) {
                    RadioRow(
                        label = stringResource(R.string.tz_mode_manual),
                        selected = settings.timeZoneMode == TimeZoneMode.MANUAL,
                        onClick = { viewModel.setTimeZoneMode(TimeZoneMode.MANUAL) }
                    )
                    RadioRow(
                        label = stringResource(R.string.tz_mode_shizuku),
                        selected = settings.timeZoneMode == TimeZoneMode.SHIZUKU,
                        onClick = { viewModel.setTimeZoneMode(TimeZoneMode.SHIZUKU) }
                    )
                    RadioRow(
                        label = stringResource(R.string.tz_mode_root),
                        selected = settings.timeZoneMode == TimeZoneMode.ROOT,
                        onClick = { viewModel.setTimeZoneMode(TimeZoneMode.ROOT) }
                    )

                    if (settings.timeZoneMode == TimeZoneMode.SHIZUKU) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = when (shizukuStatus) {
                                    ShizukuStatus.READY -> stringResource(R.string.shizuku_status_ready)
                                    ShizukuStatus.NEEDS_PERMISSION -> stringResource(R.string.shizuku_status_perm)
                                    ShizukuStatus.NOT_RUNNING -> stringResource(R.string.shizuku_status_missing)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (shizukuStatus == ShizukuStatus.READY) EmeraldPrimary else AmberWarning,
                                modifier = Modifier.weight(1f)
                            )
                            if (shizukuStatus == ShizukuStatus.NEEDS_PERMISSION) {
                                TextButton(onClick = { TimeZoneController.requestShizukuPermission() }) {
                                    Text(stringResource(R.string.enable))
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.applyCityTimeZone() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.apply_timezone_now))
                    }
                }
            }

            // Optional Network Check
            item {
                SectionCard(title = stringResource(R.string.section_network_optin)) {
                    SwitchRow(
                        title = stringResource(R.string.adv_ip_check_toggle),
                        subtitle = stringResource(R.string.adv_ip_check_desc),
                        checked = settings.allowIpCheck,
                        onCheckedChange = { viewModel.setAllowIpCheck(it) }
                    )
                }
            }

            // Clearly Separated Root & LSPosed Pro Section
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, RoseDanger.copy(alpha = 0.65f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = RoseDanger)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.section_root_lsposed),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RoseDanger
                            )
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AmberWarning)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.root_warning_banner),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Text(
                            text = if (isXposedActive) stringResource(R.string.lsposed_active) else stringResource(R.string.lsposed_inactive),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isXposedActive) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        SwitchRow(
                            title = stringResource(R.string.root_per_app_bypass),
                            subtitle = stringResource(R.string.root_per_app_bypass_desc),
                            checked = settings.xposedPerAppBypass,
                            onCheckedChange = { viewModel.updateXposedSettings(perAppBypass = it) }
                        )
                        SwitchRow(
                            title = stringResource(R.string.root_hide_sim),
                            subtitle = stringResource(R.string.root_hide_sim_desc),
                            checked = settings.xposedHideSim,
                            onCheckedChange = { viewModel.updateXposedSettings(hideSim = it) }
                        )
                        SwitchRow(
                            title = stringResource(R.string.root_hide_ismock),
                            subtitle = stringResource(R.string.root_hide_ismock_desc),
                            checked = settings.xposedHideIsMock,
                            onCheckedChange = { viewModel.updateXposedSettings(hideIsMock = it) }
                        )
                        SwitchRow(
                            title = stringResource(R.string.root_per_app_tz),
                            subtitle = stringResource(R.string.root_per_app_tz_desc),
                            checked = settings.xposedPerAppTz,
                            onCheckedChange = { viewModel.updateXposedSettings(perAppTz = it) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
