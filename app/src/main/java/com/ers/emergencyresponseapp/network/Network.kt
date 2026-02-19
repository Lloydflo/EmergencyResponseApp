package com.ers.emergencyresponseapp.network

object Network {
    fun create(): ApiService = RetrofitProvider.apiService
}
