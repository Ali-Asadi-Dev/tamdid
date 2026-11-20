package com.aliasadi.tamdid.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.aliasadi.tamdid.R
import com.aliasadi.tamdid.core.AppLanguage
import com.aliasadi.tamdid.core.AppSettings
import com.aliasadi.tamdid.core.CalendarSystem
import com.aliasadi.tamdid.core.CurrencyCode
import com.aliasadi.tamdid.ui.LocalAppFormat
import com.aliasadi.tamdid.ui.components.SelectField
import kotlinx.coroutines.launch

private data class IntroPage(val icon: ImageVector, val title: Int, val body: Int)

private val pages = listOf(
    IntroPage(Icons.Rounded.CalendarMonth, R.string.onboarding_1_title, R.string.onboarding_1_body),
    IntroPage(Icons.Rounded.NotificationsActive, R.string.onboarding_2_title, R.string.onboarding_2_body),
    IntroPage(Icons.Rounded.Savings, R.string.onboarding_3_title, R.string.onboarding_3_body),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(settings: AppSettings, onFinish: (CurrencyCode, CalendarSystem) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size + 1 })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.size

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            if (!isLast) TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pages.size) } }) {
                Text(stringResource(R.string.action_skip))
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            if (index < pages.size) IntroPageView(pages[index]) else SetupPage(settings, onFinish)
        }
        Row(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages.size + 1) { i ->
                    Box(
                        Modifier.size(if (i == pagerState.currentPage) 10.dp else 8.dp).clip(CircleShape).background(
                            if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        ),
                    )
                }
            }
            if (!isLast) {
                Button(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                    Text(stringResource(R.string.action_next))
                }
            }
        }
    }
}

@Composable
private fun IntroPageView(page: IntroPage) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(140.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            }
        }
        Spacer(Modifier.height(40.dp))
        Text(stringResource(page.title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(page.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupPage(settings: AppSettings, onFinish: (CurrencyCode, CalendarSystem) -> Unit) {
    val format = LocalAppFormat.current
    val context = LocalContext.current
    // Defaults follow the language picked on this page (e.g. Persian → toman and Solar Hijri).
    var currency by remember(format.language) { mutableStateOf(AppSettings.defaults(format.language).primaryCurrency) }
    var calendar by remember(format.language) { mutableStateOf(AppSettings.defaults(format.language).calendar) }
    fun notificationsGranted() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(notificationsGranted()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        Text(stringResource(R.string.setup_language), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (language in AppLanguage.entries) {
                FilterChip(
                    selected = format.language == language,
                    onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.code)) },
                    label = { Text(language.nativeName) },
                )
            }
        }

        SelectField(
            label = stringResource(R.string.setup_currency),
            selected = currency,
            options = CurrencyCode.entries,
            optionLabel = { "${it.displayName(format.language)} (${it.code})" },
            onSelect = { currency = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.setup_calendar), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (system in CalendarSystem.entries) {
                FilterChip(
                    selected = calendar == system,
                    onClick = { calendar = system },
                    label = { Text(stringResource(if (system == CalendarSystem.Jalali) R.string.calendar_jalali else R.string.calendar_gregorian)) },
                )
            }
        }

        Text(stringResource(R.string.setup_notifications), style = MaterialTheme.typography.titleSmall)
        if (granted) {
            Text(stringResource(R.string.setup_notifications_allowed), color = MaterialTheme.colorScheme.tertiary)
        } else {
            OutlinedButton(onClick = { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text(stringResource(R.string.setup_notifications_allow))
            }
        }

        Text(stringResource(R.string.setup_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(onClick = { onFinish(currency, calendar) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(stringResource(R.string.action_get_started))
        }
    }
}
