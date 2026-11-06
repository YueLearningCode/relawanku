package com.example.relawanku.ui

import android.text.InputType
import android.widget.CheckBox
import android.widget.LinearLayout
import com.example.relawanku.MainActivity
import com.example.relawanku.R
import com.example.relawanku.data.*

class EventScreens(private val a: MainActivity) {
    private val s = a.state
    private val u = a.ui

    fun listing() {
        val joined = s.tab == "joined"
        val manager = s.role != Role.RELAWAN
        val p =
            a.page(
                if (joined) "Kegiatanku" else if (manager) "Kelola kegiatan" else "Temukan peranmu",
                if (joined) "Pendaftaran dan riwayat partisipasimu."
                else "Cari kegiatan yang sesuai dengan kepedulianmu.",
            )
        if (manager)
            u.button(p, "Buat kegiatan baru") {
                s.selectedEvent = 0
                a.navigate("form")
            }
        if (!joined) {
            val search = u.field(p, "Cari nama atau lokasi", s.query)
            u.button(p, "Cari", true) {
                s.query = search.second.text.toString().trim()
                a.render()
            }
            u.chips(p, listOf("Semua", "Lingkungan", "Pendidikan", "Sosial"), s.category) {
                s.category = it
                a.render()
            }
        }
        val area = u.column()
        p.addView(area)
        if (joined) {
            fun load(page: Int) {
                a.load(area, { a.api.mine(page) }) { result ->
                    if (result.data.isEmpty())
                        u.empty(
                            area,
                            "Belum ada pendaftaran",
                            "Jelajahi kegiatan dan mulai ambil peran.",
                        )
                    result.data.forEach { r ->
                        r.activity?.let { event ->
                            val card = u.column()
                            area.addView(card)
                            u.badge(
                                card,
                                if (r.attendance != null) "Sudah hadir" else statusLabel(r.status),
                            )
                            u.gap(card, 8)
                            u.eventCard(card, event, r.status == "registered") {
                                if (event.deletedAt != null)
                                    a.notice(
                                        "Kegiatan telah diarsipkan. Catatan partisipasimu tetap tersimpan."
                                    )
                                else a.openEvent(event.id)
                            }
                        }
                    }
                    a.pagination(area, result) { load(it) }
                }
            }
            load(1)
        } else {
            val selected = mutableSetOf<Long>()
            if (manager)
                u.button(p, "Arsipkan kegiatan terpilih", true) {
                    if (selected.isEmpty()) a.notice("Pilih kegiatan yang akan diarsipkan.")
                    else
                        a.confirm(
                            "Arsipkan ${selected.size} kegiatan?",
                            "Kegiatan tidak lagi muncul dalam daftar aktif.",
                            "Arsipkan",
                        ) {
                            a.write(a.api.deleteActivities(mapOf("ids" to selected.toList()))) {
                                a.render()
                                a.notice("Kegiatan terpilih diarsipkan.")
                            }
                        }
                }
            fun load(page: Int) {
                selected.clear()
                val query = mutableMapOf("page" to "$page", "search" to s.query)
                if (s.category != "Semua") query["category"] = s.category
                if (s.role == Role.KOORDINATOR) query["mine"] = "1"
                a.load(area, { a.api.activities(query) }) { result ->
                    if (result.data.isEmpty())
                        u.empty(
                            area,
                            "Kegiatan belum ditemukan",
                            "Coba kata kunci lain atau ubah kategori.",
                        )
                    result.data.forEach { event ->
                        s.events[event.id] = event
                        if (s.canManage(event))
                            area.addView(
                                CheckBox(a).apply {
                                    text = "Pilih ${event.title}"
                                    typeface = u.font()
                                    setOnCheckedChangeListener { _, checked ->
                                        if (checked) selected.add(event.id)
                                        else selected.remove(event.id)
                                    }
                                }
                            )
                        u.eventCard(area, event, false) { a.openEvent(event.id) }
                    }
                    a.pagination(area, result) { load(it) }
                }
            }
            load(1)
        }
        u.button(p, "Muat ulang", true) { a.refresh() }
    }

    fun detail() {
        val p = a.page(back = true)
        val area = u.column()
        p.addView(area)
        a.load(area, { a.api.activity(s.selectedEvent) }) { response ->
            val event = response.data
            if (event == null) {
                u.empty(area, "Kegiatan tidak ditemukan", "Muat ulang daftar kegiatan.")
                return@load
            }
            s.events[event.id] = event
            content(area, event, event.myRegistration)
        }
    }

