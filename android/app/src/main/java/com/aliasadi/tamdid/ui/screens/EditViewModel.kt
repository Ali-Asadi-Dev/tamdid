package com.aliasadi.tamdid.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliasadi.tamdid.core.AppLanguage
import com.aliasadi.tamdid.core.BillingCycle
import com.aliasadi.tamdid.core.CurrencyCode
import com.aliasadi.tamdid.core.CycleUnit
import com.aliasadi.tamdid.core.Money
import com.aliasadi.tamdid.core.MoneyFormatter
import com.aliasadi.tamdid.core.ServiceCatalog
import com.aliasadi.tamdid.core.Subscription
import com.aliasadi.tamdid.core.SubscriptionCategory
import com.aliasadi.tamdid.data.Clock
import com.aliasadi.tamdid.data.TamdidRepository
import com.aliasadi.tamdid.ui.theme.TamdidColors
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class FormError { Name, Price, Trial }

/**
 * Form state for creating or editing a subscription. Plain Compose state: the form is
 * owned by this screen and validated as the user types.
 */
class EditViewModel(
    private val id: String?,
    catalogId: String?,
    private val repository: TamdidRepository,
    private val clock: Clock,
    catalog: ServiceCatalog?,
    language: AppLanguage,
) : ViewModel() {
    private val existing: Subscription? = id?.let { key -> repository.data.value?.subscriptions?.firstOrNull { it.id == key } }
    val isNew: Boolean get() = existing == null

    var name by mutableStateOf("")
    var priceText by mutableStateOf("")
    var currency by mutableStateOf(CurrencyCode.UsDollar)
    var cycleUnit by mutableStateOf(CycleUnit.Month)
    var interval by mutableStateOf(1)
    var startDate by mutableStateOf(clock.today())
    var hasTrial by mutableStateOf(false)
    var trialEnd by mutableStateOf(clock.today().plusDays(7))
    var reminderDays by mutableStateOf<Int?>(1)
    var category by mutableStateOf(SubscriptionCategory.Other)
    var colorHex by mutableStateOf(TamdidColors.Palette.first())
    var paymentMethod by mutableStateOf("")
    var url by mutableStateOf("")
    var notes by mutableStateOf("")
    var catalogID by mutableStateOf<String?>(null)
        private set

    /** Errors are shown only after the first save attempt, then live. */
    var showErrors by mutableStateOf(false)
        private set

    init {
        val settings = repository.data.value?.settings
        if (existing != null) {
            name = existing.name
            priceText = MoneyFormatter.editableText(existing.price.amountMinor, existing.price.currency)
            currency = existing.price.currency
            cycleUnit = existing.cycle.unit
            interval = existing.cycle.interval
            startDate = existing.startDate
            hasTrial = existing.trialEndDate != null
            trialEnd = existing.trialEndDate ?: existing.startDate.plusDays(7)
            reminderDays = existing.reminderDaysBefore
            category = existing.category
            colorHex = existing.colorHex
            paymentMethod = existing.paymentMethod.orEmpty()
            url = existing.url.orEmpty()
            notes = existing.notes.orEmpty()
            catalogID = existing.catalogID
        } else {
            currency = settings?.primaryCurrency ?: CurrencyCode.UsDollar
            reminderDays = settings?.defaultReminderDays
            val service = if (catalogId != null) catalog?.service(catalogId) else null
            if (service != null) {
                catalogID = service.id
                category = service.category
                colorHex = service.color
                name = service.displayName(language)
            }
        }
    }

    val parsedPrice: Long? get() = MoneyFormatter.parse(priceText, currency)

    val cycle: BillingCycle get() = BillingCycle(cycleUnit, interval.coerceAtLeast(1))

    /** Live "≈ per month" preview while typing. */
    val monthlyPreview: Money?
        get() = parsedPrice?.let { Money(Subscription.roundHalfUp(it * cycle.periodsPerMonth), currency) }

    val priceWillChange: Boolean
        get() = existing != null && parsedPrice != null && parsedPrice != existing.price.amountMinor && currency == existing.price.currency

    val errors: Set<FormError>
        get() = buildSet {
            if (name.isBlank()) add(FormError.Name)
            if (parsedPrice == null) add(FormError.Price)
            if (hasTrial && trialEnd < startDate) add(FormError.Trial)
        }

    /** Validates and saves. Returns the subscription id, or null when the form has errors. */
    fun save(onSaved: (String) -> Unit) {
        showErrors = true
        if (errors.isNotEmpty()) return
        val amount = parsedPrice ?: return
        val today = clock.today()
        val base = existing ?: Subscription(name = name.trim(), price = Money(amount, currency), startDate = startDate)
        var updated = base.copy(
            name = name.trim(),
            catalogID = catalogID,
            category = category,
            colorHex = colorHex,
            cycle = cycle,
            startDate = startDate,
            trialEndDate = if (hasTrial) trialEnd else null,
            reminderDaysBefore = reminderDays,
            paymentMethod = paymentMethod.trim().ifEmpty { null },
            url = url.trim().ifEmpty { null },
            notes = notes.trim().ifEmpty { null },
        )
        updated = when {
            existing == null -> updated
            // A different currency is a correction, not a price change: history in the old currency would mislead.
            currency != existing.price.currency ->
                updated.copy(price = Money(amount, currency), priceHistory = emptyList(), currentPriceSince = null)
            else -> updated.changePrice(amount, maxOf(today, startDate))
        }
        viewModelScope.launch {
            repository.update { data ->
                val list = if (existing == null) data.subscriptions + updated
                else data.subscriptions.map { if (it.id == updated.id) updated else it }
                data.copy(subscriptions = list)
            }
            onSaved(updated.id)
        }
    }
}
