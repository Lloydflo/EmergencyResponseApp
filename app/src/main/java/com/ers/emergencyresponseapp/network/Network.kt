package com.ers.emergencyresponseapp.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Header
import retrofit2.http.Path

// Must not include /api; endpoints already include api/... path.
private const val BASE_URL = "https://emergency-response.alertaraqc.com/"

