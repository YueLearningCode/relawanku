package com.example.relawanku.ui

import android.text.InputType
import android.util.Patterns
import com.example.relawanku.MainActivity
import com.example.relawanku.data.*

class ManagementScreens(private val a: MainActivity) {
    private val u = a.ui
    private val s = a.state

    fun dashboard() {
        val p = a.page()
        val admin = s.role == Role.ADMIN
        u.dashboardHeader(
            p,
            s.role.label,
            s.displayName,
            if (admin) "Ringkasan komunitas" else "Ringkasan kegiatan",
        ) {
            a.selectTab("profile")
        }
        val area = u.column()
        p.addView(area)
        a.load(area, { a.api.dashboard() }) { response ->
            response.data?.let { d ->
                val hero = u.panel(area, u.color(com.example.relawanku.R.color.primary_dark), 20)
                hero.addView(
                    u.text(
                            "OPERASIONAL KEGIATAN",
                            10,
                            700,
                            u.color(com.example.relawanku.R.color.sage),
                        )
                        .apply { letterSpacing = .12f }
                )
                u.gap(hero, 12)
                hero.addView(
                    u.text(
                        d.activeActivities.toString() + " kegiatan aktif",
                        26,
                        700,
                        u.color(com.example.relawanku.R.color.surface),
                    )
                )
                u.gap(hero, 6)
                hero.addView(
                    u.text(
                        if (admin) "Pantau pelaksanaan dan partisipasi seluruh komunitas."
                        else "Atur pelaksanaan dan dampingi relawanmu.",
                        13,
                        400,
                        u.color(com.example.relawanku.R.color.sage),
                    )
                )
                u.section(area, "Ringkasan")
                u.metrics(
                    area,
                    listOf(
                        "Total kegiatan" to d.activities,
                        "Peserta aktif" to d.registrations,
                        "Kehadiran" to d.attendances,
                        (if (admin) "Pengguna" else "Belum absensi") to
                            (if (admin) d.users
                            else (d.registrations - d.attendances).coerceAtLeast(0)),
                    ),
                )
            }
        }
        u.button(p, "Buat kegiatan baru") {
            s.selectedEvent = 0
            a.navigate("form")
        }
        u.section(p, "Ruang kerja")
        u.actionRow(
            p,
            "Kelola kegiatan",
            "Jadwal, peserta, tugas, dan absensi",
            com.example.relawanku.R.drawable.ic_calendar,
        ) {
            a.selectTab("explore")
        }
        if (admin)
            u.actionRow(
                p,
                "Kelola pengguna",
                "Akun dan hak akses komunitas",
                com.example.relawanku.R.drawable.ic_people,
            ) {
                a.selectTab("users")
            }
        u.actionRow(
            p,
            "Riwayat aktivitas",
            "Tinjau aktivitas akunmu",
            com.example.relawanku.R.drawable.ic_clock,
        ) {
            a.navigate("history")
        }
        u.button(p, "Muat ulang", true) { a.refresh() }
    }

    fun users() {
        if (s.role != Role.ADMIN) {
            a.selectTab("home")
            return
        }
        val p =
            a.page(
                "Pengguna",
                "Kelola akun dan hak akses. Perubahan role atau status dapat mengakhiri sesi pengguna.",
            )
        u.button(p, "Tambah pengguna") {
            s.selectedUser = null
            a.navigate("userform")
        }
        val search = u.field(p, "Cari nama atau email", s.query)
        val role =
            FormTools(a)
                .choice(
                    p,
                    "Filter role",
                    listOf("Semua" to "Semua role") + Role.entries.map { it.wire to it.label },
                    s.category,
                )
        u.button(p, "Cari pengguna", true) {
            s.query = search.second.text.toString().trim()
            s.category = role.value
            a.render()
        }
        val area = u.column()
        p.addView(area)
        fun load(page: Int) {
            val query = mutableMapOf("search" to s.query, "page" to "$page")
            if (s.category != "Semua") query["role"] = s.category
            a.load(area, { a.api.users(query) }) { result ->
                if (result.data.isEmpty())
                    u.empty(area, "Pengguna belum ditemukan", "Ubah kata kunci atau filter role.")
                result.data.forEach { user ->
                    val card = u.panel(area)
                    card.addView(u.text(user.name, 19, 700))
                    card.addView(u.text(user.email, 14, 400, u.muted))
                    u.gap(card, 8)
                    u.badge(
                        card,
                        "${Role.fromWire(user.role)?.label ?: user.role} · ${if (user.active) "Aktif" else "Nonaktif"}",
                    )
                    u.button(card, "Edit pengguna", true) {
                        s.selectedUser = user
                        a.navigate("userform")
                    }
                    if (user.id != s.user?.id)
                        u.button(
                            card,
                            if (user.active) "Nonaktifkan akun" else "Aktifkan akun",
                            true,
                        ) {
                            a.confirm("Ubah status akun?", user.email) {
                                a.write(
                                    a.api.updateUser(user.id, mapOf("is_active" to !user.active))
                                ) {
                                    a.render()
                                }
                            }
                        }
                }
                a.pagination(area, result) { load(it) }
            }
        }
        load(1)
    }

