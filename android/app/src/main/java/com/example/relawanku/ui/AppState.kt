package com.example.relawanku.ui

import androidx.lifecycle.ViewModel
import com.example.relawanku.data.ActivityDto
import com.example.relawanku.data.ApiUser
import com.example.relawanku.data.ReadCache
import com.example.relawanku.data.SessionCode

enum class Role(val label: String, val wire: String) {
    RELAWAN("Relawan", "relawan"),
    KOORDINATOR("Koordinator", "koordinator"),
    ADMIN("Admin", "admin");

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value }
    }
}

class AppState : ViewModel() {
    val reads = ReadCache()
    var user: ApiUser? = null
    val role: Role
        get() = Role.fromWire(user?.role) ?: Role.RELAWAN

    val displayName
        get() = user?.name.orEmpty()

    val email
        get() = user?.email.orEmpty()

    var screen = "welcome"
    var tab = "home"
    var selectedEvent = 0L
    var selectedUser: ApiUser? = null
    var pendingPhotoEvent = 0L
    var uploadCover = false
    var detailTab = "Ringkasan"
    var query = ""
    var category = "Semua"
    val events = mutableMapOf<Long, ActivityDto>()
    val sessions = mutableMapOf<Long, SessionCode>()
    val draft = mutableMapOf<String, String>()

    fun canManage(event: ActivityDto) =
        role == Role.ADMIN || (role == Role.KOORDINATOR && event.coordinatorId == user?.id)

    fun clear() {
        reads.clear()
        user = null
        events.clear()
        sessions.clear()
        draft.clear()
        selectedEvent = 0
        selectedUser = null
        query = ""
        category = "Semua"
        tab = "home"
    }
}
