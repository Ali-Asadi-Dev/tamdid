package com.aliasadi.tamdid.ui

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasScrollAction
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aliasadi.tamdid.SampleData
import com.aliasadi.tamdid.TamdidRoot
import com.aliasadi.tamdid.core.AppData
import com.aliasadi.tamdid.core.AppLanguage
import com.aliasadi.tamdid.core.AppSettings
import com.aliasadi.tamdid.core.ServiceCatalog
import com.aliasadi.tamdid.data.InMemoryTamdidRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Drives the real screens on the JVM with Robolectric and saves screenshots to
 * build/screenshots for review (and for the README).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class UiTest {
    @get:Rule val compose = createAndroidComposeRule<TestActivity>()

    private val catalog by lazy { ServiceCatalog.fromJson(File("../../shared/catalog.json").readText()) }

    private fun launch(data: AppData): InMemoryTamdidRepository {
        val repository = InMemoryTamdidRepository(data)
        compose.setContent { TamdidRoot(AppDependencies(repository, SampleData.clock) { catalog }) }
        compose.waitForIdle()
        return repository
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val dir = File(System.getProperty("tamdid.screenshots") ?: "build/screenshots").apply { mkdirs() }
        // Draw the window directly: works under Robolectric's native graphics without PixelCopy.
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @After fun resetLocale() = AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())

    @Test
    fun homeShowsTotalsAndUpcoming() {
        launch(SampleData.english())
        compose.onNodeWithText("$94.76").assertIsDisplayed()
        compose.onNodeWithText("Next 7 days").assertIsDisplayed()
        compose.onNodeWithText("Free trials ending").assertIsDisplayed()
        compose.onAllNodesWithText("Netflix")[0].assertIsDisplayed()
        screenshot("en-1-home")
    }

    @Test
    fun everyTabRenders() {
        launch(SampleData.english())
        compose.onNodeWithText("Plans").performClick()
        compose.onNodeWithText("City Gym").assertIsDisplayed()
        screenshot("en-2-subscriptions")
        compose.onNodeWithText("Calendar").performClick()
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        screenshot("en-4-calendar")
        compose.onNodeWithText("Insights").performClick()
        compose.onNodeWithText("Last 12 months").assertIsDisplayed()
        screenshot("en-5-insights")
        compose.onAllNodesWithText("Settings")[0].performClick()
        compose.onNodeWithText("Export backup (JSON)").assertExists()
        screenshot("en-6-settings")
    }

    @Test
    fun addSubscriptionFromCatalog() {
        val repository = launch(SampleData.english())
        compose.onNodeWithTag("fab_add").performClick()
        compose.onNodeWithText("Choose a service").assertIsDisplayed()
        screenshot("en-7-catalog")
        compose.onNodeWithText("YouTube Premium").performClick()
        compose.onNodeWithTag("name").assertIsDisplayed()
        compose.onNodeWithTag("price").performTextInput("13.99")
        screenshot("en-8-edit")
        compose.onNodeWithTag("save").performClick()
        compose.waitForIdle()
        // Lands on the new subscription's detail page.
        compose.onAllNodesWithText("$13.99")[0].assertIsDisplayed()
        screenshot("en-3-detail")
        val created = repository.data.value!!.subscriptions.single { it.catalogID == "youtube-premium" }
        assertEquals(1399, created.price.amountMinor)
    }

    @Test
    fun saveIsBlockedUntilTheFormIsValid() {
        val repository = launch(SampleData.english())
        compose.onNodeWithTag("fab_add").performClick()
        compose.onNodeWithText("Custom subscription").performClick()
        compose.onNodeWithTag("save").performClick()
        compose.onNodeWithText("Enter a name").assertIsDisplayed()
        compose.onNodeWithText("Enter a valid price").assertIsDisplayed()
        assertEquals(6, repository.data.value!!.subscriptions.size)
        compose.onNodeWithTag("name").performTextReplacement("Gym locker")
        compose.onNodeWithTag("price").performTextReplacement("۵۰")
        compose.onNodeWithTag("save").performClick()
        compose.waitForIdle()
        assertEquals(7, repository.data.value!!.subscriptions.size)
    }

    @Test
    fun detailDeleteAsksFirst() {
        val repository = launch(SampleData.english())
        compose.onAllNodesWithText("Netflix")[0].performClick()
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.onNodeWithText("Delete Netflix?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(6, repository.data.value!!.subscriptions.size)
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.onAllNodesWithText("Delete")[0].performClick()
        compose.waitForIdle()
        assertEquals(5, repository.data.value!!.subscriptions.size)
    }

    @Test
    fun emptyStateInvitesTheFirstSubscription() {
        launch(AppData(settings = AppSettings(hasCompletedOnboarding = true)))
        compose.onNodeWithText("No subscriptions yet").assertIsDisplayed()
        screenshot("en-0-empty")
    }

    @Test
    fun onboardingFinishesWithChosenSettings() {
        val repository = launch(AppData(settings = AppSettings.defaults(AppLanguage.English)))
        compose.onNodeWithText("Every subscription in one place").assertIsDisplayed()
        screenshot("en-onboarding-1")
        compose.onNodeWithText("Skip").performClick()
        compose.onNodeWithText("Make it yours").assertIsDisplayed()
        compose.onNodeWithText("Solar Hijri").performClick()
        screenshot("en-onboarding-setup")
        compose.onNodeWithText("Get started").performClick()
        compose.waitForIdle()
        val settings = repository.data.value!!.settings
        assertEquals(true, settings.hasCompletedOnboarding)
        assertEquals(com.aliasadi.tamdid.core.CalendarSystem.Jalali, settings.calendar)
    }

    @Test
    @Config(qualifiers = "fa-w400dp-h860dp-xxhdpi")
    fun persianIsRightToLeftWithJalaliDatesAndToman() {
        launch(SampleData.persian())
        compose.onNodeWithText("در ماه").assertIsDisplayed()
        compose.onNodeWithText("۷ روز آینده").assertIsDisplayed()
        screenshot("fa-1-home")
        compose.onNodeWithText("تقویم").performClick()
        compose.onNodeWithText("آبان ۱۴۰۵").assertIsDisplayed()
        screenshot("fa-4-calendar")
        compose.onNodeWithText("آمار").performClick()
        compose.onNodeWithText("۱۲ ماه گذشته").assertIsDisplayed()
        screenshot("fa-5-insights")
        compose.onNodeWithText("اشتراک‌ها").performClick()
        compose.onAllNodesWithText("فیلیمو")[0].performClick()
        compose.onNodeWithText("۱۵۹٬۰۰۰ تومان").assertIsDisplayed()
        screenshot("fa-3-detail")
    }

    @Test
    @Config(qualifiers = "ar-w400dp-h860dp-xxhdpi")
    fun arabicIsRightToLeftWithArabicDigits() {
        launch(SampleData.english())
        compose.onNodeWithText("شهريًا").assertIsDisplayed()
        compose.onNodeWithText("الأيام السبعة القادمة").assertIsDisplayed()
        screenshot("ar-1-home")
        compose.onNodeWithText("التقويم").performClick()
        compose.onNodeWithText("نوفمبر ٢٠٢٦").assertIsDisplayed()
        screenshot("ar-4-calendar")
    }

    @Test
    @Config(qualifiers = "tr-w400dp-h860dp-xxhdpi")
    fun turkish() {
        launch(SampleData.english())
        compose.onNodeWithText("Aylık").assertIsDisplayed()
        compose.onNodeWithText("$94,76").assertIsDisplayed()
        screenshot("tr-1-home")
        compose.onNodeWithText("Ayarlar").performClick()
        screenshot("tr-6-settings")
    }

    @Test
    @Config(qualifiers = "de-w400dp-h860dp-xxhdpi")
    fun german() {
        launch(SampleData.english())
        compose.onNodeWithText("Pro Monat").assertIsDisplayed()
        compose.onNodeWithText("94,76\u00A0$").assertIsDisplayed()
        screenshot("de-1-home")
        compose.onNodeWithText("Abos").performClick()
        compose.onAllNodesWithText("Netflix")[0].performClick()
        screenshot("de-3-detail")
    }

    @Test
    @Config(qualifiers = "w400dp-h860dp-night-xxhdpi")
    fun darkTheme() {
        launch(SampleData.english())
        screenshot("en-dark-home")
        compose.onNodeWithText("Insights").performClick()
        screenshot("en-dark-insights")
    }

    @Suppress("unused")
    private fun scrollTo(text: String) = compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
}
