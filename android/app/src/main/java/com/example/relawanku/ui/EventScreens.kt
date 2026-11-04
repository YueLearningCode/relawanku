package com.example.relawanku.ui

import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.*
import com.example.relawanku.MainActivity
import com.example.relawanku.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class EventScreens(private val a: MainActivity) {
    private val u = a.ui
    private val s = a.state

    fun listing() {
        val manager = s.role != Role.RELAWAN
        val mine = s.tab == "joined"
        val p =
            a.page(
                if (mine) "Kegiatanku" else if (manager) "Kelola kegiatan" else "Temukan kegiatan",
                if (mine) "Tempat semua langkah baikmu berkumpul."
                else if (manager) "Kelola setiap langkah, hadirkan dampak."
                else "Pilih kegiatan yang dekat dengan hatimu.",
            )
        if (manager) {
            u.button(p, "Tambah kegiatan") {
                s.selectedEvent = 0
                a.navigate("form")
            }
            u.gap(p, 16)
        }
        val (_, search) = u.field(p, "Cari nama atau lokasi", s.query, InputType.TYPE_CLASS_TEXT)
        val results = u.column()
        val chosen = mutableSetOf<Int>()
        var deleteButton: com.google.android.material.button.MaterialButton? = null
        fun showResults() {
            results.removeAllViews()
            val events =
                s.events.filter {
                    (!mine || it.id in s.joined) &&
                        (s.category == "Semua" || it.category == s.category) &&
                        (s.query.isBlank() ||
                            it.title.contains(s.query, true) ||
                            it.location.contains(s.query, true))
                }
            if (events.isEmpty())
                u.empty(
                    results,
                    if (mine) "Belum ada kegiatan di sini" else "Kegiatan belum ditemukan",
                    if (mine) "Jelajahi kegiatan dan mulai langkah pertamamu."
                    else "Coba kata kunci lain atau ubah kategori.",
                )
            for (event in events) {
                if (manager)
                    results.addView(
                        CheckBox(a).apply {
                            text = "Pilih kegiatan ini"
                            typeface = u.font(600)
                            setTextColor(u.muted)
                            isChecked = event.id in chosen
                            setOnCheckedChangeListener { _, checked ->
                                if (checked) chosen.add(event.id) else chosen.remove(event.id)
                                deleteButton?.isEnabled = chosen.isNotEmpty()
                                deleteButton?.text = "Arsipkan pilihan (${chosen.size})"
                            }
                        }
                    )
                u.eventCard(results, event, event.id in s.joined) { a.openEvent(event.id) }
            }
            if (mine && events.isEmpty())
                u.button(results, "Jelajahi kegiatan", true) { a.selectTab("explore") }
        }
        u.chips(p, listOf("Semua", "Lingkungan", "Pendidikan", "Sosial"), s.category) {
            s.category = it
            showResults()
        }
        if (manager) {
            deleteButton =
                u.button(p, "Arsipkan pilihan (0)", true) {
                    a.confirm(
                        "Arsipkan ${chosen.size} kegiatan?",
                        "Kegiatan pilihan akan dihapus dari daftar contoh lokal.",
                        "Arsipkan",
                    ) {
                        s.archive(chosen)
                        a.render()
                        a.notice("Kegiatan pilihan diarsipkan dalam pratinjau.")
                    }
                }
            deleteButton!!.isEnabled = false
            u.gap(p, 12)
        }
        val stateRow = u.row()
        stateRow.addView(
            u.text(
                "${if(mine)"KEGIATAN YANG DIIKUTI" else "KEGIATAN TERSEDIA"}",
                12,
                700,
                u.primary,
            ),
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        stateRow.addView(
            u.iconAction(R.drawable.ic_filter, "Pratinjau kondisi daftar") {
                MaterialAlertDialogBuilder(a)
                    .setTitle("Pratinjau kondisi daftar")
                    .setItems(arrayOf("Data tersedia", "Loading", "Data kosong", "Gagal memuat")) {
                        _,
                        index ->
                        if (index == 0) showResults()
                        else {
                            results.removeAllViews()
                            when (index) {
                                1 -> {
                                    results.addView(ProgressBar(a))
                                    u.gap(results)
                                    results.addView(
                                        u.text("Sedang memuat kegiatan…", 14, 400, u.muted)
                                    )
                                }
                                2 ->
                                    u.empty(
                                        results,
                                        "Belum ada kegiatan",
                                        "Kegiatan baru akan muncul di sini.",
                                    )
                                else ->
                                    u.empty(
                                        results,
                                        "Kegiatan belum bisa dimuat",
                                        "Contoh tampilan saat koneksi bermasalah. Coba kembali.",
                                    )
                            }
                            u.button(results, "Coba lagi", true) { showResults() }
                        }
                    }
                    .show()
            }
        )
        p.addView(stateRow)
        u.gap(p, 16)
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
                    showResults()
                }

                override fun afterTextChanged(e: Editable?) {}
            }
        )
        showResults()
    }

    fun detail() {
        val e =
            s.event()
                ?: run {
                    a.navigate("main")
                    return
                }
        val manager = s.role != Role.RELAWAN
        val p = a.page(back = true)
        val image =
            ImageView(a).apply {
                setImageResource(e.image)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "Ilustrasi ${e.category}"
                background = u.background(u.color(R.color.sage), 22)
                clipToOutline = true
            }
        p.addView(image, LinearLayout.LayoutParams(-1, u.dp(210)))
        u.gap(p, 20)
        u.badge(p, e.category)
        u.gap(p, 12)
        p.addView(u.text(e.title, 28, 700))
        u.gap(p, 10)
        p.addView(u.text("oleh Komunitas Relawanku", 14, 400, u.muted))
        u.gap(p, 20)
        val meta = u.panel(p)
        u.meta(meta, R.drawable.ic_calendar, e.date, e.time)
        u.meta(meta, R.drawable.ic_pin, e.location, "Lokasi kegiatan")
        u.meta(
            meta,
            R.drawable.ic_people,
            "${e.participants} dari ${e.quota} relawan",
            "${(e.quota-e.participants).coerceAtLeast(0)} tempat tersisa",
        )
        val tabs =
            if (manager) listOf("Ringkasan", "Peserta", "Tugas", "Absensi", "Dokumentasi")
            else if (e.id in s.joined) listOf("Ringkasan", "Tugas", "Absensi", "Dokumentasi")
            else listOf("Ringkasan")
        u.chips(p, tabs, s.detailTab) {
            s.detailTab = it
            a.render()
        }
        when (s.detailTab) {
            "Peserta" -> participants(p, e)
            "Tugas" -> tasks(p, e, manager)
            "Absensi" -> attendance(p, e, manager)
            "Dokumentasi" -> documentations(p, e, manager)
            else -> {
                u.section(p, "Tentang kegiatan")
                p.addView(u.text(e.description))
                u.section(p, "Yang perlu disiapkan")
                p.addView(
                    u.text(
                        "Bawa botol minum pribadi, datang tepat waktu, dan ikuti arahan koordinator.",
                        16,
                        400,
                        u.color(R.color.body),
                    )
                )
                u.gap(p, 20)
                if (manager) {
                    u.button(p, "Edit kegiatan") { a.navigate("form") }
                    u.button(
                        p,
                        if (e.completed) "Buka kembali kegiatan" else "Tandai kegiatan selesai",
                        true,
                    ) {
                        e.completed = !e.completed
                        a.render()
                        a.notice("Status contoh diperbarui.")
                    }
                    u.button(p, "Arsipkan kegiatan", true) {
                        a.confirm(
                            "Arsipkan kegiatan?",
                            e.title + " akan dihapus dari daftar contoh.",
                            "Arsipkan",
                        ) {
                            s.archive(setOf(e.id))
                            a.navigate("main")
                        }
                    }
                } else if (e.id in s.joined) {
                    u.badge(
                        p,
                        "Kamu sudah terdaftar",
                        u.color(R.color.success_bg),
                        u.color(R.color.success),
                    )
                    u.button(p, "Lihat tugas saya") {
                        s.detailTab = "Tugas"
                        a.render()
                    }
                    if (e.id !in s.attended && !e.completed)
                        u.button(p, "Batalkan pendaftaran", true) {
                            a.confirm(
                                "Batalkan pendaftaran?",
                                "Tempatmu pada kegiatan ini akan tersedia untuk relawan lain.",
                                "Batalkan pendaftaran",
                            ) {
                                s.leave(e)
                                a.render()
                                a.notice("Pendaftaran contoh dibatalkan.")
                            }
                        }
                } else {
                    val b =
                        u.button(
                            p,
                            when {
                                e.completed -> "Kegiatan selesai"
                                e.participants >= e.quota -> "Kuota penuh"
                                else -> "Daftar kegiatan"
                            },
                        ) {
                            a.confirm(
                                "Siap ikut berkontribusi?",
                                "Daftar sebagai relawan pada ${e.title}.",
                                "Ya, daftar",
                            ) {
                                if (s.join(e)) {
                                    a.render()
                                    a.notice("Kamu berhasil terdaftar pada kegiatan contoh.")
                                } else a.notice("Pendaftaran tidak tersedia.")
                            }
                        }
                    b.isEnabled = !e.completed && e.participants < e.quota
                }
            }
        }
        u.gap(p, 18)
        p.addView(u.text("Data contoh · perubahan hanya untuk pratinjau UI", 12, 400, u.muted))
    }

    private fun participants(p: LinearLayout, e: VolunteerEvent) {
        u.section(p, "Peserta kegiatan")
        p.addView(u.text("Contoh tampilan daftar peserta dan status kehadiran.", 14, 400, u.muted))
        u.gap(p)
        listOf("Rasyid Saputra", "Rahma Azka", "Nurul").forEachIndexed { i, name ->
            val c = u.panel(p)
            u.meta(
                c,
                R.drawable.ic_person,
                name,
                if (i == 0 && e.id in s.attended) "Sudah hadir" else "Terdaftar",
            )
            u.button(c, "Lihat detail peserta", true) {
                MaterialAlertDialogBuilder(a)
                    .setTitle(name)
                    .setMessage("Relawan contoh pada kegiatan ${e.title}.\n\nStatus: terdaftar")
                    .setPositiveButton("Tutup", null)
                    .show()
            }
        }
    }

    private fun tasks(p: LinearLayout, e: VolunteerEvent, manager: Boolean) {
        u.section(p, if (manager) "Pembagian tugas" else "Tugas untukmu")
        val task = s.tasks[e.id]
        if (task == null)
            u.empty(p, "Belum ada tugas", "Koordinator akan membagikan tugas kegiatan di sini.")
        else {
            val c = u.panel(p)
            u.badge(c, if (e.id in s.finishedTasks) "Selesai" else "Perlu dikerjakan")
            u.gap(c)
            c.addView(u.text(task, 18, 700))
            u.gap(c, 8)
            c.addView(u.text("Penanggung jawab: ${s.displayName}", 14, 400, u.muted))
            if (!manager)
                u.button(
                    c,
                    if (e.id in s.finishedTasks) "Tandai belum selesai" else "Tandai selesai",
                ) {
                    if (e.id in s.finishedTasks) s.finishedTasks.remove(e.id)
                    else s.finishedTasks.add(e.id)
                    s.history.add(0, "Memperbarui status tugas ${e.title}")
                    a.render()
                    a.notice("Status tugas contoh diperbarui.")
                }
        }
        if (manager) {
            u.button(p, if (task == null) "Bagikan tugas" else "Edit pembagian tugas") {
                val form = u.column(24)
                val (box, input) = u.field(form, "Nama tugas", task ?: "")
                form.addView(
                    u.text(
                        "Diberikan kepada ${s.displayName}, peserta contoh pada kegiatan ini.",
                        14,
                        400,
                        u.muted,
                    )
                )
                val dialog =
                    MaterialAlertDialogBuilder(a)
                        .setTitle("Bagikan tugas contoh")
                        .setView(form)
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Simpan", null)
                        .create()
                dialog.setOnShowListener {
                    dialog.getButton(-1).setOnClickListener {
                        val value = input.text.toString().trim()
                        box.error = if (value.isBlank()) "Nama tugas wajib diisi" else null
                        if (value.isNotBlank()) {
                            s.tasks[e.id] = value
                            dialog.dismiss()
                            a.render()
                            a.notice("Tugas contoh disimpan.")
                        }
                    }
                }
                dialog.show()
            }
            if (task != null)
                u.button(p, "Hapus tugas", true) {
                    a.confirm("Hapus tugas?", "Tugas ini akan dihapus dari data contoh.", "Hapus") {
                        s.tasks.remove(e.id)
                        s.finishedTasks.remove(e.id)
                        a.render()
                    }
                }
        }
    }

    private fun attendance(p: LinearLayout, e: VolunteerEvent, manager: Boolean) {
        u.section(p, "Absensi kegiatan")
        if (manager) {
            val c = u.panel(p, u.color(R.color.soft))
            c.addView(u.icon(R.drawable.ic_check, 36))
            u.gap(c)
            c.addView(
                u.text(
                    if (e.id in s.sessions) "Sesi contoh dibuka" else "Mulai sesi absensi",
                    20,
                    700,
                )
            )
            u.gap(c, 8)
            c.addView(
                u.text(
                    if (e.id in s.sessions)
                        "Kode contoh: RW2026\nBagikan kode kepada peserta kegiatan."
                    else "Buka sesi untuk melihat contoh kode yang dibagikan ke relawan.",
                    16,
                    400,
                    u.muted,
                )
            )
            u.button(p, if (e.id in s.sessions) "Tutup sesi contoh" else "Buka sesi contoh") {
                if (e.id in s.sessions) s.sessions.remove(e.id) else s.sessions.add(e.id)
                a.render()
            }
            u.section(p, "Rekap kehadiran")
            u.meta(
                p,
                R.drawable.ic_people,
                if (e.id in s.attended) "1 peserta contoh hadir" else "Belum ada kehadiran contoh",
                if (e.id in s.attended) s.displayName else "Kehadiran muncul setelah absensi.",
            )
        } else if (e.id in s.attended) {
            val c = u.panel(p, u.color(R.color.success_bg))
            c.addView(u.icon(R.drawable.ic_check, 40))
            u.gap(c)
            c.addView(u.text("Kehadiranmu tercatat!", 20, 700))
            u.gap(c, 8)
            c.addView(u.text("Terima kasih sudah hadir dan ambil bagian.", 16, 400, u.muted))
        } else {
            p.addView(
                u.text(
                    "Masukkan kode yang dibagikan koordinator. Untuk mencoba UI, gunakan kode RW2026.",
                    16,
                    400,
                    u.muted,
                )
            )
            u.gap(p)
            val (box, code) =
                u.field(
                    p,
                    "Kode absensi",
                    type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS,
                )
            u.button(p, "Konfirmasi kehadiran") {
                box.error =
                    if (code.text.toString().trim().uppercase() != "RW2026")
                        "Kode contoh belum sesuai. Gunakan RW2026."
                    else null
                if (box.error == null) {
                    s.attended.add(e.id)
                    s.history.add(0, "Melakukan absensi ${e.title}")
                    a.render()
                    a.notice("Absensi contoh berhasil.")
                }
            }
        }
    }

    private fun documentations(p: LinearLayout, e: VolunteerEvent, manager: Boolean) {
        u.section(p, "Momen kebaikan")
        p.addView(u.text("Dokumentasi yang mengabadikan langkah bersama.", 14, 400, u.muted))
        u.gap(p)
        val images = s.photos[e.id].orEmpty()
        if (images.isEmpty())
            u.empty(p, "Belum ada dokumentasi", "Foto kegiatan akan ditampilkan di sini.")
        images.forEach { uri ->
            val c = u.panel(p)
            val image =
                ImageView(a).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "Dokumentasi kegiatan"
                }
            try {
                image.setImageURI(Uri.parse(uri))
            } catch (_: SecurityException) {
                image.setImageResource(e.image)
            }
            c.addView(image, LinearLayout.LayoutParams(-1, u.dp(190)))
            u.gap(c, 10)
            c.addView(u.text("Momen ${e.title}", 14, 600))
            if (manager)
                u.button(c, "Hapus foto contoh", true) {
                    a.confirm(
                        "Hapus dokumentasi?",
                        "Foto di perangkatmu tetap tersimpan.",
                        "Hapus dari pratinjau",
                    ) {
                        s.photos[e.id]?.remove(uri)
                        a.render()
                    }
                }
        }
        if (manager) u.button(p, "Pilih foto dokumentasi") { a.pickPhoto() }
    }

    fun form() {
        val original = s.event()
        val fresh = s.selectedEvent == 0
        val p =
            a.page(
                if (fresh) "Buat kegiatan" else "Edit kegiatan",
                "Rangkai kegiatan yang menggerakkan kebaikan.",
                true,
            )
        val (titleBox, title) =
            u.field(p, "Nama kegiatan", if (fresh) "" else original?.title.orEmpty())
        val (descBox, desc) =
            u.field(
                p,
                "Deskripsi",
                if (fresh) "" else original?.description.orEmpty(),
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            )
        val (locationBox, location) =
            u.field(p, "Lokasi", if (fresh) "" else original?.location.orEmpty())
        val (quotaBox, quota) =
            u.field(
                p,
                "Kuota relawan",
                if (fresh) "" else original?.quota?.toString().orEmpty(),
                InputType.TYPE_CLASS_NUMBER,
            )
        val (dateBox, date) =
            u.field(
                p,
                "Tanggal kegiatan",
                if (fresh) "Sabtu, 28 November 2026" else original?.date.orEmpty(),
            )
        val (timeBox, time) =
            u.field(
                p,
                "Waktu kegiatan",
                if (fresh) "08.00 – 12.00 WIB" else original?.time.orEmpty(),
            )
        var category = if (fresh) "Lingkungan" else original?.category ?: "Lingkungan"
        p.addView(u.text("Kategori", 14, 600))
        u.chips(p, listOf("Lingkungan", "Pendidikan", "Sosial"), category) { category = it }
        u.button(p, "Simpan kegiatan") {
            val t = title.text.toString().trim()
            val d = desc.text.toString().trim()
            val l = location.text.toString().trim()
            val q = quota.text.toString().toIntOrNull()
            titleBox.error = if (t.isBlank()) "Nama kegiatan wajib diisi" else null
            descBox.error = if (d.isBlank()) "Deskripsi wajib diisi" else null
            locationBox.error = if (l.isBlank()) "Lokasi wajib diisi" else null
            quotaBox.error =
                when {
                    q == null || q < 1 -> "Kuota minimal 1 relawan"
                    !fresh && q < (original?.participants ?: 0) ->
                        "Kuota tidak boleh kurang dari peserta terdaftar"
                    else -> null
                }
            dateBox.error = if (date.text.toString().isBlank()) "Tanggal wajib diisi" else null
            timeBox.error = if (time.text.toString().isBlank()) "Waktu wajib diisi" else null
            if (
                listOf(titleBox, descBox, locationBox, quotaBox, dateBox, timeBox).all {
                    it.error == null
                }
            ) {
                if (fresh) {
                    val id = (s.events.maxOfOrNull { it.id } ?: 0) + 1
                    s.events.add(
                        0,
                        VolunteerEvent(
                            id,
                            t,
                            category,
                            l,
                            q!!,
                            0,
                            d,
                            date.text.toString(),
                            time.text.toString(),
                        ),
                    )
                    s.selectedEvent = id
                } else
                    original?.apply {
                        this.title = t
                        this.description = d
                        this.location = l
                        this.quota = q!!
                        this.category = category
                        this.date = date.text.toString()
                        this.time = time.text.toString()
                    }
                s.detailTab = "Ringkasan"
                s.history.add(0, "Menyimpan kegiatan $t")
                a.navigate("detail")
                a.notice("Kegiatan contoh disimpan di sesi pratinjau.")
            }
        }
        u.button(p, "Batal", true) { a.navigate(if (fresh) "main" else "detail") }
    }
}