    fun userForm() {
        if (s.role != Role.ADMIN) {
            a.selectTab("home")
            return
        }
        val user = s.selectedUser
        val self = user?.id == s.user?.id
        val p =
            a.page(
                if (user == null) "Tambah pengguna" else "Edit pengguna",
                "Password hanya diatur saat membuat akun.",
                true,
            )
        val name = u.field(p, "Nama lengkap", user?.name.orEmpty())
        val email =
            u.field(
                p,
                "Email",
                user?.email.orEmpty(),
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            )
        val phone = u.field(p, "Nomor telepon", user?.phone.orEmpty(), InputType.TYPE_CLASS_PHONE)
        val role =
            if (!self)
                FormTools(a)
                    .choice(
                        p,
                        "Role pengguna",
                        Role.entries.map { it.wire to it.label },
                        user?.role ?: "relawan",
                    )
            else null
        if (self)
            p.addView(
                u.text(
                    "Role dan status akunmu sendiri tidak dapat diubah dari sini.",
                    14,
                    400,
                    u.muted,
                )
            )
        val password =
            if (user == null)
                u.field(
                    p,
                    "Kata sandi",
                    type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                )
            else null
        val confirm =
            if (user == null)
                u.field(
                    p,
                    "Ulangi kata sandi",
                    type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                )
            else null
        val fields =
            mutableMapOf("name" to name.first, "email" to email.first, "phone" to phone.first)
        role?.let { fields["role"] = it.box }
        password?.let { fields["password"] = it.first }
        confirm?.let { fields["password_confirmation"] = it.first }
        u.button(p, "Simpan pengguna") {
            fields.values.forEach { it.error = null }
            name.first.error =
                if (name.second.text.toString().isBlank()) "Nama wajib diisi" else null
            val mail = email.second.text.toString().trim().lowercase(java.util.Locale.ROOT)
            email.first.error =
                if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) "Email tidak valid" else null
            if (password != null) {
                val value = password.second.text.toString()
                password.first.error =
                    if (
                        value.length < 8 ||
                            !value.any { it.isLetter() } ||
                            !value.any { it.isDigit() }
                    )
                        "Minimal 8 karakter dengan huruf dan angka"
                    else null
                confirm!!.first.error =
                    if (confirm.second.text.toString() != value) "Kata sandi belum sama" else null
            }
            if (fields.values.any { it.error != null }) return@button
            val body =
                mutableMapOf<String, Any?>(
                    "name" to name.second.text.toString().trim(),
                    "email" to mail,
                    "phone" to phone.second.text.toString().trim().ifEmpty { null },
                )
            if (user == null) {
                body["role"] = role!!.value
                body["password"] = password!!.second.text.toString()
                body["password_confirmation"] = confirm!!.second.text.toString()
            } else if (role != null && role.value != user.role) body["role"] = role.value
            a.write(
                if (user == null) a.api.createUser(body) else a.api.updateUser(user.id, body),
                fields,
            ) { response ->
                if (self) response.data?.let { s.user = it }
                a.selectTab("users")
                a.notice("Pengguna disimpan.")
            }
        }
    }
}
