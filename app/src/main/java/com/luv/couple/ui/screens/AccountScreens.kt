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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
    MenuBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
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
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

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
                    "Zurück",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(vertical = 8.dp)
                )
                Text("Code einlösen", fontFamily = DisplayFont, fontSize = 32.sp, color = TextPrimary)
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

@Composable
fun AdminScreen(
    vouchers: List<VoucherInfo>,
    message: String?,
    onCreate: (coins: Int, maxRedeems: Int) -> Unit,
    onBack: () -> Unit
) {
    var coins by remember { mutableStateOf("50") }
    var max by remember { mutableStateOf("10") }
    MenuBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Zurück",
                color = TextMuted,
                fontFamily = BodyFont,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 8.dp)
            )
            Text("Admin", fontFamily = DisplayFont, fontSize = 32.sp, color = TextPrimary)
            Text("Gutscheine erstellen", color = TextMuted, fontFamily = BodyFont, fontSize = 14.sp)
            SoftInput(value = coins, onValueChange = { coins = it.filter { c -> c.isDigit() }.take(5) }, hint = "Coins")
            SoftInput(value = max, onValueChange = { max = it.filter { c -> c.isDigit() }.take(5) }, hint = "Max. Einlösungen")
            if (!message.isNullOrBlank()) {
                Text(message, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
            }
            MenuButton("Code erzeugen", AccentRose, {
                onCreate(coins.toIntOrNull() ?: 50, max.toIntOrNull() ?: 1)
            })
            Spacer(modifier = Modifier.height(8.dp))
            vouchers.forEach { v ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgSoft)
                        .padding(14.dp)
                ) {
                    Text(v.code, color = TextPrimary, fontFamily = DisplayFont, fontSize = 18.sp)
                    Text(
                        "${v.coins} Coins · ${v.redeemCount}/${v.maxRedeems} genutzt",
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
fun MenuBackdrop(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF121821), BgDeep, Color(0xFF1A1220))))
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
    val labels = listOf("Home", "Lobbys", "Konto")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSoft.copy(alpha = 0.95f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (active) AccentRose else Color.Transparent)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    label,
                    color = if (active) TextPrimary else TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
            }
        }
    }
}
