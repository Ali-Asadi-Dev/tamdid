package com.aliasadi.tamdid.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliasadi.tamdid.core.AppData
import com.aliasadi.tamdid.core.AppSettings
import com.aliasadi.tamdid.core.BackupCodec
import com.aliasadi.tamdid.core.CalendarMonth
import com.aliasadi.tamdid.core.CalendarSystem
import com.aliasadi.tamdid.core.CategoryOverlap
import com.aliasadi.tamdid.core.CategorySpend
import com.aliasadi.tamdid.core.CurrencyCode
import com.aliasadi.tamdid.core.CurrencyConverter
import com.aliasadi.tamdid.core.DayCharge
import com.aliasadi.tamdid.core.Insights
import com.aliasadi.tamdid.core.Money
import com.aliasadi.tamdid.core.MoneyTotal
import com.aliasadi.tamdid.core.MonthSpend
import com.aliasadi.tamdid.core.RankedSubscription
import com.aliasadi.tamdid.core.StorageException
import com.aliasadi.tamdid.core.Subscription
import com.aliasadi.tamdid.core.SubscriptionStatus
import com.aliasadi.tamdid.core.UpcomingCharge
import com.aliasadi.tamdid.data.Clock
import com.aliasadi.tamdid.data.TamdidRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Shared by screens: map repository data to UI state that lives as long as the screen. */
private fun <T> ViewModel.stateOf(repository: TamdidRepository, initial: T, transform: (AppData) -> T): StateFlow<T> =
    repository.data
        .map { data -> data?.let(transform) ?: initial }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

// region App

sealed interface AppUiState {
    data object Loading : AppUiState
    data class Onboarding(val settings: AppSettings) : AppUiState
    data class Ready(val settings: AppSettings) : AppUiState
}

class AppViewModel(private val repository: TamdidRepository) : ViewModel() {
    val state: StateFlow<AppUiState> = repository.data
        .map { data ->
            when {
                data == null -> AppUiState.Loading
                !data.settings.hasCompletedOnboarding -> AppUiState.Onboarding(data.settings)
                else -> AppUiState.Ready(data.settings)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState.Loading)

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update { it.copy(settings = transform(it.settings)) } }
    }

    fun completeOnboarding(currency: CurrencyCode, calendar: CalendarSystem) = updateSettings {
        it.copy(primaryCurrency = currency, calendar = calendar, hasCompletedOnboarding = true)
    }
}

// endregion

// region Home

data class HomeUiState(
    val loading: Boolean = true,
    val subscriptionCount: Int = 0,
    val activeCount: Int = 0,
    val monthly: MoneyTotal = MoneyTotal.zero(CurrencyCode.UsDollar),
    val yearly: Money = Money.zero(CurrencyCode.UsDollar),
    val daily: Money = Money.zero(CurrencyCode.UsDollar),
    val upcoming: List<UpcomingCharge> = emptyList(),
    val endingTrials: List<UpcomingCharge> = emptyList(),
    val categories: List<CategorySpend> = emptyList(),
)

class HomeViewModel(repository: TamdidRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<HomeUiState> = stateOf(repository, HomeUiState()) { data ->
        val insights = Insights(data.subscriptions, data.settings, clock.today())
        val currency = data.settings.primaryCurrency
        HomeUiState(
            loading = false,
            subscriptionCount = data.subscriptions.size,
            activeCount = insights.active.size,
            monthly = insights.monthlyTotal,
            yearly = insights.yearlyTotal.primary,
            daily = Money(insights.dailyAverageMinor, currency),
            upcoming = insights.upcoming(7).filterNot { it.isTrialEnd },
            endingTrials = insights.endingTrials(14),
            categories = insights.byCategory,
        )
    }
}

// endregion

// region Subscriptions list

enum class ListFilter { All, Active, Paused, Cancelled }
enum class ListSort { NextRenewal, MonthlyCost, Name }

data class SubscriptionListItem(val subscription: Subscription, val nextRenewal: LocalDate?, val isTrial: Boolean)

data class SubscriptionsUiState(
    val loading: Boolean = true,
    val hasAny: Boolean = false,
    val query: String = "",
    val filter: ListFilter = ListFilter.All,
    val sort: ListSort = ListSort.NextRenewal,
    val items: List<SubscriptionListItem> = emptyList(),
)

