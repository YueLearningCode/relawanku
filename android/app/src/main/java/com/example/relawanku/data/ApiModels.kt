package com.example.relawanku.data

import com.example.relawanku.R
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Dates {
    fun parse(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        val normalized =
            value.replace(Regex("\\.(\\d+)(?=Z|[+-]\\d\\d:\\d\\d$)")) {
                "." + it.groupValues[1].take(3).padEnd(3, '0')
            }
        for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")) {
            try {
                return SimpleDateFormat(pattern, Locale.US)
                    .apply { isLenient = false }
                    .parse(normalized)
            } catch (_: java.text.ParseException) {
            }
        }
        return null
    }

    fun iso(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(date)

    fun day(value: String?): String =
        parse(value)?.let {
            SimpleDateFormat("EEEE, d MMMM yyyy", Locale.forLanguageTag("id-ID")).format(it)
        } ?: "Jadwal belum tersedia"

    fun time(value: String?): String =
        parse(value)?.let { SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).format(it) }
            ?: "—"

    fun full(value: String?): String = "${day(value)} · ${time(value)}"
}

data class Envelope<T>(val data: T? = null, val message: String? = null)

data class ApiPage<T>(
    val data: List<T> = emptyList(),
    @SerializedName("current_page") val currentPage: Int = 1,
    @SerializedName("last_page") val lastPage: Int = 1,
    val total: Int = 0,
)

data class ApiUser(
    val id: Long = 0,
    val name: String = "",
    val email: String = "",
    val role: String = "relawan",
    @SerializedName("is_active") val active: Boolean = true,
    val phone: String? = null,
    val bio: String? = null,
)

data class LoginData(
    val user: ApiUser? = null,
    val token: String = "",
    @SerializedName("expires_at") val expiresAt: String? = null,
)

data class ActivityDto(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val location: String = "",
    val requirements: String? = null,
    val quota: Int = 0,
    val status: String = "draft",
    @SerializedName("coordinator_id") val coordinatorId: Long = 0,
    val coordinator: ApiUser? = null,
    @SerializedName("starts_at") val startsAt: String = "",
    @SerializedName("ends_at") val endsAt: String = "",
    @SerializedName("registration_deadline") val registrationDeadline: String = "",
    @SerializedName("active_registrations_count") val participants: Int = 0,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null,
    @SerializedName("my_registration") val myRegistration: RegistrationDto? = null,
) {
    val date
        get() = Dates.day(startsAt)

    val time
        get() = "${Dates.time(startsAt)} – ${Dates.time(endsAt)}"

    val completed
        get() = status == "completed"

    val image
        get() =
            when (category.lowercase()) {
                "pendidikan" -> R.drawable.activity_learning
                "lingkungan" -> R.drawable.activity_forest
                else -> R.drawable.activity_coast
            }

    fun ongoing(now: Date = Date()) =
        status in listOf("open", "closed") &&
            (Dates.parse(startsAt)?.time ?: Long.MAX_VALUE) <= now.time &&
            (Dates.parse(endsAt)?.time ?: 0) > now.time

    fun registrationOpen(now: Date = Date()) =
        status == "open" &&
            (Dates.parse(startsAt)?.time ?: 0) > now.time &&
            (Dates.parse(registrationDeadline)?.time ?: 0) >= now.time &&
            participants < quota
}

data class AttendanceDto(
    val id: Long = 0,
    @SerializedName("checked_in_at") val checkedInAt: String? = null,
)

data class RegistrationDto(
    val id: Long = 0,
    @SerializedName("activity_id") val activityId: Long = 0,
    @SerializedName("user_id") val userId: Long = 0,
    val status: String = "",
    val user: ApiUser? = null,
    val activity: ActivityDto? = null,
    val attendance: AttendanceDto? = null,
)

data class TaskDto(
    val id: Long = 0,
    val title: String = "",
    val description: String? = null,
    val status: String = "pending",
    @SerializedName("assigned_to") val assignedTo: Long? = null,
    val assignee: ApiUser? = null,
    @SerializedName("due_at") val dueAt: String? = null,
)

data class PhotoDto(
    val id: Long = 0,
    @SerializedName("image_url") val imageUrl: String? = null,
    val caption: String? = null,
)

data class SessionDto(
    val id: Long = 0,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("closed_at") val closedAt: String? = null,
)

data class SessionCode(val session: SessionDto? = null, val code: String = "")

data class DashboardDto(
    val registrations: Int = 0,
    val attendances: Int = 0,
    @SerializedName("pending_tasks") val pendingTasks: Int = 0,
    val activities: Int = 0,
    @SerializedName("active_activities") val activeActivities: Int = 0,
    val users: Int = 0,
)

data class HistoryDto(
    val id: Long = 0,
    val event: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("activity_id") val activityId: Long? = null,
)

fun statusLabel(value: String) =
    when (value) {
        "draft" -> "Draft"
        "open" -> "Pendaftaran dibuka"
        "closed" -> "Pendaftaran ditutup"
        "completed" -> "Selesai"
        "cancelled" -> "Dibatalkan"
        "pending" -> "Belum dikerjakan"
        "in_progress" -> "Sedang dikerjakan"
        "registered" -> "Terdaftar"
        else -> value
    }
