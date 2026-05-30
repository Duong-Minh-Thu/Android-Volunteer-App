/**
 * Dự án: Volunteer Manager
 * File: WeatherService.kt
 * Chức năng: Định nghĩa giao thức kết nối API (Retrofit) để lấy dữ liệu Thời tiết từ OpenWeatherMap.
 * - WeatherResponse, Main, Weather: Các lớp DTO để phân tích dữ liệu JSON phản hồi về nhiệt độ và trạng thái thời tiết.
 * - WeatherService: Interface chứa các phương thức HTTP GET lấy thông tin thời tiết động dựa vào Kinh độ (Lon) và Vĩ độ (Lat) của chiến dịch.
 */
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
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "vi"
    ): Call<WeatherResponse>
}
