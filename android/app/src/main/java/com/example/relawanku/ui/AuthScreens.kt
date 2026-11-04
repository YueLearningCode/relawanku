package com.example.relawanku.ui

import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import com.example.relawanku.MainActivity
import com.example.relawanku.R

class AuthScreens(private val a: MainActivity) {
    private val u = a.ui
    private val s = a.state

    fun render() {
        val register = s.screen == "register"
        val p = a.page()
        val logo = u.row()
        logo.addView(u.icon(R.drawable.ic_leaf, 32))
        logo.addView(u.text("  relawanku", 22, 700, u.primary))
        p.addView(logo)
        u.gap(p, 30)
        val hero = u.panel(p, u.color(R.color.primary), 24)
        hero.addView(
            u.text("Kebaikan dimulai\ndari satu langkah.", 28, 700, u.color(R.color.surface))
        )
        u.gap(hero, 12)
        hero.addView(
            u.text(
                "Temukan kegiatan bermakna.\nBertemu orang-orang yang peduli.",
                14,
                400,
                u.color(R.color.sage),
            )
        )
        u.gap(p, 8)
        p.addView(
            u.text(if (register) "Jadi bagian dari perubahan" else "Senang bertemu lagi!", 24, 700)
        )
        u.gap(p, 8)
        p.addView(
            u.text(
                if (register) "Buat profil relawan untuk mulai berkontribusi."
                else "Masuk dan lanjutkan langkah baikmu.",
                14,
                400,
                u.muted,
            )
        )
        u.gap(p, 22)
        val name = if (register) u.field(p, "Nama lengkap") else null
        val (emailBox, email) =
            u.field(
                p,
                "Email",
                type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            )
        val (passBox, pass) =
            u.field(
                p,
                "Kata sandi",
                type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            )
        val confirm =
            if (register)
                u.field(
                    p,
                    "Ulangi kata sandi",
                    type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                )
            else null
        u.button(p, if (register) "Buat profil contoh" else "Masuk") {
            val mail = email.text.toString().trim()
            val password = pass.text.toString()
            emailBox.error =
                if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) "Masukkan email yang valid"
                else null
            passBox.error = if (password.length < 8) "Gunakan minimal 8 karakter" else null
            name?.first?.error =
                if (name?.second?.text.toString().isBlank()) "Nama wajib diisi" else null
            confirm?.first?.error =
                if (confirm?.second?.text.toString() != password) "Kata sandi belum sama" else null
            if (
                emailBox.error == null &&
                    passBox.error == null &&
                    name?.first?.error == null &&
                    confirm?.first?.error == null
            ) {
                if (register) {
                    s.role = Role.RELAWAN
                    s.displayName = name!!.second.text.toString().trim()
                } else s.role = s.selectedRole
                s.email = mail
                a.selectTab("home")
                a.notice("Pratinjau lokal dibuka. Autentikasi API belum dihubungkan.")
            }
        }
        u.button(p, if (register) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar", true) {
            a.navigate(if (register) "login" else "register")
        }
        if (!register) {
            u.gap(p, 24)
            p.addView(u.text("COBA TAMPILAN APLIKASI", 12, 700, u.primary))
            u.gap(p, 6)
            p.addView(
                u.text("Pilih peran untuk menjelajahi UI dengan data contoh.", 14, 400, u.muted)
            )
            u.chips(p, Role.entries.map { it.label }, s.selectedRole.label) { selected ->
                s.selectedRole = Role.entries.first { it.label == selected }
            }
            u.button(p, "Coba dengan data contoh", true) {
                s.role = s.selectedRole
                a.selectTab("home")
            }
        }
        u.gap(p, 16)
        p.addView(
            u.text("Pratinjau UI · data contoh lokal", 12, 600, u.muted).apply {
                gravity = Gravity.CENTER
            }
        )
    }
}
