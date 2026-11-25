package com.notremock.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.notremock.app.data.prefs.AppLanguage
import com.notremock.app.data.prefs.PreferencesRepository
import com.notremock.app.service.MockForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotreMockApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        MockForegroundService.createNotificationChannels(this)
        appScope.launch {
            val lang = PreferencesRepository(this@NotreMockApp).settingsFlow.first().language
            applyAppLocale(lang)
        }
    }

    companion object {
        fun applyAppLocale(language: AppLanguage) {
            val locales = if (language == AppLanguage.SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.tag)
            }
            if (AppCompatDelegate.getApplicationLocales() != locales) {
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }
    }
}
