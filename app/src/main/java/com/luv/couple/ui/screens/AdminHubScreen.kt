package com.luv.couple.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.data.AccountInfo
import com.luv.couple.net.LiveNoticeBus
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.PeerReportInfo
import com.luv.couple.net.PublicReportInfo
import com.luv.couple.net.StaffPermGroup
import com.luv.couple.net.StaffUserCard
import com.luv.couple.net.VoucherInfo
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private enum class AdminTab(val label: String, val icon: String) {
    Overview("Übersicht", "🏠"),
    Mods("Moderatoren", "🛡️"),
    Reports("Meldungen", "🚩"),
    Codes("Codes", "🎟️"),
    Users("Nutzer", "🎮"),
    Live("Live", "📣")
}

@Composable
fun AdminHubScreen(
    account: AccountInfo?,
    onBack: () -> Unit,
    onOpenProfile: (userId: String, nickname: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAdmin = account?.isAdmin == true
    fun can(p: String) = account?.hasPerm(p) == true

    val tabs = remember(account?.role, account?.permissions) {
        buildList {
            add(AdminTab.Overview)
            if (isAdmin) add(AdminTab.Mods)
            if (can("reports.view")) add(AdminTab.Reports)
            if (can("codes.view")) add(AdminTab.Codes)
            if (can("gm.search")) add(AdminTab.Users)
            if (can("live.notify")) add(AdminTab.Live)
        }
    }
    var tab by remember { mutableStateOf(AdminTab.Overview) }
    LaunchedEffect(tabs) {
        if (tab !in tabs) tab = tabs.firstOrNull() ?: AdminTab.Overview
    }

    var overviewUsers by remember { mutableIntStateOf(0) }
    var overviewRooms by remember { mutableIntStateOf(0) }
    var overviewPublic by remember { mutableIntStateOf(0) }
    var overviewPeer by remember { mutableIntStateOf(0) }
    var overviewMods by remember { mutableIntStateOf(0) }
    var overviewVouchers by remember { mutableIntStateOf(0) }
    var permGroups by remember { mutableStateOf<List<StaffPermGroup>>(emptyList()) }

    var moderators by remember { mutableStateOf<List<StaffUserCard>>(emptyList()) }
    var modInvite by remember { mutableStateOf("") }
    var permEdit by remember { mutableStateOf<StaffUserCard?>(null) }

    var publicReports by remember { mutableStateOf<List<PublicReportInfo>>(emptyList()) }
    var peerReports by remember { mutableStateOf<List<PeerReportInfo>>(emptyList()) }

    var vouchers by remember { mutableStateOf<List<VoucherInfo>>(emptyList()) }
    var code by remember { mutableStateOf("") }
    var coins by remember { mutableStateOf("50") }
    var forever by remember { mutableStateOf(false) }
    var validDays by remember { mutableStateOf("30") }
    var maxPeople by remember { mutableStateOf("100") }

    var userQuery by remember { mutableStateOf("") }
    var userHits by remember { mutableStateOf<List<StaffUserCard>>(emptyList()) }
    var selectedUser by remember { mutableStateOf<StaffUserCard?>(null) }
    var nickEdit by remember { mutableStateOf("") }
    var coinDelta by remember { mutableStateOf("10") }

    var liveText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun reloadOverview() {
        scope.launch {
            runCatching { LuvApiClient.fetchStaffOverview() }
                .onSuccess {
                    overviewUsers = it.users
                    overviewRooms = it.rooms
                    overviewPublic = it.openPublicReports
                    overviewPeer = it.openPeerReports
                    overviewMods = it.moderators
                    overviewVouchers = it.vouchers
                    if (it.permissionGroups.isNotEmpty()) permGroups = it.permissionGroups
                }
                .onFailure { banner = it.message }
        }
    }

    LaunchedEffect(Unit) { reloadOverview() }

    LaunchedEffect(tab) {
        when (tab) {
            AdminTab.Mods -> if (isAdmin) {
                runCatching { LuvApiClient.listModerators() }
                    .onSuccess {
                        moderators = it.first
                        if (it.second.isNotEmpty()) permGroups = it.second
                    }
            }
            AdminTab.Reports -> if (can("reports.view")) {
                publicReports = runCatching { LuvApiClient.listPublicReports() }.getOrDefault(emptyList())
                peerReports = runCatching { LuvApiClient.listPeerReports() }.getOrDefault(emptyList())
            }
            AdminTab.Codes -> if (can("codes.view")) {
                vouchers = runCatching { LuvApiClient.listVouchers() }.getOrDefault(emptyList())
            }
            else -> Unit
        }
    }

    MenuBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Zurück",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                "Geschützter Bereich · gemütlich sortiert",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 12.sp
            )
            Text(
                if (isAdmin) "Admin-Bereich" else "Moderator-Bereich",
                fontFamily = DisplayFont,
                fontSize = 30.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { t ->
                    val on = tab == t
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) AccentRose.copy(0.28f) else BgSoft)
                            .border(
                                1.dp,
                                if (on) AccentRose.copy(0.55f) else Color.White.copy(0.08f),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { tab = t }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "${t.icon} ${t.label}",
                            color = TextPrimary,
                            fontFamily = if (on) DisplayFont else BodyFont,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            if (!banner.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(banner!!, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (tab) {
                    AdminTab.Overview -> {
                        AdminStatGrid(
                            "Nutzer" to "$overviewUsers",
                            "Lobbys live" to "$overviewRooms",
                            "Bild-Meldungen" to "$overviewPublic",
                            "Lobby-Meldungen" to "$overviewPeer",
                            "Moderatoren" to "$overviewMods",
                            "Codes" to "$overviewVouchers"
                        )
                        AdminCard {
                            Text(
                                "Willkommen, ${account?.nickname ?: "Team"}.",
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = 18.sp
                            )
                            Text(
                                if (isAdmin) {
                                    "Du bist Admin (Google). Moderatoren einladen, Rechte vergeben und Live-Hinweise senden."
                                } else {
                                    "Du bist Moderator mit freigeschalteten Bereichen."
                                },
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 13.sp
                            )
                        }
                    }

                    AdminTab.Mods -> {
                        AdminCard {
                            Text("Moderator einladen", fontFamily = DisplayFont, color = TextPrimary, fontSize = 18.sp)
                            Text(
                                "Spitzname oder Google-E-Mail — Rechte danach fein einstellen.",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 13.sp
                            )
                            AdminField(modInvite, { modInvite = it }, "Name oder E-Mail …")
                            AdminPrimaryBtn("Einladen", enabled = !busy && modInvite.isNotBlank()) {
                                busy = true
                                scope.launch {
                                    runCatching { LuvApiClient.inviteModerator(modInvite) }
                                        .onSuccess {
                                            toast("${it.nickname} ist Moderator")
                                            modInvite = ""
                                            moderators = listOf(it) + moderators.filterNot { m -> m.userId == it.userId }
                                            reloadOverview()
                                        }
                                        .onFailure { banner = it.message; toast(it.message ?: "Fehler") }
                                    busy = false
                                }
                            }
                        }
                        if (moderators.isEmpty()) {
                            AdminEmpty("Noch keine Moderatoren.")
                        } else {
                            moderators.forEach { m ->
                                AdminCard {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AdminAvatar(m.nickname)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(m.nickname, color = TextPrimary, fontFamily = DisplayFont, fontSize = 17.sp)
                                            Text(
                                                m.email ?: "ohne Google-Mail",
                                                color = TextMuted,
                                                fontFamily = BodyFont,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                "${m.permissions.count { it.value }} Rechte aktiv",
                                                color = TextMuted,
                                                fontFamily = BodyFont,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AdminGhostBtn("Rechte") { permEdit = m }
                                        AdminGhostBtn("Profil") { onOpenProfile(m.userId, m.nickname) }
                                        AdminDangerBtn("Entfernen") {
                                            scope.launch {
                                                runCatching { LuvApiClient.removeModerator(m.userId) }
                                                    .onSuccess {
                                                        moderators = moderators.filterNot { it.userId == m.userId }
                                                        toast("Entfernt")
                                                        reloadOverview()
                                                    }
                                                    .onFailure { toast(it.message ?: "Fehler") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AdminTab.Reports -> {
                        Text("Öffentliche Bilder", fontFamily = DisplayFont, color = TextPrimary, fontSize = 20.sp)
                        if (publicReports.isEmpty()) AdminEmpty("Keine offenen Bild-Meldungen.")
                        publicReports.forEach { r ->
                            HubReportCard(
                                title = r.nameLine,
                                subtitle = "Host ${r.hostNickname} · von ${r.reporterNickname}",
                                imageUrl = r.imageUrl,
                                canAct = can("reports.act"),
                                onKeep = {
                                    scope.launch {
                                        runCatching { LuvApiClient.keepPublicReport(r.id) }
                                            .onSuccess {
                                                publicReports = publicReports.filterNot { it.id == r.id }
                                                toast("Behalten")
                                            }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        runCatching { LuvApiClient.deletePublicReport(r.id) }
                                            .onSuccess { banned ->
                                                publicReports = publicReports.filterNot { it.id == r.id }
                                                toast(if (banned) "Gelöscht — Host gesperrt" else "Gelöscht")
                                            }
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Lobby-Meldungen", fontFamily = DisplayFont, color = TextPrimary, fontSize = 20.sp)
                        if (peerReports.isEmpty()) AdminEmpty("Keine offenen Lobby-Meldungen.")
                        peerReports.forEach { r ->
                            HubReportCard(
                                title = r.targetNickname,
                                subtitle = "${r.lobbyName} · von ${r.reporterNickname}",
                                imageUrl = r.imageUrl,
                                canAct = can("reports.act"),
                                onKeep = {
                                    scope.launch {
                                        runCatching { LuvApiClient.keepPeerReport(r.id) }
                                            .onSuccess {
                                                peerReports = peerReports.filterNot { it.id == r.id }
                                                toast("Behalten")
                                            }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        runCatching { LuvApiClient.deletePeerReport(r.id) }
                                            .onSuccess {
                                                peerReports = peerReports.filterNot { it.id == r.id }
                                                toast("Gelöscht")
                                            }
                                    }
                                }
                            )
                        }
                    }

                    AdminTab.Codes -> {
                        if (can("codes.edit")) {
                            AdminCard {
                                Text("Code erzeugen", fontFamily = DisplayFont, color = TextPrimary, fontSize = 18.sp)
                                AdminField(code, {
                                    code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(24)
                                }, "CODE22")
                                AdminField(coins, { coins = it.filter(Char::isDigit).take(5) }, "Coins")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AdminChip("30 Tage", !forever && validDays == "30") {
                                        forever = false; validDays = "30"
                                    }
                                    AdminChip("90 Tage", !forever && validDays == "90") {
                                        forever = false; validDays = "90"
                                    }
                                    AdminChip("Für immer", forever) { forever = true }
                                }
                                AdminField(maxPeople, { maxPeople = it.filter(Char::isDigit).take(5) }, "Max. Personen")
                                AdminPrimaryBtn("Erzeugen", enabled = !busy) {
                                    val cleaned = code.trim()
                                    if (cleaned.length < 4) {
                                        toast("Code mind. 4 Zeichen"); return@AdminPrimaryBtn
                                    }
                                    busy = true
                                    scope.launch {
                                        runCatching {
                                            LuvApiClient.createVoucher(
                                                coins = coins.toIntOrNull()?.coerceAtLeast(1) ?: 50,
                                                maxRedeems = maxPeople.toIntOrNull()?.coerceAtLeast(1) ?: 100,
                                                validDays = validDays.toIntOrNull()?.coerceIn(1, 365) ?: 30,
                                                forever = forever,
                                                code = cleaned
                                            )
                                        }.onSuccess {
                                            vouchers = listOf(it) + vouchers
                                            toast("Code ${it.code} erstellt")
                                            code = ""
                                        }.onFailure { toast(it.message ?: "Fehler") }
                                        busy = false
                                    }
                                }
                            }
                        }
                        vouchers.forEach { v ->
                            AdminCard {
                                Text(v.code, color = TextPrimary, fontFamily = DisplayFont, fontSize = 18.sp)
                                Text(
                                    "${v.coins} Coins · ${v.redeemCount}/${v.maxRedeems}",
                                    color = TextMuted,
                                    fontFamily = BodyFont,
                                    fontSize = 12.sp
                                )
                                if (can("codes.revoke")) {
                                    AdminDangerBtn("Widerrufen") {
                                        scope.launch {
                                            runCatching { LuvApiClient.revokeVoucher(v.code) }
                                                .onSuccess {
                                                    vouchers = vouchers.filterNot { it.code == v.code }
                                                    toast("Widerrufen")
                                                }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AdminTab.Users -> {
                        AdminCard {
                            Text("Spieler suchen", fontFamily = DisplayFont, color = TextPrimary, fontSize = 18.sp)
                            AdminField(userQuery, { userQuery = it }, "Spitzname oder E-Mail …")
                            AdminPrimaryBtn("Suchen", enabled = userQuery.isNotBlank() && !busy) {
                                busy = true
                                scope.launch {
                                    userHits = runCatching {
                                        LuvApiClient.searchStaffUsers(userQuery)
                                    }.getOrDefault(emptyList())
                                    selectedUser = null
                                    busy = false
                                }
                            }
                        }
                        userHits.forEach { u ->
                            AdminCard {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedUser = u
                                            nickEdit = u.nickname
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(u.petEmoji, fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(u.nickname, color = TextPrimary, fontFamily = DisplayFont)
                                        Text(
                                            "${u.coins} Coins · ${u.role}${if (u.banned) " · gesperrt" else ""}",
                                            color = TextMuted,
                                            fontFamily = BodyFont,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                        selectedUser?.let { u ->
                            AdminCard {
                                Text(u.nickname, fontFamily = DisplayFont, color = TextPrimary, fontSize = 20.sp)
                                Text(u.email ?: u.userId, color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
                                if (can("gm.editNick")) {
                                    AdminField(nickEdit, { nickEdit = it.take(18) }, "Spitzname")
                                    AdminGhostBtn("Name speichern") {
                                        scope.launch {
                                            runCatching {
                                                LuvApiClient.setUserNickname(u.userId, nickEdit)
                                            }.onSuccess {
                                                selectedUser = it
                                                toast("Gespeichert")
                                            }.onFailure { toast(it.message ?: "Fehler") }
                                        }
                                    }
                                }
                                if (can("gm.editCoins")) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            AdminField(coinDelta, {
                                                coinDelta = it.filter { c -> c == '-' || c.isDigit() }.take(5)
                                            }, "Δ Coins")
                                        }
                                        AdminPrimaryBtn("Anwenden") {
                                            val d = coinDelta.toIntOrNull() ?: return@AdminPrimaryBtn
                                            scope.launch {
                                                runCatching {
                                                    LuvApiClient.adjustUserCoins(u.userId, d)
                                                }.onSuccess {
                                                    selectedUser = it
                                                    toast("Coins: ${it.coins}")
                                                }.onFailure { toast(it.message ?: "Fehler") }
                                            }
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (can("gm.block")) {
                                        AdminGhostBtn(if (u.banned) "Entsperren" else "Sperren") {
                                            scope.launch {
                                                runCatching {
                                                    LuvApiClient.setUserBanned(u.userId, !u.banned)
                                                }.onSuccess {
                                                    selectedUser = it
                                                    toast(if (it.banned) "Gesperrt" else "Entsperrt")
                                                }.onFailure { toast(it.message ?: "Fehler") }
                                            }
                                        }
                                    }
                                    AdminGhostBtn("Profil") { onOpenProfile(u.userId, u.nickname) }
                                    if (can("gm.delete")) {
                                        AdminDangerBtn("Löschen") {
                                            scope.launch {
                                                runCatching { LuvApiClient.deleteStaffUser(u.userId) }
                                                    .onSuccess {
                                                        selectedUser = null
                                                        userHits = userHits.filterNot { it.userId == u.userId }
                                                        toast("Konto gelöscht")
                                                    }
                                                    .onFailure { toast(it.message ?: "Fehler") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AdminTab.Live -> {
                        AdminCard {
                            Text("Live-Hinweis", fontFamily = DisplayFont, color = TextPrimary, fontSize = 20.sp)
                            Text(
                                "Kurze Nachricht an alle — erscheint als Popup (~5 Sekunden) mit schrumpfendem Balken.",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 13.sp
                            )
                            AdminField(liveText, { liveText = it.take(160) }, "Nachricht …")
                            Text(
                                "${liveText.length}/160",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 11.sp
                            )
                            AdminPrimaryBtn("Jetzt senden", enabled = liveText.trim().length >= 2 && !busy) {
                                busy = true
                                scope.launch {
                                    runCatching { LuvApiClient.sendLiveNotice(liveText.trim()) }
                                        .onSuccess {
                                            LiveNoticeBus.offer(it)
                                            toast("Gesendet")
                                            liveText = ""
                                        }
                                        .onFailure { toast(it.message ?: "Fehler") }
                                    busy = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    permEdit?.let { mod ->
        var draft by remember(mod.userId) {
            mutableStateOf(mod.permissions.toMutableMap())
        }
        AlertDialog(
            onDismissRequest = { permEdit = null },
            containerColor = BgSoft,
            title = {
                Text("Rechte · ${mod.nickname}", fontFamily = DisplayFont, color = TextPrimary)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    permGroups.forEach { g ->
                        Text(
                            "${g.icon} ${g.label}",
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            fontSize = 15.sp
                        )
                        Text(g.description, color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
                        g.permissions.forEach { (id, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = draft[id] == true,
                                    onCheckedChange = { checked ->
                                        draft = draft.toMutableMap().apply { put(id, checked) }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = AccentRose)
                                )
                                Text(label, color = TextPrimary, fontFamily = BodyFont, fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching {
                            LuvApiClient.setModeratorPermissions(mod.userId, draft)
                        }.onSuccess { updated ->
                            moderators = moderators.map {
                                if (it.userId == updated.userId) updated else it
                            }
                            permEdit = null
                            toast("Rechte gespeichert")
                        }.onFailure { toast(it.message ?: "Fehler") }
                    }
                }) {
                    Text("Speichern", color = AccentRose, fontFamily = DisplayFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { permEdit = null }) {
                    Text("Abbrechen", color = TextMuted)
                }
            }
        )
    }
}

@Composable
fun LiveNoticePopup() {
    val notice by LiveNoticeBus.pending.collectAsStateWithLifecycle()
    val current = notice ?: return
    val progress = remember(current.id) { Animatable(1f) }

    LaunchedEffect(current.id) {
        progress.snapTo(1f)
        progress.animateTo(0f, animationSpec = tween(5000, easing = LinearEasing))
        LiveNoticeBus.consumeShown(current.id)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .zIndex(80f),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF21A2030))
                .border(1.dp, AccentRose.copy(0.45f), RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Text(
                "📣 ${current.authorNickname}",
                color = AccentRose,
                fontFamily = DisplayFont,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                current.message,
                color = TextPrimary,
                fontFamily = BodyFont,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(AccentRose)
                )
            }
        }
    }
}

@Composable
private fun AdminStatGrid(vararg items: Pair<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.toList().chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgSoft)
                            .padding(14.dp)
                    ) {
                        Text(value, color = TextPrimary, fontFamily = DisplayFont, fontSize = 22.sp)
                        Text(label, color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AdminCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgSoft)
            .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = { content() }
    )
}

@Composable
private fun AdminEmpty(text: String) {
    Text(text, color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
}

@Composable
private fun AdminAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(AccentRose.copy(0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.take(1).uppercase(),
            color = TextPrimary,
            fontFamily = DisplayFont,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun AdminField(value: String, onChange: (String) -> Unit, hint: String) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(color = TextPrimary, fontFamily = BodyFont, fontSize = 15.sp),
        cursorBrush = SolidColor(AccentRose),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgDeep)
            .padding(14.dp),
        decorationBox = { inner ->
            if (value.isBlank()) {
                Text(hint, color = TextMuted.copy(0.7f), fontFamily = BodyFont, fontSize = 15.sp)
            }
            inner()
        }
    )
}

@Composable
private fun AdminPrimaryBtn(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (enabled) AccentRose.copy(0.85f) else Color.White.copy(0.08f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontFamily = DisplayFont, fontSize = 15.sp)
    }
}

@Composable
private fun AdminGhostBtn(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BgDeep)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = TextPrimary, fontFamily = BodyFont, fontSize = 13.sp)
    }
}

@Composable
private fun AdminChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) AccentRose.copy(0.28f) else BgDeep)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = TextPrimary, fontFamily = BodyFont, fontSize = 12.sp)
    }
}

@Composable
private fun AdminDangerBtn(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF3A2430))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
    }
}

@Composable
private fun HubReportCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    canAct: Boolean,
    onKeep: () -> Unit,
    onDelete: () -> Unit
) {
    var bitmap by remember(imageUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imageUrl) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val raw = imageUrl?.trim().orEmpty()
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
    AdminCard {
        Text(title, color = TextPrimary, fontFamily = DisplayFont, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        if (canAct) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    AdminGhostBtn("Behalten", onKeep)
                }
                Box(modifier = Modifier.weight(1f)) {
                    AdminDangerBtn("Löschen", onDelete)
                }
            }
        }
    }
}
