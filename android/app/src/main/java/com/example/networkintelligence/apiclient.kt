package com.example.networkintelligence

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// -----------------------------------------------------------------------------
// AUTHENTICATION MODELS (USER ROLE)
// -----------------------------------------------------------------------------

data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val full_name: String,
    val username: String,
    val password: String
)

data class AuthResponse(
    val status: String,
    val access_token: String,
    val username: String,
    val full_name: String,
    val role: String
)

// -----------------------------------------------------------------------------
// TELEMETRY & ML MODELS
// -----------------------------------------------------------------------------

data class SpatialReading(
    val latitude: Double,
    val longitude: Double,
    val download_speed: Double,
    val latency: Double? = null,
    val signal_strength: Int? = null,
    val network_type: String? = "5G",
    val speed_category: String? = "GREEN",
    val timestamp: String? = null,
    val username: String? = "ayush"
)

data class AnalyticsResponse(
    val status: String,
    val count: Int,
    val readings: List<SpatialReading>
)

data class RecommendationResponse(
    val best_indoor_spot: String? = null,
    val recommended_spot: String? = null,
    val best_time_window: String? = null,
    val expected_download_speed: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class TelemetryResponse(
    val status: String? = null,
    val message: String? = null
)

// -----------------------------------------------------------------------------
// RETROFIT API INTERFACE
// -----------------------------------------------------------------------------

interface ApiService {

    @POST("api/login")
    fun login(@Body request: LoginRequest): Call<AuthResponse>

    @POST("api/register")
    fun register(@Body request: RegisterRequest): Call<AuthResponse>

    @POST("api/telemetry")
    fun postTelemetry(@Body reading: SpatialReading): Call<TelemetryResponse>

    @POST("api/telemetry/batch")
    fun postTelemetryBatch(@Body readings: List<SpatialReading>): Call<TelemetryResponse>

    @GET("api/analytics")
    fun getAnalytics(
        @Query("username") username: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radius") radius: Double? = 100.0,
        @Query("hours") hours: Int? = 24
    ): Call<AnalyticsResponse>

    @GET("api/recommendation")
    fun getRecommendation(
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radius") radius: Double? = 100.0
    ): Call<RecommendationResponse>
}

// -----------------------------------------------------------------------------
// RETROFIT SINGLETON CLIENT
// -----------------------------------------------------------------------------

object ApiClient {
    private const val BASE_URL = "http://10.0.2.2:8000/"

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}