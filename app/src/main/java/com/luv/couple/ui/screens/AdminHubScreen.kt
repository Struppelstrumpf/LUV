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
import com.luv.couple.data.PeerPalette
import com.luv.couple.net.HelpMessageInfo
import com.luv.couple.net.LiveNoticeBus
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.PeerReportInfo
import com.luv.couple.net.PublicReportInfo
import com.luv.couple.net.StaffLobbyCard
import com.luv.couple.net.StaffPermGroup
import com.luv.couple.net.StaffUserCard
import com.luv.couple.net.VoucherInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    Market("Markt", "🏪"),
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
            if (isAdmin || can("mods.manage")) add(AdminTab.Mods)
            if (can("reports.view")) add(AdminTab.Reports)
            if (can("codes.view")) add(AdminTab.Codes)
            if (can("gm.search")) add(AdminTab.Users)
            if (can("market.settings")) add(AdminTab.Market)
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
    var overviewHelp by remember { mutableIntStateOf(0) }
    var overviewMods by remember { mutableIntStateOf(0) }
    var overviewVouchers by remember { mutableIntStateOf(0) }
    var permGroups by remember { mutableStateOf<List<StaffPermGroup>>(emptyList()) }

    var moderators by remember { mutableStateOf<List<StaffUserCard>>(emptyList()) }
    var modInvite by remember { mutableStateOf("") }
    var permEdit by remember { mutableStateOf<StaffUserCard?>(null) }

    var publicReports by remember { mutableStateOf<List<PublicReportInfo>>(emptyList()) }
    var peerReports by remember { mutableStateOf<List<PeerReportInfo>>(emptyList()) }
    var helpMessages by remember { mutableStateOf<List<HelpMessageInfo>>(emptyList()) }

    var vouchers by remember { mutableStateOf<List<VoucherInfo>>(emptyList()) }
    var showVoucherWizard by remember { mutableStateOf(false) }
    var liveRooms by remember { mutableStateOf<List<StaffLobbyCard>>(emptyList()) }
    var showLiveRooms by remember { mutableStateOf(false) }

    var userQuery by remember { mutableStateOf("") }
    var userHits by remember { mutableStateOf<List<StaffUserCard>>(emptyList()) }
    var selectedUser by remember { mutableStateOf<StaffUserCard?>(null) }
    var userLobbies by remember { mutableStateOf<List<StaffLobbyCard>>(emptyList()) }
    var selectedLobby by remember { mutableStateOf<StaffLobbyCard?>(null) }
    var confirmDeleteLobby by remember { mutableStateOf<StaffLobbyCard?>(null) }
    var nickEdit by remember { mutableStateOf("") }
    var coinDelta by remember { mutableStateOf("10") }

    var liveText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    var marketWindowDays by remember { mutableIntStateOf(90) }
    var marketWindowOptions by remember { mutableStateOf(listOf(7, 14, 30, 60, 90, 180)) }
    var achievementDailyCap by remember { mutableIntStateOf(12) }
    var achievementDailyCapMin by remember { mutableIntStateOf(0) }
    var achievementDailyCapMax by remember { mutableIntStateOf(500) }
    var achievementDailyCapText by remember { mutableStateOf("12") }

    fun toast(msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun loadUserLobbies(userId: String) {
        scope.launch {
            userLobbies = runCatching { LuvApiClient.listUserLobbies(userId) }
                .onFailure { toast(it.message ?: "Lobbys laden fehlgeschlagen") }
                .getOrDefault(emptyList())
        }
    }

    fun openUserById(userId: String, nickname: String = "") {
        if (!can("gm.search") || userId.isBlank()) return
        tab = AdminTab.Users
        showLiveRooms = false
        selectedLobby = null
        scope.launch {
            busy = true
            val hits = runCatching { LuvApiClient.searchStaffUsers(nickname.ifBlank { userId }) }
                .getOrDefault(emptyList())
            val hit = hits.firstOrNull { it.userId == userId } ?: hits.firstOrNull()
            if (hit != null) {
                userHits = hits
                selectedUser = hit
                nickEdit = hit.nickname
                loadUserLobbies(hit.userId)
            } else {
                toast("Nutzer nicht gefunden")
            }
            busy = false
        }
    }

    fun loadLiveRooms() {
        scope.launch {
            liveRooms = runCatching { LuvApiClient.listStaffLiveRooms() }
                .onFailure { toast(it.message ?: "Lobbys laden fehlgeschlagen") }
                .getOrDefault(emptyList())
        }
    }

    fun reloadOverview() {
        scope.launch {
            runCatching { LuvApiClient.fetchStaffOverview() }
                .onSuccess {
                    overviewUsers = it.users
                    overviewRooms = it.rooms
                    overviewPublic = it.openPublicReports
                    overviewPeer = it.openPeerReports
                    overviewHelp = it.openHelpMessages
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
            AdminTab.Mods -> if (isAdmin || can("mods.manage")) {
                runCatching { LuvApiClient.listModerators() }
                    .onSuccess {
                        moderators = it.first
                        if (it.second.isNotEmpty()) permGroups = it.second
                    }
            }
            AdminTab.Reports -> if (can("reports.view")) {
                publicReports = runCatching { LuvApiClient.listPublicReports() }.getOrDefault(emptyList())
                peerReports = runCatching { LuvApiClient.listPeerReports() }.getOrDefault(emptyList())
                helpMessages = runCatching { LuvApiClient.listHelpMessages() }.getOrDefault(emptyList())
            }
            AdminTab.Codes -> if (can("codes.view")) {
                vouchers = runCatching { LuvApiClient.listVouchers() }.getOrDefault(emptyList())
            }
            AdminTab.Market -> if (can("market.settings")) {
                runCatching { LuvApiClient.fetchAdminMarketSettings() }
                    .onSuccess {
                        marketWindowDays = it.priceWindowDays
                        marketWindowOptions = it.options
                        achievementDailyCap = it.achievementDailyCap
                        achievementDailyCapMin = it.achievementDailyCapMin
                        achievementDailyCapMax = it.achievementDailyCapMax
                        achievementDailyCapText = it.achievementDailyCap.toString()
                    }
                    .onFailure { banner = it.message }
            }
            else -> Unit
        }
    }

    if (showVoucherWizard && can("codes.edit")) {
        VoucherWizardDialog(
            busy = busy,
            onDismiss = { showVoucherWizard = false },
            onCreate = { c, items, maxR, days, forever, codeStr ->
                busy = true
                scope.launch {
                    runCatching {
                        LuvApiClient.createVoucher(
                            coins = c,
                            maxRedeems = maxR,
                            validDays = days,
                            forever = forever,
                            code = codeStr.ifBlank { null },
                            items = items
                        )
                    }.onSuccess {
                        vouchers = listOf(it) + vouchers
                        toast("Code ${it.code} erstellt")
                        showVoucherWizard = false
                        reloadOverview()
                    }.onFailure { toast(it.message ?: "Fehler") }
                    busy = false
                }
            }
        )
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
                        if (showLiveRooms && can("gm.search")) {
                            AdminCard {
                                Text(
                                    "← Übersicht",
                                    color = TextMuted,
                                    fontFamily = BodyFont,
                                    modifier = Modifier
                                        .clickable { showLiveRooms = false }
                                        .padding(vertical = 4.dp)
                                )
                                Text("Lobbys live", fontFamily = DisplayFont, color = TextPrimary, fontSize = 20.sp)
                                AdminGhostBtn("Aktualisieren") { loadLiveRooms() }
                            }
                            if (liveRooms.isEmpty()) {
                                AdminEmpty("Gerade keine Live-Lobbys.")
                            }
                            liveRooms.forEach { lobby ->
                                AdminCard {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedLobby = lobby
                                                selectedUser = null
                                                tab = AdminTab.Users
                                                showLiveRooms = false
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(lobby.name, color = TextPrimary, fontFamily = DisplayFont)
                                            Text(
                                                "${lobby.code} · ${lobby.online} online · ${lobby.memberCount}/${lobby.capacity}",
                                                color = TextMuted,
                                                fontFamily = BodyFont,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Text("›", color = TextMuted, fontSize = 22.sp)
                                    }
                                }
                            }
                        } else {
                            AdminStatGrid(
                                items = listOf(
                                    Triple("Nutzer", "$overviewUsers", {
                                        if (can("gm.search")) {
                                            tab = AdminTab.Users
                                            selectedUser = null
                                            selectedLobby = null
                                        }
                                    }),
                                    Triple("Lobbys live", "$overviewRooms", {
                                        if (can("gm.search")) {
                                            showLiveRooms = true
                                            loadLiveRooms()
                                        }
                                    }),
                                    Triple("Bild-Meldungen", "$overviewPublic", {
                                        if (can("reports.view")) tab = AdminTab.Reports
                                    }),
                                    Triple("Lobby-Meldungen", "$overviewPeer", {
                                        if (can("reports.view")) tab = AdminTab.Reports
                                    }),
                                    Triple("Hilfe", "$overviewHelp", {
                                        if (can("reports.view")) tab = AdminTab.Reports
                                    }),
                                    Triple("Moderatoren", "$overviewMods", {
                                        if (isAdmin || can("mods.manage")) tab = AdminTab.Mods
                                    }),
                                    Triple("Codes", "$overviewVouchers", {
                                        if (can("codes.view")) tab = AdminTab.Codes
                                    })
                                )
                            )
                            AdminCard {
                                Text(
                                    "Willkommen, ${account?.nickname ?: "Team"}.",
                                    color = TextPrimary,
                                    fontFamily = DisplayFont,
                                    fontSize = 18.sp
                                )
                                Text(
                                    "Tippe auf eine Kachel — alles ist verlinkt. Mods mit vollen Rechten sehen dieselben Bereiche wie Admins.",
                                    color = TextMuted,
                                    fontFamily = BodyFont,
                                    fontSize = 13.sp
                                )
                            }
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
                        Text("Hilfe-Anfragen", fontFamily = DisplayFont, color = TextPrimary, fontSize = 20.sp)
                        if (helpMessages.isEmpty()) AdminEmpty("Keine offenen Hilfe-Nachrichten.")
                        helpMessages.forEach { m ->
                            HubHelpCard(
                                nickname = m.nickname,
                                message = m.message,
                                createdAt = m.createdAt,
                                canAct = can("reports.act"),
                                canOpenUser = can("gm.search") && !m.userId.isNullOrBlank(),
                                onDelete = {
                                    scope.launch {
                                        runCatching { LuvApiClient.deleteHelpMessage(m.id) }
                                            .onSuccess {
                                                helpMessages = helpMessages.filterNot { it.id == m.id }
                                                toast("Gelöscht")
                                                reloadOverview()
                                            }
                                            .onFailure { toast(it.message ?: "Fehler") }
                                    }
                                },
                                onOpenProfile = {
                                    val uid = m.userId
                                    if (!uid.isNullOrBlank()) {
                                        onOpenProfile(uid, m.nickname)
                                    }
                                },
                                onOpenAdminUser = {
                                    val uid = m.userId
                                    if (!uid.isNullOrBlank()) {
                                        openUserById(uid, m.nickname)
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
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
                                Text(
                                    "Schritt für Schritt: Coins, Items oder beides — dann Gültigkeit und Code.",
                                    color = TextMuted,
                                    fontFamily = BodyFont,
                                    fontSize = 13.sp
                                )
                                AdminPrimaryBtn("🎟️ Neuen Code anlegen", enabled = !busy) {
                                    showVoucherWizard = true
                                }
                            }
                        }
                        vouchers.forEach { v ->
                            AdminCard {
                                Text(v.code, color = TextPrimary, fontFamily = DisplayFont, fontSize = 18.sp)
                                Text(
                                    buildString {
                                        if (v.coins > 0) append("${v.coins} Coins")
                                        else append("0 Coins")
                                        if (v.items.isNotEmpty()) {
                                            append(" · ${v.items.size} Item-Art(en)")
                                        }
                                        append(" · ${v.redeemCount}/${v.maxRedeems}")
                                    },
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
                        val lobbyView = selectedLobby
                        if (lobbyView != null) {
                            AdminCard {
                                Text(
                                    "Zurück",
                                    color = TextMuted,
                                    fontFamily = BodyFont,
                                    modifier = Modifier
                                        .clickable {
                                            selectedLobby = null
                                            selectedUser?.userId?.let { loadUserLobbies(it) }
                                        }
                                        .padding(vertical = 4.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    lobbyView.name,
                                    fontFamily = DisplayFont,
                                    color = TextPrimary,
                                    fontSize = 22.sp
                                )
                                Text(
                                    "Code ${lobbyView.code} · Host ${lobbyView.hostNickname}",
                                    color = TextMuted,
                                    fontFamily = BodyFont,
                                    fontSize = 13.sp
                                )
                                Text(
                                    buildString {
                                        append(if (lobbyView.live) "Live" else if (lobbyView.active) "Gespeichert" else "Inaktiv")
                                        append(" · ${lobbyView.online} online")
                                        append(" · ${lobbyView.memberCount}/${lobbyView.capacity} Plätze")
                                        append(if (lobbyView.isFree) " · gratis" else " · bezahlt")
                                    },
                                    color = TextPrimary,
                                    fontFamily = BodyFont,
                                    fontSize = 13.sp
                                )
                                if (lobbyView.invite.isNotBlank()) {
                                    Text(
                                        lobbyView.invite,
                                        color = TextMuted,
                                        fontFamily = BodyFont,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Mitglieder", fontFamily = DisplayFont, color = TextPrimary, fontSize = 16.sp)
                                if (lobbyView.members.isEmpty()) {
                                    Text(
                                        "Keine Mitglieder in der Liste.",
                                        color = TextMuted,
                                        fontFamily = BodyFont,
                                        fontSize = 13.sp
                                    )
                                } else {
                                    lobbyView.members.forEach { m ->
                                        val swatch = if (m.colorIndex >= 0) {
                                            Color(PeerPalette.strokeColor(m.colorIndex))
                                        } else {
                                            TextMuted.copy(0.35f)
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    openUserById(m.userId, m.nickname)
                                                }
                                                .padding(vertical = 6.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .background(swatch)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(m.petEmoji, fontSize = 18.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                m.nickname,
                                                color = TextPrimary,
                                                fontFamily = BodyFont,
                                                fontSize = 14.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                if (m.online) "online" else "offline",
                                                color = TextMuted,
                                                fontFamily = BodyFont,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                AdminDangerBtn("Lobby löschen") {
                                    confirmDeleteLobby = lobbyView
                                }
                                AdminGhostBtn("Aktualisieren") {
                                    scope.launch {
                                        runCatching { LuvApiClient.fetchStaffLobby(lobbyView.code) }
                                            .onSuccess { selectedLobby = it }
                                            .onFailure { toast(it.message ?: "Fehler") }
                                    }
                                }
                            }
                        } else {
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
                                        selectedLobby = null
                                        userLobbies = emptyList()
                                        busy = false
                                    }
                                }
                            }
                            if (selectedUser == null) {
                                userHits.forEach { u ->
                                    AdminCard {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedUser = u
                                                    nickEdit = u.nickname
                                                    selectedLobby = null
                                                    loadUserLobbies(u.userId)
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
                            }
                            selectedUser?.let { u ->
                                AdminCard {
                                    Text(
                                        "Zurück zur Suche",
                                        color = TextMuted,
                                        fontFamily = BodyFont,
                                        modifier = Modifier
                                            .clickable {
                                                selectedUser = null
                                                userLobbies = emptyList()
                                                selectedLobby = null
                                            }
                                            .padding(vertical = 4.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
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
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Hochzeit / Wartezeit", fontFamily = DisplayFont, color = TextPrimary, fontSize = 16.sp)
                                    u.marriage?.let { m ->
                                        if (m.status == "engaged" || m.status == "wedding" || m.status == "married" || m.status == "proposed") {
                                            Text(
                                                when (m.status) {
                                                    "proposed" -> "Antrag offen · von ${m.proposedBy ?: "?"}"
                                                    "engaged" -> "Verlobt · Rest ${m.engageRemainingLabel ?: "…"} · Partner ${m.partnerNickname ?: "…"}"
                                                    "wedding" -> "Hochzeitsleinwand · Rest ${m.weddingRemainingLabel ?: "…"} · ${m.weddingLobbyCode ?: ""}"
                                                    "married" -> "Verheiratet mit ${m.partnerNickname ?: "…"}"
                                                    else -> m.status
                                                },
                                                color = TextMuted,
                                                fontFamily = BodyFont,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                    // Scheidungs-Cooldown steht in der Staff-Card nur als Text wenn API sie liefert — Placeholder via remarriage note
                                    if (u.marriage == null || u.marriage?.status !in setOf("engaged", "wedding", "married", "proposed")) {
                                        Text(
                                            "Keine aktive Ehe/Verlobung",
                                            color = TextMuted,
                                            fontFamily = BodyFont,
                                            fontSize = 13.sp
                                        )
                                    }
                                    u.marriageCooldownLabel?.let { cd ->
                                        Text(
                                            "Scheidungs-Cooldown: noch $cd",
                                            color = AccentRose,
                                            fontFamily = BodyFont,
                                            fontSize = 13.sp
                                        )
                                    }
                                    u.marriage?.let { m ->
                                        if (m.status == "engaged" || m.status == "wedding") {
                                            if (can("gm.editCoins") && (m.status == "engaged" || m.status == "wedding")) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    AdminPrimaryBtn("Nächster Schritt") {
                                                        scope.launch {
                                                            runCatching {
                                                                LuvApiClient.staffAdvanceMarriage(u.userId, "next")
                                                            }.onSuccess {
                                                                selectedUser = it
                                                                toast("Weitergeschaltet")
                                                            }.onFailure { toast(it.message ?: "Fehler") }
                                                        }
                                                    }
                                                    AdminGhostBtn("1 Tag") {
                                                        scope.launch {
                                                            runCatching {
                                                                LuvApiClient.staffAdvanceMarriage(
                                                                    u.userId,
                                                                    "set_days",
                                                                    days = 1
                                                                )
                                                            }.onSuccess {
                                                                selectedUser = it
                                                                toast("Auf 1 Tag gesetzt")
                                                            }.onFailure { toast(it.message ?: "Fehler") }
                                                        }
                                                    }
                                                    AdminGhostBtn("0 Tage") {
                                                        scope.launch {
                                                            runCatching {
                                                                LuvApiClient.staffAdvanceMarriage(
                                                                    u.userId,
                                                                    "set_days",
                                                                    days = 0
                                                                )
                                                            }.onSuccess {
                                                                selectedUser = it
                                                                toast("Sofort weiter")
                                                            }.onFailure { toast(it.message ?: "Fehler") }
                                                        }
                                                    }
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
                                                            userLobbies = emptyList()
                                                            userHits = userHits.filterNot { it.userId == u.userId }
                                                            toast("Konto gelöscht")
                                                        }
                                                        .onFailure { toast(it.message ?: "Fehler") }
                                                }
                                            }
                                        }
                                    }
                                }
                                AdminCard {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Lobbys (Host)",
                                            fontFamily = DisplayFont,
                                            color = TextPrimary,
                                            fontSize = 18.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        AdminGhostBtn("Aktualisieren") { loadUserLobbies(u.userId) }
                                    }
                                    if (userLobbies.isEmpty()) {
                                        Text(
                                            "Keine gehosteten Lobbys.",
                                            color = TextMuted,
                                            fontFamily = BodyFont,
                                            fontSize = 13.sp
                                        )
                                    } else {
                                        userLobbies.forEach { lobby ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color.White.copy(0.04f))
                                                    .clickable {
                                                        scope.launch {
                                                            runCatching {
                                                                LuvApiClient.fetchStaffLobby(lobby.code)
                                                            }.onSuccess { selectedLobby = it }
                                                                .onFailure {
                                                                    selectedLobby = lobby
                                                                    toast(it.message ?: "Detail nicht geladen")
                                                                }
                                                        }
                                                    }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        lobby.name,
                                                        color = TextPrimary,
                                                        fontFamily = DisplayFont,
                                                        fontSize = 16.sp
                                                    )
                                                    Text(
                                                        "${lobby.code} · ${lobby.online} online · ${lobby.memberCount}/${lobby.capacity}" +
                                                            if (lobby.live) " · live" else "",
                                                        color = TextMuted,
                                                        fontFamily = BodyFont,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                Text("›", color = TextMuted, fontSize = 20.sp)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AdminTab.Market -> {
                        AdminCard {
                            Text(
                                "Marktplatz-Preise",
                                fontFamily = DisplayFont,
                                color = TextPrimary,
                                fontSize = 20.sp
                            )
                            Text(
                                "Zeitfenster für die Preisspanne (günstigster und teuerster Verkauf) in Angebot erstellen und Marktplatz.",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Aktuell: $marketWindowDays Tage",
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            marketWindowOptions.chunked(3).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { days ->
                                        val on = marketWindowDays == days
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (on) AccentRose.copy(0.28f)
                                                    else Color.White.copy(0.06f)
                                                )
                                                .clickable(enabled = !busy) {
                                                    busy = true
                                                    scope.launch {
                                                        runCatching {
                                                            LuvApiClient.setAdminMarketPriceWindow(days)
                                                        }.onSuccess {
                                                            marketWindowDays = it
                                                            toast("Preis-Fenster: $it Tage")
                                                        }.onFailure {
                                                            toast(it.message ?: "Speichern fehlgeschlagen")
                                                        }
                                                        busy = false
                                                    }
                                                }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "$days T",
                                                color = TextPrimary,
                                                fontFamily = if (on) DisplayFont else BodyFont,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                    repeat(3 - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text(
                                "Ohne Verkäufe im Fenster wird keine Verkaufsspanne gezeigt. Beim ersten Angebot erscheint die Spanne der aktuellen Listings.",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        AdminCard {
                            Text(
                                "Erfolge · Coins pro Tag",
                                fontFamily = DisplayFont,
                                color = TextPrimary,
                                fontSize = 20.sp
                            )
                            Text(
                                "Maximal so viele Coins darf ein Spieler pro Tag durch Erfolge abholen. " +
                                    "Ist das Limit teilweise erreicht, wird nur noch die Differenz gutgeschrieben.",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Aktuell: $achievementDailyCap Coins/Tag",
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AdminField(
                                achievementDailyCapText,
                                {
                                    achievementDailyCapText = it.filter { ch -> ch.isDigit() }.take(3)
                                },
                                "z. B. 12"
                            )
                            Text(
                                "Erlaubt: $achievementDailyCapMin–$achievementDailyCapMax",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AccentRose.copy(0.28f))
                                    .clickable(enabled = !busy) {
                                        val n = achievementDailyCapText.toIntOrNull()
                                        if (n == null || n < achievementDailyCapMin || n > achievementDailyCapMax) {
                                            toast("Wert $achievementDailyCapMin–$achievementDailyCapMax")
                                            return@clickable
                                        }
                                        busy = true
                                        scope.launch {
                                            runCatching {
                                                LuvApiClient.setAdminAchievementDailyCap(n)
                                            }.onSuccess {
                                                achievementDailyCap = it
                                                achievementDailyCapText = it.toString()
                                                toast("Erfolgs-Limit: $it Coins/Tag")
                                            }.onFailure {
                                                toast(it.message ?: "Speichern fehlgeschlagen")
                                            }
                                            busy = false
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Limit speichern",
                                    color = TextPrimary,
                                    fontFamily = DisplayFont,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    AdminTab.Live -> {
                        AdminCard {
                            Text("Live-Hinweis", fontFamily = DisplayFont, color = TextPrimary, fontSize = 20.sp)
                            Text(
                                "Nachricht an alle mit deinem Spitznamen. Wer gerade online ist, sieht sie sofort — andere beim nächsten App-Öffnen (bis 24 Stunden).",
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

    confirmDeleteLobby?.let { lobby ->
        AlertDialog(
            onDismissRequest = { confirmDeleteLobby = null },
            containerColor = BgSoft,
            title = {
                Text(
                    "Lobby löschen?",
                    fontFamily = DisplayFont,
                    color = TextPrimary,
                    fontSize = 22.sp
                )
            },
            text = {
                Text(
                    "„${lobby.name}“ (${lobby.code}) wird sofort aufgelöst. Alle Online-Spieler werden rausgeworfen — ohne Erstattung.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val code = lobby.code
                    confirmDeleteLobby = null
                    scope.launch {
                        runCatching { LuvApiClient.forceDeleteStaffLobby(code) }
                            .onSuccess {
                                selectedLobby = null
                                userLobbies = userLobbies.filterNot { it.code == code }
                                toast("Lobby gelöscht")
                                selectedUser?.userId?.let { loadUserLobbies(it) }
                                reloadOverview()
                            }
                            .onFailure { toast(it.message ?: "Löschen fehlgeschlagen") }
                    }
                }) {
                    Text("Ja, löschen", color = Color(0xFFFF5A6A), fontFamily = DisplayFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteLobby = null }) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont)
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
                "📣 Von ${current.authorNickname.ifBlank { "Team" }}",
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
fun StaffWarningPopup() {
    val warning by com.luv.couple.net.StaffWarningBus.pending.collectAsStateWithLifecycle()
    val current = warning ?: return
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .zIndex(85f),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF21A2030))
                .border(
                    1.dp,
                    if (current.severity == "gift") Color(0xFF81C784).copy(0.55f)
                    else Color(0xFFFF6B7A).copy(0.55f),
                    RoundedCornerShape(18.dp)
                )
                .padding(14.dp)
        ) {
            Text(
                when (current.severity) {
                    "gift" -> "Geschenk vom Team"
                    "final" -> "Letzte Verwarnung"
                    else -> "Verwarnung vom Team"
                },
                color = if (current.severity == "gift") Color(0xFF81C784) else Color(0xFFFF6B7A),
                fontFamily = DisplayFont,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                current.message,
                color = TextPrimary,
                fontFamily = BodyFont,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Von ${current.byNick.ifBlank { "Team" }} · bleibt unter Sozial · Freunde sichtbar",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Verstanden",
                color = AccentRose,
                fontFamily = BodyFont,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable {
                        val id = current.id
                        com.luv.couple.net.StaffWarningBus.consume(id)
                        scope.launch {
                            runCatching { LuvApiClient.ackStaffNotice(id) }
                        }
                    }
            )
        }
    }
}

@Composable
private fun AdminStatGrid(items: List<Triple<String, String, () -> Unit>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value, onClick) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgSoft)
                            .clickable(onClick = onClick)
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
internal fun AdminCard(content: @Composable () -> Unit) {
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
internal fun AdminField(value: String, onChange: (String) -> Unit, hint: String) {
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
internal fun AdminPrimaryBtn(
    label: String,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (enabled) AccentRose.copy(0.85f) else Color.White.copy(0.08f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (fillMaxWidth) 0.dp else 18.dp),
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
internal fun AdminChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun HubHelpCard(
    nickname: String,
    message: String,
    createdAt: Long,
    canAct: Boolean,
    canOpenUser: Boolean,
    onDelete: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAdminUser: () -> Unit,
) {
    val whenLabel = remember(createdAt) {
        if (createdAt <= 0L) ""
        else SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(createdAt))
    }
    AdminCard {
        Text(
            nickname,
            color = TextPrimary,
            fontFamily = DisplayFont,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (whenLabel.isNotBlank()) {
            Text(whenLabel, color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
        }
        Text(
            message,
            color = TextPrimary,
            fontFamily = BodyFont,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (canOpenUser) {
                AdminGhostBtn("Profil", onOpenProfile)
                AdminGhostBtn("Nutzer", onOpenAdminUser)
            }
            if (canAct) {
                AdminDangerBtn("Löschen", onDelete)
            }
        }
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
