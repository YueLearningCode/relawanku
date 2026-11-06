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
import com.bumptech.glide.Glide
import com.example.relawanku.MainActivity
import com.example.relawanku.R
import com.example.relawanku.data.*
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

    fun text(value: String, size: Int = 15, weight: Int = 400, tint: Int = ink): TextView =
        TextView(context).apply {
            text = value
            textSize = size.toFloat()
            typeface = font(weight)
            setTextColor(tint)
            includeFontPadding = false
            setLineSpacing(dp(2).toFloat(), 1f)
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

    fun background(fill: Int, radius: Int = 14, stroke: Int? = null) =
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
                    14,
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
            textSize = 14f
            cornerRadius = dp(12)
            backgroundTintList =
                ColorStateList.valueOf(if (secondary) color(R.color.surface) else primary)
            setTextColor(if (secondary) ink else Color.WHITE)
            strokeWidth = if (secondary) dp(1) else 0
            strokeColor = ColorStateList.valueOf(color(R.color.outline))
            setPadding(dp(16), dp(8), dp(16), dp(8))
            minimumHeight = dp(48)
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
            background = background(color(R.color.surface), 12, color(R.color.outline))
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
        val titleView = text(title, 18, 700)
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
                        dp(12).toFloat(),
                        dp(12).toFloat(),
                        dp(12).toFloat(),
                        dp(12).toFloat(),
                    )
                    boxStrokeColor = primary
                    boxBackgroundColor = color(R.color.surface)
                    boxStrokeWidth = dp(1)
                    boxStrokeWidthFocused = dp(2)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
                }
        val edit =
            TextInputEditText(box.context).apply {
                inputType = type
                setText(value)
                typeface = font()
                textSize = 14f
                minimumHeight = dp(48)
                setTextColor(ink)
                setSingleLine(type and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0)
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
                if (type and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) {
                    minLines = 3
                    maxLines = 5
                    gravity = Gravity.TOP or Gravity.START
                }
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
                background = background(fill, 8)
                setPadding(dp(10), dp(5), dp(10), dp(5))
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
        val p = panel(parent, color(R.color.surface), 18)
        val heading = row()
        heading.addView(icon(R.drawable.ic_info, 22, muted))
        heading.addView(
            text(title, 16, 700),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) },
        )
        p.addView(heading)
        gap(p, 8)
        p.addView(text(description, 14, 400, muted))
    }

    fun eyebrow(parent: LinearLayout, value: String) {
        parent.addView(
            text(value.uppercase(java.util.Locale.ROOT), 10, 700, muted).apply {
                letterSpacing = .12f
            }
        )
        gap(parent, 8)
    }

    fun avatar(name: String, size: Int = 48): TextView =
        text(
                name
                    .trim()
                    .split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }
                    .take(2)
                    .joinToString("") { it.take(1) }
                    .uppercase(java.util.Locale.ROOT),
                17,
                700,
                primary,
            )
            .apply {
                gravity = Gravity.CENTER
                background = background(color(R.color.sage), size / 2)
                layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

    fun dashboardHeader(
        parent: LinearLayout,
        role: String,
        name: String,
        title: String,
        openProfile: () -> Unit,
    ) {
        val top = row()
        val labels = column()
        eyebrow(labels, "Relawanku / " + role)
        labels.addView(text(title, 24, 700))
        top.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(
            avatar(name).apply {
                contentDescription = "Buka profil"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                isClickable = true
                isFocusable = true
                setOnClickListener { openProfile() }
            }
        )
        parent.addView(top)
        gap(parent, 6)
        parent.addView(text("Selamat datang, " + name.substringBefore(' ') + ".", 13, 400, muted))
        gap(parent, 22)
    }

    fun metrics(parent: LinearLayout, values: List<Pair<String, Int>>, columns: Int = 2) {
        for (chunk in values.chunked(columns)) {
            val line = row()
            chunk.forEachIndexed { index, (label, number) ->
                val card = column(if (columns == 3) 12 else 16)
                card.background = background(color(R.color.surface), 14, color(R.color.outline))
                card.addView(
                    text(String.format(java.util.Locale.getDefault(), "%d", number), 26, 700)
                )
                gap(card, 6)
                card.addView(
                    text(label, 12, 600, muted).apply {
                        if (columns == 3) minLines = 2
                        maxLines = 2
                    }
                )
                line.addView(
                    card,
                    LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (index > 0) marginStart = dp(10)
                    },
                )
            }
            parent.addView(line)
            gap(parent, 12)
        }
    }

    fun actionRow(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        icon: Int,
        danger: Boolean = false,
        action: () -> Unit,
    ) {
        val line = row()
        line.minimumHeight = dp(68)
        line.setPadding(dp(14), dp(12), dp(14), dp(12))
        line.background = background(color(R.color.surface), 12, color(R.color.outline))
        val tint = if (danger) color(R.color.error) else primary
        line.addView(icon(icon, 22, tint))
        val words = column()
        words.addView(text(title, 14, 700, if (danger) tint else ink))
        if (subtitle.isNotBlank()) {
            gap(words, 3)
            words.addView(text(subtitle, 12, 400, muted))
        }
        line.addView(
            words,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(8)
            },
        )
        line.addView(icon(R.drawable.ic_arrow, 18, muted))
        line.isClickable = true
        line.isFocusable = true
        line.setOnClickListener { action() }
        val attrs =
            context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        line.foreground = attrs.getDrawable(0)
        attrs.recycle()
        parent.addView(line)
        gap(parent, 10)
    }

    fun inverseButton(parent: LinearLayout, label: String, action: () -> Unit) =
        button(parent, label, true, action).apply {
            backgroundTintList = ColorStateList.valueOf(color(R.color.surface))
            setTextColor(color(R.color.primary_dark))
            strokeWidth = 0
        }

    fun eventCard(
        parent: LinearLayout,
        event: ActivityDto,
        joined: Boolean,
        onClick: () -> Unit,
    ) {
        val card = LayoutInflater.from(context).inflate(R.layout.item_activity, parent, false)
        image(card.findViewById(R.id.card_image), event.coverUrl, event.image)
        card.findViewById<ImageView>(R.id.card_image).contentDescription =
            if (event.coverUrl == null) "Ilustrasi kategori " + event.category
            else "Sampul " + event.title
        card
            .findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(
                R.id.card_capacity
            )
            .progress =
            if (event.quota > 0)
                ((event.participants.toLong() * 100) / event.quota).toInt().coerceIn(0, 100)
            else 0
        card.findViewById<TextView>(R.id.card_category).text = event.category
        card.findViewById<TextView>(R.id.card_date).text =
            event.date + " · " + Dates.time(event.startsAt)
        card.findViewById<TextView>(R.id.card_title).text = event.title
        card.findViewById<TextView>(R.id.card_location).text = event.location
        card.findViewById<TextView>(R.id.card_quota).text =
            "${event.participants}/${event.quota} relawan"
        card.findViewById<TextView>(R.id.card_status).text =
            when {
                event.deletedAt != null -> "Diarsipkan"
                joined -> "Terdaftar · ${statusLabel(event.status)}"
                event.registrationOpen() -> "Pendaftaran dibuka"
                event.status == "open" -> "Pendaftaran tidak tersedia"
                else -> statusLabel(event.status)
            }
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { onClick() }
        parent.addView(card)
    }

    fun image(view: ImageView, url: String?, placeholder: Int = R.drawable.activity_coast) {
        view.scaleType = ImageView.ScaleType.CENTER_CROP
        Glide.with(context)
            .load(ServerAddress.media((context as MainActivity).session.baseUrl, url))
            .placeholder(placeholder)
            .error(placeholder)
            .centerCrop()
            .into(view)
    }
}
