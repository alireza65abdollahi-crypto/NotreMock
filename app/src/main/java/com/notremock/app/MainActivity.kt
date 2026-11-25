package com.notremock.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.notremock.app.data.prefs.ThemeMode
import com.notremock.app.service.MockForegroundService
import com.notremock.app.ui.screens.AdvancedSettingsScreen
import com.notremock.app.ui.screens.GuideScreen
import com.notremock.app.ui.screens.HomeScreen
import com.notremock.app.ui.screens.SettingsScreen
import com.notremock.app.ui.screens.SetupWizardScreen
import com.notremock.app.ui.theme.NotreMockTheme
import com.notremock.app.ui.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var isUnlocked by mutableStateOf(false)
    private var lockChecked = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleAutoStartIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsState()
            val isSettingsLoaded by viewModel.isSettingsLoaded.collectAsState()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            LaunchedEffect(isSettingsLoaded, settings.biometricLock) {
                if (isSettingsLoaded && !lockChecked) {
                    lockChecked = true
                    if (settings.biometricLock) {
                        promptBiometricUnlock()
                    } else {
                        isUnlocked = true
                    }
                }
            }

            NotreMockTheme(darkTheme = darkTheme) {
                if (isSettingsLoaded && (isUnlocked || !settings.biometricLock)) {
                    val navController = rememberNavController()
                    LaunchedEffect(isSettingsLoaded, settings.hasCompletedOnboarding) {
                        if (isSettingsLoaded && !settings.hasCompletedOnboarding &&
                            navController.currentDestination?.route == Route.HOME
                        ) {
                            navController.navigate(Route.SETUP_WIZARD)
                        }
                    }
                    NavHost(navController = navController, startDestination = Route.HOME) {
                        composable(Route.HOME) {
                            HomeScreen(
                                viewModel = viewModel,
                                onRequestPermissions = ::requestCorePermissions,
                                onOpenSettings = { navController.navigate(Route.SETTINGS) },
                                onOpenGuide = { navController.navigate(Route.GUIDE) },
                                onOpenWizard = { navController.navigate(Route.SETUP_WIZARD) }
                            )
                        }
                        composable(Route.SETTINGS) {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenAdvanced = { navController.navigate(Route.ADVANCED) },
                                onOpenWizard = { navController.navigate(Route.SETUP_WIZARD) }
                            )
                        }
                        composable(Route.ADVANCED) {
                            AdvancedSettingsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Route.GUIDE) {
                            GuideScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Route.SETUP_WIZARD) {
                            SetupWizardScreen(
                                viewModel = viewModel,
                                onRequestPermissions = ::requestCorePermissions,
                                onDone = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAutoStartIntent(intent)
    }

    private fun handleAutoStartIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_AUTO_START_ON_OPEN, false) == true) {
            MockForegroundService.start(this, -1L)
        }
    }

    private fun requestCorePermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun promptBiometricUnlock() {
        val bm = BiometricManager.from(this)
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
        if (bm.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            isUnlocked = true
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isUnlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        finish()
                    } else {
                        isUnlocked = true
                    }
                }
            }
        )
        val infoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            infoBuilder.setAllowedAuthenticators(authenticators)
        } else {
            infoBuilder.setNegativeButtonText(getString(R.string.cancel))
        }
        prompt.authenticate(infoBuilder.build())
    }

    object Route {
        const val HOME = "home"
        const val SETTINGS = "settings"
        const val ADVANCED = "advanced"
        const val GUIDE = "guide"
        const val SETUP_WIZARD = "setup_wizard"
    }

    companion object {
        const val EXTRA_AUTO_START_ON_OPEN = "extra_auto_start_on_open"
    }
}
