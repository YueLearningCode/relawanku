package com.example.relawanku.ui

import android.view.Gravity
import android.widget.LinearLayout
import com.example.relawanku.MainActivity
import com.example.relawanku.R

class VolunteerHome(private val a: MainActivity) {
    fun render() {
        val u = a.ui
        val s = a.state
        val p = a.page()
        val brand = u.row()
        brand.addView(u.icon(R.drawable.ic_leaf, 26))
        brand.addView(
            u.text("  relawanku", 20, 700, u.primary),
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        brand.addView(u.iconAction(R.drawable.ic_person, "Buka profil") { a.selectTab("profile") })
        p.addView(brand)
        u.gap(p, 22)
        p.addView(u.text("Halo, ${s.displayName.substringBefore(" ")}", 28, 700))
        u.gap(p, 6)
        p.addView(u.text("Siap berbagi kebaikan hari ini?", 16, 400, u.muted))
        u.gap(p, 24)
        val hero = u.panel(p, u.primary, 24)
        u.badge(hero, "BERSAMA KITA BISA", u.color(R.color.primary_dark), u.color(R.color.sage))
        u.gap(hero, 16)
        hero.addView(u.text("Langkah kecil,\ndampak berarti.", 28, 700, u.color(R.color.surface)))
        u.gap(hero, 12)
        hero.addView(
            u.text(
                "Temukan kegiatan yang dekat\ndengan hati dan sekitarmu.",
                14,
                400,
                u.color(R.color.sage),
            )
        )
        u.button(hero, "Jelajahi kegiatan", true) { a.selectTab("explore") }
        val stat = u.row()
        val left = u.column(16)
        val right = u.column(16)
        left.addView(u.text(s.joined.size.toString().padStart(2, '0'), 24, 700, u.primary))
        left.addView(u.text("Kegiatan diikuti", 12, 600, u.muted))
        right.addView(
            u.text(
                s.joined.count { it !in s.finishedTasks }.toString().padStart(2, '0'),
                24,
                700,
                u.primary,
            )
        )
        right.addView(u.text("Tugas menanti", 12, 600, u.muted))
        stat.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        stat.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        stat.background = u.background(u.color(R.color.sage), 20)
        p.addView(stat)
        u.gap(p, 12)
        val upcoming = s.events.firstOrNull { it.id in s.joined && !it.completed }
        if (upcoming != null) {
            u.section(p, "Langkahmu berikutnya")
            val c = u.panel(p)
            u.meta(c, R.drawable.ic_calendar, upcoming.title, upcoming.date)
            u.button(c, "Lihat kegiatan saya", true) { a.openEvent(upcoming.id) }
        }
        u.section(p, "Kegiatan pilihan", "Lihat semua") { a.selectTab("explore") }
        s.events
            .filter { !it.completed }
            .take(3)
            .forEach { u.eventCard(p, it, it.id in s.joined) { a.openEvent(it.id) } }
        if (s.events.isEmpty())
            u.empty(p, "Kebaikan baru segera hadir", "Belum ada kegiatan untuk ditampilkan.")
        p.addView(
            u.text("Pratinjau UI · data contoh lokal", 12, 600, u.muted).apply {
                gravity = Gravity.CENTER
            }
        )
    }
}
