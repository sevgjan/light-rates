package io.github.sevgjan.rates

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thelightphone.sdk.LightJob
import com.thelightphone.sdk.LightJobHandler
import com.thelightphone.sdk.LightJobResult
import com.thelightphone.sdk.SealedLightContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

const val REFRESH_JOB = "refresh-rates"
val DEFAULT_FAVORITES = listOf("USD", "GBP", "CHF", "JPY")

class RatesStore(private val context: SealedLightContext) {
    private val dataStore: DataStore<Preferences> get() = context.dataStore

    private val seed: RatesSnapshot by lazy {
        json.decodeFromString(context.readAsset("rates_seed.json").decodeToString())
    }

    val currencyNames: Map<String, String> by lazy {
        json.decodeFromString(context.readAsset("currencies.json").decodeToString())
    }

    val snapshot: Flow<RatesSnapshot> = dataStore.data.map { prefs ->
        prefs[SNAPSHOT]?.let { runCatching { json.decodeFromString<RatesSnapshot>(it) }.getOrNull() } ?: seed
    }
    val base: Flow<String> = dataStore.data.map { it[BASE] ?: "EUR" }
    val favorites: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[FAVORITES]?.split(',')?.filter { it.isNotBlank() } ?: DEFAULT_FAVORITES
    }

    suspend fun save(snapshot: RatesSnapshot) {
        dataStore.edit { it[SNAPSHOT] = json.encodeToString(RatesSnapshot.serializer(), snapshot) }
    }

    suspend fun setBase(code: String) {
        dataStore.edit { it[BASE] = code }
    }

    suspend fun setFavorites(codes: List<String>) {
        dataStore.edit { it[FAVORITES] = codes.joinToString(",") }
    }

    private companion object {
        val SNAPSHOT = stringPreferencesKey("snapshot")
        val BASE = stringPreferencesKey("base")
        val FAVORITES = stringPreferencesKey("favorites")
    }
}

object RatesApi {
    private const val LATEST = "https://api.frankfurter.dev/v1/latest?base=EUR"

    suspend fun fetchLatest(): RatesSnapshot = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }.use { it.get(LATEST).body() }
}

@LightJob(REFRESH_JOB)
val refreshRates: LightJobHandler = { context, _ ->
    try {
        RatesStore(context).save(RatesApi.fetchLatest())
        LightJobResult.Success(outputFilePath = null, message = null)
    } catch (e: Exception) {
        LightJobResult.Retry
    }
}
