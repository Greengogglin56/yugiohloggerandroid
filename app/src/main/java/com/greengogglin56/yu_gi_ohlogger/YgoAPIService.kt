package com.greengogglin56.yu_gi_ohlogger

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface YgoApiService {
    @GET("api/v7/cardsets.php")
    suspend fun getCardSets(): List<YgoCardSetInfo>

    @GET("api/v7/cardinfo.php")
    suspend fun getCardBySetName(@Query("cardset") fullSetName: String): YgoApiResponse

    @GET("api/v7/cardinfo.php")
    suspend fun getCardById(@Query("id") id: String): YgoApiResponse

    @GET("api/v7/cardinfo.php")
    suspend fun getCardByFname(@Query("fname") fname: String): YgoApiResponse

    companion object {
        private const val BASE_URL = "https://db.ygoprodeck.com/"

        val api: YgoApiService by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(YgoApiService::class.java)
        }
    }
}