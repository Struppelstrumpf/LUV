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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.LuvApp
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.QuietHoursSchedule
import com.luv.couple.data.QuietWindow
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun QuietHoursScreen(onBack: () -> Unit) {
    val accent = PeerPalette.menuAccent()
    val prefs = LuvApp.instance.prefs
    val schedule by prefs.quietHoursFlow.collectAsStateWithLifecycle(
        initialValue = QuietHoursSchedule.EMPTY
    )
    val scope = rememberCoroutineScope()
    var editor by remember { mutableStateOf<QuietEditor?>(null) }

    editor?.let { ed ->
        QuietWindowDialog(
            accent = accent,
            initial = ed.window,
            onDismiss = { editor = null },
            onSave = { window ->
                val day = ed.day
                val list = schedule.windowsFor(day).toMutableList()
                if (ed.index >= 0 && ed.index < list.size) {
                    list[ed.index] = window
                } else {
                    list += window
                }
                scope.launch { prefs.setQuietHours(schedule.withDay(day, list)) }
                editor = null
            },
            onDelete = if (ed.index >= 0) {
                {
                    val day = ed.day
                    val list = schedule.windowsFor(day).toMutableList()
                    if (ed.index in list.indices) list.removeAt(ed.index)
                    scope.launch { prefs.setQuietHours(schedule.withDay(day, list)) }
                    editor = null
                }
            } else null
        )
    }

    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Zurück",
                color = TextMuted,
                fontFamily = BodyFont,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 8.dp)
            )
            Text("Ruhezeiten", fontFamily = DisplayFont, fontSize = 34.sp, color = TextPrimary)
            Text(
                "In diesen Fenstern sendet LUV keine Haptik, keine Benachrichtigungen und keine Nähe-Impulse — komplett ruhig.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            QuietHoursSchedule.DAY_LABELS.forEachIndexed { index, label ->
                val day = index + 1
                val windows = schedule.windowsFor(day)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, color = TextPrimary, fontFamily = DisplayFont, fontSize = 18.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        windows.forEachIndexed { i, window ->
                            QuietChip(
                                label = window.label(),
                                accent = accent,
                                filled = true,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    editor = QuietEditor(day = day, index = i, window = window)
                                }
                            )
                        }
                        QuietChip(
                            label = "+",
                            accent = accent,
                            filled = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                editor = QuietEditor(
                                    day = day,
                                    index = -1,
                                    window = QuietWindow(22 * 60, 7 * 60)
                                )
                            }
                        )
                        // Platzhalter, damit eine einzelne + Kachel nicht die ganze Breite nimmt
                        repeat((3 - windows.size).coerceAtLeast(0)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private data class QuietEditor(
    val day: Int,
    val index: Int,
    val window: QuietWindow
)

@Composable
private fun QuietChip(
    label: String,
    accent: Color,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (filled) accent.copy(alpha = 0.22f) else BgSoft)
            .border(
                1.dp,
                if (filled) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (filled) TextPrimary else TextMuted,
            fontFamily = DisplayFont,
            fontSize = if (label == "+") 22.sp else 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QuietWindowDialog(
    accent: Color,
    initial: QuietWindow,
    onDismiss: () -> Unit,
    onSave: (QuietWindow) -> Unit,
    onDelete: (() -> Unit)?
) {
    var startH by remember { mutableIntStateOf(initial.startMinutes / 60) }
    var startM by remember { mutableIntStateOf((initial.startMinutes % 60) / 15 * 15) }
    var endH by remember { mutableIntStateOf(initial.endMinutes / 60) }
    var endM by remember { mutableIntStateOf((initial.endMinutes % 60) / 15 * 15) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgSoft,
        title = {
            Text("Ruhefenster", fontFamily = DisplayFont, fontSize = 22.sp, color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Von", color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
                TimeSteppers(
                    hour = startH,
                    minute = startM,
                    onHour = { startH = it },
                    onMinute = { startM = it },
                    accent = accent
                )
                Text("Bis", color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
                TimeSteppers(
                    hour = endH,
                    minute = endM,
                    onHour = { endH = it },
                    onMinute = { endM = it },
                    accent = accent
                )
                Text(
                    "Über Mitternacht möglich (z. B. 22:00–07:00).",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = startH * 60 + startM
                val end = endH * 60 + endM
                if (start != end) onSave(QuietWindow(start, end))
            }) {
                Text("Speichern", color = accent, fontFamily = DisplayFont, fontSize = 15.sp)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Löschen", color = AccentRose, fontFamily = BodyFont, fontSize = 15.sp)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont, fontSize = 15.sp)
                }
            }
        }
    )
}

@Composable
private fun TimeSteppers(
    hour: Int,
    minute: Int,
    onHour: (Int) -> Unit,
    onMinute: (Int) -> Unit,
    accent: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Stepper(
            value = "%02d".format(hour),
            onMinus = { onHour((hour + 23) % 24) },
            onPlus = { onHour((hour + 1) % 24) },
            accent = accent,
            modifier = Modifier.weight(1f)
        )
        Text(":", color = TextPrimary, fontFamily = DisplayFont, fontSize = 22.sp)
        Stepper(
            value = "%02d".format(minute),
            onMinus = { onMinute((minute + 45) % 60) },
            onPlus = { onMinute((minute + 15) % 60) },
            accent = accent,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BgDeep)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "−",
            color = accent,
            fontFamily = DisplayFont,
            fontSize = 22.sp,
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = onMinus)
                .padding(6.dp),
            textAlign = TextAlign.Center
        )
        Text(value, color = TextPrimary, fontFamily = DisplayFont, fontSize = 20.sp)
        Text(
            "+",
            color = accent,
            fontFamily = DisplayFont,
            fontSize = 22.sp,
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = onPlus)
                .padding(6.dp),
            textAlign = TextAlign.Center
        )
    }
}
