package com.example.relawanku

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.relawanku.data.*
import com.example.relawanku.ui.Role
import com.google.android.material.textfield.TextInputLayout
import java.util.Date
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun views(v: View): List<View> =
        listOf(v) +
            if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) }
            else emptyList()

    private fun text(a: MainActivity, value: String) =
        views(a.findViewById(android.R.id.content)).filterIsInstance<TextView>().any {
            it.text.toString() == value
        }

    private fun tap(a: MainActivity, value: String) {
        var target: View =
            views(a.findViewById(android.R.id.content)).filterIsInstance<TextView>().firstOrNull {
                it.text.toString() == value
            } ?: error("Missing UI: $value")
        while (!target.isClickable && target.parent is View) target = target.parent as View
        assertTrue(target.performClick())
    }

    private fun field(a: MainActivity, label: String, value: String) {
        views(a.findViewById(android.R.id.content))
            .filterIsInstance<TextInputLayout>()
            .first { it.hint.toString() == label }
            .editText!!
            .setText(value)
    }

    private fun await(
        scenario: ActivityScenario<MainActivity>,
        message: String,
        condition: (MainActivity) -> Boolean,
    ) {
        val end = System.currentTimeMillis() + 20000
        var ready = false
        while (!ready && System.currentTimeMillis() < end) {
            scenario.onActivity { ready = condition(it) }
            if (!ready) Thread.sleep(100)
        }
        assertTrue(message, ready)
    }

    private fun <T> ok(response: Response<T>): T {
        assertTrue(
            "HTTP ${response.code()}: ${response.errorBody()?.string()}",
            response.isSuccessful,
        )
        return response.body()!!
    }

    private fun clickDialog(label: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var clicked = false
        repeat(30) {
            if (!clicked) {
                val node =
                    automation.rootInActiveWindow
                        ?.findAccessibilityNodeInfosByText(label)
                        ?.firstOrNull { it.text?.toString() == label }
                if (node != null) {
                    var target = node
                    while (target != null && !target.isClickable) target = target.parent
                    clicked =
                        target?.performAction(
                            android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
                        ) == true
                }
                if (!clicked) Thread.sleep(100)
            }
        }
        assertTrue("Dialog action missing: $label", clicked)
    }

    private fun capture(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(300, 5000)
        Thread.sleep(300)
        val bitmap =
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        java.io.File(context.getExternalFilesDir(null), "qa-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun unreachable_server_shows_retry_without_fake_login() {
        val session = SessionStore(context)
        val previous = session.baseUrl
        try {
            session.setBaseUrl("http://127.0.0.1:1/api/")
            session.save("test-only", null)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.navigate("restore") }
                await(scenario, "Connection error should offer retry") { text(it, "Coba lagi") }
                scenario.onActivity {
                    assertNull(it.state.user)
                    assertEquals("restore", it.state.screen)
                }
                capture("connection-error")
            }
        } finally {
            session.setBaseUrl(previous)
            session.clear()
        }
    }

    @Before
    fun clear() {
        SessionStore(context).clear()
    }

    @Test
    fun welcome_xml_routes_to_login_and_registration() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario, "Welcome not shown") { it.state.screen == "welcome" }
            scenario.onActivity { a ->
                assertNotNull(a.findViewById<View>(R.id.welcome_screen))
                assertEquals(
                    a.getString(R.string.welcome_title),
                    a.findViewById<TextView>(R.id.welcome_title).text,
                )
                a.findViewById<View>(R.id.welcome_start_button).performClick()
                assertEquals("login", a.state.screen)
                a.onBackPressedDispatcher.onBackPressed()
                assertEquals("welcome", a.state.screen)
                a.findViewById<View>(R.id.welcome_register_button).performClick()
                assertEquals("register", a.state.screen)
            }
        }
    }

    @Test
    fun invalid_login_stays_on_form_without_preview_roles() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                a.navigate("login")
                field(a, "Email", "invalid")
                tap(a, "Masuk")
                assertEquals("login", a.state.screen)
                assertNull(a.state.user)
                assertFalse(text(a, "Coba dengan data contoh"))
                val boxes =
                    views(a.findViewById(android.R.id.content)).filterIsInstance<TextInputLayout>()
                assertNotNull(boxes.first { it.hint == "Email" }.error)
                assertNotNull(boxes.first { it.hint == "Kata sandi" }.error)
            }
        }
    }

    @Test
    fun bearer_is_encrypted_and_scoped_to_server() {
        val session = SessionStore(context)
        val old = session.baseUrl
        try {
            session.setBaseUrl("http://10.0.2.2:8765/api/")
            session.save("test-secret-bearer", null)
            val prefs = context.getSharedPreferences("session_secure", Context.MODE_PRIVATE)
            assertFalse(prefs.all.values.any { it.toString().contains("test-secret-bearer") })
            assertEquals("test-secret-bearer", SessionStore(context).token)
            session.setBaseUrl("http://10.0.2.2:8766/api/")
            assertNull(session.token)
            assertNull(SessionStore(context).token)
        } finally {
            session.setBaseUrl(old)
            session.clear()
        }
    }

    @Test
    fun isolated_laravel_workflow_and_three_role_navigation() {
        val args = InstrumentationRegistry.getArguments()
        val base = args.getString("integrationBaseUrl")
        val password = args.getString("integrationPassword")
        assumeTrue(
            "Requires an isolated Laravel database and runner arguments",
            !base.isNullOrBlank() && !password.isNullOrBlank(),
        )
        val session = SessionStore(context)
        val previous = session.baseUrl
        session.setBaseUrl(base!!)
        val public = ApiClient.create(base) { null }
        fun login(role: String) =
            ok(
                    public
                        .login(
                            mapOf(
                                "email" to "$role@gmail.com",
                                "password" to password,
                                "device_name" to "Android integration test",
                            )
                        )
                        .execute()
                )
                .data!!

        val manager = login("koordinator")
        val admin = login("admin")
        val volunteer = login("relawan")
        val m = ApiClient.create(base) { manager.token }
        val ad = ApiClient.create(base) { admin.token }
        val v = ApiClient.create(base) { volunteer.token }
        val suffix = System.currentTimeMillis().toString()
        val start = System.currentTimeMillis() + 3 * 86400000L
        val event =
            ok(
                    m.createActivity(
                            mapOf(
                                "title" to "Android Integration $suffix",
                                "description" to "Isolated Android integration fixture",
                                "category" to "Lingkungan",
                                "location" to "Bengkulu",
                                "quota" to 10,
                                "status" to "open",
                                "starts_at" to Dates.iso(Date(start)),
                                "ends_at" to Dates.iso(Date(start + 7200000)),
                                "registration_deadline" to Dates.iso(Date(start - 3600000)),
                            )
                        )
                        .execute()
                )
                .data!!
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { a ->
                    a.navigate("register")
                    field(a, "Nama lengkap", "Android Registrant")
                    field(a, "Email", "registered-$suffix@example.test")
                    field(a, "Kata sandi", "IntegrationUser123!")
                    field(a, "Ulangi kata sandi", "IntegrationUser123!")
                    tap(a, "Daftar sebagai relawan")
                }
                await(scenario, "Registration did not lead to login") { it.state.screen == "login" }
                scenario.onActivity { a ->
                    field(a, "Email", "relawan@gmail.com")
                    field(a, "Kata sandi", "WrongPassword123!")
                    tap(a, "Masuk")
                }
                await(scenario, "Server validation not visible") { a ->
                    views(a.findViewById(android.R.id.content))
                        .filterIsInstance<TextInputLayout>()
                        .any { it.hint == "Email" && it.error != null }
                }
                scenario.onActivity { a ->
                    field(a, "Kata sandi", password!!)
                    tap(a, "Masuk")
                }
                await(scenario, "Relawan login failed") {
                    it.state.screen == "main" && it.state.user != null
                }
                await(scenario, "Dashboard data not loaded") {
                    text(it, "Kontribusimu") && text(it, event.title)
                }
                capture("volunteer-home")
                scenario.onActivity { a ->
                    assertEquals(Role.RELAWAN, a.state.role)
                    a.openEvent(event.id)
                }
                await(scenario, "Join action missing") { text(it, "Daftar kegiatan") }
                scenario.onActivity { tap(it, "Daftar kegiatan") }
                clickDialog("Ya, daftar")
                await(scenario, "Registration missing") { text(it, "Kamu sudah terdaftar") }
                assertTrue(
                    ok(v.mine(1).execute()).data.any {
                        it.activityId == event.id && it.status == "registered"
                    }
                )
                val task =
                    ok(
                            m.createTask(
                                    event.id,
                                    mapOf(
                                        "title" to "Tugas Android $suffix",
                                        "assigned_to" to volunteer.user!!.id,
                                    ),
                                )
                                .execute()
                        )
                        .data!!
                scenario.onActivity { a ->
                    a.state.detailTab = "Tugas"
                    a.render()
                }
                await(scenario, "Assigned task missing") { text(it, task.title) }
                scenario.onActivity { tap(it, "Selesai") }
                await(scenario, "Task update missing") {
                    text(it, task.title) && text(it, "Selesai")
                }
                // Poll the server rather than assuming a chip click already completed the write.
                var completed = false
                repeat(30) {
                    if (!completed) {
                        completed =
                            ok(v.tasks(event.id, 1).execute())
                                .data
                                .first { it.id == task.id }
                                .status == "completed"
                        if (!completed) Thread.sleep(100)
                    }
                }
                assertTrue(completed)
                val now = System.currentTimeMillis()
                ok(
                    m.updateActivity(
                            event.id,
                            mapOf(
                                "starts_at" to Dates.iso(Date(now - 60000)),
                                "ends_at" to Dates.iso(Date(now + 3600000)),
                                "registration_deadline" to Dates.iso(Date(now - 120000)),
                            ),
                        )
                        .execute()
                )
                val attendance =
                    ok(m.openSession(event.id, mapOf("duration_minutes" to 15)).execute()).data!!
                scenario.onActivity { a ->
                    a.state.detailTab = "Absensi"
                    a.refresh()
                }
                await(scenario, "Attendance form missing") { text(it, "Konfirmasi kehadiran") }
                scenario.onActivity { a ->
                    field(a, "Kode absensi", attendance.code)
                    tap(a, "Konfirmasi kehadiran")
                }
                await(scenario, "Check in not confirmed") { text(it, "Kehadiran tercatat") }
                capture("attendance")
                assertTrue(
                    ok(m.attendances(event.id, 1).execute()).data.any {
                        it.userId == volunteer.user!!.id
                    }
                )
                val bitmap =
                    android.graphics.Bitmap.createBitmap(
                        8,
                        8,
                        android.graphics.Bitmap.Config.ARGB_8888,
                    )
                val bytes =
                    java.io
                        .ByteArrayOutputStream()
                        .also {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        }
                        .toByteArray()
                bitmap.recycle()
                val part =
                    MultipartBody.Part.createFormData(
                        "image",
                        "integration.png",
                        bytes.toRequestBody("image/png".toMediaType()),
                    )
                val photo = ok(m.uploadPhoto(event.id, part, null).execute()).data!!
                ok(
                    m.caption(event.id, photo.id, mapOf("caption" to "Dokumentasi Android"))
                        .execute()
                )
                scenario.onActivity { a ->
                    a.state.detailTab = "Dokumentasi"
                    a.render()
                }
                await(scenario, "Documentation missing") { text(it, "Dokumentasi Android") }
                ok(m.deletePhoto(event.id, photo.id).execute())
                ok(m.closeSession(event.id, attendance.session!!.id).execute())
                scenario.recreate()
                scenario.onActivity { assertNotNull(it.session.token) }
                ok(ApiClient.create(base) { SessionStore(context).token }.logout().execute())
                scenario.onActivity { it.selectTab("profile") }
                await(scenario, "Revoked session did not redirect") {
                    it.state.screen == "login" && it.state.user == null
                }
                assertNull(SessionStore(context).token)
                scenario.onActivity { a ->
                    field(a, "Email", "koordinator@gmail.com")
                    field(a, "Kata sandi", password!!)
                    tap(a, "Masuk")
                }
                await(scenario, "Koordinator login failed") {
                    it.state.screen == "main" && it.state.role == Role.KOORDINATOR
                }
                await(scenario, "Dashboard statistics missing") { a ->
                    views(a.findViewById(android.R.id.content)).filterIsInstance<TextView>().any {
                        it.text.toString().contains(" kegiatan aktif")
                    }
                }
                capture("coordinator-dashboard")
                scenario.onActivity { a -> a.openEvent(event.id) }
                await(scenario, "Edit activity missing") { text(it, "Edit kegiatan") }
                scenario.onActivity {
                    tap(it, "Edit kegiatan")
                    field(it, "Nama kegiatan", "Android Updated $suffix")
                    tap(it, "Simpan kegiatan")
                }
                await(scenario, "Updated activity missing") {
                    it.state.screen == "detail" && text(it, "Android Updated $suffix")
                }
                assertEquals(
                    "Android Updated $suffix",
                    ok(m.activity(event.id).execute()).data!!.title,
                )
                scenario.onActivity { a -> a.acceptLogin(admin) }
                await(scenario, "Admin dashboard missing") { text(it, "Kelola pengguna") }
                scenario.onActivity { a -> a.selectTab("users") }
                await(scenario, "Admin users missing") {
                    text(it, "Tambah pengguna") && text(it, "relawan@gmail.com")
                }
                capture("admin-users")
                scenario.onActivity {
                    tap(it, "Tambah pengguna")
                    field(it, "Nama lengkap", "Android User")
                    field(it, "Email", "android-$suffix@example.test")
                    field(it, "Kata sandi", "IntegrationUser123!")
                    field(it, "Ulangi kata sandi", "IntegrationUser123!")
                    tap(it, "Simpan pengguna")
                }
                await(scenario, "User creation did not return to list") {
                    it.state.screen == "main" && text(it, "android-$suffix@example.test")
                }
                assertTrue(
                    ok(ad.users(mapOf("search" to "android-$suffix@example.test")).execute())
                        .data
                        .isNotEmpty()
                )
                assertEquals(403, v.users(emptyMap()).execute().code())
                assertTrue(
                    ok(v.history(1).execute()).data.any { it.event == "attendance.checked_in" }
                )
                ok(m.deleteTask(event.id, task.id).execute())
            }
        } finally {
            m.deleteActivity(event.id).execute().closeError()
            listOf(m, ad, v).forEach { it.logout().execute().closeError() }
            session.setBaseUrl(previous)
            session.clear()
        }
    }

    private fun Response<*>.closeError() {
        errorBody()?.close()
    }
}
