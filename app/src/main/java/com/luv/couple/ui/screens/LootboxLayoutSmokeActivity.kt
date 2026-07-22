package com.luv.couple.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.LuvTheme
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary

/**
 * Layout-Probe für Lootbox-Footer (adb): gleiche Safe-Frame-Logik wie der echte Dialog.
 * Nicht im Launcher — nur per Component-Start.
 */
class LootboxLayoutSmokeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LuvTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xF20A0D14))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, end = 12.dp, top = 40.dp, bottom = 96.dp)
                    ) {
                        LootboxFooterSmokeChrome()
                    }
                }
            }
        }
    }
}

@Composable
private fun LootboxFooterSmokeChrome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(BgDeep)
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Lootbox", color = TextPrimary, fontFamily = DisplayFont, fontSize = 28.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "Tippen zum Öffnen · 10 Coins",
            color = TextPrimary,
            fontFamily = DisplayFont,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.weight(0.15f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lootbox_footer")
                .padding(top = 8.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Kauf bestätigen",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 14.sp,
                        modifier = Modifier.testTag("lootbox_confirm_title")
                    )
                    Text(
                        "Vor dem Kauf nachfragen · Menge wählbar",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("lootbox_confirm_sub")
                    )
                }
                Switch(checked = true, onCheckedChange = null)
            }
            Text(
                "Zufälliger Inhalt · Duplikate möglich · nicht erstattungsfähig — Details in den AGB.",
                color = TextMuted.copy(alpha = 0.85f),
                fontFamily = BodyFont,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.testTag("lootbox_confirm_legal")
            )
        }
    }
}
