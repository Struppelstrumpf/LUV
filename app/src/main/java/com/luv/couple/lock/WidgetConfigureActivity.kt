package com.luv.couple.lock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.luv.couple.data.Lobby
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
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    lobbies = LuvApp.instance.prefs.snapshot().lobbies
                }

                WidgetPickScreen(
                    lobbies = lobbies,
                    onPick = { lobby ->
                        scope.launch {
                            LuvApp.instance.prefs.bindWidget(appWidgetId, lobby.id)
                            LockScreenWidgetProvider.bind(appWidgetId, lobby.id, lobby.name)
                            LockScreenWidgetProvider.requestUpdate(this@WidgetConfigureActivity)
                            val result = Intent().putExtra(
                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                appWidgetId
                            )
                            setResult(Activity.RESULT_OK, result)
                            finish()
                        }
                    },
                    onEmpty = {
                        Toast.makeText(
                            this,
                            "Zuerst eine Lobby in LUV erstellen.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun WidgetPickScreen(
    lobbies: List<Lobby>,
    onPick: (Lobby) -> Unit,
    onEmpty: () -> Unit
) {
    LaunchedEffect(lobbies) {
        if (lobbies.isEmpty()) onEmpty()
    }
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
            "Welche Lobby soll dieses Widget / Sperrbildschirm-Widget zeigen?",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = 14.sp
        )
        lobbies.forEach { lobby ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgSoft)
                    .clickable { onPick(lobby) },
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    lobby.name,
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            }
        }
        if (lobbies.isEmpty()) {
            Text("Keine Lobby vorhanden.", color = AccentRose, fontFamily = BodyFont)
        }
    }
}
