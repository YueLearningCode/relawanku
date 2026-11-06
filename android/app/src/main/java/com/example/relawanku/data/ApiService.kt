package com.example.relawanku.data

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.*

@JvmSuppressWildcards
interface ApiService {
    @GET("health") fun health(): Call<Map<String, String>>

    @POST("auth/login") fun login(@Body data: Map<String, Any?>): Call<Envelope<LoginData>>

    @POST("auth/register") fun register(@Body data: Map<String, Any?>): Call<Envelope<ApiUser>>

    @GET("auth/me") fun me(): Call<Envelope<ApiUser>>

    @PATCH("auth/me") fun updateProfile(@Body data: Map<String, Any?>): Call<Envelope<ApiUser>>

    @POST("auth/logout") fun logout(): Call<Envelope<Unit>>

    @GET("dashboard") fun dashboard(): Call<Envelope<DashboardDto>>

    @GET("history") fun history(@Query("page") page: Int): Call<ApiPage<HistoryDto>>

    @GET("my/registrations") fun mine(@Query("page") page: Int): Call<ApiPage<RegistrationDto>>

    @GET("activities")
    fun activities(@QueryMap filters: Map<String, String>): Call<ApiPage<ActivityDto>>

    @GET("activities/{id}") fun activity(@Path("id") id: Long): Call<Envelope<ActivityDto>>

    @POST("activities")
    fun createActivity(@Body data: Map<String, Any?>): Call<Envelope<ActivityDto>>

    @PATCH("activities/{id}")
    fun updateActivity(
        @Path("id") id: Long,
        @Body data: Map<String, Any?>,
    ): Call<Envelope<ActivityDto>>

    @DELETE("activities/{id}") fun deleteActivity(@Path("id") id: Long): Call<Envelope<Unit>>

    @HTTP(method = "DELETE", path = "activities/bulk", hasBody = true)
    fun deleteActivities(@Body data: Map<String, Any?>): Call<Envelope<Unit>>

    @Multipart
    @POST("activities/{id}/cover")
    fun cover(@Path("id") id: Long, @Part image: MultipartBody.Part): Call<Envelope<ActivityDto>>

    @POST("activities/{id}/registrations")
    fun join(@Path("id") id: Long): Call<Envelope<RegistrationDto>>

    @DELETE("activities/{id}/registration") fun leave(@Path("id") id: Long): Call<Envelope<Unit>>

    @GET("activities/{id}/registrations")
    fun participants(@Path("id") id: Long, @Query("page") page: Int): Call<ApiPage<RegistrationDto>>

    @DELETE("activities/{id}/registrations/{registration}")
    fun cancelParticipant(
        @Path("id") id: Long,
        @Path("registration") registration: Long,
    ): Call<Envelope<Unit>>

    @GET("activities/{id}/tasks")
    fun tasks(@Path("id") id: Long, @Query("page") page: Int): Call<ApiPage<TaskDto>>

    @POST("activities/{id}/tasks")
    fun createTask(@Path("id") id: Long, @Body data: Map<String, Any?>): Call<Envelope<TaskDto>>

    @PATCH("activities/{id}/tasks/{task}")
    fun updateTask(
        @Path("id") id: Long,
        @Path("task") task: Long,
        @Body data: Map<String, Any?>,
    ): Call<Envelope<TaskDto>>

    @PATCH("activities/{id}/tasks/{task}/status")
    fun taskStatus(
        @Path("id") id: Long,
        @Path("task") task: Long,
        @Body data: Map<String, Any?>,
    ): Call<Envelope<TaskDto>>

    @DELETE("activities/{id}/tasks/{task}")
    fun deleteTask(@Path("id") id: Long, @Path("task") task: Long): Call<Envelope<Unit>>

    @HTTP(method = "DELETE", path = "activities/{id}/tasks/bulk", hasBody = true)
    fun deleteTasks(@Path("id") id: Long, @Body data: Map<String, Any?>): Call<Envelope<Unit>>

    @POST("activities/{id}/attendance-sessions")
    fun openSession(
        @Path("id") id: Long,
        @Body data: Map<String, Any?>,
    ): Call<Envelope<SessionCode>>

    @DELETE("activities/{id}/attendance-sessions/{session}")
    fun closeSession(@Path("id") id: Long, @Path("session") session: Long): Call<Envelope<Unit>>

    @POST("activities/{id}/attendances")
    fun attend(@Path("id") id: Long, @Body data: Map<String, Any?>): Call<Envelope<AttendanceDto>>

    @GET("activities/{id}/attendances")
    fun attendances(@Path("id") id: Long, @Query("page") page: Int): Call<ApiPage<RegistrationDto>>

    @GET("activities/{id}/documentations")
    fun photos(@Path("id") id: Long, @Query("page") page: Int): Call<ApiPage<PhotoDto>>

    @Multipart
    @POST("activities/{id}/documentations")
    fun uploadPhoto(
        @Path("id") id: Long,
        @Part image: MultipartBody.Part,
        @Part("caption") caption: okhttp3.RequestBody?,
    ): Call<Envelope<PhotoDto>>

    @PATCH("activities/{id}/documentations/{photo}")
    fun caption(
        @Path("id") id: Long,
        @Path("photo") photo: Long,
        @Body data: Map<String, Any?>,
    ): Call<Envelope<PhotoDto>>

    @DELETE("activities/{id}/documentations/{photo}")
    fun deletePhoto(@Path("id") id: Long, @Path("photo") photo: Long): Call<Envelope<Unit>>

    @GET("users") fun users(@QueryMap filters: Map<String, String>): Call<ApiPage<ApiUser>>

    @POST("users") fun createUser(@Body data: Map<String, Any?>): Call<Envelope<ApiUser>>

    @PATCH("users/{id}")
    fun updateUser(@Path("id") id: Long, @Body data: Map<String, Any?>): Call<Envelope<ApiUser>>
}
