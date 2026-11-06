package com.example.relawanku.ui

import com.example.relawanku.MainActivity
import com.example.relawanku.R

class VolunteerHome(private val a: MainActivity) {
    fun render() {
        val u = a.ui
        val p =
            a.page(
                "Halo, ${a.state.displayName.substringBefore(' ')}!",
                "Langkah kecilmu bisa berarti besar untuk sesama.",
            )
        val hero = u.panel(p, u.primary, 24)
        hero.addView(u.text("Ambil peran.\nHadirkan perubahan.", 28, 700, u.color(R.color.surface)))
        u.button(hero, "Temukan kegiatan", true) { a.selectTab("explore") }
        val stats = u.column()
        p.addView(stats)
        a.load(stats, { a.api.dashboard() }) { response ->
            response.data?.let { d ->
                val panel = u.panel(stats)
                panel.addView(u.text("Kontribusimu", 18, 700))
                panel.addView(
                    u.text(
                        "${d.registrations} pendaftaran aktif · ${d.attendances} kehadiran\n${d.pendingTasks} tugas belum selesai",
                        15,
                        400,
                        u.muted,
                    )
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
