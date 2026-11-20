package com.aliasadi.tamdid

import android.app.Application
import android.content.Context
import com.aliasadi.tamdid.core.AppData
import com.aliasadi.tamdid.core.AppLanguage
import com.aliasadi.tamdid.core.AppSettings
import com.aliasadi.tamdid.core.FileStore
import com.aliasadi.tamdid.core.ServiceCatalog
import com.aliasadi.tamdid.data.Clock
import com.aliasadi.tamdid.data.FileTamdidRepository
import com.aliasadi.tamdid.data.TamdidRepository
import com.aliasadi.tamdid.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.Locale

class TamdidApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency injection: the object graph is small enough to wire by hand. */
class AppContainer(context: Context) {
    val clock: Clock = Clock.System
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val reminderScheduler = ReminderScheduler(context.applicationContext, clock)

    val repository: TamdidRepository = FileTamdidRepository(
        store = FileStore(File(File(context.filesDir, "tamdid"), "data.json")),
        scope = applicationScope,
        ioDispatcher = Dispatchers.IO,
        initialData = {
            val language = AppLanguage.forLocale(Locale.getDefault().language)
            AppData(settings = AppSettings.defaults(language))
        },
        onChange = reminderScheduler::reschedule,
    )

    val catalog: ServiceCatalog by lazy {
        ServiceCatalog.fromJson(context.assets.open("catalog.json").bufferedReader().use { it.readText() })
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as TamdidApplication).container
