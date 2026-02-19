package com.ers.emergencyresponseapp.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// -- Set your backend base URL here (use http, not https, for local LAN testing) --
private const val BASE_URL = "http://192.168.1.7:3000/"

object Network {
    // Create the Retrofit ApiService using the BASE_URL constant
    fun create(): ApiService {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
