package com.luv.couple.ui.wedding

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.LuvApiException
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun WeddingGuestbookDialog(
    coupleUserId: String,
    onDismiss: () -> Unit,
    onWritten: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = BgSoft,
        title = {
            Text("Gästebuch", fontFamily = DisplayFont, color = TextPrimary)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Hinterlasse dem Brautpaar eine Nachricht.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 280) text = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    placeholder = {
                        Text(
                            "Dein Eintrag … (+5 Coins)",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && text.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching {
                            LuvApiClient.postGuestbook(coupleUserId, text.trim())
                        }.onSuccess {
                            Toast.makeText(
                                context,
                                if (it.coinsGranted > 0) "Eintrag gespeichert · +${it.coinsGranted} Coins"
                                else "Eintrag gespeichert",
                                Toast.LENGTH_SHORT
                            ).show()
                            onWritten()
                            onDismiss()
                        }.onFailure { e ->
                            Toast.makeText(
                                context,
                                (e as? LuvApiException)?.message ?: e.message ?: "Fehler",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        busy = false
                    }
                }
            ) {
                Text("Eintragen", color = AccentRose, fontFamily = DisplayFont)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Schließen", fontFamily = BodyFont)
            }
        }
    )
}
