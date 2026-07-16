package com.luv.couple.lock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.LuvApp
import com.luv.couple.MainActivity
import com.luv.couple.data.Lobby
import com.luv.couple.data.Role
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.LuvTheme
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch

class WidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            LuvTheme {
                var lobbies by remember { mutableStateOf<List<Lobby>>(emptyList()) }
                var loaded by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    lobbies = LuvApp.instance.prefs.snapshot().lobbies
                    loaded = true
                    // Eine Lobby → direkt zuweisen
                    if (lobbies.size == 1) {
                        bindAndFinish(lobbies.first())
                    }
                }

                WidgetPickScreen(
                    loaded = loaded,
                    lobbies = lobbies,
                    onPick = { lobby ->
                        scope.launch { bindAndFinish(lobby) }
                    },
                    onOpenApp = {
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }

    private suspend fun bindAndFinish(lobby: Lobby) {
        LuvApp.instance.prefs.bindWidget(appWidgetId, lobby.id)
        LockScreenWidgetProvider.bind(appWidgetId, lobby.id, lobby.name)
        LockScreenWidgetProvider.requestUpdate(this@WidgetConfigureActivity)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}

@Composable
private fun WidgetPickScreen(
    loaded: Boolean,
    lobbies: List<Lobby>,
    onPick: (Lobby) -> Unit,
    onOpenApp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Widget für Lobby", color = TextPrimary, fontFamily = DisplayFont, fontSize = 28.sp)
        Text(
            "Wähle, welche Lobby dieses Widget zeigen soll.",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = 14.sp
        )

        if (!loaded) {
            Text("Lobbys werden geladen…", color = TextMuted, fontFamily = BodyFont)
            return@Column
        }

        if (lobbies.isEmpty()) {
            Text(
                "Noch keine Lobby auf diesem Gerät. Öffne LUV, erstelle oder tritt einer bei — dann Widget erneut hinzufügen.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AccentRose)
                    .clickable(onClick = onOpenApp),
                contentAlignment = Alignment.Center
            ) {
                Text("LUV öffnen", color = TextPrimary, fontFamily = DisplayFont, fontSize = 17.sp)
            }
            return@Column
        }

        lobbies.forEach { lobby ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(BgSoft)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                    .clickable { onPick(lobby) }
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        lobby.name,
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 20.sp
                    )
                    Text(
                        if (lobby.role == Role.HOST) "Du hostest" else "Beigetreten",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tipp: Auf Samsung-Handys erscheinen App-Widgets auf dem Homescreen — nicht in den Sperrbildschirm-Widgets (nur Samsung-eigene).",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}
