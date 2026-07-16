package com.luv.couple.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.data.AccountInfo
import com.luv.couple.data.PeerPalette
import com.luv.couple.net.ShopPack
import com.luv.couple.net.VoucherInfo
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary

@Composable
fun AccountHomeScreen(
    account: AccountInfo?,
    colorIndex: Int,
    message: String?,
    shopEnabled: Boolean,
    packs: List<ShopPack>,
    onClaimDaily: () -> Unit,
    onOpenRedeem: () -> Unit,
    onOpenAdmin: () -> Unit,
    onBuyPack: (ShopPack) -> Unit,
    onRefresh: () -> Unit
) {
    val accent = PeerPalette.composeColor(colorIndex)
    var legalDoc by remember { mutableStateOf<LegalDoc?>(null) }
    MenuBackdrop(includeNavigationBars = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Konto", fontFamily = DisplayFont, fontSize = 34.sp, color = TextPrimary)
            Text(
                "Fair nutzbar jeden Tag — Coins nur wenn du richtig viel malst.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 14.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(BgSoft)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (account?.nickname ?: "?").take(1).uppercase(),
                        color = Color(0xFF1A1F2E),
                        fontFamily = DisplayFont,
                        fontSize = 22.sp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(account?.nickname ?: "…", color = TextPrimary, fontFamily = DisplayFont, fontSize = 22.sp)
                    Text(
                        "${account?.coins ?: 0} Coins · ${account?.freeSessionsLeft ?: 0}/${account?.freeSessionsPerDay ?: 5} frei heute",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp
                    )
                }
            }

            if (!message.isNullOrBlank()) {
                Text(message, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
            }

            MenuButton(
                label = if (account?.canClaimDaily == true) "Tagesbonus +${account.dailyCoins} holen" else "Tagesbonus schon abgeholt",
                color = if (account?.canClaimDaily == true) accent else BgSoft,
                onClick = onClaimDaily,
                enabled = account?.canClaimDaily == true
            )
            MenuButton("Code einlösen", BgSoft, onOpenRedeem, bordered = true)
            if (account?.isAdmin == true) {
                MenuButton("Admin", Color(0xFF3A2430), onOpenAdmin)
            }
            MenuButton("Aktualisieren", BgSoft, onRefresh, bordered = true)

            Spacer(modifier = Modifier.height(4.dp))
            Text("Shop", fontFamily = DisplayFont, fontSize = 22.sp, color = TextPrimary)
            if (!shopEnabled) {
                Text(
                    "Shop bald mit Mollie — bis dahin reichen Daily Coins & Gutscheine völlig.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
            }
            packs.forEach { pack ->
                MenuButton(
                    label = "${pack.label} · ${pack.amountEur} €",
                    color = if (shopEnabled) accent else BgSoft,
                    onClick = { if (shopEnabled) onBuyPack(pack) },
                    enabled = shopEnabled,
                    bordered = !shopEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    "Impressum",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { legalDoc = LegalDoc.Impressum }
                        .padding(vertical = 6.dp)
                )
                Text(
                    "AGB",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { legalDoc = LegalDoc.Agb }
                        .padding(vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    legalDoc?.let { doc ->
        LegalDialog(doc = doc, onDismiss = { legalDoc = null })
    }
}

private enum class LegalDoc { Impressum, Agb }

@Composable
private fun LegalDialog(doc: LegalDoc, onDismiss: () -> Unit) {
    val title = when (doc) {
        LegalDoc.Impressum -> "Impressum"
        LegalDoc.Agb -> "AGB"
    }
    val body = when (doc) {
        LegalDoc.Impressum -> IMPRESSUM_TEXT
        LegalDoc.Agb -> AGB_TEXT
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen", color = AccentRose, fontFamily = BodyFont)
            }
        },
        title = {
            Text(title, fontFamily = DisplayFont, fontSize = 22.sp, color = TextPrimary)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    body,
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        },
        containerColor = BgSoft,
        titleContentColor = TextPrimary,
        textContentColor = TextMuted
    )
}

private val IMPRESSUM_TEXT = """
Reineke GbR
Matthias und Jane Reineke
Elisabethstraße 31
49201 Dissen

E-Mail: info@reineke.pro
Tel: 015561 048098

Verantwortlich für den Inhalt der App LUV.

Hinweis zu Käufen:
Coins und sonstige kostenpflichtige Inhalte sind an dein Geräte-/App-Konto gebunden. Wenn du die App deinstallierst, das Gerät wechselst oder App-Daten löschst, kann es sein, dass gekaufte Inhalte, Coins und Fortschritt verloren gehen und nicht wiederherstellbar sind.
""".trimIndent()

private val AGB_TEXT = """
Allgemeine Geschäftsbedingungen für LUV (digitale Inhalte)

1. Anbieter
Reineke GbR, Matthias und Jane Reineke, Elisabethstraße 31, 49201 Dissen (info@reineke.pro).

2. Leistungsbeschreibung
LUV ermöglicht gemeinsames Zeichnen in Lobbys. Kostenlose Funktionen (u. a. Daily Coins, freie Sessions) und optionale Coin-Pakete können angeboten werden. Technische Verfügbarkeit kann schwanken.

3. Käufe / Coins
Käufe erfolgen über den integrierten Shop (z. B. Mollie). Mit Abschluss der Zahlung erwirbst du digitale Guthaben-/Nutzungsrechte (Coins) in der App. Preise werden vor dem Kauf angezeigt. Es gelten die Zahlungsbedingungen des jeweiligen Zahlungsdienstleisters.

4. Widerruf
Bei digitalen Inhalten, deren Ausführung mit ausdrücklicher Zustimmung vor Ablauf der Widerrufsfrist begonnen hat und bei denen du zur Kenntnis genommen hast, dass du dein Widerrufsrecht verlierst, kann das Widerrufsrecht entfallen (§ 356 Abs. 5 BGB). Details dazu werden im Checkout-Prozess berücksichtigt, soweit anwendbar.

5. Verlust bei Deinstallation
Coins, gekaufte Inhalte und sonstiger Fortschritt sind an die App-Installation bzw. das lokale Gerätekonto gebunden. Bei Deinstallation der App, Löschen der App-Daten, Gerätewechsel ohne erfolgreiche Wiederherstellung oder ähnlichen Maßnahmen kann Guthaben und gekaufte Inhalte unwiderruflich verloren gehen. Eine Wiederherstellung ist nicht geschuldet und technisch möglicherweise nicht möglich. Bewahre dein Gerät und die App entsprechend auf, wenn du Guthaben behalten möchtest.

6. Missbrauch
Manipulation, Mehrfach-Konten zur Umgehung von Limits oder Missbrauch des Shops können zur Sperrung führen. Bereits gezahlte Beträge werden in solchen Fällen nicht erstattet, soweit gesetzlich zulässig.

7. Haftung
Für leicht fahrlässige Pflichtverletzungen haften wir nur bei Verletzung wesentlicher Vertragspflichten und begrenzt auf den typischen vorhersehbaren Schaden. Unberührt bleiben Haftung bei Vorsatz, grober Fahrlässigkeit, Verletzung von Leben, Körper oder Gesundheit sowie zwingende gesetzliche Ansprüche.

8. Änderungen
Wir können diese AGB mit Wirkung für die Zukunft anpassen, soweit dies erforderlich ist und dich nicht unangemessen benachteiligt. Die jeweils aktuelle Fassung findest du in der App.

9. Schlussbestimmungen
Es gilt das Recht der Bundesrepublik Deutschland unter Ausschluss des UN-Kaufrechts. Verbraucherschutzvorschriften am Wohnsitz bleiben unberührt. Sollten einzelne Klauseln unwirksam sein, bleibt der Rest wirksam.
""".trimIndent()

@Composable
fun RedeemScreen(
    error: String?,
    onRedeem: (String) -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    MenuBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Zur\u00FCck",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(vertical = 8.dp)
                )
                Text("Code einl\u00F6sen", fontFamily = DisplayFont, fontSize = 32.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Gutschein einfügen. (Admin-Zugang nur über Server — nicht in der App gespeichert.)",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                SoftInput(value = code, onValueChange = { code = it }, hint = "Gutscheincode")
                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
                }
            }
            MenuButton("Einlösen", AccentRose, { onRedeem(code.trim()) })
        }
    }
}

