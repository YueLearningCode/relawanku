package com.example.relawanku.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.relawanku.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class UiKit(val context: Context) {
    val primary = color(R.color.primary)
    val ink = color(R.color.ink)
    val muted = color(R.color.muted)

    fun color(id: Int) = ContextCompat.getColor(context, id)

    fun dp(value: Int) = (context.resources.displayMetrics.density * value).toInt()

    fun font(weight: Int = 400) =
        ResourcesCompat.getFont(
            context,
            when (weight) {
                700 -> R.font.nunito_bold
                600 -> R.font.nunito_semibold
                else -> R.font.nunito_regular
            },
        )

    fun text(value: String, size: Int = 16, weight: Int = 400, tint: Int = ink): TextView =
        TextView(context).apply {
            text = value
            textSize = size.toFloat()
            typeface = font(weight)
            setTextColor(tint)
            includeFontPadding = false
            setLineSpacing(dp(3).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

    fun column(padding: Int = 0): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

    fun row(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

    fun gap(parent: LinearLayout, height: Int = 16) {
        parent.addView(Space(context), LinearLayout.LayoutParams(1, dp(height)))
    }

    fun background(fill: Int, radius: Int = 18, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            stroke?.let { setStroke(dp(1), it) }
        }

    fun panel(
        parent: LinearLayout,
        fill: Int = color(R.color.surface),
        padding: Int = 18,
    ): LinearLayout =
        column(padding).also {
            it.background =
                background(
                    fill,
                    20,
                    if (fill == color(R.color.surface)) color(R.color.outline) else null,
                )
            parent.addView(it)
            gap(parent)
        }

    fun button(
        parent: LinearLayout,
        label: String,
        secondary: Boolean = false,
        action: () -> Unit,
    ): MaterialButton =
        MaterialButton(context).apply {
            text = label
            isAllCaps = false
            typeface = font(700)
            textSize = 16f
            cornerRadius = dp(16)
            backgroundTintList =
                ColorStateList.valueOf(if (secondary) color(R.color.sage) else primary)
            setTextColor(if (secondary) color(R.color.primary_dark) else Color.WHITE)
            minimumHeight = dp(56)
            insetTop = 0
            insetBottom = 0
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            setOnClickListener { action() }
            parent.addView(this)
        }

    fun icon(id: Int, size: Int = 24, tint: Int = primary): ImageView =
        ImageView(context).apply {
            setImageResource(id)
            imageTintList = ColorStateList.valueOf(tint)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    fun iconAction(id: Int, label: String, action: () -> Unit): View =
        FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            contentDescription = label
            isClickable = true
            isFocusable = true
            background = background(color(R.color.sage), 16)
            addView(icon(id), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { action() }
        }

    fun section(
        parent: LinearLayout,
        title: String,
        actionLabel: String? = null,
        action: (() -> Unit)? = null,
    ) {
        gap(parent, 12)
        val r = row()
        val titleView = text(title, 20, 700)
        r.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        if (actionLabel != null) {
            r.addView(
                text(actionLabel, 14, 700, primary).apply {
                    layoutParams = LinearLayout.LayoutParams(-2, dp(48))
                    gravity = Gravity.CENTER
                    setPadding(dp(10), 0, 0, 0)
                    setOnClickListener { action?.invoke() }
                    isFocusable = true
                }
            )
        }
        parent.addView(r)
        gap(parent, 12)
    }

    fun field(
        parent: LinearLayout,
        label: String,
        value: String = "",
        type: Int = InputType.TYPE_CLASS_TEXT,
    ): Pair<TextInputLayout, TextInputEditText> {
        val box =
            TextInputLayout(
                    context,
                    null,
                    com.google.android.material.R.attr.textInputOutlinedStyle,
                )
                .apply {
                    hint = label
                    boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                    setBoxCornerRadii(
                        dp(14).toFloat(),
                        dp(14).toFloat(),
                        dp(14).toFloat(),
                        dp(14).toFloat(),
                    )
                    boxStrokeColor = primary
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
                }
        val edit =
            TextInputEditText(box.context).apply {
                inputType = type
                setText(value)
                typeface = font()
                textSize = 16f
                minimumHeight = dp(56)
                setTextColor(ink)
                setSingleLine(type and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0)
            }
        box.addView(edit)
        if (
            type and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 &&
                type and InputType.TYPE_CLASS_NUMBER == 0
        )
            box.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        parent.addView(box)
        return box to edit
    }

    fun chips(
        parent: LinearLayout,
        labels: List<String>,
        selected: String,
        onSelect: (String) -> Unit,
    ): ChipGroup {
        val group =
            ChipGroup(context).apply {
                isSingleSelection = true
                isSelectionRequired = true
                chipSpacingHorizontal = dp(6)
            }
        for (label in labels) {
            group.addView(
                Chip(context).apply {
                    text = label
                    typeface = font(600)
                    textSize = 12f
                    isCheckable = true
                    isChecked = label == selected
                    isCheckedIconVisible = false
                    chipMinHeight = dp(40).toFloat()
                    chipBackgroundColor =
                        ColorStateList(
                            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                            intArrayOf(color(R.color.sage), color(R.color.surface)),
                        )
                    chipStrokeColor = ColorStateList.valueOf(color(R.color.outline))
                    chipStrokeWidth = dp(1).toFloat()
                    setTextColor(color(R.color.primary_dark))
                    setOnClickListener { onSelect(label) }
                }
            )
        }
        parent.addView(group)
        gap(parent, 12)
        return group
    }

    fun badge(
        parent: LinearLayout,
        label: String,
        fill: Int = color(R.color.sage),
        tint: Int = color(R.color.primary_dark),
    ) {
        parent.addView(
            text(label, 12, 600, tint).apply {
                background = background(fill, 12)
                setPadding(dp(12), dp(7), dp(12), dp(7))
                layoutParams = LinearLayout.LayoutParams(-2, -2)
            }
        )
    }

    fun meta(parent: LinearLayout, icon: Int, title: String, subtitle: String) {
        val r = row()
        r.setPadding(0, dp(10), 0, dp(10))
        r.addView(icon(icon))
        val col = column()
        r.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) })
        col.addView(text(title, 14, 600))
        col.addView(text(subtitle, 14, 400, muted))
        parent.addView(r)
    }

    fun empty(parent: LinearLayout, title: String, description: String) {
        val p = panel(parent, color(R.color.soft), 24)
        p.addView(icon(R.drawable.ic_leaf, 40))
        gap(p)
        p.addView(text(title, 20, 700))
        gap(p, 8)
        p.addView(text(description, 14, 400, muted))
    }

    fun eventCard(
        parent: LinearLayout,
        event: VolunteerEvent,
        joined: Boolean,
        onClick: () -> Unit,
    ) {
        val card = LayoutInflater.from(context).inflate(R.layout.item_activity, parent, false)
        card.findViewById<ImageView>(R.id.card_image).setImageResource(event.image)
        card.findViewById<TextView>(R.id.card_category).text = event.category
        card.findViewById<TextView>(R.id.card_date).text =
            event.date.substringBefore(" 2026") + " · " + event.time.substringBefore(" –")
        card.findViewById<TextView>(R.id.card_title).text = event.title
        card.findViewById<TextView>(R.id.card_location).text = event.location
        card.findViewById<TextView>(R.id.card_quota).text =
            "${event.participants}/${event.quota} relawan"
        card.findViewById<TextView>(R.id.card_status).text =
            when {
                event.completed -> "Selesai"
                joined -> "Sudah terdaftar"
                event.participants >= event.quota -> "Kuota penuh"
                else -> "Pendaftaran dibuka"
            }
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { onClick() }
        parent.addView(card)
    }
}
