package com.luv.couple.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luv.couple.net.LuvApiClient
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch

private val BUG_LOCATIONS = listOf(
    "home" to "Hauptmenü",
    "inventar" to "Inventar",
    "markt" to "Markt",
    "sozial" to "Sozial",
    "canvas" to "Leinwand",
    "shop" to "Itemshop / Coinshop",
    "account" to "Konto",
    "other" to "Sonstiges",
)

private val BUG_VISIBILITY = listOf(
    "self" to "Nur ich sehe ihn",
    "others" to "Andere sehen ihn",
    "both" to "Ich und andere",
)

@Composable
fun BugReportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var description by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    var reproducible by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf("home") }
    var locationOther by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("self") }
    var busy by remember { mutableStateOf(false) }

    val gold = Color(0xFFFFD54F)
    val descOk = description.trim().length >= 10
    val otherOk = location != "other" || locationOther.trim().length >= 2
    val canSend = descOk && otherOk && !busy

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDeep.copy(0.92f))
                .clickable(enabled = !busy, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 640.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF3A2430), BgSoft, BgDeep)
                        )
                    )
                    .border(1.dp, AccentRose.copy(0.35f), RoundedCornerShape(24.dp))
                    .clickable(enabled = false) {}
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Bug melden",
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            fontSize = 24.sp
                        )
                        Text(
                            "Hilfreiche Meldungen → +10 🪙",
                            color = gold,
                            fontFamily = DisplayFont,
                            fontSize = 13.sp
                        )
                    }
                    TextButton(onClick = { if (!busy) onDismiss() }) {
                        Text("✕", color = TextPrimary, fontFamily = DisplayFont, fontSize = 18.sp)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Wenn deine Meldung dem Team wirklich hilft, erhältst du 10 Coins. " +
                            "Die Belohnung holst du danach unter Sozial → Freunde ab.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    FieldLabel("Screenshot (Direktlink)")
                    Text(
                        "Bild bei postimages.org hochladen → „Direct link“ kopieren und hier einfügen.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp
                    )
                    Text(
                        "→ postimages.org öffnen",
                        color = AccentRose,
                        fontFamily = DisplayFont,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://postimages.org/")
                                        )
                                    )
                                }
                            }
                            .padding(vertical = 2.dp)
                    )
                    BugTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it.take(500) },
                        placeholder = "https://i.postimg.cc/…",
                        singleLine = true
                    )

                    FieldLabel("Video-Link (optional)")
                    BugTextField(
                        value = videoUrl,
                        onValueChange = { videoUrl = it.take(500) },
                        placeholder = "https://… (YouTube, Drive, …)",
                        singleLine = true
                    )

                    FieldLabel("Was ist passiert?")
                    BugTextField(
                        value = description,
                        onValueChange = { description = it.take(1200) },
                        placeholder = "Kurz beschreiben, was du erwartet hast und was passiert ist …",
                        minHeight = 110.dp
                    )
                    Text(
                        "${description.trim().length}/1200",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 11.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgDeep.copy(0.4f))
                            .clickable { reproducible = !reproducible }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = reproducible,
                            onCheckedChange = { reproducible = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentRose,
                                uncheckedColor = TextMuted,
                                checkmarkColor = TextPrimary
                            )
                        )
                        Text(
                            "Bug ist reproduzierbar (ich kann ihn wiederholen)",
                            color = TextPrimary,
                            fontFamily = BodyFont,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    FieldLabel("Wo war der Bug?")
                    ChipRow(
                        items = BUG_LOCATIONS,
                        selected = location,
                        onSelect = { location = it }
                    )
                    if (location == "other") {
                        BugTextField(
                            value = locationOther,
                            onValueChange = { locationOther = it.take(80) },
                            placeholder = "Wo genau?",
                            singleLine = true
                        )
                    }

                    FieldLabel("Wer sieht den Bug?")
                    ChipRow(
                        items = BUG_VISIBILITY,
                        selected = visibility,
                        onSelect = { visibility = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (canSend) AccentRose.copy(0.9f) else TextMuted.copy(0.22f))
                        .clickable(enabled = canSend) {
                            busy = true
                            scope.launch {
                                runCatching {
                                    LuvApiClient.submitBugReport(
                                        description = description,
                                        imageUrl = imageUrl,
                                        videoUrl = videoUrl,
                                        reproducible = reproducible,
                                        location = location,
                                        locationOther = locationOther,
                                        visibility = visibility,
                                    )
                                }.onSuccess {
                                    Toast.makeText(
                                        context,
                                        "Danke! Bei hilfreichen Bugs: +10 🪙 unter Sozial abholen.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onDismiss()
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "Senden fehlgeschlagen",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                busy = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (busy) "Sendet …" else "Bug absenden",
                        color = if (canSend) TextPrimary else TextMuted,
                        fontFamily = DisplayFont,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        color = TextPrimary,
        fontFamily = DisplayFont,
        fontSize = 14.sp
    )
}

@Composable
private fun BugTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = false,
    minHeight: Dp = 44.dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(BgDeep.copy(0.55f))
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.isBlank()) {
            Text(
                placeholder,
                color = TextMuted.copy(0.7f),
                fontFamily = BodyFont,
                fontSize = 14.sp
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(
                color = TextPrimary,
                fontFamily = BodyFont,
                fontSize = 14.sp,
                lineHeight = 20.sp
            ),
            cursorBrush = SolidColor(AccentRose),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChipRow(
    items: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (id, label) ->
                    val on = selected == id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) AccentRose.copy(0.35f) else BgDeep.copy(0.45f))
                            .border(
                                1.dp,
                                if (on) AccentRose.copy(0.6f) else Color.White.copy(0.08f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelect(id) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = TextPrimary,
                            fontFamily = if (on) DisplayFont else BodyFont,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