data class AdminVoucherDraft(
    val code: String,
    val coins: Int,
    val forever: Boolean,
    val validDays: Int,
    val maxPeople: Int
)

@Composable
fun AdminScreen(
    vouchers: List<VoucherInfo>,
    message: String?,
    onCreate: (AdminVoucherDraft) -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var coins by remember { mutableStateOf("50") }
    var forever by remember { mutableStateOf(false) }
    var validDays by remember { mutableStateOf("30") }
    var maxPeople by remember { mutableStateOf("100") }
    MenuBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Zur\u00FCck",
                color = TextMuted,
                fontFamily = BodyFont,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 8.dp)
            )
            Text("Admin", fontFamily = DisplayFont, fontSize = 32.sp, color = TextPrimary)
            Text(
                "Gutscheincode anlegen. Jede Person kann einen Code nur einmal einl\u00F6sen.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 14.sp
            )

            FieldLabel("Code (z.B. CODE22)")
            SoftInput(
                value = code,
                onValueChange = {
                    code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(24)
                },
                hint = "CODE22"
            )

            FieldLabel("Coins pro Einl\u00F6sung")
            SoftInput(
                value = coins,
                onValueChange = { coins = it.filter { c -> c.isDigit() }.take(5) },
                hint = "50"
            )

            FieldLabel("G\u00FCltigkeit")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("30 Tage", !forever && validDays == "30") {
                    forever = false
                    validDays = "30"
                }
                ChoiceChip("90 Tage", !forever && validDays == "90") {
                    forever = false
                    validDays = "90"
                }
                ChoiceChip("F\u00FCr immer", forever) {
                    forever = true
                }
            }
            if (!forever) {
                SoftInput(
                    value = validDays,
                    onValueChange = { validDays = it.filter { c -> c.isDigit() }.take(3) },
                    hint = "Tage"
                )
            }

            FieldLabel("Max. Personen (jede nur 1x)")
            SoftInput(
                value = maxPeople,
                onValueChange = { maxPeople = it.filter { c -> c.isDigit() }.take(5) },
                hint = "100"
            )

            if (!message.isNullOrBlank()) {
                Text(message, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
            }
            MenuButton("Code erzeugen", AccentRose, {
                val cleaned = code.trim()
                if (cleaned.length < 4) return@MenuButton
                onCreate(
                    AdminVoucherDraft(
                        code = cleaned,
                        coins = coins.toIntOrNull()?.coerceAtLeast(1) ?: 50,
                        forever = forever,
                        validDays = validDays.toIntOrNull()?.coerceIn(1, 365) ?: 30,
                        maxPeople = maxPeople.toIntOrNull()?.coerceAtLeast(1) ?: 100
                    )
                )
            })
            Spacer(modifier = Modifier.height(8.dp))
            vouchers.forEach { v ->
                val validity = when {
                    v.expiresAt == null -> "f\u00FCr immer"
                    else -> {
                        val daysLeft = ((v.expiresAt - System.currentTimeMillis()) / 86400000L)
                            .coerceAtLeast(0)
                        "noch ${daysLeft}d"
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgSoft)
                        .padding(14.dp)
                ) {
                    Text(v.code, color = TextPrimary, fontFamily = DisplayFont, fontSize = 18.sp)
                    Text(
                        "${v.coins} Coins \u00B7 ${v.redeemCount}/${v.maxRedeems} Personen \u00B7 $validity",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = TextPrimary, fontFamily = BodyFont, fontSize = 13.sp)
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) AccentRose else BgSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = TextPrimary, fontFamily = BodyFont, fontSize = 12.sp)
    }
}

@Composable
fun MenuBackdrop(
    includeNavigationBars: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF121821), BgDeep, Color(0xFF1A1220))))
            .statusBarsPadding()
            .then(if (includeNavigationBars) Modifier.navigationBarsPadding() else Modifier)
    ) { content() }
}

@Composable
fun SoftInput(value: String, onValueChange: (String) -> Unit, hint: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgSoft)
            .padding(18.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = TextPrimary, fontFamily = BodyFont, fontSize = 15.sp),
            cursorBrush = SolidColor(AccentRose),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isBlank()) Text(hint, color = TextMuted, fontFamily = BodyFont)
                inner()
            }
        )
    }
}

@Composable
fun MenuButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    bordered: Boolean = false,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .then(
                if (bordered) Modifier.border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) TextPrimary else TextMuted,
            fontFamily = DisplayFont,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SimpleBottomBar(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val labels = listOf("Home", "Konto")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xEE171C24))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            labels.forEachIndexed { index, label ->
                val active = selected == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (active) AccentRose.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (active) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(AccentRose)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            label,
                            color = if (active) TextPrimary else TextMuted,
                            fontFamily = DisplayFont,
                            fontSize = 15.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
