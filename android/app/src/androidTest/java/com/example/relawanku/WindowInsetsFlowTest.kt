package com.example.relawanku

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.relawanku.data.*
import com.example.relawanku.ui.Role
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WindowInsetsFlowTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun views(v: View): List<View> =
        listOf(v) +
            if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) }
            else emptyList()

    private fun text(a: MainActivity, label: String) =
        views(a.findViewById(android.R.id.content)).filterIsInstance<TextView>().any {
            it.text.toString() == label
        }

    private fun field(a: MainActivity, label: String): EditText =
        views(a.findViewById(android.R.id.content))
            .filterIsInstance<TextInputLayout>()
            .first { it.hint.toString() == label }
            .editText!!

    private fun await(
        s: ActivityScenario<MainActivity>,
        message: String,
        condition: (MainActivity) -> Boolean,
    ) {
        val end = System.currentTimeMillis() + 12000
        var ready = false
        while (!ready && System.currentTimeMillis() < end) {
            s.onActivity { ready = condition(it) }
            if (!ready) Thread.sleep(80)
        }
        assertTrue(message, ready)
    }

    private fun ime(a: MainActivity) =
        ViewCompat.getRootWindowInsets(a.findViewById(R.id.main))
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun y(view: View): Int {
        val xy = IntArray(2)
        view.getLocationOnScreen(xy)
        return xy[1]
    }

    private fun show(a: MainActivity, edit: EditText) {
        edit.requestFocus()
        edit.requestRectangleOnScreen(Rect(0, 0, edit.width, edit.height), true)
        edit.post {
            WindowInsetsControllerCompat(a.window, a.findViewById(R.id.main))
                .show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun assertNavigation(a: MainActivity) {
        val root = a.findViewById<View>(R.id.main)
        val nav = a.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val bars =
            ViewCompat.getRootWindowInsets(root)!!.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
        assertEquals("Root must not add a second bottom inset", 0, root.paddingBottom)
        assertEquals(View.VISIBLE, nav.visibility)
        assertEquals(
            "Navigation should include the safe area exactly once",
            a.ui.dp(64) + bars.bottom,
            nav.height,
        )
        assertEquals(bars.bottom, nav.paddingBottom)
        assertEquals("Navigation must reach the bottom of the app window", root.height, nav.bottom)
    }

    private fun assertFocusedField(a: MainActivity, edit: EditText) {
        val fieldRect = Rect()
        assertTrue("Focused field must be visible", edit.getGlobalVisibleRect(fieldRect))
        val root = a.findViewById<View>(R.id.main)
        val bottom =
            ViewCompat.getRootWindowInsets(root)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
        assertTrue(
            "Focused field should stay above keyboard",
            fieldRect.bottom <= y(root) + root.height - bottom + a.ui.dp(2),
        )
        assertTrue(edit.hasFocus())
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(250, 5000)
        Thread.sleep(250)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        java.io.File(context.getExternalFilesDir(null), "ui-" + name + ".png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun server(role: Role): MockWebServer =
        MockWebServer().apply {
            dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val user =
                            ApiUser(
                                id = 10,
                                name = "Rasyid Saputra",
                                email = "rasyid@example.test",
                                role = role.wire,
                                phone = "081234567890",
                                bio = "Relawan komunitas dan kegiatan lingkungan.",
                            )
                        val body =
                            when (request.requestUrl!!.encodedPath) {
                                "/api/auth/me" -> mapOf("data" to user)
                                "/api/dashboard" ->
                                    mapOf(
                                        "data" to
                                            mapOf(
                                                "activities" to 8,
                                                "active_activities" to 5,
                                                "registrations" to 24,
                                                "attendances" to 18,
                                                "pending_tasks" to 3,
                                                "users" to 42,
                                            )
                                    )
                                else ->
                                    mapOf(
                                        "data" to
                                            listOf(
                                                mapOf(
                                                    "id" to 1,
                                                    "title" to "Bersih Sungai, Ruang Hidup Bersama",
                                                    "category" to "Lingkungan",
                                                    "location" to "Taman Kota Bengkulu",
                                                    "quota" to 30,
                                                    "active_registrations_count" to 18,
                                                    "status" to "open",
                                                    "starts_at" to "2027-01-12T08:00:00+07:00",
                                                )
                                            ),
                                        "current_page" to 1,
                                        "last_page" to 1,
                                        "total" to 1,
                                    )
                            }
                        return MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody(Gson().toJson(body))
                    }
                }
            start()
        }

    @Test
    fun navigation_and_profile_keyboard_are_correct_for_every_role() {
        val settings = SessionStore(context)
        val previous = settings.baseUrl
        try {
            for (role in Role.entries) {
                val server = server(role)
                try {
                    settings.setBaseUrl(server.url("/api/").toString())
                    settings.clear()
                    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                        scenario.onActivity { a ->
                            a.state.clear()
                            a.state.user =
                                ApiUser(id = 10, name = "Rasyid Saputra", role = role.wire)
                            a.session.save("layout-test-token", null)
                            a.api = ApiClient.create(a.session.baseUrl) { a.session.token }
                            a.selectTab("home")
                        }
                        await(scenario, "Dashboard should load") { a ->
                            if (role == Role.RELAWAN) text(a, "Bersih Sungai, Ruang Hidup Bersama")
                            else text(a, "5 kegiatan aktif")
                        }
                        scenario.onActivity { a ->
                            assertNavigation(a)
                            val avatar =
                                views(a.findViewById(android.R.id.content)).first {
                                    it.contentDescription == "Buka profil"
                                }
                            val bounds = Rect()
                            assertTrue(
                                "Profile action should remain visible",
                                avatar.getGlobalVisibleRect(bounds),
                            )
                            assertTrue(
                                "Profile action should remain tappable",
                                bounds.width() >= a.ui.dp(48) && bounds.height() >= a.ui.dp(48),
                            )
                        }
                        capture(role.wire + "-dashboard")
                        scenario.onActivity { it.selectTab("profile") }
                        await(scenario, "Profile should load") { text(it, "Simpan profil") }
                        var headerY = 0
                        scenario.onActivity { a ->
                            assertNavigation(a)
                            headerY = y(a.findViewById(R.id.page_header))
                        }
                        capture(role.wire + "-profile")
                        scenario.onActivity { a ->
                            field(a, "Tentang saya")
                                .setText("Draft profil tetap tersimpan saat keyboard dibuka.")
                            show(a, field(a, "Tentang saya"))
                        }
                        await(scenario, "Keyboard should open") { ime(it) }
                        Thread.sleep(350)
                        scenario.onActivity { a ->
                            assertEquals(
                                View.GONE,
                                a.findViewById<View>(R.id.bottom_navigation).visibility,
                            )
                            assertEquals(
                                "Only form should scroll; toolbar must stay put",
                                headerY,
                                y(a.findViewById(R.id.page_header)),
                            )
                            assertFocusedField(a, field(a, "Tentang saya"))
                            assertEquals(
                                "Draft profil tetap tersimpan saat keyboard dibuka.",
                                field(a, "Tentang saya").text.toString(),
                            )
                        }
                        capture(role.wire + "-keyboard")
                        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                        await(scenario, "Keyboard should close") {
                            !ime(it) &&
                                it.findViewById<View>(R.id.bottom_navigation).visibility ==
                                    View.VISIBLE
                        }
                        scenario.onActivity { a ->
                            assertNavigation(a)
                            assertEquals("profile", a.state.tab)
                            assertEquals(headerY, y(a.findViewById(R.id.page_header)))
                            assertEquals(
                                "Draft profil tetap tersimpan saat keyboard dibuka.",
                                field(a, "Tentang saya").text.toString(),
                            )
                            a.selectTab("home")
                        }
                        await(scenario, "Dashboard navigation should recover") {
                            !ime(it) && it.state.tab == "home"
                        }
                        scenario.onActivity { a ->
                            assertNavigation(a)
                            val avatar =
                                views(a.findViewById(android.R.id.content)).first {
                                    it.contentDescription == "Buka profil"
                                }
                            val bounds = Rect()
                            assertTrue(
                                "Profile action should remain visible",
                                avatar.getGlobalVisibleRect(bounds),
                            )
                            assertTrue(
                                "Profile action should remain tappable",
                                bounds.width() >= a.ui.dp(48) && bounds.height() >= a.ui.dp(48),
                            )
                        }
                    }
                } finally {
                    server.shutdown()
                }
            }
        } finally {
            settings.setBaseUrl(previous)
            settings.clear()
        }
    }

    @Test
    fun login_remains_scrollable_above_keyboard() {
        val session = SessionStore(context)
        session.clear()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                a.navigate("login")
                show(a, field(a, "Email"))
            }
            await(scenario, "Login keyboard should open") { ime(it) }
            Thread.sleep(350)
            scenario.onActivity { a ->
                assertEquals(View.GONE, a.findViewById<View>(R.id.bottom_navigation).visibility)
                assertFocusedField(a, field(a, "Email"))
                assertEquals("login", a.state.screen)
            }
            capture("login-keyboard")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await(scenario, "Login keyboard should close without leaving screen") { !ime(it) }
            scenario.onActivity { assertEquals("login", it.state.screen) }
        }
    }
}
