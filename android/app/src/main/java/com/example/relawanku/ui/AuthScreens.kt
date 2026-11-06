package com.example.relawanku.ui

import android.text.InputType
import android.util.Patterns
import com.example.relawanku.MainActivity
import com.example.relawanku.R

class AuthScreens(private val a: MainActivity) {
    private val u = a.ui

    fun render() {
        val register = a.state.screen == "register"
        val p = a.page()
        p.addView(u.text("relawanku", 26, 700, u.primary))
        u.gap(p, 24)
        val hero = u.panel(p, u.primary, 24)
        hero.addView(
            u.text("Kebaikan dimulai\ndari satu langkah.", 28, 700, u.color(R.color.surface))
        )
        u.gap(hero, 12)
        hero.addView(
            u.text(
                "Temukan kegiatan bermakna. Ambil peran bersama orang-orang yang peduli.",
                14,
                400,
                u.color(R.color.sage),
            )
        )
        p.addView(
            u.text(if (register) "Jadi bagian dari perubahan" else "Senang bertemu lagi!", 24, 700)
        )
        u.gap(p, 10)
        p.addView(
            u.text(
                if (register) "Pendaftaran membuat akun Relawan. Akun Koordinator disediakan Admin."
                else "Masuk dengan akunmu. Hak akses mengikuti role dari server.",
                14,
                400,
                u.muted,
            )
        )
        u.gap(p)
        val name = if (register) u.field(p, "Nama lengkap") else null
        val email =
            u.field(
                p,
                "Email",
                type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            )
        val pass =
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
        u.button(p, if (register) "Daftar sebagai relawan" else "Masuk") {
            val mail = email.second.text.toString().trim().lowercase(java.util.Locale.ROOT)
            val password = pass.second.text.toString()
            email.first.error =
                if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) "Masukkan email yang valid"
                else null
            pass.first.error =
                if (password.isBlank()) "Kata sandi wajib diisi"
                else if (
                    register &&
                    (password.length < 8 ||
                        !password.any { it.isLetter() } ||
                        !password.any { it.isDigit() })
                )
                    "Minimal 8 karakter dengan huruf dan angka"
                else null
            name?.first?.error =
                if (name?.second?.text.toString().trim().isEmpty()) "Nama wajib diisi" else null
            confirm?.first?.error =
                if (confirm?.second?.text.toString() != password) "Kata sandi belum sama" else null
            if (
                listOfNotNull(email.first, pass.first, name?.first, confirm?.first).any {
                    it.error != null
                }
            )
                return@button
            val body = mutableMapOf<String, Any?>("email" to mail, "password" to password)
            val fields = mutableMapOf("email" to email.first, "password" to pass.first)
            if (register) {
                body["name"] = name!!.second.text.toString().trim()
                body["password_confirmation"] = password
                fields["name"] = name.first
                fields["password_confirmation"] = confirm!!.first
                a.write(a.api.register(body), fields) {
                    a.navigate("login")
                    a.notice("Akun berhasil dibuat. Silakan masuk.")
                }
            } else {
                body["device_name"] = "Relawanku Android"
                a.write(a.api.login(body), fields) { response ->
                    response.data?.let { a.acceptLogin(it) }
                        ?: a.notice("Respons login tidak lengkap.")
                }
            }
        }
        u.button(p, if (register) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar", true) {
            a.navigate(if (register) "login" else "register")
        }
        u.gap(p)
        u.button(p, "Atur koneksi server", true) { a.serverSettings() }
//        p.addView(u.text(a.session.baseUrl, 12, 400, u.muted))
    }
}
