package com.luv.couple.lock

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

/**
 * Schönes Overlay für interaktive Lobby-Spiele (Galgenmännchen, Vier gewinnt, …).
 */
class GamePlayOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var myPeerHint: String? = null
    var onAction: ((action: String, payload: JSONObject) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val card: LinearLayout
    private val titleView: TextView
    private val subtitleView: TextView
    private val body: LinearLayout
    private val footer: TextView
    private var lastType: String? = null
    private var lastJson: String? = null
    private var lastGame: JSONObject? = null

    init {
        visibility = View.GONE
        setBackgroundColor(0xCC0B0E14.toInt())
        isClickable = true

        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 28 * dp
                setColor(0xFF141A24.toInt())
                setStroke((1.2f * dp).toInt(), 0x33FFFFFF)
            }
            setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (14 * dp).toInt())
            elevation = 24f
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                marginStart = (16 * dp).toInt()
                marginEnd = (16 * dp).toInt()
            }
        }
        titleView = TextView(context).apply {
            setTextColor(0xFFF4F1EC.toInt())
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        subtitleView = TextView(context).apply {
            setTextColor(0xFF9AA3B2.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, (4 * dp).toInt(), 0, (10 * dp).toInt())
        }
        body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        footer = TextView(context).apply {
            text = "Spiel beenden"
            gravity = Gravity.CENTER
            setTextColor(0xFF9AA3B2.toInt())
            textSize = 13f
            setPadding(0, (12 * dp).toInt(), 0, (2 * dp).toInt())
            setOnClickListener { onClose?.invoke() }
        }
        card.addView(titleView)
        card.addView(subtitleView)
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(body)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.52f).toInt()
            )
        }
        card.addView(scroll)
        card.addView(footer)
        addView(card)
    }

    fun hideGame() {
        visibility = View.GONE
        lastType = null
        lastJson = null
        lastGame = null
        body.removeAllViews()
    }

    /** Nur Countdown-Zeile aktualisieren (ohne Tastatur/Board neu zu bauen). */
    fun tickClock() {
        val game = lastGame ?: return
        if (visibility != View.VISIBLE) return
        val status = game.optString("status")
        if (status == "ended") return
        subtitleView.text = subtitleFor(game.optString("type"), game)
    }

    fun showState(game: JSONObject) {
        val type = game.optString("type")
        val raw = game.toString()
        // Voll neu zeichnen wenn Typ wechselt oder Status/mask sich ändert
        if (visibility == View.VISIBLE && type == lastType && raw == lastJson) {
            tickClock()
            return
        }
        lastType = type
        lastJson = raw
        lastGame = game
        visibility = View.VISIBLE

        val status = game.optString("status")
        val ended = status == "ended"
        titleView.text = titleFor(type)
        subtitleView.text = when {
            ended -> game.optString("message").ifBlank { "Runde vorbei" }
            else -> subtitleFor(type, game)
        }

        body.removeAllViews()
        when (type) {
            "hangman" -> renderHangman(game, ended)
            "connect4" -> renderConnect4(game, ended)
            "memory" -> renderMemory(game, ended)
            "quiz" -> renderQuiz(game, ended)
            "reaction" -> renderReaction(game, ended)
            "categories" -> renderCategories(game, ended)
            "emoji" -> renderEmoji(game, ended)
            "rather" -> renderRather(game, ended)
            "taprace" -> renderTapRace(game, ended)
            "story" -> renderStory(game, ended)
            else -> body.addView(label("Unbekanntes Spiel"))
        }

        if (ended) {
            body.addView(scoreBoard(game))
        }
    }

    private fun titleFor(type: String) = when (type) {
        "hangman" -> "🪢  Galgenmännchen"
        "connect4" -> "🔴  Vier gewinnt"
        "memory" -> "🃏  Memory Herzen"
        "quiz" -> "💜  Herz-Quiz"
        "reaction" -> "⚡  Blitz!"
        "categories" -> "🏷️  Kategorie-Blitz"
        "emoji" -> "🧩  Emoji-Rätsel"
        "rather" -> "⚖️  Was eher?"
        "taprace" -> "💓  Herzchen-Jagd"
        "story" -> "📖  Kettenwort"
        else -> "Spiel"
    }

    private fun subtitleFor(type: String, game: JSONObject): String {
        val endsAt = game.optLong("endsAt", 0L)
        val clock = if (endsAt > 0) {
            val left = ((endsAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
            " · ${left}s"
        } else ""
        return when (type) {
            "hangman" -> "Gemeinsam retten — ${game.optInt("lives")} Leben$clock"
            "connect4" -> "Abwechselnd setzen$clock"
            "memory" -> "Paare finden$clock"
            "quiz" -> "Runde ${game.optInt("round")}/${game.optInt("maxRounds")}$clock"
            "reaction" -> if (game.optString("phase") == "go") "JETZT tippen!" else "Warten…"
            "categories" -> "Kategorie: ${game.optString("category")}$clock"
            "emoji" -> "Was bedeuten die Emojis?$clock"
            "rather" -> "Stimmt ab$clock"
            "taprace" -> "Tippt so oft ihr könnt$clock"
            "story" -> "Hängt ein Wort an"
            else -> ""
        }
    }

    private fun renderHangman(game: JSONObject, ended: Boolean) {
        val lives = game.optInt("lives", 0)
        val gallows = when {
            lives >= 7 -> "☁️"
            lives == 6 -> "╷"
            lives == 5 -> "┌─"
            lives == 4 -> "┌─╴"
            lives == 3 -> "┌─╴🙂"
            lives == 2 -> "┌─╴😮"
            lives == 1 -> "┌─╴😰"
            else -> "┌─╴💀"
        }
        body.addView(
            TextView(context).apply {
                text = gallows
                textSize = 36f
                gravity = Gravity.CENTER
                setTextColor(0xFFF4F1EC.toInt())
                setPadding(0, 0, 0, (8 * dp).toInt())
            }
        )
        body.addView(
            TextView(context).apply {
                text = game.optString("mask").toList().joinToString(" ")
                textSize = 28f
                letterSpacing = 0.08f
                gravity = Gravity.CENTER
                setTextColor(0xFFFF6B8A.toInt())
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, (12 * dp).toInt())
            }
        )
        if (!ended) {
            val row = ChipWrap(context)
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { ch ->
                val key = ch.lowercaseChar().toString()
                val used = jsonArrayContains(game.optJSONArray("guessed"), key) ||
                    jsonArrayContains(game.optJSONArray("wrong"), key)
                row.addChip(chip(ch.toString(), enabled = !used) {
                    onAction?.invoke("letter", JSONObject().put("letter", key))
                })
            }
            body.addView(row)
        } else if (!game.optString("word").isNullOrBlank()) {
            body.addView(label("Wort: ${game.optString("word")}"))
        }
    }

    private fun renderConnect4(game: JSONObject, ended: Boolean) {
        val board = game.optJSONArray("board") ?: return
        val grid = GridLayout(context).apply {
            columnCount = 7
            rowCount = 6
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val cell = (min(resources.displayMetrics.widthPixels - 80 * dp, 320 * dp) / 7f).toInt()
        for (r in 0 until 6) {
            val row = board.optJSONArray(r) ?: continue
            for (c in 0 until 7) {
                val v = row.opt(c)
                val filled = v != null && v != JSONObject.NULL
                val color = when {
                    !filled -> 0x22FFFFFF
                    row.optInt(c, -1) == 0 -> 0xFF00B7E4.toInt()
                    else -> 0xFFC218A8.toInt()
                }
                val slot = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                    }
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = cell - (4 * dp).toInt()
                        height = cell - (4 * dp).toInt()
                        setMargins((2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt())
                    }
                    if (!ended && !filled) {
                        isClickable = true
                        setOnClickListener {
                            onAction?.invoke("drop", JSONObject().put("col", c))
                        }
                    }
                }
                grid.addView(slot)
            }
        }
        // Tippen auf Spalte: oben eine Reihe Buttons
        if (!ended) {
            val cols = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (c in 0 until 7) {
                cols.addView(
                    TextView(context).apply {
                        text = "↓"
                        gravity = Gravity.CENTER
                        setTextColor(0xFFF4F1EC.toInt())
                        textSize = 16f
                        background = GradientDrawable().apply {
                            cornerRadius = 10 * dp
                            setColor(0xFF1E2633.toInt())
                        }
                        layoutParams = LinearLayout.LayoutParams(cell, (36 * dp).toInt()).apply {
                            marginStart = (2 * dp).toInt()
                            marginEnd = (2 * dp).toInt()
                        }
                        setOnClickListener {
                            onAction?.invoke("drop", JSONObject().put("col", c))
                        }
                    }
                )
            }
            body.addView(cols)
        }
        body.addView(grid)
    }

    private fun renderMemory(game: JSONObject, ended: Boolean) {
        val cards = game.optJSONArray("cards") ?: return
        val grid = GridLayout(context).apply {
            columnCount = 4
            rowCount = 4
        }
        val cell = (min(resources.displayMetrics.widthPixels - 72 * dp, 300 * dp) / 4f).toInt()
        for (i in 0 until cards.length()) {
            val c = cards.optJSONObject(i) ?: continue
            val open = c.optBoolean("open") || c.optBoolean("matched")
            val emoji = c.optString("emoji").ifBlank { "✦" }
            grid.addView(
                TextView(context).apply {
                    text = if (open) emoji else "💜"
                    gravity = Gravity.CENTER
                    textSize = if (open) 22f else 18f
                    background = GradientDrawable().apply {
                        cornerRadius = 14 * dp
                        setColor(if (c.optBoolean("matched")) 0xFF2A4035.toInt() else 0xFF1E2633.toInt())
                        setStroke((1f * dp).toInt(), if (open) 0xFFFF6B8A.toInt() else 0x33FFFFFF)
                    }
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = cell - (6 * dp).toInt()
                        height = cell - (6 * dp).toInt()
                        setMargins((3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt())
                    }
                    if (!ended && !open) {
                        setOnClickListener {
                            onAction?.invoke("flip", JSONObject().put("id", c.optInt("id")))
                        }
                    }
                }
            )
        }
        body.addView(grid)
    }

    private fun renderQuiz(game: JSONObject, ended: Boolean) {
        body.addView(
            TextView(context).apply {
                text = game.optString("question")
                setTextColor(0xFFF4F1EC.toInt())
                textSize = 18f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, (14 * dp).toInt())
            }
        )
        val answers = game.optJSONArray("answers") ?: return
        for (i in 0 until answers.length()) {
            val text = answers.optString(i)
            body.addView(
                primaryBtn(text) {
                    if (!ended) onAction?.invoke("answer", JSONObject().put("index", i))
                }
            )
        }
    }

    private fun renderReaction(game: JSONObject, ended: Boolean) {
        val phase = game.optString("phase")
        val big = TextView(context).apply {
            text = when {
                ended -> "🏁"
                phase == "go" -> "JETZT!"
                else -> "…"
            }
            textSize = if (phase == "go") 48f else 40f
            gravity = Gravity.CENTER
            setTextColor(if (phase == "go") 0xFF3DDC97.toInt() else 0xFF9AA3B2.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (20 * dp).toInt(), 0, (20 * dp).toInt())
        }
        body.addView(big)
        if (!ended) {
            body.addView(
                primaryBtn(if (phase == "go") "Tippen!" else "Noch nicht…") {
                    onAction?.invoke("tap", JSONObject())
                }
            )
        }
    }

    private fun renderCategories(game: JSONObject, ended: Boolean) {
        body.addView(
            TextView(context).apply {
                text = game.optString("category")
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(0xFFFF6B8A.toInt())
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, (10 * dp).toInt())
            }
        )
        if (!ended) {
            body.addView(textEntry("Wort eingeben…") { word ->
                onAction?.invoke("word", JSONObject().put("text", word))
            })
        }
        val entries = game.optJSONObject("entries")
        if (entries != null) {
            val keys = entries.keys()
            while (keys.hasNext()) {
                val nick = keys.next()
                val arr = entries.optJSONArray(nick)
                val words = buildList {
                    if (arr != null) for (i in 0 until arr.length()) add(arr.optString(i))
                }.joinToString(", ")
                body.addView(label("$nick: $words"))
            }
        }
    }

    private fun renderEmoji(game: JSONObject, ended: Boolean) {
        body.addView(
            TextView(context).apply {
                text = game.optString("puzzle")
                textSize = 34f
                gravity = Gravity.CENTER
                setPadding(0, (8 * dp).toInt(), 0, (16 * dp).toInt())
            }
        )
        if (!ended) {
            body.addView(textEntry("Lösung tippen…") { word ->
                onAction?.invoke("guess", JSONObject().put("text", word))
            })
        } else if (!game.optString("solvedBy").isNullOrBlank()) {
            body.addView(label("${game.optString("solvedBy")} hat’s!"))
        }
    }

    private fun renderRather(game: JSONObject, ended: Boolean) {
        val revealed = game.optBoolean("revealed") || ended
        body.addView(
            primaryBtn(
                if (revealed) "A · ${game.optString("optionA")} (${game.optInt("tallyA")})"
                else game.optString("optionA")
            ) {
                if (!ended) onAction?.invoke("vote", JSONObject().put("side", "a"))
            }
        )
        body.addView(
            TextView(context).apply {
                text = "oder"
                gravity = Gravity.CENTER
                setTextColor(0xFF9AA3B2.toInt())
                setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            }
        )
        body.addView(
            primaryBtn(
                if (revealed) "B · ${game.optString("optionB")} (${game.optInt("tallyB")})"
                else game.optString("optionB")
            ) {
                if (!ended) onAction?.invoke("vote", JSONObject().put("side", "b"))
            }
        )
        body.addView(label("${game.optInt("voteCount")} Stimmen"))
    }

    private fun renderTapRace(game: JSONObject, ended: Boolean) {
        if (!ended) {
            body.addView(
                TextView(context).apply {
                    text = "❤️"
                    textSize = 72f
                    gravity = Gravity.CENTER
                    setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
                    isClickable = true
                    setOnClickListener {
                        animate().scaleX(0.9f).scaleY(0.9f).setDuration(60).withEndAction {
                            animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                        }.start()
                        onAction?.invoke("tap", JSONObject())
                    }
                }
            )
            body.addView(label("Tippe das Herz so oft du kannst!"))
        }
        val taps = game.optJSONObject("taps")
        if (taps != null) {
            val keys = taps.keys()
            while (keys.hasNext()) {
                val nick = keys.next()
                body.addView(label("$nick: ${taps.optInt(nick)} ❤️"))
            }
        }
    }

    private fun renderStory(game: JSONObject, ended: Boolean) {
        val words = game.optJSONArray("words")
        val story = buildString {
            if (words != null) {
                for (i in 0 until words.length()) {
                    val w = words.optJSONObject(i) ?: continue
                    if (isNotEmpty()) append(' ')
                    append(w.optString("text"))
                }
            }
        }
        body.addView(
            TextView(context).apply {
                text = story.ifBlank { "Noch leer — schreibt eure Geschichte…" }
                setTextColor(0xFFF4F1EC.toInt())
                textSize = 17f
                gravity = Gravity.CENTER
                setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt())
            }
        )
        if (!ended) {
            body.addView(label("${words?.length() ?: 0}/${game.optInt("maxWords")} Wörter"))
            body.addView(textEntry("Dein Wort…") { word ->
                onAction?.invoke("word", JSONObject().put("text", word))
            })
        }
    }

    private fun scoreBoard(game: JSONObject): View {
        val scores = game.optJSONArray("scores") ?: return label("")
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (12 * dp).toInt(), 0, 0)
        }
        box.addView(label("Punkte"))
        for (i in 0 until scores.length()) {
            val s = scores.optJSONObject(i) ?: continue
            box.addView(label("${s.optString("nickname")}: ${s.optInt("score")}"))
        }
        return box
    }

    private fun label(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(0xFF9AA3B2.toInt())
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(0, (3 * dp).toInt(), 0, (3 * dp).toInt())
    }

    private fun primaryBtn(text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(0xFFF4F1EC.toInt())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 16 * dp
                setColor(0xFFFF6B8A.toInt())
            }
            setPadding((12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
            setOnClickListener { onClick() }
        }
    }

    private fun chip(text: String, enabled: Boolean, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(if (enabled) 0xFFF4F1EC.toInt() else 0x55FFFFFF)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 12 * dp
                setColor(if (enabled) 0xFF1E2633.toInt() else 0xFF12161E.toInt())
            }
            val size = (36 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins((3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt())
            }
            isEnabled = enabled
            if (enabled) setOnClickListener { onClick() }
        }
    }

    private fun textEntry(hint: String, onSend: (String) -> Unit): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
        }
        val input = EditText(context).apply {
            this.hint = hint
            setHintTextColor(0xFF6B7380.toInt())
            setTextColor(0xFFF4F1EC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            filters = arrayOf(InputFilter.LengthFilter(32))
            background = GradientDrawable().apply {
                cornerRadius = 14 * dp
                setColor(0xFF0E131A.toInt())
                setStroke((1f * dp).toInt(), 0x33FFFFFF)
            }
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sendAction = {
            val t = input.text?.toString()?.trim().orEmpty()
            if (t.isNotBlank()) {
                onSend(t)
                input.text?.clear()
            }
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                sendAction()
                true
            } else {
                false
            }
        }
        val send = TextView(context).apply {
            text = "→"
            gravity = Gravity.CENTER
            setTextColor(0xFF0E1116.toInt())
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 14 * dp
                setColor(0xFFFF6B8A.toInt())
            }
            layoutParams = LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt()).apply {
                marginStart = (8 * dp).toInt()
            }
            setOnClickListener { sendAction() }
        }
        row.addView(input)
        row.addView(send)
        return row
    }

    private fun jsonArrayContains(arr: JSONArray?, value: String): Boolean {
        if (arr == null) return false
        for (i in 0 until arr.length()) {
            if (arr.optString(i) == value) return true
        }
        return false
    }

    /** Wrap-Layout für Buchstaben-Chips */
    private class ChipWrap(context: Context) : LinearLayout(context) {
        private val wrap = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        private var current: LinearLayout
        private val maxW =
            (context.resources.displayMetrics.widthPixels - 64 * context.resources.displayMetrics.density).toInt()
        private var used = 0

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            current = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
            }
            wrap.addView(current)
            addView(wrap)
        }

        fun addChip(child: View) {
            val w = (36 * context.resources.displayMetrics.density).toInt() +
                (6 * context.resources.displayMetrics.density).toInt()
            if (used + w > maxW && used > 0) {
                current = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER
                }
                wrap.addView(current)
                used = 0
            }
            current.addView(child)
            used += w
        }
    }
}
