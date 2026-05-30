package com.volunteer.manager.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

data class WeatherResponse(
    val main: Main,
    val weather: List<Weather>
)

data class Main(val temp: Double)
data class Weather(val description: String)

interface WeatherService {
    @GET("weather")
    fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): Call<WeatherResponse>
}
