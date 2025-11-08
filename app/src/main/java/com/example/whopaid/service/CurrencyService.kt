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
     */
    suspend fun convert(
        amount: Double,
        fromCurrency: String,
        toCurrency: String = "RON"
    ): Double = withContext(Dispatchers.IO) {
        val response = api.getRates(fromCurrency)
        val rate = response.rates[toCurrency]
            ?: throw Exception("Exchange rate not available for $toCurrency")
        amount * rate
    }

    /**
     * Returns all available currency codes from API (keys of rates map).
     * Default base is USD to get the full list.
     */
    suspend fun getAvailableCurrencies(): List<String> = withContext(Dispatchers.IO) {
        val response = api.getRates("USD")
        val codes = response.rates.keys.toMutableList()
        // Add USD manually since it might not appear as a key
        if (!codes.contains("USD")) codes.add("USD")
        codes.sorted()
    }
}