    private fun content(p: LinearLayout, event: ActivityDto, registration: RegistrationDto?) {
        val manage = s.canManage(event)
        val participant =
            registration != null &&
                (registration.status == "registered" || registration.attendance != null)
        val image =
            android.widget.ImageView(a).apply {
                layoutParams = LinearLayout.LayoutParams(-1, u.dp(190))
                contentDescription =
                    if (event.coverUrl == null) "Ilustrasi kategori kegiatan" else "Sampul kegiatan"
            }
        u.image(image, event.coverUrl, event.image)
        p.addView(image)
        u.gap(p)
        u.badge(p, statusLabel(event.status))
        u.gap(p)
        p.addView(u.text(event.title, 26, 700))
        u.gap(p, 8)
        p.addView(
            u.text(
                "${event.category} · ${event.participants}/${event.quota} relawan",
                14,
                400,
                u.muted,
            )
        )
        val tabs = mutableListOf("Ringkasan")
        if (manage) tabs.add("Peserta")
        if (manage || participant) tabs.addAll(listOf("Tugas", "Absensi", "Dokumentasi"))
        if (s.detailTab !in tabs) s.detailTab = "Ringkasan"
        u.gap(p)
        u.chips(p, tabs, s.detailTab) {
            s.detailTab = it
            a.render()
        }
        when (s.detailTab) {
            "Peserta" -> ActivityActions(a, event).participants(p)
            "Tugas" -> ActivityActions(a, event).tasks(p, manage)
            "Absensi" -> ActivityActions(a, event).attendance(p, manage, registration)
            "Dokumentasi" -> ActivityActions(a, event).photos(p, manage)
            else -> {
                u.meta(p, R.drawable.ic_calendar, event.date, event.time)
                u.meta(p, R.drawable.ic_pin, "Lokasi", event.location)
                u.meta(p, R.drawable.ic_person, "Koordinator", event.coordinator?.name ?: "—")
                u.section(p, "Tentang kegiatan")
                p.addView(u.text(event.description, 15))
                u.section(p, "Persyaratan")
                p.addView(
                    u.text(
                        event.requirements?.takeIf { it.isNotBlank() }
                            ?: "Tidak ada persyaratan tambahan.",
                        15,
                        400,
                        u.muted,
                    )
                )
                u.gap(p)
                p.addView(
                    u.text("Batas pendaftaran: ${Dates.full(event.registrationDeadline)}", 14, 600)
                )
                if (s.role == Role.RELAWAN) {
                    if (registration?.status == "registered") {
                        u.gap(p)
                        u.badge(
                            p,
                            if (registration.attendance != null) "Kehadiran tercatat"
                            else "Kamu sudah terdaftar",
                        )
                        if (
                            registration.attendance == null &&
                                (Dates.parse(event.startsAt)?.time ?: 0) >
                                    System.currentTimeMillis()
                        )
                            u.button(p, "Batalkan pendaftaran", true) {
                                a.confirm(
                                    "Batalkan pendaftaran?",
                                    "Tempatmu akan tersedia untuk relawan lain.",
                                    "Batalkan pendaftaran",
                                ) {
                                    a.write(a.api.leave(event.id)) {
                                        a.render()
                                        a.notice("Pendaftaran dibatalkan.")
                                    }
                                }
                            }
                    } else if (event.registrationOpen())
                        u.button(p, "Daftar kegiatan") {
                            a.confirm(
                                "Daftar kegiatan?",
                                "Pastikan kamu dapat hadir sesuai jadwal.",
                                "Ya, daftar",
                            ) {
                                a.write(a.api.join(event.id)) {
                                    a.render()
                                    a.notice("Pendaftaran berhasil.")
                                }
                            }
                        }
                    else
                        u.empty(
                            p,
                            "Pendaftaran tidak tersedia",
                            "Periksa jadwal, status, dan sisa kuota kegiatan.",
                        )
                }
                if (manage) {
                    u.button(p, "Edit kegiatan", true) { a.navigate("form") }
                    u.button(p, "Ubah foto sampul", true) { a.pickPhoto(true) }
                    u.button(p, "Arsipkan kegiatan", true) {
                        a.confirm(
                            "Arsipkan kegiatan?",
                            "Riwayat partisipasi tetap tersimpan.",
                            "Arsipkan",
                        ) {
                            a.write(a.api.deleteActivity(event.id)) {
                                s.events.remove(event.id)
                                a.selectTab("explore")
                                a.notice("Kegiatan diarsipkan.")
                            }
                        }
                    }
                }
            }
        }
        u.button(p, "Muat ulang", true) { a.refresh() }
    }

