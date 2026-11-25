package com.notremock.app.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notremock.app.R
import com.notremock.app.core.location.MockErrorType
import com.notremock.app.core.privacy.SystemIntentHelper
import com.notremock.app.core.privacy.TimeZoneApplyResult
import com.notremock.app.data.model.City
import com.notremock.app.ui.theme.AmberWarning
import com.notremock.app.ui.theme.EmeraldPrimary
import com.notremock.app.ui.theme.RoseDanger
import com.notremock.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val isPersian = LocalConfiguration.current.locales[0]?.language == "fa"

    val settings by viewModel.settings.collectAsState()
    val runtime by viewModel.runtimeState.collectAsState()
    val selectedCity by viewModel.selectedCity.collectAsState()
    val favorites by viewModel.favoriteCities.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val totalCount by viewModel.totalCitiesCount.collectAsState()
    val audit by viewModel.auditReport.collectAsState()
    val hasLocPerm by viewModel.hasLocationPermission.collectAsState()
    val tzFeedback by viewModel.tzFeedback.collectAsState()

    LaunchedEffect(tzFeedback) {
        when (val fb = tzFeedback) {
            is TimeZoneApplyResult.Applied -> {
                Toast.makeText(context, context.getString(R.string.tz_applied_toast, fb.timeZoneId), Toast.LENGTH_SHORT).show()
                viewModel.clearTzFeedback()
            }
            is TimeZoneApplyResult.RequiresManual -> {
                Toast.makeText(context, context.getString(R.string.tz_manual_toast, fb.timeZoneId), Toast.LENGTH_LONG).show()
                SystemIntentHelper.openDateAndTimeSettings(context)
                viewModel.clearTzFeedback()
            }
            is TimeZoneApplyResult.Failed -> {
                SystemIntentHelper.openDateAndTimeSettings(context)
                viewModel.clearTzFeedback()
            }
            null -> Unit
        }
    }

    val displayCity = runtime.activeCity ?: selectedCity

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_shield_pin),
                            contentDescription = null,
                            tint = if (runtime.isRunning) EmeraldPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    Surface(
                        color = when {
                            audit.score >= 75 -> MaterialTheme.colorScheme.primaryContainer
                            audit.score >= 45 -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable { onOpenSettings() }
                    ) {
                        Text(
                            text = stringResource(R.string.privacy_score_label, audit.score),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    IconButton(onClick = onOpenGuide) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.nav_guide))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Setup Alert Card (auto-hides when ready)
            if (!hasLocPerm || !audit.isMockAppSelected || runtime.errorType != MockErrorType.NONE) {
                item {
                    SetupChecklistCard(
                        hasLocationPermission = hasLocPerm,
                        isMockSelected = audit.isMockAppSelected,
                        onRequestLocation = onRequestPermissions,
                        onOpenDevOptions = { SystemIntentHelper.openDeveloperOptions(context) }
                    )
                }
            }

            // Hero One-Tap Toggle Card
            item {
                HeroStatusCard(
                    city = displayCity,
                    isPersian = isPersian,
                    isRunning = runtime.isRunning,
                    isPaused = runtime.isPaused,
                    pausedBy = runtime.pausedByPackage,
                    currentLat = if (runtime.isRunning) runtime.currentLat else displayCity?.lat ?: 0.0,
                    currentLng = if (runtime.isRunning) runtime.currentLng else displayCity?.lng ?: 0.0,
                    bypassCount = if (settings.bypassEnabled) settings.bypassPackages.size else 0,
                    isTzMatching = audit.isTimeZoneMatching,
                    onToggleStartStop = {
                        if (!hasLocPerm) {
                            onRequestPermissions()
                        } else if (!audit.isMockAppSelected) {
                            SystemIntentHelper.openDeveloperOptions(context)
                        } else {
                            viewModel.toggleMocking()
                        }
                    },
                    onKillSwitch = { viewModel.triggerKillSwitch() },
                    onRandomize = { viewModel.randomizePoint() },
                    onApplyTimeZone = { viewModel.applyCityTimeZone() },
                    onCopyCoords = { lat, lng ->
                        clipboard.setText(AnnotatedString("%.5f, %.5f".format(lat, lng)))
                        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Favorites & Recent Quick Chips
            if (favorites.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.favorites_recent),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(favorites, key = { it.id }) { city ->
                                val isSelected = displayCity?.id == city.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectCity(city) },
                                    label = { Text(city.displayName(isPersian)) }
                                )
                            }
                        }
                    }
                }
            }

            // City Search Box
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.search_cities_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(
                    text = stringResource(R.string.all_cities_count, totalCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (searchResults.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_cities_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                }
            } else {
                items(searchResults, key = { it.id }) { city ->
                    CityListItem(
                        city = city,
                        isPersian = isPersian,
                        isSelected = displayCity?.id == city.id,
                        isFavorite = settings.favoriteCityIds.contains(city.id),
                        onSelect = { viewModel.selectCity(city) },
                        onToggleFavorite = { viewModel.toggleFavorite(city.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SetupChecklistCard(
    hasLocationPermission: Boolean,
    isMockSelected: Boolean,
    onRequestLocation: () -> Unit,
    onOpenDevOptions: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.setup_needed_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (!hasLocationPermission) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.setup_location_perm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRequestLocation) {
                        Text(stringResource(R.string.enable), fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!isMockSelected) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.setup_mock_app),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpenDevOptions) {
                        Text(stringResource(R.string.open_dev_options), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStatusCard(
    city: City?,
    isPersian: Boolean,
    isRunning: Boolean,
    isPaused: Boolean,
    pausedBy: String?,
    currentLat: Double,
    currentLng: Double,
    bypassCount: Int,
    isTzMatching: Boolean,
    onToggleStartStop: () -> Unit,
    onKillSwitch: () -> Unit,
    onRandomize: () -> Unit,
    onApplyTimeZone: () -> Unit,
    onCopyCoords: (Double, Double) -> Unit
) {
    val borderColor = when {
        isRunning && isPaused -> AmberWarning
        isRunning -> EmeraldPrimary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, borderColor.copy(alpha = 0.7f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isRunning && !isPaused) Icons.Default.CheckCircle else Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = when {
                            isRunning && isPaused -> AmberWarning
                            isRunning -> EmeraldPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            isRunning && isPaused -> stringResource(R.string.status_paused, pausedBy ?: "")
                            isRunning -> stringResource(R.string.status_active)
                            else -> stringResource(R.string.status_inactive)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isRunning) {
                    IconButton(onClick = onRandomize) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.randomize_point)
                        )
                    }
                }
            }

            if (city != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${city.displayName(isPersian)} — ${city.displayCountry(isPersian)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${city.timeZoneId} (${city.formattedUtcOffset()}) • ${"%.4f, %.4f".format(currentLat, currentLng)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onCopyCoords(currentLat, currentLng) }
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.no_city_selected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Primary One-Tap Action Button
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onToggleStartStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.surfaceVariant else EmeraldPrimary,
                        contentColor = if (isRunning) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isRunning) stringResource(R.string.tap_to_stop) else stringResource(R.string.tap_to_start),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isRunning) {
                    OutlinedButton(
                        onClick = onKillSwitch,
                        border = BorderStroke(1.dp, RoseDanger),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseDanger),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.notif_action_stop),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Minimal Quick Status Row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                stringResource(R.string.vpn_independent_badge),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
                item {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                stringResource(R.string.bypass_count_badge, bypassCount),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
                item {
                    AssistChip(
                        onClick = onApplyTimeZone,
                        label = {
                            Text(
                                text = if (isTzMatching) {
                                    stringResource(R.string.tz_match_badge)
                                } else {
                                    stringResource(R.string.tz_mismatch_badge)
                                },
                                color = if (isTzMatching) EmeraldPrimary else AmberWarning,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CityListItem(
    city: City,
    isPersian: Boolean,
    isSelected: Boolean,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = if (isSelected) BorderStroke(1.5.dp, EmeraldPrimary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = city.displayName(isPersian),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (city.isCapital) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.capital_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = city.displaySubtitle(isPersian),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = stringResource(R.string.favorite_toggle),
                    tint = if (isFavorite) AmberWarning else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