class SubscriptionsViewModel(repository: TamdidRepository, private val clock: Clock) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(ListFilter.All)
    private val sort = MutableStateFlow(ListSort.NextRenewal)

    val state: StateFlow<SubscriptionsUiState> = combine(repository.data, query, filter, sort) { data, q, f, s ->
        if (data == null) return@combine SubscriptionsUiState()
        val today = clock.today()
        val items = data.subscriptions
            .filter { sub ->
                when (f) {
                    ListFilter.All -> true
                    ListFilter.Active -> sub.status == SubscriptionStatus.Active
                    ListFilter.Paused -> sub.status == SubscriptionStatus.Paused
                    ListFilter.Cancelled -> sub.status == SubscriptionStatus.Cancelled
                }
            }
            .filter { q.isBlank() || it.name.contains(q.trim(), ignoreCase = true) || (it.notes ?: "").contains(q.trim(), ignoreCase = true) }
            .map { SubscriptionListItem(it, it.nextRenewal(today), it.isInTrial(today)) }
            .let { list ->
                when (s) {
                    ListSort.NextRenewal -> list.sortedWith(compareBy(nullsLast()) { it.nextRenewal })
                    ListSort.MonthlyCost -> list.sortedByDescending { it.subscription.monthlyCostMinor }
                    ListSort.Name -> list.sortedBy { it.subscription.name.lowercase() }
                }
            }
        SubscriptionsUiState(false, data.subscriptions.isNotEmpty(), q, f, s, items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsUiState())

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: ListFilter) { filter.value = value }
    fun setSort(value: ListSort) { sort.value = value }
}

// endregion

// region Detail

data class DetailUiState(
    val loading: Boolean = true,
    val subscription: Subscription? = null,
    val nextRenewal: LocalDate? = null,
    val totalSpent: Money? = null,
    val paymentCount: Int = 0,
    val isInTrial: Boolean = false,
)

class DetailViewModel(private val id: String, private val repository: TamdidRepository, private val clock: Clock) : ViewModel() {
    val state: StateFlow<DetailUiState> = stateOf(repository, DetailUiState()) { data ->
        val today = clock.today()
        val sub = data.subscriptions.firstOrNull { it.id == id } ?: return@stateOf DetailUiState(loading = false)
        DetailUiState(
            loading = false,
            subscription = sub,
            nextRenewal = sub.nextRenewal(today),
            totalSpent = Money(sub.totalSpent(today), sub.price.currency),
            paymentCount = sub.paymentCount(today),
            isInTrial = sub.isInTrial(today),
        )
    }

    private fun modify(transform: (Subscription) -> Subscription) {
        viewModelScope.launch {
            repository.update { data -> data.copy(subscriptions = data.subscriptions.map { if (it.id == id) transform(it) else it }) }
        }
    }

    fun pause() = modify { it.pause(clock.today()) }
    fun resume() = modify { it.resume(clock.today()) }
    fun cancel() = modify { it.cancel(clock.today()) }
    fun delete() {
        viewModelScope.launch { repository.update { data -> data.copy(subscriptions = data.subscriptions.filterNot { it.id == id }) } }
    }
}

// endregion

// region Calendar

data class CalendarUiState(
    val loading: Boolean = true,
    val month: CalendarMonth,
    val selected: LocalDate,
    val charges: Map<LocalDate, List<DayCharge>> = emptyMap(),
    val monthTotal: MoneyTotal,
) {
    val selectedCharges: List<DayCharge> get() = charges[selected].orEmpty()
}

class CalendarViewModel(repository: TamdidRepository, private val clock: Clock) : ViewModel() {
    private val selection = MutableStateFlow<Pair<CalendarMonth?, LocalDate>>(null to clock.today())