    fun form() {
        val event = s.events[s.selectedEvent]
        if (event != null && !s.canManage(event)) {
            a.navigate("main")
            return
        }
        val p =
            a.page(
                if (event == null) "Buat kegiatan baru" else "Edit kegiatan",
                "Isi jadwal dan informasi kegiatan.",
                true,
            )
        if (s.role == Role.ADMIN) {
            val area = u.column()
            p.addView(area)
            area.addView(u.text("Memuat koordinator…"))
            a.allPages(
                {
                    a.api.users(
                        mapOf("role" to "koordinator", "per_page" to "100", "page" to "$it")
                    )
                },
                failure = { error ->
                    area.removeAllViews()
                    u.empty(area, "Koordinator belum dapat dimuat", error.message)
                    u.button(area, "Coba lagi", true) { a.render() }
                },
            ) { users ->
                area.removeAllViews()
                activityForm(
                    area,
                    event,
                    users.filter { it.active || it.id == event?.coordinatorId },
                )
            }
        } else activityForm(p, event, emptyList())
    }

    private fun activityForm(p: LinearLayout, event: ActivityDto?, coordinators: List<ApiUser>) {
        val f = FormTools(a)
        val title = u.field(p, "Nama kegiatan", event?.title.orEmpty())
        val desc =
            u.field(
                p,
                "Deskripsi",
                event?.description.orEmpty(),
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            )
        val category = u.field(p, "Kategori", event?.category ?: "Lingkungan")
        val location = u.field(p, "Lokasi", event?.location.orEmpty())
        val quota =
            u.field(
                p,
                "Kuota relawan",
                event?.quota?.toString().orEmpty(),
                InputType.TYPE_CLASS_NUMBER,
            )
        val requirements =
            u.field(
                p,
                "Persyaratan",
                event?.requirements.orEmpty(),
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            )
        val start = f.date(p, "Mulai", event?.startsAt.orEmpty())
        val end = f.date(p, "Selesai", event?.endsAt.orEmpty())
        val deadline = f.date(p, "Batas pendaftaran", event?.registrationDeadline.orEmpty())
        val status =
            f.choice(
                p,
                "Status",
                listOf(
                    "draft" to "Draft",
                    "open" to "Buka pendaftaran",
                    "closed" to "Tutup pendaftaran",
                    "completed" to "Selesai",
                    "cancelled" to "Dibatalkan",
                ),
                event?.status ?: "draft",
            )
        val coordinator =
            if (s.role == Role.ADMIN)
                f.choice(
                    p,
                    "Koordinator",
                    coordinators.map { it.id.toString() to "${it.name} (${it.email})" },
                    event?.coordinatorId?.toString().orEmpty(),
                )
            else null
        val fields =
            mapOf(
                    "title" to title.first,
                    "description" to desc.first,
                    "category" to category.first,
                    "location" to location.first,
                    "quota" to quota.first,
                    "requirements" to requirements.first,
                    "starts_at" to start.box,
                    "ends_at" to end.box,
                    "registration_deadline" to deadline.box,
                    "status" to status.box,
                )
                .toMutableMap()
        coordinator?.let { fields["coordinator_id"] = it.box }
        u.button(p, "Simpan kegiatan") {
            fields.values.forEach { it.error = null }
            listOf(title, desc, category, location).forEach {
                if (it.second.text.toString().isBlank()) it.first.error = "Wajib diisi"
            }
            val count = quota.second.text.toString().toIntOrNull()
            if (count == null || count !in 1..100000) quota.first.error = "Kuota 1–100000"
            listOf(start, end, deadline).forEach {
                if (it.value.isBlank()) it.box.error = "Pilih tanggal dan waktu"
            }
            if (coordinator != null && coordinator.value.toLongOrNull() == null)
                coordinator.box.error = "Pilih koordinator aktif"
            if (fields.values.any { it.error != null }) return@button
            val body =
                mutableMapOf<String, Any?>(
                    "title" to title.second.text.toString().trim(),
                    "description" to desc.second.text.toString().trim(),
                    "category" to category.second.text.toString().trim(),
                    "location" to location.second.text.toString().trim(),
                    "quota" to count,
                    "requirements" to requirements.second.text.toString().trim().ifEmpty { null },
                    "starts_at" to start.value,
                    "ends_at" to end.value,
                    "registration_deadline" to deadline.value,
                    "status" to status.value,
                )
            coordinator?.let { body["coordinator_id"] = it.value.toLong() }
            val call =
                if (event == null) a.api.createActivity(body)
                else a.api.updateActivity(event.id, body)
            a.write(call, fields) { response ->
                response.data?.let { saved ->
                    s.events[saved.id] = saved
                    a.openEvent(saved.id)
                    a.notice("Kegiatan disimpan.")
                } ?: a.notice("Respons server tidak lengkap.")
            }
        }
    }
}
