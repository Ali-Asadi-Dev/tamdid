package com.aliasadi.tamdid.data

import com.aliasadi.tamdid.core.AppData
import com.aliasadi.tamdid.core.FileStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** The single source of truth for everything the app stores. */
interface TamdidRepository {
    /** null until the first load finishes. */
    val data: StateFlow<AppData?>

    /** Applies [transform] atomically and persists the result. */
    suspend fun update(transform: (AppData) -> AppData)

    /** Replaces everything, e.g. when restoring a backup. */
    suspend fun replace(data: AppData) = update { data }
}

/** Persists to a JSON file through the shared [FileStore]; all disk I/O on [ioDispatcher]. */
class FileTamdidRepository(
    private val store: FileStore,
    scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val initialData: () -> AppData,
    private val onChange: (AppData) -> Unit = {},
) : TamdidRepository {
    private val state = MutableStateFlow<AppData?>(null)
    private val mutex = Mutex()
    override val data: StateFlow<AppData?> = state.asStateFlow()

    init {
        scope.launch {
            val loaded = withContext(ioDispatcher) {
                // A corrupt file should never brick the app: start fresh and keep the old file aside.
                runCatching { store.load() }.getOrElse {
                    store.file.renameTo(java.io.File(store.file.parentFile, "data-unreadable-${System.currentTimeMillis()}.json"))
                    null
                }
            }
            mutex.withLock { if (state.value == null) state.value = loaded ?: initialData() }
            state.value?.let(onChange)
        }
    }

    override suspend fun update(transform: (AppData) -> AppData) {
        val next = mutex.withLock {
            val current = state.value ?: return
            val next = transform(current)
            if (next == current) return
            withContext(ioDispatcher) { store.save(next) }
            state.value = next
            next
        }
        onChange(next)
    }
}

/** Simple in-memory repository for previews and tests. */
class InMemoryTamdidRepository(initial: AppData = AppData()) : TamdidRepository {
    private val state = MutableStateFlow<AppData?>(initial)
    override val data: StateFlow<AppData?> = state.asStateFlow()
    override suspend fun update(transform: (AppData) -> AppData) {
        state.value = transform(state.value ?: AppData())
    }
}

/** "Today" is injected so screens and tests agree on the date. */
fun interface Clock {
    fun today(): LocalDate

    companion object {
        val System = Clock { LocalDate.now(ZoneId.systemDefault()) }
    }
}
