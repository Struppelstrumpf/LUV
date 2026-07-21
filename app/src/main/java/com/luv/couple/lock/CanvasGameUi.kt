package com.luv.couple.lock

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luv.couple.R
import kotlin.math.min

object CanvasGameUi {

    data class GameOption(
        val id: String,
        val emoji: String,
        val title: String,
        val desc: String
    )

    fun showGameMenu(
        activity: Activity,
        coins: Int,
        activeGame: String?,
        onStart: (String) -> Unit,
        onStop: () -> Unit,
        onNeedCoins: () -> Unit
    ) {
        val dp = activity.resources.displayMetrics.density
        val screenH = activity.resources.displayMetrics.heightPixels
        val screenW = activity.resources.displayMetrics.widthPixels

        val options = listOf(
            GameOption("words", "✏️", activity.getString(R.string.game_words_title), activity.getString(R.string.game_words_desc)),
            GameOption("hangman", "🪢", activity.getString(R.string.game_hangman_title), activity.getString(R.string.game_hangman_desc)),
            GameOption("connect4", "🔴", activity.getString(R.string.game_connect4_title), activity.getString(R.string.game_connect4_desc)),
            GameOption("memory", "🃏", activity.getString(R.string.game_memory_title), activity.getString(R.string.game_memory_desc)),
            GameOption("quiz", "💜", activity.getString(R.string.game_quiz_title), activity.getString(R.string.game_quiz_desc)),
            GameOption("reaction", "⚡", activity.getString(R.string.game_reaction_title), activity.getString(R.string.game_reaction_desc)),
            GameOption("categories", "🏷️", activity.getString(R.string.game_categories_title), activity.getString(R.string.game_categories_desc)),
            GameOption("emoji", "🧩", activity.getString(R.string.game_emoji_title), activity.getString(R.string.game_emoji_desc)),
            GameOption("rather", "⚖️", activity.getString(R.string.game_rather_title), activity.getString(R.string.game_rather_desc)),
            GameOption("taprace", "💓", activity.getString(R.string.game_taprace_title), activity.getString(R.string.game_taprace_desc)),
            GameOption("story", "📖", activity.getString(R.string.game_story_title), activity.getString(R.string.game_story_desc)),
            GameOption("ttt", "✕◯", activity.getString(R.string.game_ttt_title), activity.getString(R.string.game_ttt_desc))
        )

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * dp).toInt(), (8 * dp).toInt(), (18 * dp).toInt(), (12 * dp).toInt())
        }
        root.addView(
            TextView(activity).apply {
                text = "Dein Guthaben: $coins Coins"
                setTextColor(0xFFFF6B8A.toInt())
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, (10 * dp).toInt())
            }
        )
        root.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.game_menu_hint)
                setTextColor(0xFF9AA3B2.toInt())
                textSize = 13f
                setPadding(0, 0, 0, (12 * dp).toInt())
            }
        )

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.game_menu_title)
            .create()

        options.forEach { opt ->
            val active = activeGame == opt.id
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = 18 * dp
                    setColor(if (active) 0xFF2A2030.toInt() else 0xFF171C24.toInt())
                    setStroke((1.2f * dp).toInt(), if (active) 0xFFFF6B8A.toInt() else 0x33FFFFFF)
                }
                setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (10 * dp).toInt() }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dialog.dismiss()
                    if (active) {
                        onStart(opt.id) // stop path handled by caller
                        return@setOnClickListener
                    }
                    if (coins < 1) {
                        onNeedCoins()
                    } else {
                        onStart(opt.id)
                    }
                }
            }
            card.addView(
                TextView(activity).apply {
                    text = "${opt.emoji}  ${opt.title}"
                    setTextColor(0xFFF4F1EC.toInt())
                    textSize = 17f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
            )
            card.addView(
                TextView(activity).apply {
                    text = opt.desc
                    setTextColor(0xFF9AA3B2.toInt())
                    textSize = 12.5f
                    setPadding(0, (4 * dp).toInt(), 0, (6 * dp).toInt())
                }
            )
            card.addView(
                TextView(activity).apply {
                    text = if (active) "Aktiv" else activity.getString(R.string.game_cost_label)
                    setTextColor(if (active) 0xFF3DDC97.toInt() else 0xFFFF6B8A.toInt())
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                }
            )
            root.addView(card)
        }

        if (!activeGame.isNullOrBlank()) {
            root.addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.game_stop)
                    gravity = Gravity.CENTER
                    setTextColor(0xFF9AA3B2.toInt())
                    textSize = 14f
                    setPadding(0, (8 * dp).toInt(), 0, (4 * dp).toInt())
                    isClickable = true
                    setOnClickListener {
                        dialog.dismiss()
                        onStop()
                    }
                }
            )
        }

        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(root)
        }
        dialog.setView(scroll)
        dialog.show()
        dialog.window?.setLayout(
            min(screenW - (28 * dp).toInt(), (400 * dp).toInt()),
            (screenH * 0.78f).toInt()
        )
    }

    fun showWordPick(
        activity: Activity,
        options: List<String>,
        title: String? = null,
        onPick: (String) -> Unit
    ) {
        if (options.isEmpty()) return
        val dp = activity.resources.displayMetrics.density
        val wrap = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title?.takeIf { it.isNotBlank() } ?: activity.getString(R.string.game_pick_word))
            .setCancelable(false)
            .create()
        options.forEach { word ->
            val btn = TextView(activity).apply {
                text = word
                gravity = Gravity.CENTER
                setTextColor(0xFFF4F1EC.toInt())
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 16 * dp
                    setColor(0xFFFF6B8A.toInt())
                }
                setPadding((12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (10 * dp).toInt() }
                setOnClickListener {
                    dialog.dismiss()
                    onPick(word)
                }
            }
            wrap.addView(btn)
        }
        dialog.setView(wrap)
        dialog.show()
    }

    fun showGuessDialog(
        activity: Activity,
        onGuess: (String) -> Unit
    ) {
        val dp = activity.resources.displayMetrics.density
        val input = EditText(activity).apply {
            hint = "Dein Tipp…"
            setHintTextColor(0xFF6B7380.toInt())
            setTextColor(0xFFF4F1EC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            filters = arrayOf(InputFilter.LengthFilter(40))
            setPadding((14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 14 * dp
                setColor(0xFF171C24.toInt())
                setStroke((1f * dp).toInt(), 0x44FFFFFF)
            }
        }
        val pad = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * dp).toInt(), (10 * dp).toInt(), (18 * dp).toInt(), (4 * dp).toInt())
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.game_guess_btn)
            .setView(pad)
            .setPositiveButton("Raten") { d, _ ->
                val text = input.text?.toString()?.trim().orEmpty()
                d.dismiss()
                if (text.isNotBlank()) onGuess(text)
            }
            .setNegativeButton("Abbrechen", null)
            .create()
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isNotBlank()) {
                    dialog.dismiss()
                    onGuess(text)
                }
                true
            } else {
                false
            }
        }
        dialog.show()
        input.requestFocus()
    }

    fun showColorPickerSheet(
        activity: Activity,
        mine: Int,
        taken: Set<Int>,
        colorCount: Int,
        strokeColor: (Int) -> Int,
        onPick: (Int) -> Unit
    ) {
        val dm = activity.resources.displayMetrics
        val dp = dm.density
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        // Schmale Displays: 4 Spalten, sonst 5 — verhindert Abschneiden
        val cols = if (screenW < (360 * dp).toInt()) 4 else 5
        val rows = (colorCount + cols - 1) / cols

        val dialogW = (screenW - (28 * dp).toInt()).coerceAtMost((340 * dp).toInt())
        val sidePad = (12 * dp).toInt()
        val gap = (6 * dp).toInt()
        val gridW = dialogW - sidePad * 2
        val cellByW = (gridW - gap * (cols - 1)) / cols
        val usableH = (screenH * 0.42f).toInt().coerceIn((160 * dp).toInt(), (280 * dp).toInt())
        val cellByH = (usableH - gap * (rows - 1)) / rows
        val cell = min(cellByW, cellByH).coerceIn((28 * dp).toInt(), (48 * dp).toInt())

        val wrap = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sidePad, (4 * dp).toInt(), sidePad, (8 * dp).toInt())
            gravity = Gravity.CENTER_HORIZONTAL
        }
        wrap.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.color_picker_hint)
                setTextColor(0xFF9AA3B2.toInt())
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, (10 * dp).toInt())
            }
        )

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.color_picker_title)
            .create()

        val grid = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        var index = 0
        repeat(rows) { rowIdx ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                if (rowIdx > 0) {
                    setPadding(0, gap, 0, 0)
                }
            }
            repeat(cols) { colIdx ->
                if (index >= colorCount) return@repeat
                val colorIndex = index
                val blocked = colorIndex in taken && colorIndex != mine
                val swatch = View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(cell, cell).apply {
                        if (colIdx > 0) marginStart = gap
                    }
                    alpha = if (blocked) 0.28f else 1f
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(strokeColor(colorIndex))
                        if (colorIndex == mine) {
                            setStroke((3f * dp).toInt(), 0xFFFFFFFF.toInt())
                        } else {
                            setStroke((1.2f * dp).toInt(), 0x33FFFFFF)
                        }
                    }
                    isClickable = !blocked
                    isEnabled = !blocked
                    setOnClickListener {
                        if (blocked) return@setOnClickListener
                        onPick(colorIndex)
                        dialog.dismiss()
                    }
                }
                row.addView(swatch)
                index++
            }
            grid.addView(row)
        }
        val scroller = android.widget.ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(grid)
        }
        wrap.addView(scroller)
        dialog.setView(wrap)
        dialog.show()
        dialog.window?.setLayout(dialogW, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