    val state: StateFlow<CalendarUiState> = combine(repository.data, selection) { data, (chosenMonth, selected) ->
        val settings = data?.settings ?: AppSettings()
        val month = chosenMonth?.takeIf { it.system == settings.calendar } ?: CalendarMonth.containing(selected, settings.calendar)
        val insights = Insights(data?.subscriptions.orEmpty(), settings, clock.today())
        val charges = insights.charges(month.firstDay, month.lastDay)
        val converter = CurrencyConverter(settings)
        val total = charges.values.flatten().fold(MoneyTotal.zero(settings.primaryCurrency)) { acc, charge ->
            acc.plus(charge.amount.amountMinor, charge.amount.currency, converter)
        }
        CalendarUiState(data == null, month, selected, charges, total)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CalendarUiState(month = CalendarMonth.containing(clock.today(), CalendarSystem.Gregorian), selected = clock.today(), monthTotal = MoneyTotal.zero(CurrencyCode.UsDollar)),
    )

    fun showMonth(month: CalendarMonth) {
        selection.value = month to (selection.value.second.takeIf { CalendarMonth.containing(it, month.system) == month } ?: month.firstDay)
    }

    fun select(date: LocalDate) {
        selection.value = selection.value.first to date
    }

    fun goToToday() {
        selection.value = null to clock.today()
    }
}

// endregion

// region Insights

data class InsightsUiState(
    val loading: Boolean = true,
    val hasData: Boolean = false,
    val monthly: MoneyTotal = MoneyTotal.zero(CurrencyCode.UsDollar),
    val yearly: Money = Money.zero(CurrencyCode.UsDollar),
    val daily: Money = Money.zero(CurrencyCode.UsDollar),
    val months: List<MonthSpend> = emptyList(),
    val categories: List<CategorySpend> = emptyList(),
    val mostExpensive: List<RankedSubscription> = emptyList(),
    val overlaps: List<CategoryOverlap> = emptyList(),
    val calendar: CalendarSystem = CalendarSystem.Gregorian,
)

class InsightsViewModel(repository: TamdidRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<InsightsUiState> = stateOf(repository, InsightsUiState()) { data ->
        val insights = Insights(data.subscriptions, data.settings, clock.today())
        InsightsUiState(
            loading = false,
            hasData = insights.active.isNotEmpty(),
            monthly = insights.monthlyTotal,
            yearly = insights.yearlyTotal.primary,
            daily = Money(insights.dailyAverageMinor, data.settings.primaryCurrency),
            months = insights.spendByMonth(12, data.settings.calendar),
            categories = insights.byCategory,
            mostExpensive = insights.mostExpensive(5),
            overlaps = insights.overlaps,
            calendar = data.settings.calendar,
        )
    }
}

// endregion

// region Settings

sealed interface ImportState {
    data object Idle : ImportState
    data class Confirm(val data: AppData, val replacing: Int) : ImportState
    data object Done : ImportState
    data object Unreadable : ImportState
    data object TooNew : ImportState
}

class SettingsViewModel(private val repository: TamdidRepository, private val clock: Clock) : ViewModel() {
    val data: StateFlow<AppData?> = repository.data

    private val importStateFlow = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = importStateFlow.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update { it.copy(settings = transform(it.settings)) } }
    }

    fun setExchangeRate(currency: CurrencyCode, rate: Double?) = update { settings ->
        val rates = settings.exchangeRates.toMutableMap()
        if (rate == null || rate <= 0) rates.remove(currency) else rates[currency] = rate
        settings.withExchangeRates(rates)
    }

    fun exportJson(): String = BackupCodec.encode(repository.data.value ?: AppData())

    fun exportCsv(): String = BackupCodec.csv(repository.data.value?.subscriptions.orEmpty(), clock.today())

    /** Validates a backup and asks for confirmation before replacing anything. */
    fun prepareImport(text: String) {
        importStateFlow.value = try {
            val imported = BackupCodec.decode(text)
            ImportState.Confirm(imported, repository.data.value?.subscriptions?.size ?: 0)
        } catch (_: StorageException.UnsupportedSchemaVersion) {
            ImportState.TooNew
        } catch (_: StorageException) {
            ImportState.Unreadable
        }
    }

    fun confirmImport() {
        val pending = importStateFlow.value as? ImportState.Confirm ?: return
        viewModelScope.launch {
            // Keep onboarding finished even if the backup was made before it was.
            repository.replace(pending.data.copy(settings = pending.data.settings.copy(hasCompletedOnboarding = true)))
            importStateFlow.value = ImportState.Done
        }
    }

    fun dismissImport() {
        importStateFlow.value = ImportState.Idle
    }
}

// endregion
