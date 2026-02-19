package com.ers.emergencyresponseapp.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// Must not include /api; endpoints already include api/... path.
private const val BASE_URL = "https://hytographicallly-nondistorted-aurelia.ngrok-free.dev/"

object Network {
    // Create the Retrofit ApiService using the BASE_URL constant
    fun create(): ApiService {
        val bodyLogger = HttpLoggingInterceptor { message ->
            Log.d("NetworkBody", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                Log.d("Network", "Request URL: ${request.method} ${request.url}")
                chain.proceed(request)
            }
            .addInterceptor(bodyLogger)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
