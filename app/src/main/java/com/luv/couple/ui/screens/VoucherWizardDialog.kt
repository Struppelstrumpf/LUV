package com.luv.couple.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luv.couple.net.VoucherItemGrant
import com.luv.couple.shop.ShopCatalog
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.MaleBlue
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary

private enum class VoucherStep(val title: String, val hint: String) {
    Reward("Was gibt’s?", "Coins, Items — oder beides."),
    Limits("Gültigkeit", "Wie lange und für wie viele Personen?"),
    Code("Code wählen", "Nur Buchstaben und Zahlen, mind. 4 Zeichen."),
    Review("Prüfen", "Alles nochmal ansehen und erzeugen.")
}

/**
 * Schritt-für-Schritt Code-Erzeugung für Admin/Mod.
 */
@Composable
fun VoucherWizardDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (
        coins: Int,
        items: List<VoucherItemGrant>,
        maxRedeems: Int,
        validDays: Int,
        forever: Boolean,
        code: String
    ) -> Unit
) {
    var step by remember { mutableStateOf(VoucherStep.Reward) }
    var coins by remember { mutableStateOf("50") }
    var items by remember { mutableStateOf<List<VoucherItemGrant>>(emptyList()) }
    var forever by remember { mutableStateOf(false) }
    var validDays by remember { mutableStateOf("30") }
    var maxPeople by remember { mutableStateOf("100") }
    var code by remember { mutableStateOf("") }
    var pickKind by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.82f).dp

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = maxH)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A1F28), BgDeep)))
                .border(1.dp, AccentRose.copy(0.35f), RoundedCornerShape(26.dp))
                .padding(18.dp)
        ) {
            Text(
                "Code erzeugen",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 24.sp
            )
            Text(
                "Schritt ${step.ordinal + 1}/${VoucherStep.entries.size} · ${step.title}",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                VoucherStep.entries.forEach { s ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (s.ordinal <= step.ordinal) AccentRose
                                else Color.White.copy(0.12f)
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(step.hint, color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (step) {
                    VoucherStep.Reward -> {
                        Text("Coins", color = TextPrimary, fontFamily = DisplayFont, fontSize = 16.sp)
                        AdminField(coins, { coins = it.filter(Char::isDigit).take(5) }, "0 = nur Items")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("0", "25", "50", "100", "250").forEach { n ->
                                AdminChip(n, coins == n) { coins = n }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Items", color = TextPrimary, fontFamily = DisplayFont, fontSize = 16.sp)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items.forEachIndexed { idx, grant ->
                                val label = when (grant.kind) {
                                    "pets", "stickers", "emojis" -> grant.itemId
                                    "themes" -> ShopCatalog.THEMES.firstOrNull { it.id == grant.itemId }?.emoji
                                        ?: "🖼️"
                                    else -> "📦"
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(BgSoft)
                                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(14.dp))
                                        .clickable {
                                            items = items.toMutableList().also { it.removeAt(idx) }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        "$label ×${grant.qty}",
                                        color = TextPrimary,
                                        fontFamily = BodyFont,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaleBlue.copy(0.35f))
                                    .border(1.dp, MaleBlue.copy(0.6f), RoundedCornerShape(14.dp))
                                    .clickable { pickKind = "pets" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = Color.White, fontFamily = DisplayFont, fontSize = 22.sp)
                            }
                        }
                        if (pickKind != null) {
                            ItemPicker(
                                kind = pickKind!!,
                                onPickKind = { pickKind = it },
                                onAdd = { grant ->
                                    items = mergeGrant(items, grant)
                                    pickKind = null
                                },
                                onClose = { pickKind = null }
                            )
                        }
                    }

                    VoucherStep.Limits -> {
                        Text("Dauer", color = TextPrimary, fontFamily = DisplayFont, fontSize = 16.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminChip("30 Tage", !forever && validDays == "30") {
                                forever = false; validDays = "30"
                            }
                            AdminChip("90 Tage", !forever && validDays == "90") {
                                forever = false; validDays = "90"
                            }
                            AdminChip("Für immer", forever) { forever = true }
                        }
                        Text("Max. Personen", color = TextPrimary, fontFamily = DisplayFont, fontSize = 16.sp)
                        AdminField(maxPeople, { maxPeople = it.filter(Char::isDigit).take(5) }, "100")
                    }

                    VoucherStep.Code -> {
                        Text(
                            "Nur A–Z und 0–9 — auch rein Buchstaben sind ok.",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 13.sp
                        )
                        AdminField(
                            code,
                            { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(24) },
                            "z. B. LUVLOVE"
                        )
                    }

                    VoucherStep.Review -> {
                        val c = coins.toIntOrNull() ?: 0
                        AdminCard {
                            Text("Zusammenfassung", fontFamily = DisplayFont, color = TextPrimary, fontSize = 18.sp)
                            Text(
                                if (c > 0) "$c Coins" else "Keine Coins",
                                color = TextPrimary,
                                fontFamily = BodyFont
                            )
                            if (items.isEmpty()) {
                                Text("Keine Items", color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
                            } else {
                                items.forEach { g ->
                                    Text(
                                        "• ${g.kind}: ${g.itemId} ×${g.qty}",
                                        color = TextMuted,
                                        fontFamily = BodyFont,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            Text(
                                if (forever) "Für immer" else "$validDays Tage",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 13.sp
                            )
                            Text(
                                "Max. ${maxPeople.ifBlank { "1" }} Personen",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 13.sp
                            )
                            Text(
                                "Code: ${code.ifBlank { "(automatisch)" }}",
                                color = AccentRose,
                                fontFamily = DisplayFont,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            if (!error.isNullOrBlank()) {
                Text(error!!, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    error = null
                    when (step) {
                        VoucherStep.Reward -> onDismiss()
                        else -> step = VoucherStep.entries[step.ordinal - 1]
                    }
                }, enabled = !busy) {
                    Text(
                        if (step == VoucherStep.Reward) "Abbrechen" else "Zurück",
                        color = TextMuted,
                        fontFamily = BodyFont
                    )
                }
                AdminPrimaryBtn(
                    label = when (step) {
                        VoucherStep.Review -> if (busy) "…" else "Erzeugen"
                        else -> "Weiter"
                    },
                    enabled = !busy,
                    fillMaxWidth = false
                ) {
                    error = null
                    when (step) {
                        VoucherStep.Reward -> {
                            val c = coins.toIntOrNull() ?: 0
                            if (c < 1 && items.isEmpty()) {
                                error = "Coins oder mindestens ein Item wählen."
                                return@AdminPrimaryBtn
                            }
                            step = VoucherStep.Limits
                        }
                        VoucherStep.Limits -> step = VoucherStep.Code
                        VoucherStep.Code -> {
                            val cleaned = code.trim()
                            if (cleaned.isNotEmpty() && cleaned.length < 4) {
                                error = "Code mind. 4 Zeichen (oder leer lassen)."
                                return@AdminPrimaryBtn
                            }
                            step = VoucherStep.Review
                        }
                        VoucherStep.Review -> {
                            val cleaned = code.trim()
                            onCreate(
                                coins.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                                items,
                                maxPeople.toIntOrNull()?.coerceAtLeast(1) ?: 100,
                                validDays.toIntOrNull()?.coerceIn(1, 365) ?: 30,
                                forever,
                                cleaned
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun mergeGrant(
    current: List<VoucherItemGrant>,
    grant: VoucherItemGrant
): List<VoucherItemGrant> {
    val next = current.toMutableList()
    val idx = next.indexOfFirst { it.kind == grant.kind && it.itemId == grant.itemId }
    if (idx >= 0) {
        val old = next[idx]
        next[idx] = old.copy(qty = (old.qty + grant.qty).coerceAtMost(50))
    } else {
        next.add(grant)
    }
    return next
}

@Composable
private fun ItemPicker(
    kind: String,
    onPickKind: (String) -> Unit,
    onAdd: (VoucherItemGrant) -> Unit,
    onClose: () -> Unit
) {
    var qty by remember { mutableIntStateOf(1) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgSoft)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "pets" to "Begleiter",
                "themes" to "Hintergründe",
                "stickers" to "Sticker",
                "emojis" to "Emojis"
            ).forEach { (k, label) ->
                AdminChip(label, kind == k) { onPickKind(k) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Anzahl", color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(8.dp))
            listOf(1, 2, 5).forEach { n ->
                AdminChip("$n", qty == n) { qty = n }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "Schließen",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onClose)
            )
        }
        val options: List<Pair<String, String>> = when (kind) {
            "pets" -> ShopCatalog.PETS.map { it.emoji to it.emoji }
            "themes" -> ShopCatalog.THEMES.map { it.id to "${it.emoji} ${it.label}" }
            "stickers" -> ShopCatalog.STICKERS.take(80).map { it.emoji to it.emoji }
            "emojis" -> ShopCatalog.EMOJIS.take(80).map { it.emoji to it.emoji }
            else -> emptyList()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (id, label) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgDeep)
                        .clickable {
                            onAdd(VoucherItemGrant(kind = kind, itemId = id, qty = qty))
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(label, color = TextPrimary, fontFamily = BodyFont, fontSize = 14.sp)
                }
            }
        }
    }
}
