package com.example.relawanku.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.LinearLayout
import com.example.relawanku.MainActivity
import com.example.relawanku.data.Dates
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar

class Choice(val box: TextInputLayout, private val read: () -> String) {
    val value
        get() = read()
}

class FormTools(private val a: MainActivity) {
    fun choice(
        parent: LinearLayout,
        label: String,
        options: List<Pair<String, String>>,
        initial: String,
    ): Choice {
        var selected = initial
        val (box, edit) =
            a.ui.field(
                parent,
                label,
                options.firstOrNull { it.first == selected }?.second.orEmpty(),
            )
        edit.isFocusable = false
        edit.isClickable = true
        edit.contentDescription = label
        edit.setOnClickListener {
            MaterialAlertDialogBuilder(a)
                .setTitle(label)
                .setSingleChoiceItems(
                    options.map { it.second }.toTypedArray(),
                    options.indexOfFirst { it.first == selected },
                ) { dialog, index ->
                    selected = options[index].first
                    edit.setText(options[index].second)
                    box.error = null
                    dialog.dismiss()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
        return Choice(box) { selected }
    }

    fun date(parent: LinearLayout, label: String, initial: String = ""): Choice {
        var value = initial
        val (box, edit) =
            a.ui.field(parent, label, if (initial.isBlank()) "" else Dates.full(initial))
        edit.isFocusable = false
        edit.isClickable = true
        edit.setOnClickListener {
            val cal = Calendar.getInstance()
            Dates.parse(value)?.let { cal.time = it }
            DatePickerDialog(
                a,
                { _, year, month, day ->
                    cal.set(year, month, day)
                    TimePickerDialog(
                        a,
                        { _, hour, minute ->
                            cal.set(Calendar.HOUR_OF_DAY, hour)
                            cal.set(Calendar.MINUTE, minute)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            value = Dates.iso(cal.time)
                            edit.setText(Dates.full(value))
                            box.error = null
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        true,
                    )
                        .show()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH),
            )
                .show()
        }
        return Choice(box) { value }
    }
}
