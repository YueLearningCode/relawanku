package com.example.relawanku.ui

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Patterns
import android.widget.LinearLayout
import com.example.relawanku.MainActivity
import com.example.relawanku.R

class ManagementScreens(private val a: MainActivity) {
    private val u = a.ui
    private val s = a.state

    fun dashboard() {
        val admin = s.role == Role.ADMIN
        val p = a.page()
        val row = u.row()
        row.addView(u.icon(R.drawable.ic_leaf, 26))
        row.addView(u.text("  relawanku", 20, 700, u.primary), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(u.iconAction(R.drawable.ic_person, "Buka profil") { a.selectTab("profile") })
        p.addView(row)
        u.gap(p, 22)
        u.badge(p, if (admin) "RUANG ADMIN" else "RUANG KOORDINATOR")
        u.gap(p, 16)
        p.addView(
            u.text(
                if (admin) "Pantau dampak\nkebaikan bersama."
                else "Kelola kegiatan,\ntumbuhkan dampak.",
                28,
                700,
            )
        )
        u.gap(p, 10)
        p.addView(
            u.text(
                if (admin) "Ringkasan pengguna dan seluruh kegiatan."
                else "Semua kegiatanmu, dalam satu tempat.",
                16,
                400,
                u.muted,
            )
        )
        u.gap(p, 24)
        val hero = u.panel(p, u.primary, 24)
        hero.addView(
            u.text(
                if (admin) "${s.users.count{it.active}}" else "${s.events.sumOf{it.participants}}",
                40,
                700,
                u.color(R.color.surface),
            )
        )
        hero.addView(
            u.text(
                if (admin) "pengguna aktif dalam komunitas" else "relawan siap berkontribusi",
                16,
                600,
                u.color(R.color.sage),
            )
        )
        u.button(hero, if (admin) "Kelola pengguna" else "Buat kegiatan baru", true) {
            if (admin) a.selectTab("users")
            else {
                s.selectedEvent = 0
                a.navigate("form")
            }
        }
        val stats = u.row()
        for ((number, label) in
            listOf(
                s.events.count { !it.completed }.toString() to "Kegiatan aktif",
                s.attended.size.toString() to "Hadir di contoh",
            )) {
            val c = u.column(16)
            c.addView(u.text(number.padStart(2, '0'), 24, 700, u.primary))
            c.addView(u.text(label, 12, 600, u.muted))
            stats.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
        }
        stats.background = u.background(u.color(R.color.sage), 20)
        p.addView(stats)
        u.gap(p, 12)
        u.section(p, if (admin) "Semua kegiatan" else "Kegiatan yang dikelola", "Lihat semua") {
            a.selectTab("explore")
        }
        if (s.events.isEmpty())
            u.empty(
                p,
                "Belum ada kegiatan",
                "Buat kegiatan pertama untuk mengajak relawan berkontribusi.",
            )
        s.events.take(3).forEach { u.eventCard(p, it, it.id in s.joined) { a.openEvent(it.id) } }
        p.addView(u.text("Pratinjau UI · data contoh lokal", 12, 600, u.muted))
    }

    fun users() {
        val p = a.page("Pengguna", "Kelola orang-orang di balik langkah baik ini.")
        u.button(p, "Tambah pengguna") {
            a.intent.putExtra("preview_user_id", 0)
            a.navigate("userform")
        }
        u.gap(p)
        val (_, search) = u.field(p, "Cari nama atau email", s.query)
        var role = "Semua"
        val results = u.column()
        fun populate() {
            results.removeAllViews()
            val users =
                s.users.filter {
                    (role == "Semua" || it.role.label == role) &&
                        (s.query.isBlank() ||
                            it.name.contains(s.query, true) ||
                            it.email.contains(s.query, true))
                }
            if (users.isEmpty())
                u.empty(
                    results,
                    "Pengguna tidak ditemukan",
                    "Coba nama, email, atau peran lainnya.",
                )
            users.forEach { user ->
                val c = u.panel(results)
                u.meta(c, R.drawable.ic_person, user.name, user.email)
                u.badge(c, user.role.label + if (user.active) " · Aktif" else " · Nonaktif")
                u.button(c, "Edit pengguna", true) {
                    a.intent.putExtra("preview_user_id", user.id)
                    a.navigate("userform")
                }
                u.button(c, if (user.active) "Nonaktifkan akun" else "Aktifkan akun", true) {
                    a.confirm(
                        if (user.active) "Nonaktifkan akun?" else "Aktifkan akun?",
                        "Perubahan ini hanya diterapkan pada data contoh ${user.name}.",
                        "Konfirmasi",
                    ) {
                        user.active = !user.active
                        populate()
                        a.notice("Status akun contoh diperbarui.")
                    }
                }
            }
        }
        u.chips(p, listOf("Semua", "Relawan", "Koordinator", "Admin"), role) {
            role = it
            populate()
        }
        p.addView(results)
        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    t: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(t: CharSequence?, start: Int, before: Int, count: Int) {
                    s.query = t.toString()
                    populate()
                }

                override fun afterTextChanged(e: Editable?) {}
            }
        )
        populate()
    }

    fun userForm() {
        val id = a.intent.getIntExtra("preview_user_id", 0)
        val original = s.users.firstOrNull { it.id == id }
        val p =
            a.page(
                if (original == null) "Tambah pengguna" else "Edit pengguna",
                "Atur identitas dan peran pengguna contoh.",
                true,
            )
        val (nameBox, name) = u.field(p, "Nama lengkap", original?.name.orEmpty())
        val (emailBox, email) =
            u.field(
                p,
                "Email",
                original?.email.orEmpty(),
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            )
        var role = original?.role ?: Role.RELAWAN
        p.addView(u.text("Peran pengguna", 14, 600))
        u.chips(p, Role.entries.map { it.label }, role.label) { label ->
            role = Role.entries.first { it.label == label }
        }
        p.addView(
            u.text(
                "Form ini mengubah data contoh. Pembuatan akun dan kata sandi sesungguhnya akan ditangani API Laravel.",
                14,
                400,
                u.muted,
            )
        )
        u.gap(p, 20)
        u.button(p, "Simpan pengguna") {
            val n = name.text.toString().trim()
            val e = email.text.toString().trim().lowercase()
            nameBox.error = if (n.isBlank()) "Nama wajib diisi" else null
            emailBox.error =
                when {
                    !Patterns.EMAIL_ADDRESS.matcher(e).matches() -> "Email belum valid"
                    s.users.any { it.id != id && it.email == e } -> "Email contoh sudah digunakan"
                    else -> null
                }
            if (nameBox.error == null && emailBox.error == null) {
                if (original == null)
                    s.users.add(PreviewUser((s.users.maxOfOrNull { it.id } ?: 0) + 1, n, e, role))
                else {
                    original.name = n
                    original.email = e
                    original.role = role
                }
                a.selectTab("users")
                a.notice("Pengguna contoh disimpan.")
            }
        }
        u.button(p, "Batal", true) { a.selectTab("users") }
    }
}
