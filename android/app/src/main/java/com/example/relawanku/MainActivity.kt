package com.example.relawanku

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.relawanku.ui.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    lateinit var state: PreviewState
    lateinit var ui: UiKit
    private lateinit var host: FrameLayout
    private lateinit var navigation: BottomNavigationView
    private val handler = Handler(Looper.getMainLooper())
    private var rendering = false
    private var navigationRole: Role? = null
    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                    state.photos
                        .getOrPut(state.pendingPhotoEvent) { mutableListOf() }
                        .add(uri.toString())
                    state.history.add(0, "Menambahkan dokumentasi contoh lokal")
                    render()
                    notice("Foto ditambahkan ke pratinjau lokal.")
                } catch (_: SecurityException) {
                    notice("Foto tidak dapat dibaca. Pilih foto lain.")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ui = UiKit(this)
        state = ViewModelProvider(this)[PreviewState::class.java]
        host = findViewById(R.id.screen_host)
        navigation = findViewById(R.id.bottom_navigation)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        navigation.itemIconTintList =
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ui.primary, ui.muted),
            )
        navigation.itemTextColor = navigation.itemIconTintList
        navigation.itemActiveIndicatorColor = ColorStateList.valueOf(ui.color(R.color.sage))
        navigation.backgroundTintList = ColorStateList.valueOf(ui.color(R.color.surface))
        navigation.elevation = 0f
        navigation.setOnItemSelectedListener { item ->
            if (!rendering) {
                state.tab =
                    when (item.itemId) {
                        11 -> "explore"
                        12 -> "joined"
                        13 -> "profile"
                        14 -> "users"
                        else -> "home"
                    }
                state.screen = "main"
                state.query = ""
                state.category = "Semua"
                render()
            }
            true
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        state.screen == "register" -> navigate("login")
                        state.screen in listOf("detail", "form", "history", "userform") ->
                            navigate("main")

                        state.screen == "main" && state.tab != "home" -> selectTab("home")
                        else -> {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            },
        )
        if (savedInstanceState == null) state.screen = "splash1"
        render()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    fun navigate(screen: String) {
        state.screen = screen
        render()
    }

    fun selectTab(tab: String) {
        state.tab = tab
        state.screen = "main"
        state.query = ""
        state.category = "Semua"
        render()
    }

    fun openEvent(id: Int) {
        state.selectedEvent = id
        state.detailTab = "Ringkasan"
        navigate("detail")
    }

    fun notice(message: String) {
        Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_LONG).show()
    }

    fun confirm(
        title: String,
        message: String,
        label: String = "Ya, lanjutkan",
        action: () -> Unit,
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Batal", null)
            .setPositiveButton(label) { _, _ -> action() }
            .show()
    }

    fun pickPhoto() {
        state.pendingPhotoEvent = state.selectedEvent
        photoPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
    }

    fun page(title: String? = null, subtitle: String? = null, back: Boolean = false): LinearLayout {
        val view = layoutInflater.inflate(R.layout.view_page, host, false)
        host.addView(view)
        val content = view.findViewById<LinearLayout>(R.id.page_content)
        // Limit line lengths on larger displays without shrinking text.
        view.post {
            val max = ui.dp(640)
            if (view.width > max) {
                val side = (view.width - max) / 2 + ui.dp(24)
                content.setPadding(side, ui.dp(20), side, ui.dp(32))
            }
        }
        if (back) {
            val row = ui.row()
            row.addView(ui.iconAction(R.drawable.ic_back, "Kembali") { navigate("main") })
            content.addView(row)
            ui.gap(content)
        }
        if (title != null) {
            content.addView(ui.text(title, 24, 700))
            ui.gap(content, 6)
        }
        if (subtitle != null) {
            content.addView(ui.text(subtitle, 14, 400, ui.muted))
            ui.gap(content, 20)
        }
        return content
    }

    private fun configureNavigation() {
        rendering = true
        if (navigationRole != state.role) {
            navigation.menu.clear()
            val manager = state.role != Role.RELAWAN
            navigation.menu
                .add(0, 10, 0, if (manager) "Dashboard" else "Beranda")
                .setIcon(if (manager) R.drawable.ic_chart else R.drawable.ic_home)
            if (state.role == Role.ADMIN)
                navigation.menu.add(0, 14, 1, "Pengguna").setIcon(R.drawable.ic_people)
            navigation.menu
                .add(0, 11, 2, if (manager) "Kegiatan" else "Jelajahi")
                .setIcon(if (manager) R.drawable.ic_calendar else R.drawable.ic_compass)
            if (!manager)
                navigation.menu.add(0, 12, 3, "Kegiatanku").setIcon(R.drawable.ic_calendar)
            navigation.menu.add(0, 13, 4, "Profil").setIcon(R.drawable.ic_person)
            navigationRole = state.role
        }
        navigation.selectedItemId =
            when (state.tab) {
                "explore" -> 11
                "joined" -> 12
                "profile" -> 13
                "users" -> 14
                else -> 10
            }
        rendering = false
    }

    fun render() {
        handler.removeCallbacksAndMessages(null)
        host.removeAllViews()
        navigation.visibility = if (state.screen == "main") View.VISIBLE else View.GONE
        when (state.screen) {
            "splash1",
            "splash2" -> splash()

            "login",
            "register" -> AuthScreens(this).render()

            "detail" -> EventScreens(this).detail()
            "form" -> EventScreens(this).form()
            "userform" -> ManagementScreens(this).userForm()
            "history" -> history()
            else -> {
                configureNavigation()
                when (state.tab) {
                    "profile" -> profile()
                    "explore",
                    "joined" -> EventScreens(this).listing()

                    "users" -> ManagementScreens(this).users()
                    else ->
                        if (state.role == Role.RELAWAN) VolunteerHome(this).render()
                        else ManagementScreens(this).dashboard()
                }
            }
        }
    }

    private fun splash() {
        val first = state.screen == "splash1"
        val panel =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(ui.dp(32), ui.dp(24), ui.dp(32), ui.dp(24))
                setBackgroundColor(ui.color(if (first) R.color.primary else R.color.background))
            }
        host.addView(panel, FrameLayout.LayoutParams(-1, -1))
        val logo =
            ui.icon(
                if (first) R.drawable.ic_leaf else R.drawable.ic_heart,
                88,
                ui.color(if (first) R.color.sage else R.color.primary),
            )
        panel.addView(logo)
        ui.gap(panel, 24)
        panel.addView(
            ui.text(
                if (first) "Relawanku" else "Langkah kecil,\ndampak berarti.",
                if (first) 36 else 28,
                700,
                ui.color(if (first) R.color.surface else R.color.ink),
            )
                .apply { gravity = Gravity.CENTER }
        )
        ui.gap(panel, 14)
        panel.addView(
            ui.text(
                if (first) "Temukan kegiatan. Ambil peran."
                else "Bersama, kita hadirkan perubahan.",
                16,
                400,
                ui.color(if (first) R.color.sage else R.color.muted),
            )
                .apply { gravity = Gravity.CENTER }
        )
        logo.alpha = 0f
        logo.scaleX = .7f
        logo.scaleY = .7f
        logo
            .animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .rotation(if (first) 0f else 12f)
            .setDuration(650)
            .start()
        handler.postDelayed(
            {
                if (!isFinishing) {
                    state.screen = if (first) "splash2" else "login"
                    render()
                }
            },
            if (first) 1000L else 1300L,
        )
    }

    private fun profile() {
        val p = page("Profil", "Ruang kecil untuk mengenal kontribusimu.")
        val top = ui.panel(p, ui.color(R.color.sage), 24)
        top.addView(ui.icon(R.drawable.ic_person, 48))
        ui.gap(top)
        top.addView(ui.text(state.displayName, 24, 700))
        top.addView(ui.text(state.email, 14, 400, ui.muted))
        ui.gap(top)
        ui.badge(top, state.role.label)
        val summary = ui.panel(p)
        ui.meta(
            summary,
            R.drawable.ic_calendar,
            "${state.joined.size} kegiatan diikuti",
            "Setiap langkah punya arti.",
        )
        ui.meta(
            summary,
            R.drawable.ic_check,
            "${state.attended.size} kehadiran tercatat",
            "Terima kasih sudah hadir.",
        )
        ui.button(p, "Edit profil", true) {
            val form = ui.column(24)
            val (nameBox, name) = ui.field(form, "Nama lengkap", state.displayName)
            val (emailBox, email) =
                ui.field(
                    form,
                    "Email",
                    state.email,
                    android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                )
            val dialog =
                MaterialAlertDialogBuilder(this)
                    .setTitle("Edit profil contoh")
                    .setView(form)
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Simpan", null)
                    .create()
            dialog.setOnShowListener {
                dialog.getButton(-1).setOnClickListener {
                    val n = name.text.toString().trim()
                    val e = email.text.toString().trim()
                    nameBox.error = if (n.isBlank()) "Nama wajib diisi" else null
                    emailBox.error =
                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches())
                            "Email belum valid"
                        else null
                    if (nameBox.error == null && emailBox.error == null) {
                        state.displayName = n
                        state.email = e
                        dialog.dismiss()
                        render()
                        notice("Profil contoh diperbarui.")
                    }
                }
            }
            dialog.show()
        }
        ui.button(p, "Riwayat aktivitas", true) { navigate("history") }
        ui.gap(p, 24)
        p.addView(ui.text("PRATINJAU ANTARMUKA", 12, 700, ui.primary))
        ui.gap(p, 6)
        p.addView(
            ui.text(
                "Data pada tahap ini adalah contoh lokal. Perubahan belum dikirim ke server Laravel.",
                14,
                400,
                ui.muted,
            )
        )
        ui.button(p, "Ganti peran pratinjau", true) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Lihat sebagai")
                .setItems(Role.entries.map { it.label }.toTypedArray()) { _, i ->
                    state.role = Role.entries[i]
                    state.tab = "home"
                    render()
                }
                .show()
        }
        ui.button(p, "Keluar") {
            confirm(
                "Keluar dari pratinjau?",
                "Kamu bisa masuk kembali dan memilih peran lain.",
                "Keluar",
            ) {
                state.screen = "login"
                state.tab = "home"
                render()
            }
        }
    }

    private fun history() {
        val p = page("Riwayat aktivitas", "Jejak kontribusi dan aktivitas contohmu.", true)
        if (state.history.isEmpty())
            ui.empty(
                p,
                "Belum ada aktivitas",
                "Aktivitas akan muncul setelah kamu mengikuti kegiatan.",
            )
        else
            state.history.forEach {
                val c = ui.panel(p)
                c.addView(ui.text(it, 16, 600))
                ui.gap(c, 8)
                c.addView(ui.text("Sesi pratinjau ini", 12, 400, ui.muted))
            }
    }
}
