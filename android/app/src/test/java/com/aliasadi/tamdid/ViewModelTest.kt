package com.aliasadi.tamdid

import com.aliasadi.tamdid.core.AppData
import com.aliasadi.tamdid.core.CurrencyCode
import com.aliasadi.tamdid.core.CycleUnit
import com.aliasadi.tamdid.core.SubscriptionCategory
import com.aliasadi.tamdid.core.SubscriptionStatus
import com.aliasadi.tamdid.data.InMemoryTamdidRepository
import com.aliasadi.tamdid.ui.screens.CalendarViewModel
import com.aliasadi.tamdid.ui.screens.DetailViewModel
import com.aliasadi.tamdid.ui.screens.EditViewModel
import com.aliasadi.tamdid.ui.screens.FormError
import com.aliasadi.tamdid.ui.screens.HomeViewModel
import com.aliasadi.tamdid.ui.screens.ImportState
import com.aliasadi.tamdid.ui.screens.ListFilter
import com.aliasadi.tamdid.ui.screens.ListSort
import com.aliasadi.tamdid.ui.screens.SettingsViewModel
import com.aliasadi.tamdid.ui.screens.SubscriptionsViewModel
import com.aliasadi.tamdid.core.AppLanguage
import com.aliasadi.tamdid.core.BackupCodec
import com.aliasadi.tamdid.core.ServiceCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val clock = SampleData.clock

    @Test
    fun homeSummarisesTotalsUpcomingAndTrials() = runTest {
        val vm = HomeViewModel(InMemoryTamdidRepository(SampleData.english()), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals(6, state.activeCount)
        // 15.49 + 12.99 + 2.99 + 20.00 + 390.00/12 + (9.99 EUR × 1.08)
        assertEquals(1549L + 1299 + 299 + 2000 + 3250 + 1079, state.monthly.primary.amountMinor)
        assertTrue(state.monthly.isComplete)
        assertEquals(listOf("Netflix", "Spotify"), state.upcoming.map { it.subscription.name })
        assertEquals(listOf("Claude Pro"), state.endingTrials.map { it.subscription.name })
        assertEquals(SubscriptionCategory.Health, state.categories.first().category)
    }

    @Test
    fun listFiltersSearchesAndSorts() = runTest {
        val repo = InMemoryTamdidRepository(SampleData.english())
        val vm = SubscriptionsViewModel(repo, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals("Netflix", vm.state.value.items.first().subscription.name) // renews on the 22nd
        vm.setSort(ListSort.MonthlyCost)
        assertEquals("City Gym", vm.state.value.items.first().subscription.name)
        vm.setSort(ListSort.Name)
        assertEquals("City Gym", vm.state.value.items.first().subscription.name)
        vm.setQuery("spot")
        assertEquals(listOf("Spotify"), vm.state.value.items.map { it.subscription.name })
        vm.setQuery("")
        vm.setFilter(ListFilter.Paused)
        assertTrue(vm.state.value.items.isEmpty())
        assertTrue(vm.state.value.items.isEmpty() && vm.state.value.hasAny)
    }

    @Test
    fun detailPauseResumeCancelDelete() = runTest {
        val repo = InMemoryTamdidRepository(SampleData.english())
        val vm = DetailViewModel("netflix", repo, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(LocalDate.of(2026, 11, 22), vm.state.value.nextRenewal)
        assertEquals(19, vm.state.value.paymentCount) // Apr 2025 … Oct 2026; Nov 22 hasn't come yet
        vm.pause()
        assertEquals(SubscriptionStatus.Paused, vm.state.value.subscription?.status)
        assertNull(vm.state.value.nextRenewal)
        vm.resume()
        assertEquals(SubscriptionStatus.Active, vm.state.value.subscription?.status)
        assertEquals(clock.today(), vm.state.value.nextRenewal)
        vm.cancel()
        assertEquals(SubscriptionStatus.Cancelled, vm.state.value.subscription?.status)
        vm.delete()
        assertNull(vm.state.value.subscription)
        assertFalse(repo.data.value!!.subscriptions.any { it.id == "netflix" })
    }

    @Test
    fun editValidatesAndCreates() = runTest {
        val repo = InMemoryTamdidRepository(SampleData.english())
        val catalog = ServiceCatalog.fromJson(File(System.getProperty("user.dir"), "../../shared/catalog.json").readText())
        val vm = EditViewModel(null, "spotify", repo, clock, catalog, AppLanguage.English)
        assertEquals("Spotify", vm.name)
        assertEquals(SubscriptionCategory.Music, vm.category)
        vm.name = " "
        vm.priceText = "abc"
        var saved: String? = null
        vm.save { saved = it }
        assertEquals(setOf(FormError.Name, FormError.Price), vm.errors)
        assertNull(saved)

        vm.name = "Spotify Family"
        vm.priceText = "16.99"
        vm.cycleUnit = CycleUnit.Year
        vm.hasTrial = true
        vm.trialEnd = clock.today().minusDays(1)
        assertEquals(setOf(FormError.Trial), vm.errors)
        vm.hasTrial = false
        vm.save { saved = it }
        val created = repo.data.value!!.subscriptions.single { it.id == saved }
        assertEquals("Spotify Family", created.name)
        assertEquals(1699, created.price.amountMinor)
        assertEquals(CycleUnit.Year, created.cycle.unit)
        assertEquals(1, created.reminderDaysBefore) // default from settings
    }

    @Test
    fun editingThePriceKeepsHistory() = runTest {
        val repo = InMemoryTamdidRepository(SampleData.english())
        val vm = EditViewModel("netflix", null, repo, clock, null, AppLanguage.English)
        assertEquals("15.49", vm.priceText)
        vm.priceText = "17.99"
        assertTrue(vm.priceWillChange)
        vm.save {}
        val netflix = repo.data.value!!.subscriptions.single { it.id == "netflix" }
        assertEquals(1799, netflix.price.amountMinor)
        assertEquals(1549, netflix.priceOn(LocalDate.of(2026, 10, 22)))
        assertEquals(clock.today(), netflix.currentPriceSince)

        // Changing currency is treated as a correction, not a price change.
        val vm2 = EditViewModel("netflix", null, repo, clock, null, AppLanguage.English)
        vm2.currency = CurrencyCode.Euro
        vm2.save {}
        assertTrue(repo.data.value!!.subscriptions.single { it.id == "netflix" }.priceHistory.isEmpty())
    }

    @Test
    fun calendarMonthNavigationAndSelection() = runTest {
        val vm = CalendarViewModel(InMemoryTamdidRepository(SampleData.persian()), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val aban = vm.state.value.month
        assertEquals(1405 to 8, aban.year to aban.month)
        vm.select(LocalDate.of(2026, 11, 22))
        assertEquals(listOf("طاقچه"), vm.state.value.selectedCharges.map { it.subscription.name })
        vm.showMonth(aban.plusMonths(1))
        assertEquals(9, vm.state.value.month.month)
        vm.goToToday()
        assertEquals(8, vm.state.value.month.month)
    }

    @Test
    fun settingsImportNeedsConfirmationAndRejectsBadFiles() = runTest {
        val repo = InMemoryTamdidRepository(AppData())
        val vm = SettingsViewModel(repo, clock)
        vm.prepareImport("not json")
        assertEquals(ImportState.Unreadable, vm.importState.value)
        vm.prepareImport("""{"schemaVersion": 7, "subscriptions": [], "settings": {}}""")
        assertEquals(ImportState.TooNew, vm.importState.value)

        vm.prepareImport(BackupCodec.encode(SampleData.persian()))
        assertTrue(vm.importState.value is ImportState.Confirm)
        assertTrue(repo.data.value!!.subscriptions.isEmpty()) // nothing replaced yet
        vm.confirmImport()
        assertEquals(ImportState.Done, vm.importState.value)
        assertEquals(5, repo.data.first()!!.subscriptions.size)
        assertTrue(repo.data.value!!.settings.hasCompletedOnboarding)

        vm.setExchangeRate(CurrencyCode.Euro, 120_000.0)
        assertEquals(120_000.0, repo.data.value!!.settings.exchangeRates[CurrencyCode.Euro])
        vm.setExchangeRate(CurrencyCode.Euro, null)
        assertNull(repo.data.value!!.settings.exchangeRates[CurrencyCode.Euro])
        assertTrue(vm.exportCsv().startsWith("name,category"))
    }
}
