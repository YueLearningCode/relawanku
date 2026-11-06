package com.example.relawanku

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
import com.example.relawanku.data.*
import com.example.relawanku.ui.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.Executors
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {
    lateinit var state: AppState
    lateinit var ui: UiKit
    lateinit var session: SessionStore
    lateinit var api: ApiService
    private lateinit var host: FrameLayout
    private lateinit var navigation: BottomNavigationView
    private val handler = Handler(Looper.getMainLooper())
    private val uploadExecutor = Executors.newSingleThreadExecutor()
    private val calls = mutableSetOf<Call<*>>()
    private var generation = 0
    private var rendering = false
    private var navigationRole: Role? = null
    private var overlay: View? = null
    private var mutating = false
    private var feedback: Snackbar? = null
    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val event = state.pendingPhotoEvent
                val cover = state.uploadCover
                val epoch = generation
                block("Menyiapkan foto…")
                uploadExecutor.execute {
                    try {
                        val part = UploadPart.fromUri(applicationContext, uri)
                        runOnUiThread {
                            unblock()
                            if (isDestroyed || epoch != generation) {
                                part.file.delete()
                                return@runOnUiThread
                            }
                            val call =
                                if (cover) api.cover(event, part.part)
                                else api.uploadPhoto(event, part.part, null)
                            write(call, cleanup = { part.file.delete() }) {
                                render()
                                notice(
                                    if (cover) "Foto sampul diperbarui."
                                    else "Dokumentasi berhasil diunggah."
                                )
                            }
                        }
                    } catch (error: Exception) {
                        runOnUiThread {
                            unblock()
                            if (!isDestroyed) notice(error.message ?: "Foto belum dapat dibaca.")
                        }
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ui = UiKit(this)
        state = ViewModelProvider(this)[AppState::class.java]
        session = SessionStore(this)
        api = ApiClient.create(session.baseUrl) { session.token }
        host = findViewById(R.id.screen_host)
        navigation = findViewById(R.id.bottom_navigation)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
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
            if (!rendering && !mutating)
                selectTab(
                    when (item.itemId) {
                        11 -> "explore"
                        12 -> "joined"
                        13 -> "profile"
                        14 -> "users"
                        else -> "home"
                    }
                )
            !mutating
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        mutating -> notice("Tunggu sampai proses selesai.")
                        state.screen == "login" || state.screen == "register" -> navigate("welcome")
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
        else if (
            state.user == null &&
                session.token != null &&
                state.screen !in listOf("splash1", "splash2", "welcome", "login", "register")
        )
            state.screen = "restore"
        render()
    }

    override fun onDestroy() {
        generation++
        calls.toList().forEach { it.cancel() }
        calls.clear()
        handler.removeCallbacksAndMessages(null)
        uploadExecutor.shutdownNow()
        super.onDestroy()
    }

    fun navigate(screen: String) {
        state.screen = screen
        render()
    }

    fun selectTab(tab: String) {
        state.tab = tab
        state.query = ""
        state.category = "Semua"
        navigate("main")
    }

    fun openEvent(id: Long) {
        state.selectedEvent = id
        state.detailTab = "Ringkasan"
        navigate("detail")
    }

    fun notice(message: String) {
        feedback?.dismiss()
        feedback =
            Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_LONG).also { it.show() }
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

    fun page(title: String? = null, subtitle: String? = null, back: Boolean = false): LinearLayout {
        val view = layoutInflater.inflate(R.layout.view_page, host, false)
        host.addView(view)
        val content = view.findViewById<LinearLayout>(R.id.page_content)
        view.post {
            if (view.width > ui.dp(640)) {
                val side = (view.width - ui.dp(640)) / 2 + ui.dp(24)
                content.setPadding(side, ui.dp(20), side, ui.dp(32))
            }
        }
        if (back) {
            val row = ui.row()
            row.addView(ui.iconAction(R.drawable.ic_back, "Kembali") { navigate("main") })
            content.addView(row)
            ui.gap(content)
        }
        title?.let {
            content.addView(ui.text(it, 24, 700))
            ui.gap(content, 6)
        }
        subtitle?.let {
            content.addView(ui.text(it, 14, 400, ui.muted))
            ui.gap(content, 20)
        }
        return content
    }

    private fun block(message: String = "Memproses…") {
        mutating = true
        val panel =
            ui.column(28).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setBackgroundColor(ui.color(R.color.background))
                addView(ProgressBar(this@MainActivity))
                ui.gap(this)
                addView(ui.text(message, 16, 600).apply { gravity = Gravity.CENTER })
            }
        host.addView(panel, FrameLayout.LayoutParams(-1, -1))
        overlay = panel
    }

    private fun unblock() {
        overlay?.let { host.removeView(it) }
        overlay = null
        mutating = false
    }

    private fun expire() {
        calls.toList().forEach { it.cancel() }
        session.clear()
        state.clear()
        navigate("login")
        notice("Sesi berakhir. Silakan masuk kembali.")
    }

    fun <T> request(
        call: Call<T>,
        failure: (ApiFailure) -> Unit = { notice(it.message) },
        cleanup: () -> Unit = {},
        success: (T) -> Unit,
    ) {
        val epoch = generation
        val http = call.request()
        val mutation = http.method != "GET"
        val scope =
            if (session.token != null && state.user != null)
                "${session.baseUrl}|${state.user!!.id}|${state.user!!.role}"
            else null
        if (mutation) state.reads.afterMutation(http.url)
        val cacheRevision = state.reads.revision
        if (!mutation && scope != null) {
            state.reads.get<T>(scope, http.url)?.let { cached ->
                cleanup()
                success(cached)
                return
            }
        }
        calls.add(call)
        call.enqueue(
            object : Callback<T> {
                override fun onResponse(call: Call<T>, response: Response<T>) {
                    calls.remove(call)
                    if (mutation) state.reads.afterMutation(http.url)
                    cleanup()
                    if (isDestroyed || epoch != generation) return
                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        if (!mutation && scope != null)
                            state.reads.put(scope, http.url, body as Any, cacheRevision)
                        success(body)
                    } else {
                        val error =
                            if (response.isSuccessful)
                                ApiFailure(message = "Respons server kosong.")
                            else ApiFailure.http(response.code(), response.errorBody()?.string())
                        if (error.status == 403) state.reads.clear()
                        if (error.status == 401) expire() else failure(error)
                    }
                }

                override fun onFailure(call: Call<T>, error: Throwable) {
                    calls.remove(call)
                    if (mutation) state.reads.afterMutation(http.url)
                    cleanup()
                    if (!call.isCanceled && !isDestroyed && epoch == generation)
                        failure(ApiFailure.network(error))
                }
            }
        )
    }

    fun <T> write(
        call: Call<T>,
        fields: Map<String, TextInputLayout> = emptyMap(),
        cleanup: () -> Unit = {},
        success: (T) -> Unit,
    ) {
        if (mutating) {
            cleanup()
            return
        }
        block()
        request(
            call,
            failure = { error ->
                unblock()
                fields.values.forEach { it.error = null }
                error.fields.forEach { (name, messages) ->
                    fields[name]?.error = messages.firstOrNull()
                }
                notice(error.message)
            },
            cleanup = cleanup,
        ) { value ->
            unblock()
            success(value)
        }
    }

    fun <T> load(container: LinearLayout, factory: () -> Call<T>, success: (T) -> Unit) {
        container.removeAllViews()
        container.addView(ProgressBar(this))
        ui.gap(container)
        container.addView(ui.text("Memuat data…", 14, 400, ui.muted))
        request(
            factory(),
            failure = { error ->
                container.removeAllViews()
                ui.empty(container, "Data belum dapat dimuat", error.message)
                ui.button(container, "Coba lagi", true) { load(container, factory, success) }
            },
        ) { value ->
            container.removeAllViews()
            success(value)
        }
    }

    fun <T> allPages(
        factory: (Int) -> Call<ApiPage<T>>,
        failure: (ApiFailure) -> Unit = { notice(it.message) },
        success: (List<T>) -> Unit,
    ) {
        val items = mutableListOf<T>()
        fun next(page: Int) {
            request(factory(page), failure) { result ->
                items.addAll(result.data)
                if (result.currentPage < result.lastPage) next(result.currentPage + 1)
                else success(items)
            }
        }
        next(1)
    }

    fun <T> pagination(parent: LinearLayout, page: ApiPage<T>, change: (Int) -> Unit) {
        if (page.lastPage <= 1) return
        ui.gap(parent)
        parent.addView(
            ui.text(
                "Halaman ${page.currentPage} dari ${page.lastPage} · ${page.total} data",
                12,
                600,
                ui.muted,
            )
        )
        if (page.currentPage > 1)
            ui.button(parent, "Halaman sebelumnya", true) { change(page.currentPage - 1) }
        if (page.currentPage < page.lastPage)
            ui.button(parent, "Halaman berikutnya", true) { change(page.currentPage + 1) }
    }

    fun acceptLogin(data: LoginData) {
        val user = data.user
        if (user == null || Role.fromWire(user.role) == null || data.token.isBlank()) {
            notice("Respons login belum lengkap.")
            return
        }
        try {
            session.save(data.token, data.expiresAt)
        } catch (_: Exception) {
            notice("Sesi belum dapat disimpan dengan aman. Silakan coba lagi.")
            return
        }
        state.clear()
        state.user = user
        selectTab("home")
    }

    fun serverSettings() {
        val form = ui.column(24)
        val (box, input) =
            ui.field(
                form,
                "Alamat API",
                session.baseUrl,
                android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI,
            )
        form.addView(
            ui.text(
                "Emulator: http://10.0.2.2:8000/api/\nHP: gunakan IP laptop dan jaringan Wi-Fi yang sama. Mengganti server mengakhiri sesi lokal.",
                14,
                400,
                ui.muted,
            )
        )
        val dialog =
            MaterialAlertDialogBuilder(this)
                .setTitle("Koneksi server")
                .setView(form)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                try {
                    session.setBaseUrl(input.text.toString())
                    state.clear()
                    api = ApiClient.create(session.baseUrl) { session.token }
                    dialog.dismiss()
                    navigate("login")
                    notice("Alamat server disimpan.")
                } catch (error: IllegalArgumentException) {
                    box.error = error.message
                }
            }
        }
        dialog.show()
    }

    fun pickPhoto(cover: Boolean = false) {
        state.pendingPhotoEvent = state.selectedEvent
        state.uploadCover = cover
        picker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
    }

    fun refresh() {
        state.reads.refresh(state.screen, state.tab, state.selectedEvent)
        render()
    }

    fun render() {
        feedback?.dismiss()
        feedback = null
        generation++
        handler.removeCallbacksAndMessages(null)
        calls.toList().forEach { it.cancel() }
        calls.clear()
        unblock()
        host.removeAllViews()
        if (
            state.user == null &&
                state.screen in listOf("main", "detail", "form", "history", "userform")
        )
            state.screen = if (session.token != null) "restore" else "login"
        navigation.visibility = if (state.screen == "main") View.VISIBLE else View.GONE
        when (state.screen) {
            "splash1",
            "splash2" -> splash()

            "welcome" -> welcome()
            "login",
            "register" -> AuthScreens(this).render()

            "restore" -> restore()
            "detail" -> EventScreens(this).detail()
            "form" -> EventScreens(this).form()
            "userform" -> ManagementScreens(this).userForm()
            "history" -> AccountScreens(this).history()
            else -> {
                configureNavigation()
                when (state.tab) {
                    "profile" -> AccountScreens(this).profile()
                    "users" -> ManagementScreens(this).users()
                    "explore",
                    "joined" -> EventScreens(this).listing()

                    else ->
                        if (state.role == Role.RELAWAN) VolunteerHome(this).render()
                        else ManagementScreens(this).dashboard()
                }
            }
        }
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

    private fun restore() {
        val p = page("Memeriksa sesi", "Menghubungkan akunmu kembali.")
        val area = ui.column()
        p.addView(area)
        load(area, { api.me() }) { response ->
            val user = response.data
            if (user != null && Role.fromWire(user.role) != null) {
                state.user = user
                selectTab("home")
            } else {
                session.clear()
                state.clear()
                navigate("login")
            }
        }
        ui.button(p, "Atur koneksi server", true) { serverSettings() }
        ui.button(p, "Masuk dengan akun lain", true) {
            session.clear()
            state.clear()
            navigate("login")
        }
    }

    private fun welcome() {
        val view = layoutInflater.inflate(R.layout.screen_welcome, host, false)
        host.addView(view)
        view.findViewById<View>(R.id.welcome_start_button).setOnClickListener {
            navigate(if (session.token != null) "restore" else "login")
        }
        view.findViewById<View>(R.id.welcome_register_button).setOnClickListener {
            navigate("register")
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
                if (!isFinishing)
                    navigate(
                        if (first) "splash2"
                        else if (session.token != null) "restore" else "welcome"
                    )
            },
            if (first) 1000L else 1300L,
        )
    }
}
