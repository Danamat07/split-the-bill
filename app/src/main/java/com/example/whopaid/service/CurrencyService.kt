package com.example.whopaid.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface for ExchangeRate-API.
 */
interface CurrencyApi {
    @GET("v4/latest/{base}")
    suspend fun getRates(@Path("base") base: String): CurrencyRatesResponse
}

/**
 * Response model for ExchangeRate-API.
 */
data class CurrencyRatesResponse(
    val base: String,
    val date: String,
    val time_last_updated: Long,
    val rates: Map<String, Double>
)

object CurrencyService {
    private const val BASE_URL = "https://api.exchangerate-api.com/"

    private val api: CurrencyApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CurrencyApi::class.java)
    }

    /**
     * Converts an amount from one currency to another.
     *
     * Strategy:
     * 1) Try direct conversion: request rates with base = fromCurrency and look up toCurrency.
     * 2) If direct not available, request rates with base = "USD" and compute cross-rate:
     *      amount * (rate_to / rate_from)
     * This is robust when the API provides a large set of rates for a stable pivot currency.
     */
    suspend fun convert(
        amount: Double,
        fromCurrency: String,
        toCurrency: String = "RON"
    ): Double = withContext(Dispatchers.IO) {
        // Quick-return when currencies equal
        if (fromCurrency == toCurrency) return@withContext amount

        try {
            // 1) Try direct: rates with base = fromCurrency
            val directResp = api.getRates(fromCurrency)
            val directRate = directResp.rates[toCurrency]
            if (directRate != null) {
                return@withContext amount * directRate
            }
        } catch (e: Exception) {
            // ignore and try fallback; network error may occur here
        }

        // 2) Fallback: use pivot base USD to compute cross rate
        try {
            val pivot = "USD"
            val pivotResp = api.getRates(pivot)
            val rateFrom = pivotResp.rates[fromCurrency]
                ?: throw Exception("Exchange rate not available for $fromCurrency (pivot lookup)")
            val rateTo = pivotResp.rates[toCurrency]
                ?: throw Exception("Exchange rate not available for $toCurrency (pivot lookup)")

            // fromCurrency -> pivot -> toCurrency
            // amount (in fromCurrency) -> in pivot = amount * (rateFrom)
            // but pivotResp rates are defined as 1 pivot -> X currency? Depends on API.
            // For exchangerate-api.com v4/latest/{base} rates are: 1 base unit = rates[target] units.
            // pivotResp.rates[fromCurrency] = 1 USD = X fromCurrency
            // We want multiplier M such that: amount_from * M = amount_to
            // M = (rateTo / rateFrom)
            val cross = rateTo / rateFrom
            return@withContext amount * cross
        } catch (e: Exception) {
            throw Exception("Conversion failed: ${e.message}", e)
        }
    }

    /**
     * Returns all available currency codes from API (keys of rates map).
     * Default base is USD to get the full list.
     */
    suspend fun getAvailableCurrencies(): List<String> = withContext(Dispatchers.IO) {
        // Use pivot USD for comprehensive list
        val response = api.getRates("USD")
        val codes = response.rates.keys.toMutableList()
        if (!codes.contains("USD")) codes.add("USD")
        return@withContext codes.sorted()
    }
}
