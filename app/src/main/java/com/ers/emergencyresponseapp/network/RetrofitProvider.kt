package com.ers.emergencyresponseapp.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitProvider {
    // Must not include /api; endpoints already include api/... path.
    private const val BASE_URL = "https://hytographicallly-nondistorted-aurelia.ngrok-free.dev/"

    private val loggingClient: OkHttpClient by lazy {
        val bodyLogger = HttpLoggingInterceptor { message ->
            Log.d("RetrofitBody", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                Log.d("RetrofitProvider", "Request URL: ${request.method} ${request.url}")
                chain.proceed(request)
            }
            .addInterceptor(bodyLogger)
            .build()
    }
    private const val BASE_URL = "http://192.168.1.7:3000/"

    private val loggingClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                Log.d("RetrofitProvider", "Request URL: ${request.method} ${request.url}")
                chain.proceed(request)
            }
            .build()
    }

    private val httpLoggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor { message ->
            Log.d("RetrofitHttp", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                Log.d("RetrofitProvider", "Request URL: ${request.method} ${request.url}")
                chain.proceed(request)
            }
            .addInterceptor(httpLoggingInterceptor)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(loggingClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi by lazy {
        retrofit.create(AuthApi::class.java)
    }
}
