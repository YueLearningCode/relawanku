package com.example.relawanku.ui

import android.text.InputType
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.relawanku.MainActivity
import com.example.relawanku.data.*

class ActivityActions(private val a: MainActivity, private val event: ActivityDto) {
    private val u = a.ui

    fun participants(p: LinearLayout) {
        val area = u.column()
        p.addView(area)
        fun load(page: Int) {
            a.load(area, { a.api.participants(event.id, page) }) { result ->
                if (result.data.isEmpty())
                    u.empty(area, "Belum ada peserta", "Peserta akan muncul setelah mendaftar.")
                result.data.forEach { r ->
                    val card = u.panel(area)
                    card.addView(u.text(r.user?.name ?: "Relawan", 18, 700))
                    card.addView(u.text(r.user?.email.orEmpty(), 14, 400, u.muted))
                    u.gap(card, 8)
                    u.badge(
                        card,
                        if (r.attendance != null) "Sudah hadir" else statusLabel(r.status),
                    )
                    if (
                        r.status == "registered" &&
                            r.attendance == null &&
                            (Dates.parse(event.startsAt)?.time ?: 0) > System.currentTimeMillis()
                    )
                        u.button(card, "Batalkan peserta", true) {
                            a.confirm(
                                "Batalkan peserta?",
                                r.user?.name.orEmpty(),
                                "Batalkan peserta",
                            ) {
                                a.write(a.api.cancelParticipant(event.id, r.id)) { a.render() }
                            }
                        }
                }
                a.pagination(area, result) { load(it) }
            }
        }
        load(1)
    }

