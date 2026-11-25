package com.notremock.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.notremock.app.core.privacy.OemBrand
import com.notremock.app.core.privacy.OemGuideHelper
import com.notremock.app.core.privacy.SetupDiagnostics
import com.notremock.app.core.privacy.SystemIntentHelper
import com.notremock.app.ui.theme.AmberWarning
import com.notremock.app.ui.theme.EmeraldPrimary
import com.notremock.app.ui.viewmodel.MainViewModel
import com.notremock.app.widget.NotreMockWidgetProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val diag by viewModel.diagnostics.collectAsState()
    var step by remember { mutableIntStateOf(1) }
    var selectedBrand by remember { mutableStateOf(OemBrand.detect()) }
    val totalSteps = 4

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wizard_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.completeOnboarding()
                        onDone()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.completeOnboarding()
                        onDone()
                    }) {
                        Text(stringResource(R.string.wizard_skip))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.wizard_step_counter, step, totalSteps),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = { step / totalSteps.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DiagnosticSummaryCard(diag)

                when (step) {
                    1 -> Step1Permissions(
                        diag = diag,
                        onRequestPermissions = onRequestPermissions,
                        onOpenLocationSettings = { SystemIntentHelper.openLocationSettings(context) }
                    )
                    2 -> Step2DeveloperAndMock(
                        diag = diag,
                        selectedBrand = selectedBrand,
                        onSelectBrand = { selectedBrand = it },
                        onOpenAboutPhone = { OemGuideHelper.openAboutPhoneSettings(context) },
                        onOpenDevOptions = { SystemIntentHelper.openDeveloperOptions(context) }
                    )
                    3 -> Step3BatteryOptimization(
                        diag = diag,
                        selectedBrand = selectedBrand,
                        onSelectBrand = { selectedBrand = it },
                        onRequestExemption = { SystemIntentHelper.requestBatteryExemption(context) },
                        onOpenAutostart = { OemGuideHelper.openOemAutostartOrBatterySettings(context) },
                        onOpenAppInfo = { SystemIntentHelper.openAppDetails(context) }
                    )
                    4 -> Step4TileAndWidget(
                        onPinWidget = {
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
                        }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.wizard_prev))
                    }
                }
                Button(
                    onClick = {
                        if (step < totalSteps) {
                            step++
                        } else {
                            viewModel.completeOnboarding()
                            onDone()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (step < totalSteps) stringResource(R.string.wizard_next)
                        else stringResource(R.string.wizard_finish)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticSummaryCard(diag: SetupDiagnostics) {
    val blockerMessage = when {
        !diag.hasLocationPermission -> stringResource(R.string.diag_issue_loc_perm)
        !diag.isDeviceLocationOn -> stringResource(R.string.diag_issue_gps_off)
        !diag.isDeveloperOptionsOn -> stringResource(R.string.diag_issue_dev_off)
        !diag.isMockAppSelected -> stringResource(R.string.diag_issue_mock_unset)
        !diag.isBatteryExempt -> stringResource(R.string.diag_issue_battery)
        else -> stringResource(R.string.diag_all_ready)
    }
    val allOk = diag.isCoreReady && diag.isBatteryExempt

    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (allOk) EmeraldPrimary else AmberWarning),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = if (allOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (allOk) EmeraldPrimary else AmberWarning
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.diag_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = blockerMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Step1Permissions(
    diag: SetupDiagnostics,
    onRequestPermissions: () -> Unit,
    onOpenLocationSettings: () -> Unit
) {
    SectionCard(title = stringResource(R.string.wizard_step1_title)) {
        Text(
            text = stringResource(R.string.wizard_step1_desc),
            style = MaterialTheme.typography.bodyMedium
        )
        StatusPillRow(stringResource(R.string.setup_location_perm), diag.hasLocationPermission)
        StatusPillRow(stringResource(R.string.setup_notif_perm), diag.hasNotificationPermission)
        StatusPillRow(stringResource(R.string.wizard_turn_on_gps), diag.isDeviceLocationOn)

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.wizard_grant_perms))
        }
        if (!diag.isDeviceLocationOn) {
            OutlinedButton(
                onClick = onOpenLocationSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.wizard_turn_on_gps))
            }
        }
    }
}

