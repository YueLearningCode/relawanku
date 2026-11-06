package com.example.relawanku.ui

import android.text.InputType
import android.util.Patterns
import com.example.relawanku.MainActivity
import com.example.relawanku.data.*

class AccountScreens(private val a: MainActivity) {
    private val u = a.ui

    fun profile() {
        val p = a.page("Profil", "Kelola informasi akunmu.")
        val area = u.column()
        p.addView(area)
        a.load(area, { a.api.me() }) { response ->
            response.data?.let { user ->
                a.state.user = user
                u.badge(area, Role.fromWire(user.role)?.label ?: user.role)
                u.gap(area)
                val name = u.field(area, "Nama lengkap", user.name)
                val email =
                    u.field(
                        area,
                        "Email",
                        user.email,
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    )
                val phone =
                    u.field(area, "Nomor telepon", user.phone.orEmpty(), InputType.TYPE_CLASS_PHONE)
                val bio =
                    u.field(
                        area,
                        "Tentang saya",
                        user.bio.orEmpty(),
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                    )
                u.button(area, "Simpan profil") {
                    val mail = email.second.text.toString().trim().lowercase(java.util.Locale.ROOT)
                    name.first.error =
                        if (name.second.text.toString().isBlank()) "Nama wajib diisi" else null
                    email.first.error =
                        if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) "Email tidak valid"
                        else null
                    if (name.first.error != null || email.first.error != null) return@button
                    a.write(
                        a.api.updateProfile(
                            mapOf(
                                "name" to name.second.text.toString().trim(),
                                "email" to mail,
                                "phone" to phone.second.text.toString().trim().ifEmpty { null },
                                "bio" to bio.second.text.toString().trim().ifEmpty { null },
                            )
                        ),
                        mapOf(
                            "name" to name.first,
                            "email" to email.first,
                            "phone" to phone.first,
                            "bio" to bio.first,
                        ),
                    ) { result ->
                        result.data?.let { a.state.user = it }
                        a.render()
                        a.notice("Profil diperbarui.")
                    }
                }
            }
        }
        u.button(p, "Riwayat aktivitas", true) { a.navigate("history") }
        u.button(p, "Atur koneksi server", true) { a.serverSettings() }
        u.button(p, "Keluar", true) {
            a.confirm(
                "Keluar dari akun?",
                "Token sesi perangkat ini akan dicabut di server.",
                "Keluar",
            ) {
                a.write(a.api.logout()) {
                    a.session.clear()
                    a.state.clear()
                    a.navigate("login")
                }
            }
        }
        u.button(p, "Hapus sesi lokal", true) {
            a.confirm(
                "Hapus sesi lokal?",
                "Gunakan saat server tidak terjangkau. Token server tetap berlaku sampai kedaluwarsa atau dicabut.",
                "Hapus sesi lokal",
            ) {
                a.session.clear()
                a.state.clear()
                a.navigate("login")
            }
        }
    }

    fun history() {
        val p = a.page("Riwayat aktivitas", "Catatan tindakan akunmu dari server.", true)
        val area = u.column()
        p.addView(area)
        val labels =
            mapOf(
                "activity.created" to "Membuat kegiatan",
                "activity.updated" to "Mengubah kegiatan",
                "activity.deleted" to "Mengarsipkan kegiatan",
                "registration.created" to "Mendaftar kegiatan",
                "registration.cancelled" to "Membatalkan pendaftaran",
                "attendance.checked_in" to "Melakukan absensi",
                "attendance_session.opened" to "Membuka sesi absensi",
                "attendance_session.closed" to "Menutup sesi absensi",
                "task.created" to "Membuat tugas",
                "task.updated" to "Mengubah tugas",
                "task.deleted" to "Mengarsipkan tugas",
                "task.status_updated" to "Memperbarui status tugas",
                "documentation.created" to "Mengunggah dokumentasi",
                "documentation.updated" to "Mengubah dokumentasi",
                "documentation.deleted" to "Mengarsipkan dokumentasi",
            )

        fun load(page: Int) {
            a.load(area, { a.api.history(page) }) { result ->
                if (result.data.isEmpty())
                    u.empty(area, "Belum ada riwayat", "Aktivitasmu akan tercatat di sini.")
                result.data.forEach { item ->
                    val panel = u.panel(area)
                    panel.addView(
                        u.text(labels[item.event] ?: item.event.replace('.', ' '), 17, 700)
                    )
                    panel.addView(u.text(Dates.full(item.createdAt), 13, 400, u.muted))
                    item.activityId?.let {
                        panel.addView(u.text("Kegiatan #$it", 12, 400, u.muted))
                    }
                }
                a.pagination(area, result) { load(it) }
            }
        }
        load(1)
    }
}
