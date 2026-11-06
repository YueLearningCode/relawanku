package com.example.relawanku.ui

import com.example.relawanku.MainActivity
import com.example.relawanku.R

class VolunteerHome(private val a: MainActivity) {
    fun render() {
        val u = a.ui
        val p = a.page()
        u.dashboardHeader(p, "Relawan", a.state.displayName, "Ruang kontribusi") {
            a.selectTab("profile")
        }
        val hero = u.panel(p, u.primary, 20)
        hero.background =
            android.graphics.drawable
                .GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(u.color(R.color.primary_dark), u.primary),
                )
                .apply { cornerRadius = u.dp(16).toFloat() }
        hero.addView(
            u.text("AMBIL BAGIAN", 10, 700, u.color(R.color.sage)).apply { letterSpacing = .12f }
        )
        u.gap(hero, 10)
        hero.addView(u.text("Temukan kegiatan\nyang berarti.", 24, 700, u.color(R.color.surface)))
        u.gap(hero, 8)
        hero.addView(
            u.text("Berkontribusi bersama komunitas yang peduli.", 13, 400, u.color(R.color.sage))
        )
        u.gap(hero, 6)
        u.inverseButton(hero, "Temukan kegiatan") { a.selectTab("explore") }
        u.section(p, "Kontribusimu")
        val stats = u.column()
        p.addView(stats)
        a.load(stats, { a.api.dashboard() }) { response ->
            response.data?.let { d ->
                u.metrics(
                    stats,
                    listOf(
                        "Terdaftar" to d.registrations,
                        "Kehadiran" to d.attendances,
                        "Tugas aktif" to d.pendingTasks,
                    ),
                    3,
                )
            }
        }
        u.section(p, "Kegiatan terbaru", "Lihat semua") { a.selectTab("explore") }
        val list = u.column()
        p.addView(list)
        a.load(list, { a.api.activities(mapOf("status" to "open", "per_page" to "3")) }) { result ->
            if (result.data.isEmpty())
                u.empty(list, "Belum ada kegiatan", "Kegiatan baru akan muncul di sini.")
            result.data.forEach { event ->
                a.state.events[event.id] = event
                u.eventCard(list, event, false) { a.openEvent(event.id) }
            }
        }
        u.button(p, "Muat ulang", true) { a.refresh() }
    }
}
