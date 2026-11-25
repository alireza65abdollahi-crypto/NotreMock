package com.notremock.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notremock.app.R
import com.notremock.app.core.privacy.ForegroundAppDetector
import com.notremock.app.core.privacy.IpCheckState
import com.notremock.app.core.privacy.SystemIntentHelper
import com.notremock.app.data.prefs.AppLanguage
import com.notremock.app.data.prefs.ThemeMode
import com.notremock.app.ui.components.BypassAppsDialog
import com.notremock.app.ui.theme.AmberWarning
import com.notremock.app.ui.theme.EmeraldPrimary
import com.notremock.app.ui.viewmodel.MainViewModel
import com.notremock.app.widget.NotreMockWidgetProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenWizard: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val audit by viewModel.auditReport.collectAsState()
    val isBatteryExempt by viewModel.isBatteryExempt.collectAsState()
    val isAccessibilityOn by viewModel.isAccessibilityEnabled.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val ipState by viewModel.ipCheckState.collectAsState()
    val selectedCity by viewModel.selectedCity.collectAsState()

    var showBypassDialog by remember { mutableStateOf(false) }

    if (showBypassDialog) {
        BypassAppsDialog(
            selectedPackages = settings.bypassPackages,
            installedApps = installedApps,
            onLoadInstalledApps = { viewModel.loadInstalledApps() },
            onTogglePackage = { viewModel.toggleBypassPackage(it) },
            onDismiss = { showBypassDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
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
            // General Section
            item {
                SectionCard(title = stringResource(R.string.section_general)) {
                    Text(stringResource(R.string.setting_language), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.language == AppLanguage.SYSTEM,
                            onClick = { viewModel.setLanguage(AppLanguage.SYSTEM) },
                            label = { Text(stringResource(R.string.lang_system)) }
                        )
                        FilterChip(
                            selected = settings.language == AppLanguage.PERSIAN,
                            onClick = { viewModel.setLanguage(AppLanguage.PERSIAN) },
                            label = { Text(stringResource(R.string.lang_fa)) }
                        )
                        FilterChip(
                            selected = settings.language == AppLanguage.ENGLISH,
                            onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) },
                            label = { Text(stringResource(R.string.lang_en)) }
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.setting_theme), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.DARK,
                            onClick = { viewModel.setTheme(ThemeMode.DARK) },
                            label = { Text(stringResource(R.string.theme_dark)) }
                        )
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.setTheme(ThemeMode.LIGHT) },
                            label = { Text(stringResource(R.string.theme_light)) }
                        )
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.setTheme(ThemeMode.SYSTEM) },
                            label = { Text(stringResource(R.string.theme_system)) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SwitchRow(
                        title = stringResource(R.string.setting_boot_start),
                        subtitle = stringResource(R.string.setting_boot_start_desc),
                        checked = settings.autoStartOnBoot,
                        onCheckedChange = { viewModel.setAutoStartOnBoot(it) }
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.setting_battery_opt), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                text = if (isBatteryExempt) stringResource(R.string.setting_battery_opt_ok) else stringResource(R.string.setting_battery_opt_warn),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isBatteryExempt) EmeraldPrimary else AmberWarning
                            )
                        }
                        if (!isBatteryExempt) {
                            TextButton(onClick = { SystemIntentHelper.requestBatteryExemption(context) }) {
                                Text(stringResource(R.string.fix_now))
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onOpenWizard,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.wizard_open_tour))
                        }
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val mgr = AppWidgetManager.getInstance(context)
                                    val provider = ComponentName(context, NotreMockWidgetProvider::class.java)
                                    if (mgr.isRequestPinAppWidgetSupported) {
                                        mgr.requestPinAppWidget(provider, null, null)
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.pin_widget_unsupported), Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, context.getString(R.string.pin_widget_unsupported), Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.pin_widget_btn))
                        }
                    }
                }
            }

            // App Bypass Section
            item {
                SectionCard(title = stringResource(R.string.section_bypass)) {
                    SwitchRow(
                        title = stringResource(R.string.bypass_enable),
                        subtitle = stringResource(R.string.bypass_enable_desc),
                        checked = settings.bypassEnabled,
                        onCheckedChange = { viewModel.setBypassEnabled(it) }
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAccessibilityOn) stringResource(R.string.bypass_accessibility_ok) else stringResource(R.string.bypass_accessibility_needed),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (isAccessibilityOn) EmeraldPrimary else AmberWarning
                            )
                            Text(
                                text = stringResource(R.string.bypass_accessibility_privacy),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!isAccessibilityOn) {
                            TextButton(onClick = { ForegroundAppDetector.openUsageAccessSettings(context) }) {
                                Text(stringResource(R.string.enable))
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showBypassDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.bypass_manage_apps, settings.bypassPackages.size))
                    }
                }
            }

            // Privacy & Leak Checkup
            item {
                SectionCard(title = stringResource(R.string.section_privacy_audit)) {
                    AuditRow(
                        label = stringResource(R.string.audit_mock_app),
                        ok = audit.isMockAppSelected,
                        onFix = { SystemIntentHelper.openDeveloperOptions(context) }
                    )
                    AuditRow(
                        label = stringResource(R.string.audit_timezone_match),
                        ok = audit.isTimeZoneMatching,
                        onFix = { viewModel.applyCityTimeZone() }
                    )
                    AuditRow(
                        label = stringResource(R.string.audit_auto_tz_off),
                        ok = audit.isAutoTimeZoneOff,
                        onFix = { SystemIntentHelper.openDateAndTimeSettings(context) }
                    )
                    AuditRow(
                        label = stringResource(R.string.audit_wifi_scan_off),
                        ok = audit.isWifiScanningOff,
                        onFix = { SystemIntentHelper.openLocationSettings(context) }
                    )
                    AuditRow(
                        label = stringResource(R.string.audit_bt_scan_off),
                        ok = audit.isBleScanningOff,
                        onFix = { SystemIntentHelper.openLocationSettings(context) }
                    )
                    AuditRow(
                        label = if (audit.isCellularProtected) stringResource(R.string.audit_sim_ok) else stringResource(R.string.audit_sim_risk),
                        subtext = if (!audit.isCellularProtected) stringResource(R.string.audit_sim_warn) else null,
                        ok = audit.isCellularProtected,
                        onFix = { SystemIntentHelper.openWirelessSettings(context) }
                    )

                    if (settings.allowIpCheck) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.audit_ip_check), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                val statusText = when (val st = ipState) {
                                    IpCheckState.Idle -> ""
                                    IpCheckState.Loading -> stringResource(R.string.audit_ip_checking)
                                    is IpCheckState.Result -> if (st.matchesCity) {
                                        stringResource(R.string.audit_ip_match, st.ipCountryCode)
                                    } else {
                                        stringResource(R.string.audit_ip_mismatch, st.ipCountryCode, selectedCity?.countryCode ?: "")
                                    }
                                    IpCheckState.Error -> stringResource(R.string.audit_ip_error)
                                }
                                if (statusText.isNotEmpty()) {
                                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            TextButton(onClick = { viewModel.checkIpMatch() }) {
                                Text(stringResource(R.string.run_ip_check))
                            }
                        }
                    }
                }
            }

            // Advanced Settings Entry
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAdvanced)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.advanced_entry_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.advanced_entry_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AuditRow(
    label: String,
    ok: Boolean,
    subtext: String? = null,
    onFix: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (ok) EmeraldPrimary else AmberWarning
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (!subtext.isNullOrBlank()) {
                Text(subtext, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!ok) {
            TextButton(onClick = onFix) {
                Text(stringResource(R.string.fix_now))
            }
        }
    }
}
