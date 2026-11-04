package com.example.relawanku.ui

import androidx.lifecycle.ViewModel
import com.example.relawanku.R

enum class Role(val label: String) {
    RELAWAN("Relawan"),
    KOORDINATOR("Koordinator"),
    ADMIN("Admin"),
}

data class VolunteerEvent(
    val id: Int,
    var title: String,
    var category: String,
    var location: String,
    var quota: Int,
    var participants: Int,
    var description: String,
    var date: String = "Sabtu, 14 November 2026",
    var time: String = "08.00 – 12.00 WIB",
    val image: Int = R.drawable.activity_coast,
    var completed: Boolean = false,
)

data class PreviewUser(
    val id: Int,
    var name: String,
    var email: String,
    var role: Role,
    var active: Boolean = true,
)

/** Local preview state. This class never authenticates or writes to the Laravel API. */
class PreviewState : ViewModel() {
    var role = Role.RELAWAN
    var selectedRole = Role.RELAWAN
    var screen = "login"
    var tab = "home"
    var pendingPhotoEvent = 0
    var selectedEvent = 1
    var detailTab = "Ringkasan"
    var displayName = "Rasyid Saputra"
    var email = "rasyid@contoh.id"
    var query = ""
    var category = "Semua"
    var events =
        mutableListOf(
            VolunteerEvent(
                1,
                "Bersih pantai, jaga masa depan",
                "Lingkungan",
                "Pantai Panjang, Bengkulu",
                30,
                18,
                "Bersama kita menjaga pesisir tetap bersih. Bergabunglah untuk mengumpulkan dan memilah sampah, sekaligus mengajak masyarakat lebih peduli pada laut.",
            ),
            VolunteerEvent(
                2,
                "Berbagi ilmu, tumbuhkan mimpi",
                "Pendidikan",
                "Rumah Belajar, Bengkulu",
                20,
                12,
                "Temani adik-adik belajar membaca dan berhitung melalui kegiatan kreatif. Tidak perlu pengalaman mengajar, cukup semangat untuk berbagi.",
                "Minggu, 15 November 2026",
                "09.00 – 11.30 WIB",
                R.drawable.activity_learning,
            ),
            VolunteerEvent(
                3,
                "Satu pohon, sejuta harapan",
                "Lingkungan",
                "Taman Hutan Raya, Bengkulu",
                25,
                25,
                "Tanam bibit dan rawat ruang hijau bersama komunitas. Kenali cara menanam yang baik serta langkah sederhana menjaga lingkungan.",
                "Sabtu, 21 November 2026",
                "07.30 – 11.00 WIB",
                R.drawable.activity_forest,
            ),
            VolunteerEvent(
                4,
                "Dapur berbagi untuk sesama",
                "Sosial",
                "Dapur Komunitas, Bengkulu",
                15,
                9,
                "Siapkan paket makanan bersama relawan untuk dibagikan kepada masyarakat. Mari hadirkan kepedulian melalui satu porsi kebaikan.",
                "Minggu, 22 November 2026",
                "08.00 – 12.00 WIB",
                R.drawable.activity_learning,
            ),
        )
    val joined = mutableSetOf(2)
    val attended = mutableSetOf<Int>()
    val finishedTasks = mutableSetOf<Int>()
    val tasks =
        mutableMapOf(
            1 to "Menyiapkan kantong sampah dan sarung tangan",
            2 to "Menyiapkan alat tulis untuk adik-adik",
        )
    val sessions = mutableSetOf<Int>()
    val photos = mutableMapOf<Int, MutableList<String>>()
    val history = mutableListOf("Bergabung di kegiatan Berbagi ilmu, tumbuhkan mimpi")
    val users =
        mutableListOf(
            PreviewUser(1, "Rasyid Saputra", "rasyid@contoh.id", Role.RELAWAN),
            PreviewUser(2, "Gosyen", "gosyen@contoh.id", Role.KOORDINATOR),
            PreviewUser(3, "Rahma Azka", "rahma@contoh.id", Role.RELAWAN),
            PreviewUser(4, "Nurul", "nurul@contoh.id", Role.RELAWAN),
        )

    fun event() = events.firstOrNull { it.id == selectedEvent }

    fun join(event: VolunteerEvent): Boolean {
        if (event.id in joined || event.participants >= event.quota || event.completed) return false
        joined.add(event.id)
        event.participants++
        history.add(0, "Mendaftar ${event.title}")
        return true
    }

    fun leave(event: VolunteerEvent) {
        joined.remove(event.id)
        event.participants--
        history.add(0, "Membatalkan pendaftaran ${event.title}")
    }

    fun archive(ids: Set<Int>) {
        events.removeAll { it.id in ids }
        joined.removeAll(ids)
        history.add(0, "Mengarsipkan ${ids.size} kegiatan")
    }
}
