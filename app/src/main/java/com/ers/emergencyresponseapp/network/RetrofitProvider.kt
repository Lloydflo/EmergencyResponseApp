package com.ers.emergencyresponseapp.network

import android.util.Log
import com.ers.emergencyresponseapp.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitProvider {

    private const val BASE_URL = BuildConfig.BASE_URL

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

                // ✅ log url
                Log.d("RetrofitProvider", "Request: ${request.method} ${request.url}")
                Log.d("RetrofitProvider", "Headers: ${request.headers}")

                // ✅ log body safely (may crash if duplex/one-shot, so try/catch)
                try {
                    val body = request.body
                    if (body != null) {
                        val buffer = Buffer()
                        body.writeTo(buffer)
                        val bodyString = buffer.readUtf8()
                        Log.d("RetrofitProvider", "Body: $bodyString")
                    } else {
                        Log.d("RetrofitProvider", "Body: <empty>")
                    }
                } catch (t: Throwable) {
                    Log.d("RetrofitProvider", "Body: <unable to read> ${t.message}")
                }

                // ✅ MUST return the response
                chain.proceed(request)
            }
            .addInterceptor(httpLoggingInterceptor)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL) // must end with /
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
}