    fun tasks(p: LinearLayout, manage: Boolean) {
        if (manage) u.button(p, "Tambah tugas") { taskForm(p, null) }
        val selected = mutableSetOf<Long>()
        if (manage)
            u.button(p, "Arsipkan tugas terpilih", true) {
                if (selected.isEmpty()) a.notice("Pilih tugas terlebih dahulu.")
                else
                    a.confirm(
                        "Arsipkan tugas?",
                        "${selected.size} tugas akan diarsipkan.",
                        "Arsipkan",
                    ) {
                        a.write(a.api.deleteTasks(event.id, mapOf("ids" to selected.toList()))) {
                            a.render()
                        }
                    }
            }
        val area = u.column()
        p.addView(area)
        fun load(page: Int) {
            selected.clear()
            a.load(area, { a.api.tasks(event.id, page) }) { result ->
                if (result.data.isEmpty())
                    u.empty(
                        area,
                        "Belum ada tugas",
                        if (manage) "Bagikan tugas kepada peserta aktif."
                        else "Tugas yang ditugaskan kepadamu akan muncul di sini.",
                    )
                result.data.forEach { task ->
                    val card = u.panel(area)
                    if (manage)
                        card.addView(
                            CheckBox(a).apply {
                                text = "Pilih tugas"
                                setOnCheckedChangeListener { _, checked ->
                                    if (checked) selected.add(task.id) else selected.remove(task.id)
                                }
                            }
                        )
                    card.addView(u.text(task.title, 18, 700))
                    u.gap(card, 8)
                    u.badge(card, statusLabel(task.status))
                    u.gap(card, 8)
                    card.addView(u.text(task.description.orEmpty(), 14))
                    card.addView(
                        u.text(
                            "Penanggung jawab: ${task.assignee?.name ?: "Belum ditugaskan"}",
                            14,
                            400,
                            u.muted,
                        )
                    )
                    task.dueAt?.let {
                        card.addView(u.text("Batas: ${Dates.full(it)}", 13, 400, u.muted))
                    }
                    if (manage) {
                        u.button(card, "Edit tugas", true) { taskForm(p, task) }
                        u.button(card, "Arsipkan tugas", true) {
                            a.confirm("Arsipkan tugas?", task.title, "Arsipkan") {
                                a.write(a.api.deleteTask(event.id, task.id)) { a.render() }
                            }
                        }
                    } else if (task.assignedTo == a.state.user?.id) {
                        u.chips(
                            card,
                            listOf("Belum dikerjakan", "Sedang dikerjakan", "Selesai"),
                            statusLabel(task.status),
                        ) { label ->
                            val status =
                                when (label) {
                                    "Selesai" -> "completed"
                                    "Sedang dikerjakan" -> "in_progress"
                                    else -> "pending"
                                }
                            if (status != task.status)
                                a.write(
                                    a.api.taskStatus(event.id, task.id, mapOf("status" to status))
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

    private fun taskForm(parent: LinearLayout, task: TaskDto?) {
        // Render inline so field validation remains visible when the server rejects a save.
        parent.removeAllViews()
        parent.addView(u.text(if (task == null) "Tambah tugas" else "Edit tugas", 24, 700))
        u.gap(parent)
        val waiting = u.text("Memuat peserta aktif…")
        parent.addView(waiting)
        a.allPages(
            { a.api.participants(event.id, it) },
            failure = { error ->
                parent.removeView(waiting)
                u.empty(parent, "Peserta belum dapat dimuat", error.message)
                u.button(parent, "Kembali", true) { a.render() }
            },
        ) { registrations ->
            parent.removeView(waiting)
            val title = u.field(parent, "Judul tugas", task?.title.orEmpty())
            val desc =
                u.field(
                    parent,
                    "Deskripsi tugas",
                    task?.description.orEmpty(),
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                )
            val options =
                listOf("" to "Belum ditugaskan") +
                    registrations
                        .filter { it.status == "registered" }
                        .map { it.userId.toString() to (it.user?.name ?: "Relawan #${it.userId}") }
            val f = FormTools(a)
            val assignee =
                f.choice(
                    parent,
                    "Penanggung jawab",
                    options,
                    task?.assignedTo?.toString().orEmpty(),
                )
            val due = f.date(parent, "Batas tugas (opsional)", task?.dueAt.orEmpty())
            val noDue =
                CheckBox(a).apply {
                    text = "Tanpa batas waktu"
                    isChecked = task?.dueAt == null
                }
            parent.addView(noDue)
            val status =
                f.choice(
                    parent,
                    "Status tugas",
                    listOf(
                        "pending" to "Belum dikerjakan",
                        "in_progress" to "Sedang dikerjakan",
                        "completed" to "Selesai",
                    ),
                    task?.status ?: "pending",
                )
            u.button(parent, "Simpan tugas") {
                title.first.error =
                    if (title.second.text.toString().isBlank()) "Judul wajib diisi" else null
                due.box.error =
                    if (!noDue.isChecked && due.value.isBlank())
                        "Pilih batas waktu atau centang tanpa batas"
                    else null
                if (title.first.error != null || due.box.error != null) return@button
                val data =
                    mapOf<String, Any?>(
                        "title" to title.second.text.toString().trim(),
                        "description" to desc.second.text.toString().trim().ifEmpty { null },
                        "assigned_to" to assignee.value.toLongOrNull(),
                        "due_at" to if (noDue.isChecked) null else due.value,
                        "status" to status.value,
                    )
                a.write(
                    if (task == null) a.api.createTask(event.id, data)
                    else a.api.updateTask(event.id, task.id, data),
                    mapOf(
                        "title" to title.first,
                        "description" to desc.first,
                        "assigned_to" to assignee.box,
                        "due_at" to due.box,
                        "status" to status.box,
                    ),
                ) {
                    a.render()
                    a.notice("Tugas disimpan.")
                }
            }
            u.button(parent, "Batal", true) { a.render() }
        }
    }

    fun attendance(p: LinearLayout, manage: Boolean, registration: RegistrationDto?) {
        if (manage) {
            val cached = a.state.sessions[event.id]
            if (cached != null) {
                val card = u.panel(p)
                card.addView(u.text("Kode sesi terakhir", 14, 600))
                card.addView(
                    u.text(cached.code, 32, 700, u.primary).apply { setTextIsSelectable(true) }
                )
                card.addView(
                    u.text(
                        "Berlaku sampai ${Dates.full(cached.session?.expiresAt)}. Status akhir mengikuti server.",
                        13,
                        400,
                        u.muted,
                    )
                )
                cached.session?.let { session ->
                    u.button(card, "Tutup sesi absensi", true) {
                        a.write(a.api.closeSession(event.id, session.id)) {
                            a.state.sessions.remove(event.id)
                            a.render()
                            a.notice("Sesi ditutup.")
                        }
                    }
                }
            }
            if (event.ongoing()) {
                val duration = u.field(p, "Durasi sesi (menit)", "15", InputType.TYPE_CLASS_NUMBER)
                u.button(p, "Buka sesi absensi") {
                    val minutes = duration.second.text.toString().toIntOrNull()
                    duration.first.error =
                        if (minutes == null || minutes !in 1..120) "Gunakan 1–120 menit" else null
                    if (duration.first.error == null)
                        a.confirm(
                            "Buka sesi baru?",
                            "Sesi sebelumnya akan ditutup. Kode baru hanya ditampilkan sekali.",
                            "Buka sesi",
                        ) {
                            a.write(
                                a.api.openSession(event.id, mapOf("duration_minutes" to minutes)),
                                mapOf("duration_minutes" to duration.first),
                            ) { response ->
                                response.data?.let { a.state.sessions[event.id] = it }
                                a.render()
                            }
                        }
                }
            } else
                u.empty(
                    p,
                    "Absensi belum tersedia",
                    "Sesi hanya dapat dibuka saat kegiatan berlangsung dengan status terbuka atau ditutup.",
                )
            p.addView(
                u.text(
                    "Kode disimpan sementara di memori. Jika aplikasi ditutup, buka sesi baru untuk memperoleh kode baru.",
                    13,
                    400,
                    u.muted,
                )
            )
            u.section(p, "Kehadiran peserta")
            val area = u.column()
            p.addView(area)
            fun load(page: Int) {
                a.load(area, { a.api.attendances(event.id, page) }) { result ->
                    if (result.data.isEmpty())
                        u.empty(
                            area,
                            "Belum ada kehadiran",
                            "Peserta melakukan absensi menggunakan kode sesi.",
                        )
                    result.data.forEach { r ->
                        val card = u.panel(area)
                        card.addView(u.text(r.user?.name ?: "Relawan", 17, 700))
                        card.addView(
                            u.text(Dates.full(r.attendance?.checkedInAt), 14, 400, u.muted)
                        )
                    }
                    a.pagination(area, result) { load(it) }
                }
            }
            load(1)
        } else if (registration?.attendance != null) {
            u.empty(p, "Kehadiran tercatat", Dates.full(registration.attendance.checkedInAt))
        } else if (registration?.status == "registered" && event.ongoing()) {
            p.addView(u.text("Minta kode sesi aktif kepada koordinator.", 15, 400, u.muted))
            val code = u.field(p, "Kode absensi")
            code.second.filters = arrayOf(android.text.InputFilter.LengthFilter(8))
            u.button(p, "Konfirmasi kehadiran") {
                val value = code.second.text.toString().trim().uppercase(java.util.Locale.ROOT)
                code.first.error =
                    if (!Regex("[A-F0-9]{8}").matches(value))
                        "Masukkan 8 karakter kode dari koordinator"
                    else null
                if (code.first.error == null)
                    a.write(
                        a.api.attend(event.id, mapOf("code" to value)),
                        mapOf("code" to code.first),
                    ) {
                        a.render()
                        a.notice("Kehadiran berhasil dicatat.")
                    }
            }
        } else
            u.empty(
                p,
                "Absensi belum tersedia",
                "Absensi tersedia bagi peserta aktif saat kegiatan berlangsung.",
            )
    }

    fun photos(p: LinearLayout, manage: Boolean) {
        if (manage) {
            p.addView(u.text("JPEG, PNG, atau WebP, maksimal 5 MB.", 14, 400, u.muted))
            u.button(p, "Unggah dokumentasi") { a.pickPhoto() }
        }
        val area = u.column()
        p.addView(area)
        fun load(page: Int) {
            a.load(area, { a.api.photos(event.id, page) }) { result ->
                if (result.data.isEmpty())
                    u.empty(
                        area,
                        "Belum ada dokumentasi",
                        "Foto kegiatan yang diunggah koordinator akan muncul di sini.",
                    )
                result.data.forEach { photo ->
                    val card = u.panel(area)
                    val image =
                        ImageView(a).apply {
                            layoutParams = LinearLayout.LayoutParams(-1, u.dp(200))
                            contentDescription = photo.caption ?: "Dokumentasi kegiatan"
                        }
                    u.image(image, photo.imageUrl, com.example.relawanku.R.drawable.ic_image)
                    card.addView(image)
                    u.gap(card, 10)
                    card.addView(u.text(photo.caption ?: "Tanpa keterangan", 14))
                    if (manage) {
                        val caption = u.field(card, "Keterangan foto", photo.caption.orEmpty())
                        u.button(card, "Simpan keterangan", true) {
                            a.write(
                                a.api.caption(
                                    event.id,
                                    photo.id,
                                    mapOf(
                                        "caption" to
                                            caption.second.text.toString().trim().ifEmpty { null }
                                    ),
                                ),
                                mapOf("caption" to caption.first),
                            ) {
                                a.render()
                            }
                        }
                        u.button(card, "Arsipkan foto", true) {
                            a.confirm(
                                "Arsipkan foto?",
                                "Foto ini akan disembunyikan dari dokumentasi.",
                                "Arsipkan",
                            ) {
                                a.write(a.api.deletePhoto(event.id, photo.id)) { a.render() }
                            }
                        }
                    }
                }
                a.pagination(area, result) { load(it) }
            }
        }
        load(1)
    }
}
