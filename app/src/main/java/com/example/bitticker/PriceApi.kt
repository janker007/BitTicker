// PriceApi.kt
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface PriceApi {
    @GET("simple/price")
    suspend fun getBitcoinPrice(
        @Query("ids") ids: String = "bitcoin",
        @Query("vs_currencies") vsCurrencies: String
    ): BitcoinPriceResponse
}

data class BitcoinPriceResponse(
    val bitcoin: Map<String, Double>
)

// RetrofitClient.kt
object RetrofitClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.coingecko.com/api/v3/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val priceApi: PriceApi = retrofit.create(PriceApi::class.java)
}