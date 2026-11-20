# Handoff log

> Working notes for whoever (human or AI) continues this project. Delete this file once it has served its purpose; the permanent docs live in `docs/`.

## What this is

Tamdid (تمدید, "renewal") is a free, open-source subscription tracker built as a portfolio project for **Ali Asadi** (Senior iOS Engineer, GitHub `Ali-Asadi-Dev`) and as a free app for the Iranian Android store **Cafe Bazaar**. Two native apps (SwiftUI + Jetpack Compose) share rules through test fixtures, not code. Five languages: en, fa, ar, tr, de.

Owner's goals, in their words: a "free version of paid apps", professional enough for a job search, with Android on Bazaar. They want every step tested "like a QA", with screenshots.

## Status (end of first session, 2026-11-20)

| Area | State | Verified how |
|---|---|---|
| `TamdidKit` (Swift core + storage) | Done | `swift test` on Linux: 61 tests, all vectors |
| Android `:core` | Done | JUnit: 28 tests, same vectors |
| Android app (all v1 screens, reminders, 5 languages, dark mode) | Done | 20 JVM tests incl. Robolectric UI tests with screenshots; lint 0 warnings; debug + release APK build |
| iOS app (all v1 screens, reminders, 5 languages) | Written, **not yet compiled by a human** | No Mac in the first session. GitHub Actions `iOS` workflow builds and tests it on macOS; check its result first |
| CI | `shared.yml`, `android.yml`, `ios.yml` | See Actions tab |
| Docs | README, docs/PRODUCT, ARCHITECTURE, DESIGN | — |

## First things to do on the Mac

1. `git clone https://github.com/Ali-Asadi-Dev/tamdid && cd tamdid`
2. Check the latest **iOS** workflow run on GitHub. If it failed, the log artifact `ios-results/xcodebuild.log` has the compiler errors. The iOS UI code was written without a compiler, so expect a few small fixes.
3. `brew install xcodegen && cd ios && xcodegen generate && open Tamdid.xcodeproj`, then run on a simulator. Set your Team under Signing to run on a device.
4. Android: open `android/` in Android Studio, or `cd android && ./gradlew :app:installDebug`.
5. Consider committing the generated `ios/Tamdid.xcodeproj` (currently git-ignored) if you prefer opening the project without XcodeGen.

## Repository map

```
ios/Packages/TamdidKit/Sources/TamdidCore   CivilDate, Jalali, Money(+Formatter), Language, Subscription, Billing,
                                            AppData(+settings, converter), Insights(+ReminderPlanner), CalendarDisplay, Catalog
ios/Packages/TamdidKit/Sources/TamdidData   FileStore (atomic JSON), BackupCodec (JSON, CSV)
ios/Tamdid/App                              TamdidApp (entry, RootView, tabs, navigation), AppModel (@Observable store), SampleData
ios/Tamdid/Features/*                       Home, Subscriptions (list/detail/edit/add flow), Calendar, Insights, Settings, Onboarding
ios/Tamdid/Shared                           AppFormat (environment), Components, MonthGridView
ios/Tamdid/Services/ReminderScheduler.swift UNUserNotificationCenter scheduling of the shared plan
ios/project.yml                             XcodeGen spec (bundle id com.aliasadi.tamdid, iOS 17+)
android/core                                Kotlin mirror of TamdidCore + TamdidData
android/app/src/main/java/.../ui/screens    One file per screen + ViewModels.kt + EditViewModel.kt
android/app/src/main/java/.../reminders     AlarmManager scheduler, notification + reschedule receivers
android/app/src/test                        ViewModelTest, UiTest (Robolectric, screenshots to app/build/screenshots)
shared/test-vectors/business-rules.json     Fixtures BOTH test suites run. Add a case here first when changing a rule.
shared/catalog.json                         31 preset services, en/fa names, no logos
shared/i18n/translations.json               All UI text; run `python3 tools/i18n.py` after editing
tools/i18n.py                               Generates Android values-*/strings.xml, plurals.xml and iOS Localizable.xcstrings
```

## Conventions

- **Changing a business rule:** add or adjust a case in `shared/test-vectors/business-rules.json`, then change Swift and Kotlin until both suites pass.
- **Adding UI text:** use English in code (Swift literal / Android `values/strings.xml`), add the translations to `shared/i18n/translations.json`, run `python3 tools/i18n.py`. CI runs `--check`.
- Money is always minor units (`Int64`/`Long`). Dates in rules are date-only (`CivilDate`/`LocalDate`).
- Format money and dates only through `MoneyFormatter` and `DateText` (never platform formatters), so both apps print the same.
- Persian is the reference RTL language; Arabic uses Arabic-Indic digits.

## Decisions worth knowing

- Renewal dates are computed from the anchor (`trialEnd ?? start`), never by repeated addition.
- Cancelled/paused: `endDate` is the last day a charge can happen (inclusive). Resume carries spending into `carriedSpendMinor`/`carriedPaymentCount` and restarts the series on the resume day.
- Price history: `priceHistory` holds earlier prices with their start dates; `currentPriceSince` is when the current price started. Changing currency on edit is a correction (history cleared).
- Monthly totals include active trials (they'll charge unless cancelled). Currencies without a user-entered rate are reported separately, never guessed.
- Reminders: planner caps at 60 (iOS limit 64), fires at `reminderHour` (09:00). Android uses inexact alarms (no exact-alarm permission needed) plus a receiver for boot / time change / app update.
- Per-app language: Android uses `AppCompatDelegate.setApplicationLocales` (in-app picker); iOS uses the system Settings → Tamdid → Language (button opens it).
- Android package and iOS bundle id: `com.aliasadi.tamdid`. **Bazaar package names are permanent**, so decide before the first upload.

## Environment notes from the first session (cloud Linux container)

- Swift 6.3.1 toolchain installed from download.swift.org to `/opt/tools/swift`; Android SDK 36 to `/opt/android-sdk`. The cloud environment "Tamdid (iOS + Android builds)" allows `dl.google.com` and `download.swift.org`.
- Maven Central redirected large files to a blocked host; `~/.gradle/init.d/maven-mirror.gradle` (container-local, not in the repo) put Google's Maven Central mirror first and pointed Robolectric at it. Not needed on a normal machine.
- Building XcodeGen on Linux was not permitted, so the `.xcodeproj` is generated in CI / on the Mac.
- The session could not create GitHub repos or change repo settings; the owner created `Ali-Asadi-Dev/tamdid`.

## Known gaps / next steps

1. **Verify the iOS build** (see above) and look at the UI-test screenshots in the `ios-results` artifact.
2. iOS screenshots for the README (Android ones are in `docs/screenshots`).
3. Bazaar release: create a release keystore (keep it backed up!), add `android/keystore.properties` (git-ignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, then `./gradlew :app:assembleRelease`. Bazaar needs Persian store text and screenshots; `UiTest` already produces Persian ones.
4. v2 ideas from `docs/PRODUCT.md`: widgets, app lock, iCloud/Drive sync, bundled Vazirmatn font, Shortcuts.
5. Optional polish: Android app is ~2 MB release; the debug APK is large because of `material-icons-extended` (R8 strips it in release).
