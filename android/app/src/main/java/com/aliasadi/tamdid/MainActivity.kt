package com.aliasadi.tamdid

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aliasadi.tamdid.core.ThemePreference
import com.aliasadi.tamdid.ui.AppDependencies
import com.aliasadi.tamdid.ui.AppFormat
import com.aliasadi.tamdid.ui.LocalAppFormat
import com.aliasadi.tamdid.ui.TamdidApp
import com.aliasadi.tamdid.ui.currentLanguage
import com.aliasadi.tamdid.ui.screens.AppUiState
import com.aliasadi.tamdid.ui.screens.AppViewModel
import com.aliasadi.tamdid.ui.screens.OnboardingScreen
import com.aliasadi.tamdid.ui.theme.TamdidTheme

// AppCompatActivity (not ComponentActivity) so the per-app language picker works on every API level.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = appContainer
        val deps = AppDependencies(container.repository, container.clock) { container.catalog }
        setContent { TamdidRoot(deps) }
    }
}

@Composable
fun TamdidRoot(deps: AppDependencies) {
    val appViewModel: AppViewModel = viewModel { AppViewModel(deps.repository) }
    val state by appViewModel.state.collectAsStateWithLifecycle()
    val settings = when (val s = state) {
        is AppUiState.Ready -> s.settings
        is AppUiState.Onboarding -> s.settings
        AppUiState.Loading -> null
    }
    val language = currentLanguage()
    val today = remember { deps.clock.today() }
    TamdidTheme(settings?.theme ?: ThemePreference.System) {
        val format = AppFormat(language, settings?.calendar ?: com.aliasadi.tamdid.core.CalendarSystem.Gregorian, today)
        CompositionLocalProvider(LocalAppFormat provides format) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                when (val s = state) {
                    AppUiState.Loading -> Unit
                    is AppUiState.Onboarding -> OnboardingScreen(s.settings, appViewModel::completeOnboarding)
                    is AppUiState.Ready -> TamdidApp(deps)
                }
            }
        }
    }
}