@Composable
private fun Step2DeveloperAndMock(
    diag: SetupDiagnostics,
    selectedBrand: OemBrand,
    onSelectBrand: (OemBrand) -> Unit,
    onOpenAboutPhone: () -> Unit,
    onOpenDevOptions: () -> Unit
) {
    SectionCard(title = stringResource(R.string.wizard_step2_title)) {
        Text(stringResource(R.string.wizard_step2_desc), style = MaterialTheme.typography.bodyMedium)
        StatusPillRow(
            label = if (diag.isDeveloperOptionsOn) stringResource(R.string.wizard_dev_status_on)
            else stringResource(R.string.wizard_dev_status_off),
            ok = diag.isDeveloperOptionsOn
        )
        StatusPillRow(
            label = if (diag.isMockAppSelected) stringResource(R.string.wizard_mock_status_on)
            else stringResource(R.string.wizard_mock_status_off),
            ok = diag.isMockAppSelected
        )

        OemBrandSelector(selectedBrand = selectedBrand, onSelectBrand = onSelectBrand)

        val guideText = when (selectedBrand) {
            OemBrand.SAMSUNG -> stringResource(R.string.oem_dev_samsung)
            OemBrand.XIAOMI -> stringResource(R.string.oem_dev_xiaomi)
            OemBrand.HUAWEI -> stringResource(R.string.oem_dev_huawei)
            OemBrand.OPPO_REALME_ONEPLUS -> stringResource(R.string.oem_dev_oppo)
            OemBrand.STOCK_OTHER -> stringResource(R.string.oem_dev_stock)
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = guideText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!diag.isDeveloperOptionsOn) {
                OutlinedButton(
                    onClick = onOpenAboutPhone,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.wizard_open_about_phone))
                }
            }
            Button(
                onClick = onOpenDevOptions,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.open_dev_options))
            }
        }
    }
}

@Composable
private fun Step3BatteryOptimization(
    diag: SetupDiagnostics,
    selectedBrand: OemBrand,
    onSelectBrand: (OemBrand) -> Unit,
    onRequestExemption: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    SectionCard(title = stringResource(R.string.wizard_step3_title)) {
        Text(stringResource(R.string.wizard_step3_desc), style = MaterialTheme.typography.bodyMedium)
        StatusPillRow(
            label = if (diag.isBatteryExempt) stringResource(R.string.setting_battery_opt_ok)
            else stringResource(R.string.setting_battery_opt_warn),
            ok = diag.isBatteryExempt
        )

        Button(
            onClick = onRequestExemption,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.setting_battery_opt))
        }

        OemBrandSelector(selectedBrand = selectedBrand, onSelectBrand = onSelectBrand)

        val batGuide = when (selectedBrand) {
            OemBrand.SAMSUNG -> stringResource(R.string.oem_bat_samsung)
            OemBrand.XIAOMI -> stringResource(R.string.oem_bat_xiaomi)
            OemBrand.HUAWEI -> stringResource(R.string.oem_bat_huawei)
            OemBrand.OPPO_REALME_ONEPLUS -> stringResource(R.string.oem_bat_oppo)
            OemBrand.STOCK_OTHER -> stringResource(R.string.oem_bat_stock)
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = batGuide,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onOpenAutostart,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.wizard_open_oem_autostart))
            }
            OutlinedButton(
                onClick = onOpenAppInfo,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.wizard_open_app_info))
            }
        }
    }
}

@Composable
private fun Step4TileAndWidget(onPinWidget: () -> Unit) {
    SectionCard(title = stringResource(R.string.wizard_step4_title)) {
        Text(
            text = stringResource(R.string.wizard_step4_desc),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onPinWidget,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.pin_widget_btn))
        }
    }
}

@Composable
private fun OemBrandSelector(
    selectedBrand: OemBrand,
    onSelectBrand: (OemBrand) -> Unit
) {
    val brands = listOf(
        OemBrand.SAMSUNG to stringResource(R.string.oem_samsung),
        OemBrand.XIAOMI to stringResource(R.string.oem_xiaomi),
        OemBrand.HUAWEI to stringResource(R.string.oem_huawei),
        OemBrand.OPPO_REALME_ONEPLUS to stringResource(R.string.oem_oppo),
        OemBrand.STOCK_OTHER to stringResource(R.string.oem_stock)
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(brands) { (brand, label) ->
            FilterChip(
                selected = selectedBrand == brand,
                onClick = { onSelectBrand(brand) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun StatusPillRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (ok) EmeraldPrimary else AmberWarning
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}
