package com.aliasadi.tamdid.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.time.LocalDate

sealed class StorageException(message: String) : Exception(message) {
    class UnsupportedSchemaVersion(val version: Int) : StorageException("Backup schema $version is newer than this app")
    class UnreadableBackup(cause: Throwable? = null) : StorageException("Not a Tamdid backup") {
        init { cause?.let(::initCause) }
    }
}

object BackupCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        coerceInputValues = true // unknown enum values fall back to the property default
        explicitNulls = false // omit nulls, like iOS
        encodeDefaults = true
    }

    fun encode(data: AppData): String = json.encodeToString(AppData.serializer(), data)

    fun decode(text: String): AppData {
        val version = try {
            json.parseToJsonElement(text).jsonObject["schemaVersion"]!!.jsonPrimitive.int
        } catch (e: Exception) {
            throw StorageException.UnreadableBackup(e)
        }
        if (version > AppData.CURRENT_SCHEMA_VERSION) throw StorageException.UnsupportedSchemaVersion(version)
        return try {
            json.decodeFromString(AppData.serializer(), text)
        } catch (e: SerializationException) {
            throw StorageException.UnreadableBackup(e)
        } catch (e: IllegalArgumentException) {
            throw StorageException.UnreadableBackup(e)
        } catch (e: java.time.DateTimeException) {
            throw StorageException.UnreadableBackup(e)
        }
    }

    /** A spreadsheet-friendly export. Amounts are in major units with a dot decimal separator. */
    fun csv(subscriptions: List<Subscription>, today: LocalDate): String {
        val header = listOf(
            "name", "category", "price", "currency", "cycle_unit", "cycle_interval",
            "start_date", "trial_end", "status", "end_date", "next_renewal",
            "monthly_cost", "total_spent", "payment_method", "url", "notes",
        )
        val lines = mutableListOf(header.joinToString(","))
        for (s in subscriptions.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })) {
            val c = s.price.currency
            val row = listOf(
                s.name,
                s.category.key,
                MoneyFormatter.editableText(s.price.amountMinor, c),
                c.code,
                s.cycle.unit.name.lowercase(),
                s.cycle.interval.toString(),
                s.startDate.toString(),
                s.trialEndDate?.toString().orEmpty(),
                s.status.name.lowercase(),
                s.endDate?.toString().orEmpty(),
                s.nextRenewal(today)?.toString().orEmpty(),
                MoneyFormatter.editableText(s.monthlyCostMinor, c),
                MoneyFormatter.editableText(s.totalSpent(today), c),
                s.paymentMethod.orEmpty(),
                s.url.orEmpty(),
                s.notes.orEmpty(),
            )
            lines += row.joinToString(",") { escape(it) }
        }
        return lines.joinToString("\r\n") + "\r\n"
    }

    /** RFC 4180 quoting. Also neutralises leading `= + - @` so spreadsheets don't run formulas. */
    internal fun escape(field: String): String {
        var value = field
        if (value.isNotEmpty() && value[0] in "=+-@") value = "'$value"
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}

/**
 * Saves [AppData] as a single JSON file with atomic writes (write to a temp file, then rename).
 * Same design and format as the iOS `FileStore`.
 */
class FileStore(val file: File) {
    /** Loads saved data, or null on first launch. */
    fun load(): AppData? = if (file.exists()) BackupCodec.decode(file.readText()) else null

    fun save(data: AppData) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(BackupCodec.encode(data))
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) throw IOException("Could not save ${file.path}")
        }
    }
}

@Serializable
data class CatalogService(
    val id: String,
    val name: Names,
    val category: SubscriptionCategory = SubscriptionCategory.Other,
    val color: String,
) {
    @Serializable
    data class Names(val en: String, val fa: String)

    fun displayName(language: AppLanguage): String = if (language == AppLanguage.Persian) name.fa else name.en
}

/** Preset services the user can pick when adding a subscription. */
class ServiceCatalog(val services: List<CatalogService>) {
    /**
     * Matches either language, ignoring case, diacritics, zero-width joiners, and
     * the Arabic/Persian variants of ی and ک that keyboards mix up.
     */
    fun search(query: String): List<CatalogService> {
        val needle = normalize(query)
        if (needle.isEmpty()) return services
        return services.filter { normalize(it.name.en).contains(needle) || normalize(it.name.fa).contains(needle) }
    }

    fun service(id: String): CatalogService? = services.firstOrNull { it.id == id }

    companion object {
        @Serializable
        private data class CatalogFile(val services: List<CatalogService>)

        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun fromJson(text: String): ServiceCatalog = ServiceCatalog(json.decodeFromString(CatalogFile.serializer(), text).services)

        internal fun normalize(text: String): String {
            val mapped = buildString {
                for (c in text.lowercase()) {
                    when (c) {
                        'ي', 'ى' -> append('ی') // ي ى → ی
                        'ك' -> append('ک') // ك → ک
                        '‌', '‍', ' ', '-', '+', '.' -> Unit
                        else -> append(c)
                    }
                }
            }
            return Normalizer.normalize(mapped, Normalizer.Form.NFD).filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        }
    }
}
