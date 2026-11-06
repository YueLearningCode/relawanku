package com.example.relawanku

import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.relawanku.data.ApiUser
import com.example.relawanku.data.SessionStore
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasswordVisibilityTest {
    private fun views(v: View): List<View> =
        listOf(v) +
            if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) }
            else emptyList()

    private fun box(a: MainActivity, label: String) =
        views(a.findViewById(android.R.id.content)).filterIsInstance<TextInputLayout>().first {
            it.hint.toString() == label
        }

    private fun toggle(box: TextInputLayout, label: String) {
        assertEquals(label, box.endIconContentDescription.toString())
        val icon = views(box).first { it.contentDescription?.toString() == label && it.isClickable }
        assertTrue("Password toggle must remain available during validation", icon.isShown)
        assertTrue(icon.performClick())
    }

    private fun checkField(box: TextInputLayout) {
        val edit = box.editText!!
        val sample = "TestOnly123!"
        edit.setText(sample)
        edit.setSelection(2, 6)
        assertTrue(
            "Password must be hidden on first render",
            edit.transformationMethod is PasswordTransformationMethod,
        )
        assertNotEquals(
            sample,
            edit.transformationMethod.getTransformation(edit.text, edit).toString(),
        )
        box.error = "Periksa kata sandi"
        toggle(box, "Tampilkan kata sandi")
        assertFalse(edit.transformationMethod is PasswordTransformationMethod)
        assertEquals("Periksa kata sandi", box.error.toString())
        assertEquals(sample, edit.text.toString())
        assertEquals(2, edit.selectionStart)
        assertEquals(6, edit.selectionEnd)
        assertEquals(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            edit.inputType and InputType.TYPE_MASK_VARIATION,
        )
        toggle(box, "Sembunyikan kata sandi")
        assertTrue(edit.transformationMethod is PasswordTransformationMethod)
        assertNotEquals(
            sample,
            edit.transformationMethod.getTransformation(edit.text, edit).toString(),
        )
        assertEquals("Periksa kata sandi", box.error.toString())
        assertEquals(sample, edit.text.toString())
        assertEquals(2, edit.selectionStart)
        assertEquals(6, edit.selectionEnd)
    }

    @org.junit.After
    @Before
    fun clear() {
        SessionStore(InstrumentationRegistry.getInstrumentation().targetContext).clear()
    }

    @Test
    fun login_password_starts_hidden_and_each_click_performs_its_labelled_action() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                a.navigate("login")
                checkField(box(a, "Kata sandi"))
                toggle(box(a, "Kata sandi"), "Tampilkan kata sandi")
                a.navigate("register")
                a.navigate("login")
                assertTrue(
                    box(a, "Kata sandi").editText!!.transformationMethod
                        is PasswordTransformationMethod
                )
            }
        }
    }

    @Test
    fun registration_password_and_confirmation_toggle_independently() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                a.navigate("register")
                val password = box(a, "Kata sandi")
                val confirmation = box(a, "Ulangi kata sandi")
                checkField(password)
                checkField(confirmation)
                toggle(password, "Tampilkan kata sandi")
                assertTrue(
                    confirmation.editText!!.transformationMethod is PasswordTransformationMethod
                )
                assertNotEquals(TextInputLayout.END_ICON_CUSTOM, box(a, "Email").endIconMode)
            }
        }
    }

    @Test
    fun admin_create_user_passwords_also_start_hidden() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                a.state.user = ApiUser(id = 99, name = "Admin test", role = "admin")
                a.session.save("password-ui-test", null)
                a.state.selectedUser = null
                a.navigate("userform")
                checkField(box(a, "Kata sandi"))
                checkField(box(a, "Ulangi kata sandi"))
            }
        }
    }
}
