package com.luv.couple.data

/** Sanfte Zeilen nur für echte Zwei-Personen-Lobbys. */
object CoupleLines {
    private val lines = listOf(
        "Nur ihr beide — eure kleine Welt.",
        "Zwei Farben, eine Leinwand.",
        "Hier bleibt alles zwischen euch.",
        "Kein Publikum. Nur Nähe.",
        "Ein stiller Raum, nur für euch.",
        "Was ihr malt, bleibt bei euch.",
        "Zwei Herzen, eine Fläche.",
        "Heute nur wir."
    )

    fun pick(seed: String = ""): String {
        if (lines.isEmpty()) return "Nur ihr beide."
        val idx = ((seed.hashCode().toLong() and 0x7fffffffL) % lines.size).toInt()
        return lines[idx]
    }

    fun rotate(index: Int): String = lines[index.mod(lines.size)]
}
