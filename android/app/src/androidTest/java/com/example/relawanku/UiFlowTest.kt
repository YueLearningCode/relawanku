package com.example.relawanku

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.relawanku.ui.Role
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    private fun views(view: View): List<View> =
        listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
            else emptyList()

    private fun tap(a: MainActivity, text: String) {
        var target: View =
            views(a.findViewById(android.R.id.content)).filterIsInstance<TextView>().firstOrNull {
                it.text.toString() == text
            } ?: error("Missing UI: $text")
        while (!target.isClickable && target.parent is View) {
            target = target.parent as View
        }
        assertTrue(target.performClick())
    }

    private fun field(a: MainActivity, label: String, value: String) {
        val box =
            views(a.findViewById(android.R.id.content)).filterIsInstance<TextInputLayout>().first {
                it.hint.toString() == label
            }
        box.editText!!.setText(value)
    }

    private fun enter(a: MainActivity, role: String) {
        a.navigate("login")
        tap(a, role)
        tap(a, "Coba dengan data contoh")
    }

    private fun clickDialog(label: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        var clicked = false
        repeat(20) {
            if (!clicked) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                val node =
                    root?.findAccessibilityNodeInfosByText(label)?.firstOrNull {
                        it.text?.toString() == label
                    }
                if (node != null) {
                    var target: AccessibilityNodeInfo = node
                    while (!target.isClickable) {
                        target = target.parent ?: break
                    }
                    clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                if (!clicked) Thread.sleep(100)
            }
        }
        assertTrue("Dialog action missing: $label", clicked)
        instrumentation.waitForIdleSync()
    }

    @Test
    fun volunteer_can_search_join_and_complete_attendance() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                enter(a, "Relawan")
                tap(a, "Jelajahi")
                assertTrue(
                    a.findViewById<
                            com.google.android.material.bottomnavigation.BottomNavigationView
                        >(
                            R.id.bottom_navigation
                        )
                        .menu
                        .findItem(11)
                        .isChecked
                )
                field(a, "Cari nama atau lokasi", "tidak-ditemukan")
                assertTrue(
                    views(a.findViewById(android.R.id.content)).filterIsInstance<TextView>().any {
                        it.text == "Kegiatan belum ditemukan"
                    }
                )
                field(a, "Cari nama atau lokasi", "")
                tap(a, "Bersih pantai, jaga masa depan")
                tap(a, "Daftar kegiatan")
            }
            clickDialog("Ya, daftar")
            scenario.onActivity { a ->
                assertTrue(1 in a.state.joined)
                tap(a, "Absensi")
                tap(a, "Konfirmasi kehadiran")
                val box =
                    views(a.findViewById(android.R.id.content))
                        .filterIsInstance<TextInputLayout>()
                        .first { it.hint == "Kode absensi" }
                assertNotNull(box.error)
                field(a, "Kode absensi", "RW2026")
                tap(a, "Konfirmasi kehadiran")
                assertTrue(1 in a.state.attended)
            }
            scenario.recreate()
            scenario.onActivity { a ->
                assertTrue(1 in a.state.attended)
                assertEquals("detail", a.state.screen)
            }
        }
    }

    @Test
    fun coordinator_can_validate_create_and_archive_activity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                enter(a, "Koordinator")
                tap(a, "Buat kegiatan baru")
                tap(a, "Simpan kegiatan")
                val title =
                    views(a.findViewById(android.R.id.content))
                        .filterIsInstance<TextInputLayout>()
                        .first { it.hint == "Nama kegiatan" }
                assertNotNull(title.error)
                field(a, "Nama kegiatan", "Uji kegiatan UI")
                field(a, "Deskripsi", "Kegiatan contoh pengujian")
                field(a, "Lokasi", "Bengkulu")
                field(a, "Kuota relawan", "12")
                tap(a, "Simpan kegiatan")
                assertEquals("Uji kegiatan UI", a.state.event()?.title)
                tap(a, "Arsipkan kegiatan")
            }
            clickDialog("Arsipkan")
            scenario.onActivity { a ->
                assertFalse(a.state.events.any { it.title == "Uji kegiatan UI" })
                assertEquals("main", a.state.screen)
            }
        }
    }

    @Test
    fun admin_can_add_user_and_switch_preview_role() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                enter(a, "Admin")
                tap(a, "Kelola pengguna")
                tap(a, "Tambah pengguna")
                field(a, "Nama lengkap", "Relawan Pengujian")
                field(a, "Email", "uji@contoh.id")
                tap(a, "Simpan pengguna")
                assertTrue(a.state.users.any { it.email == "uji@contoh.id" })
                a.selectTab("profile")
                tap(a, "Ganti peran pratinjau")
            }
            clickDialog("Relawan")
            scenario.onActivity { a ->
                assertEquals(Role.RELAWAN, a.state.role)
                assertEquals("home", a.state.tab)
            }
        }
    }
}
