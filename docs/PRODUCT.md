# Tamdid — Product Spec

**Tamdid** (Persian *تمدید*, "renewal") is a free, open-source subscription tracker for iOS and Android.
It answers three questions: *What am I paying for? How much does it cost me per month? What renews next?*

## Principles

- **Free, no ads, no account.** Data lives on the device. Nothing leaves it unless the user exports a backup.
- **Five languages.** English, Persian, Arabic, Turkish and German, with right-to-left layouts, native digits, the toman, and the Solar Hijri (Jalali) calendar.
- **Same product on both platforms.** One spec, one design system, one set of business rules verified by shared test vectors.

## Screens (v1)

| # | Screen | Contents |
|---|---|---|
| 1 | **Onboarding** | Three short pages: track, get reminded, see the total. |
| 2 | **Setup** | Primary currency, calendar (Jalali / Gregorian), notification permission. |
| 3 | **Dashboard** | Monthly and yearly total in the primary currency, renewals in the next 7 days, ending trials, spend by category. |
| 4 | **Subscriptions** | Searchable list; filter by status/category; sort by next renewal, price, or name. |
| 5 | **Subscription detail** | Price and cycle, next renewal, total spent so far, payment count, price history, notes, manage/cancel link; pause, cancel, delete. |
| 6 | **Add / Edit** | Name, price, currency, cycle (every N days/weeks/months/years), first payment date, free trial end, reminder, category, color, payment method, URL, notes. |
| 7 | **Service catalog** | Searchable list of common services (global and Iranian) to prefill name, category, and color. |
| 8 | **Calendar** | Month grid in the chosen calendar with renewal markers; tap a day to see its charges. |
| 9 | **Insights** | Spend by category, last 12 months of actual charges, most expensive subscriptions, average per day, overlapping services. |
| 10 | **Settings** | Primary currency and manual exchange rates, calendar, default reminder, theme, export/import (JSON, CSV), about. |

## Behaviour rules

- **Renewal dates** are computed from the anchor date (trial end, otherwise first payment), never by repeated addition, so a subscription started on Jan 31 renews on Feb 28 and then on Mar 31.
- **Free trials:** the first charge happens on the trial end date. The user is reminded before it.
- **Paused** subscriptions have no upcoming renewals and are excluded from totals. **Cancelled** ones stop renewing after the cancellation date but keep their history.
- **Price changes** are recorded with an effective date, so "total spent" uses the price that applied at each charge.
- **Totals across currencies** use the user's manual exchange rates. Amounts with no rate are shown separately rather than guessed.
- **Reminders** fire at 09:00 local time, N days before a renewal (default 1). At most 60 are scheduled ahead; the plan is rebuilt whenever data changes.

## Out of scope for v1 (planned for v2)

Home-screen widgets, app lock (Face ID / fingerprint), iCloud / Drive sync, Apple Watch, bundled Persian typeface (Vazirmatn), Siri / App Shortcuts.
