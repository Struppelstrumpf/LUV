package com.luv.couple.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.data.AccountInfo
import com.luv.couple.data.PeerPalette
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.PublicReportInfo
import com.luv.couple.net.VoucherInfo
import com.luv.couple.update.AppChangelog
import com.luv.couple.update.AppUpdater
import com.luv.couple.update.UpdateUiState
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Composable
fun AccountHomeScreen(
    account: AccountInfo?,
    colorIndex: Int,
    message: String?,
    onOpenSettings: () -> Unit,
    onOpenRedeem: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAdmin: () -> Unit,
    googleEnabled: Boolean = false,
    googleBusy: Boolean = false,
    onGoogleConnect: () -> Unit = {},
    onLogout: () -> Unit = {},
    updateState: UpdateUiState = UpdateUiState.Idle,
    onUpdateApp: () -> Unit = {}
) {
    val accent = PeerPalette.menuAccent()
    var legalDoc by remember { mutableStateOf<LegalDoc?>(null) }
    var showChangelog by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var showBugReport by remember { mutableStateOf(false) }
    MenuBackdrop(includeNavigationBars = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            UpdateBanner(state = updateState, onUpdate = onUpdateApp)

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
                        color = Color.White,
                        fontFamily = DisplayFont,
                        fontSize = 22.sp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(account?.nickname ?: "…", color = TextPrimary, fontFamily = DisplayFont, fontSize = 22.sp)
                    Text(
                        buildString {
                            append("${account?.coins ?: 0} Coins")
                            if (account?.googleLinked == true) append(" · mit Google gesichert")
                        },
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp
                    )
                }
            }

            if (!message.isNullOrBlank()) {
                Text(message, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
            }

            if (googleEnabled && account?.googleLinked != true) {
                MenuButton(
                    if (googleBusy) "Google…" else "Mit Google anmelden",
                    AccentRose,
                    onGoogleConnect,
                    enabled = !googleBusy
                )
                Text(
                    "Melde dich an, um Coins, Freunde und Lobbys zu sichern — auch auf einem neuen Handy.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 12.sp
                )
            }

            MenuButton("Einstellungen", BgSoft, onOpenSettings, bordered = true)
            MenuButton("Code einlösen", BgSoft, onOpenRedeem, bordered = true)
            MenuButton("Hilfe", BgSoft, onOpenHelp, bordered = true)
            MenuButton("Bug melden  +10 🪙", BgSoft, { showBugReport = true }, bordered = true)
            if (account?.isStaff == true || account?.isAdmin == true) {
                MenuButton(
                    if (account?.isAdmin == true) "Admin" else "Moderator",
                    Color(0xFF3A2430),
                    onOpenAdmin
                )
            }
            if (account?.googleLinked == true) {
                MenuButton("Abmelden", BgSoft, { confirmLogout = true }, bordered = true)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
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
                Spacer(modifier = Modifier.width(18.dp))
                Text(
                    "AGB",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { legalDoc = LegalDoc.Agb }
                        .padding(vertical = 6.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    AppUpdater.versionLabel(),
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable { showChangelog = true }
                        .padding(vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    legalDoc?.let { doc ->
        LegalDialog(doc = doc, onDismiss = { legalDoc = null })
    }
    if (showChangelog) {
        ChangelogDialog(onDismiss = { showChangelog = false })
    }
    if (showBugReport) {
        BugReportDialog(onDismiss = { showBugReport = false })
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) {
                    Text("Abmelden", color = AccentRose, fontFamily = BodyFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont)
                }
            },
            title = {
                Text("Abmelden?", color = TextPrimary, fontFamily = DisplayFont, fontSize = 20.sp)
            },
            text = {
                Text(
                    if (account?.googleLinked == true) {
                        "Du kannst dich später mit Google wieder anmelden — Coins bleiben auf dem Konto."
                    } else {
                        "Ohne Google gehen Coins auf diesem Gerät verloren. Besser vorher „Mit Google speichern“."
                    },
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            containerColor = BgSoft
        )
    }
}

private enum class LegalDoc { Impressum, Agb }

@Composable
private fun ChangelogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen", color = AccentRose, fontFamily = BodyFont)
            }
        },
        title = {
            Text("Was ist neu", color = TextPrimary, fontFamily = DisplayFont, fontSize = 22.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppChangelog.entries.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            entry.version,
                            color = AccentRose,
                            fontFamily = DisplayFont,
                            fontSize = 16.sp
                        )
                        entry.highlights.forEach { line ->
                            Text(
                                "· $line",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        },
        containerColor = BgDeep
    )
}

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
""".trimIndent()

private val AGB_TEXT = """
Allgemeine Geschäftsbedingungen für LUV (digitale Inhalte)

1. Anbieter
Reineke GbR, Matthias und Jane Reineke, Elisabethstraße 31, 49201 Dissen (info@reineke.pro).

2. Leistungsbeschreibung
LUV ermöglicht gemeinsames Zeichnen in Lobbys. Kostenlose Funktionen (u. a. Daily Coins) und optionale Coin-Pakete können angeboten werden. Das Zeichnen in deinen Lobbys ist kostenlos; Kosten können z. B. für das Erstellen zusätzlicher Lobbys, Spiele oder Shop-Inhalte anfallen. Technische Verfügbarkeit kann schwanken.

3. Käufe / Coins
Käufe von Coin-Paketen erfolgen ausschließlich über Google Play (In-App-Käufe). Mit Abschluss der Zahlung erwirbst du digitale Guthaben-/Nutzungsrechte (Coins) in der App. Preise werden vor dem Kauf in der Google-Play-Oberfläche angezeigt. Es gelten die Nutzungsbedingungen und Zahlungsbedingungen von Google Play.

3a. Lootboxen / Zufallsinhalte
In der App können Lootboxen gegen Coins erworben werden. Der Inhalt einer Lootbox wird zufällig aus dem Itemshop bestimmt (z. B. Sticker, Emojis, Hintergründe, Begleiter). Die Wahrscheinlichkeit hängt u. a. vom Shop-Preis des jeweiligen Items ab; teurere Items sind seltener. Vor dem Kauf werden Preis und Hinweise angezeigt. Mit dem Kauf erklärst du dich mit dem Zufallscharakter einverstanden. Eingesetzte Coins und erhaltene Zufallsinhalte sind nicht erstattungsfähig, soweit gesetzlich zulässig. Lootboxen sind Unterhaltung innerhalb der App und kein Glücksspiel um Echtgeld; ein Umtausch von Lootbox-Inhalten in Geld findet nicht statt.

4. Widerruf
Bei digitalen Inhalten, deren Ausführung mit ausdrücklicher Zustimmung vor Ablauf der Widerrufsfrist begonnen hat und bei denen du zur Kenntnis genommen hast, dass du dein Widerrufsrecht verlierst, kann das Widerrufsrecht entfallen (§ 356 Abs. 5 BGB). Details dazu werden im Checkout-Prozess berücksichtigt, soweit anwendbar.

5. Konto, Google-Anmeldung und Wiederherstellung
Zur Nutzung von LUV ist eine Anmeldung mit einem Google-Konto erforderlich. Dabei werden zur Anmeldung die von Google bereitgestellten Basisdaten (insbesondere Google-Nutzer-ID, ggf. Name/E-Mail) an unsere Server übermittelt und zur Kontowiederherstellung genutzt. Coins und Fortschritt sind an dein LUV-Konto gebunden und können mit erfolgreicher Google-Anmeldung auf einem neuen Gerät wiederhergestellt werden, soweit technisch verfügbar.

6. Missbrauch, Cheating und Echtgeldhandel
Manipulation der App oder API (u. a. Mod-APKs, gefälschte Requests, Umgehung von Limits), Mehrfach-Konten zur Umgehung von Limits oder Missbrauch des Shops können zur Sperrung führen. Bereits gezahlte Beträge werden in solchen Fällen nicht erstattet, soweit gesetzlich zulässig.

Der Handel mit In-App-Items, Coins, Konten oder sonstigen digitalen Inhalten gegen Echtgeld (oder geldwerte Vorteile außerhalb von LUV) ist untersagt — einschließlich Kauf, Verkauf, Tausch oder Vermittlung über Drittplattformen. Verstöße können mit Verwarnung, Entzug von Items/Coins und dauerhafter Kontosperrung (Ban) geahndet werden, ohne Anspruch auf Erstattung.

7. Haftung
Für leicht fahrlässige Pflichtverletzungen haften wir nur bei Verletzung wesentlicher Vertragspflichten und begrenzt auf den typischen vorhersehbaren Schaden. Unberührt bleiben Haftung bei Vorsatz, grober Fahrlässigkeit, Verletzung von Leben, Körper oder Gesundheit sowie zwingende gesetzliche Ansprüche.

8. Öffentliche Bilder / nutzergenerierte Inhalte
Du kannst gespeicherte Momente in LUV öffentlich veröffentlichen. Mit der Veröffentlichung erklärst du dich mit diesen AGB einverstanden und sicherst zu, dass du zur Veröffentlichung berechtigt bist (insbesondere eigene Rechte am Bild bzw. Einwilligung aller abgebildeten bzw. mitzeichnenden Personen).

Veröffentlichte Bilder können anderen Nutzer:innen zufällig beim App-Start angezeigt werden und etwa 30 Tage gespeichert bleiben; sie können Namen/Nicknames der Beteiligten enthalten. Du kannst deine Veröffentlichung in der App wieder zurücknehmen, soweit technisch verfügbar.

Du verpflichtest dich, keine Inhalte zu veröffentlichen, die Rechte Dritter verletzen (u. a. Urheber-, Marken-, Persönlichkeits- und Datenschutzrechte), die gegen geltendes Recht verstoßen oder die beleidigend, diskriminierend, pornografisch, gewaltverherrlichend oder sonst unzulässig sind. Das Veröffentlichen von Bildern anderer gegen deren Willen ist untersagt.

Wir prüfen Inhalte nicht vorab vollständig. Für von Nutzer:innen veröffentlichte Inhalte sind die jeweiligen Nutzer:innen verantwortlich. Soweit gesetzlich zulässig, haften wir nicht für Urheberrechtsverletzungen, Persönlichkeitsrechtsverletzungen oder sonstige Ansprüche Dritter, die aus der Veröffentlichung durch Nutzer:innen entstehen — insbesondere nicht, wenn ein Bild ohne Einwilligung einer beteiligten Person veröffentlicht wird. Wir können Inhalte sperren, entfernen oder Konten einschränken, wenn Hinweise auf Rechtsverstöße oder Missbrauch vorliegen. Melde- und Prüfprozesse dienen der Missbrauchsbekämpfung und begründen keinen Anspruch auf bestimmte Prüfung oder Verfügbarkeit.

9. Änderungen
Wir können diese AGB mit Wirkung für die Zukunft anpassen, soweit dies erforderlich ist und dich nicht unangemessen benachteiligt. Die jeweils aktuelle Fassung findest du in der App.

10. Schlussbestimmungen
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
                    "Gutscheincode einfügen — Coins landen sofort auf deinem Konto.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                SoftInput(
                    value = code,
                    onValueChange = { code = it },
                    hint = "Gutscheincode",
                    onConfirm = { onRedeem(code.trim()) }
                )
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
    reports: List<PublicReportInfo> = emptyList(),
    message: String?,
    onCreate: (AdminVoucherDraft) -> Unit,
    onKeepReport: (PublicReportInfo) -> Unit = {},
    onDeleteReport: (PublicReportInfo) -> Unit = {},
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
                hint = "100",
                onConfirm = {
                    val cleaned = code.trim()
                    if (cleaned.length >= 4) {
                        onCreate(
                            AdminVoucherDraft(
                                code = cleaned,
                                coins = coins.toIntOrNull()?.coerceAtLeast(1) ?: 50,
                                forever = forever,
                                validDays = validDays.toIntOrNull()?.coerceIn(1, 365) ?: 30,
                                maxPeople = maxPeople.toIntOrNull()?.coerceAtLeast(1) ?: 100
                            )
                        )
                    }
                }
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

            Spacer(modifier = Modifier.height(16.dp))
            Text("Gemeldete Bilder", fontFamily = DisplayFont, fontSize = 24.sp, color = TextPrimary)
            Text(
                "Behalten = bleibt öffentlich. Löschen = weg — nach 10 Löschungen vom selben Host wird das Konto gesperrt.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            if (reports.isEmpty()) {
                Text(
                    "Keine offenen Meldungen.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
            } else {
                reports.forEach { report ->
                    AdminReportCard(
                        report = report,
                        onKeep = { onKeepReport(report) },
                        onDelete = { onDeleteReport(report) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminReportCard(
    report: PublicReportInfo,
    onKeep: () -> Unit,
    onDelete: () -> Unit
) {
    var bitmap by remember(report.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(report.id, report.imageUrl) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val raw = report.imageUrl
                if (raw.isBlank()) return@runCatching null
                val url = if (raw.startsWith("http")) raw
                else LuvApiClient.baseUrl().trimEnd('/') + raw
                val client = OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .build()
                client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                }
            }.getOrNull()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgSoft)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(report.nameLine, color = TextPrimary, fontFamily = DisplayFont, fontSize = 18.sp)
        Text(
            "Host: ${report.hostNickname} · gemeldet von ${report.reporterNickname}",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = 12.sp
        )
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgDeep),
                contentAlignment = Alignment.Center
            ) {
                Text("Bild lädt…", color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                MenuButton("Behalten", BgDeep, onKeep, bordered = true)
            }
            Box(modifier = Modifier.weight(1f)) {
                MenuButton("Löschen", Color(0xFF3A2430), onDelete)
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
    // Transparent: Event-/Menü-Ambient aus LuvAppNav bleibt fullscreen sichtbar
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .then(if (includeNavigationBars) Modifier.navigationBarsPadding() else Modifier),
        content = { content() }
    )
}

@Composable
fun SoftInput(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    onConfirm: (() -> Unit)? = null
) {
    val keyboard = LocalSoftwareKeyboardController.current
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
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboard?.hide()
                    onConfirm?.invoke()
                }
            ),
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
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) TextPrimary else TextMuted,
            fontFamily = DisplayFont,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SimpleBottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    sozialBadge: Boolean = false,
    marketBadge: Boolean = false,
    inventarBadge: Boolean = false
) {
    // Home · Sozial · Inventar · Markt · Zahnrad (Konto)
    val labels = listOf("Home", "Sozial", "Inventar", "Markt", null)
    // Volle Breite mit weichem Verlauf — keine farbige Kante über der Leiste
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xE0121821),
                        Color(0xF0121821)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xEE171C24))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, label ->
                val active = selected == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (active) AccentRose.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (label == null) {
                        BottomBarGear(
                            color = if (active) TextPrimary else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            label,
                            color = if (active) TextPrimary else TextMuted,
                            fontFamily = DisplayFont,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            softWrap = false,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (
                        (index == 1 && sozialBadge) ||
                        (index == 2 && inventarBadge) ||
                        (index == 3 && marketBadge)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 10.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentRose)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBarGear(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val cx = center.x
        val cy = center.y
        val ringR = size.minDimension * 0.28f
        val toothR = size.minDimension * 0.42f
        val holeR = size.minDimension * 0.12f
        drawCircle(
            color = color,
            radius = ringR,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
        val teeth = 8
        for (i in 0 until teeth) {
            val a = (Math.PI * 2.0 * i) / teeth
            drawCircle(
                color = color,
                radius = stroke * 0.85f,
                center = androidx.compose.ui.geometry.Offset(
                    cx + kotlin.math.cos(a).toFloat() * toothR,
                    cy + kotlin.math.sin(a).toFloat() * toothR
                )
            )
        }
        drawCircle(
            color = color,
            radius = holeR,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke * 0.85f)
        )
    }
}
