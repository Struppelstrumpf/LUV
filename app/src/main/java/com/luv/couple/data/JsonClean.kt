package com.luv.couple.data

import org.json.JSONObject

/**
 * Android [JSONObject.optString] liefert bei JSON-`null` den Literal-String `"null"`.
 * Damit werden UI-Texte wie „Begriff null“ vermieden.
 */
fun String?.asCleanJsonString(): String? {
    val t = this?.trim().orEmpty()
    if (t.isEmpty()) return null
    if (t.equals("null", ignoreCase = true)) return null
    if (t.equals("undefined", ignoreCase = true)) return null
    return t
}

fun JSONObject.optCleanString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).asCleanJsonString()
}
