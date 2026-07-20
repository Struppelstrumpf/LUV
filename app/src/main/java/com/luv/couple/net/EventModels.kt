package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class EventDecor(
    val particles: String = "none",
    val accentHex: String = "#E94E77",
    val bannerText: String = "",
    val intensity: Float = 0.55f,
    val ornaments: String = "none",
) {
    companion object {
        fun fromJson(o: JSONObject?): EventDecor {
            if (o == null) return EventDecor()
            return EventDecor(
                particles = o.optString("particles", "none").ifBlank { "none" },
                accentHex = o.optString("accentHex", "#E94E77").ifBlank { "#E94E77" },
                bannerText = o.optString("bannerText", ""),
                intensity = o.optDouble("intensity", 0.55).toFloat().coerceIn(0f, 1f),
                ornaments = o.optString("ornaments", "none").ifBlank { "none" },
            )
        }
    }
}

data class EventRewardItem(
    val kind: String,
    val itemId: String,
    val emoji: String,
    val label: String,
) {
    companion object {
        fun fromJson(o: JSONObject?): EventRewardItem? {
            if (o == null) return null
            val kind = o.optString("kind").trim()
            val itemId = o.optString("itemId").trim()
            if (kind.isEmpty() || itemId.isEmpty()) return null
            return EventRewardItem(
                kind = kind,
                itemId = itemId,
                emoji = o.optString("emoji", itemId),
                label = o.optString("label", itemId),
            )
        }
    }
}

data class EventQuest(
    val id: String,
    val title: String,
    val hint: String,
    val metric: String,
    val target: Int,
    val rewardCoins: Int,
    val progress: Int,
    val done: Boolean,
    val claimed: Boolean,
) {
    companion object {
        fun fromJson(o: JSONObject): EventQuest = EventQuest(
            id = o.optString("id"),
            title = o.optString("title", "Quest"),
            hint = o.optString("hint"),
            metric = o.optString("metric"),
            target = o.optInt("target", 1).coerceAtLeast(1),
            rewardCoins = o.optInt("rewardCoins", 0),
            progress = o.optInt("progress", 0).coerceAtLeast(0),
            done = o.optBoolean("done", false),
            claimed = o.optBoolean("claimed", false),
        )
    }
}

data class EventContestFeedItem(
    val entryId: String,
    val nickname: String,
    val prompt: String?,
    val imageUrl: String?,
    val strokes: Int = 0,
) {
    companion object {
        fun fromJson(o: JSONObject?): EventContestFeedItem? {
            if (o == null) return null
            val entryId = o.optString("entryId").trim()
            if (entryId.isEmpty()) return null
            return EventContestFeedItem(
                entryId = entryId,
                nickname = o.optString("nickname", "Jemand"),
                prompt = o.optString("prompt").takeIf { it.isNotBlank() },
                imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() },
                strokes = o.optInt("strokes", 0),
            )
        }
    }
}

data class EventContestWinner(
    val place: Int,
    val nickname: String,
    val entryId: String,
    val prompt: String?,
    val imageUrl: String?,
    val score: Int = 0,
) {
    companion object {
        fun fromJson(o: JSONObject): EventContestWinner = EventContestWinner(
            place = o.optInt("place", 0),
            nickname = o.optString("nickname", "Jemand"),
            entryId = o.optString("entryId"),
            prompt = o.optString("prompt").takeIf { it.isNotBlank() },
            imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() },
            score = o.optInt("score", 0),
        )
    }
}

data class EventContestPrize(
    val place: Int,
    val coins: Int,
    val rewardItem: EventRewardItem?,
    val grantMedal: Boolean = false,
) {
    companion object {
        fun fromJson(o: JSONObject?): EventContestPrize? {
            if (o == null) return null
            return EventContestPrize(
                place = o.optInt("place", 0),
                coins = o.optInt("coins", 0),
                rewardItem = EventRewardItem.fromJson(o.optJSONObject("rewardItem")),
                grantMedal = o.optBoolean("grantMedal", false),
            )
        }
    }
}

data class EventContestInfo(
    val enabled: Boolean = false,
    val votingOpen: Boolean = false,
    val canVote: Boolean = false,
    val feedItem: EventContestFeedItem? = null,
    val winners: List<EventContestWinner> = emptyList(),
    val claimablePrize: EventContestPrize? = null,
    val prizeClaimed: Boolean = false,
    val promptHint: String? = null,
    val lobbyCreated: Boolean = false,
    val prizesReady: Boolean = false,
) {
    companion object {
        fun fromJson(o: JSONObject?): EventContestInfo? {
            if (o == null) return null
            if (!o.optBoolean("enabled", true) && !o.has("votingOpen")) return null
            val winnersArr = o.optJSONArray("winners")
            val winners = buildList {
                if (winnersArr != null) {
                    for (i in 0 until winnersArr.length()) {
                        val w = winnersArr.optJSONObject(i) ?: continue
                        add(EventContestWinner.fromJson(w))
                    }
                }
            }
            return EventContestInfo(
                enabled = o.optBoolean("enabled", true),
                votingOpen = o.optBoolean("votingOpen", false),
                canVote = o.optBoolean("canVote", false),
                feedItem = EventContestFeedItem.fromJson(o.optJSONObject("feedItem")),
                winners = winners,
                claimablePrize = EventContestPrize.fromJson(o.optJSONObject("claimablePrize")),
                prizeClaimed = o.optBoolean("prizeClaimed", false),
                promptHint = o.optString("promptHint").takeIf { it.isNotBlank() },
                lobbyCreated = o.optBoolean("lobbyCreated", false),
                prizesReady = o.optBoolean("prizesReady", false),
            )
        }
    }
}

data class SeasonEvent(
    val id: String,
    val title: String,
    val emoji: String,
    val description: String,
    val hint: String,
    val rewardCoinsPerCollect: Int,
    val collectTarget: Int,
    val milestoneBonusCoins: Int,
    val progress: Int,
    val collectedToday: Boolean,
    val claimedMilestone: Boolean,
    val itemGranted: Boolean,
    val canCollect: Boolean,
    val windowStart: String?,
    val windowEnd: String?,
    val decor: EventDecor,
    val rewardItem: EventRewardItem?,
    val quests: List<EventQuest> = emptyList(),
    val lobbyEnabled: Boolean = false,
    val contestEnabled: Boolean = false,
    val canCreateLobby: Boolean = false,
    val eventPrompt: String? = null,
    val contest: EventContestInfo? = null,
) {
    companion object {
        fun fromJson(o: JSONObject): SeasonEvent {
            val questsArr = o.optJSONArray("quests")
            val quests = buildList {
                if (questsArr != null) {
                    for (i in 0 until questsArr.length()) {
                        val q = questsArr.optJSONObject(i) ?: continue
                        add(EventQuest.fromJson(q))
                    }
                }
            }
            return SeasonEvent(
            id = o.optString("id"),
            title = o.optString("title"),
            emoji = o.optString("emoji", "🎉"),
            description = o.optString("description"),
            hint = o.optString("hint"),
            rewardCoinsPerCollect = o.optInt("rewardCoinsPerCollect", 2),
            collectTarget = o.optInt("collectTarget", 3).coerceAtLeast(1),
            milestoneBonusCoins = o.optInt("milestoneBonusCoins", 0),
            progress = o.optInt("progress", 0),
            collectedToday = o.optBoolean("collectedToday", false),
            claimedMilestone = o.optBoolean("claimedMilestone", false),
            itemGranted = o.optBoolean("itemGranted", false),
            canCollect = o.optBoolean("canCollect", false),
            windowStart = o.optString("windowStart").takeIf { it.isNotBlank() },
            windowEnd = o.optString("windowEnd").takeIf { it.isNotBlank() },
            decor = EventDecor.fromJson(o.optJSONObject("decor")),
            rewardItem = EventRewardItem.fromJson(o.optJSONObject("rewardItem")),
            quests = quests,
            lobbyEnabled = o.optJSONObject("lobby")?.optBoolean("enabled", false) == true,
            contestEnabled = o.optJSONObject("contest")?.optBoolean("enabled", false) == true,
            canCreateLobby = o.optBoolean("canCreateLobby", false),
            eventPrompt = o.optString("eventPrompt").takeIf { it.isNotBlank() },
            contest = EventContestInfo.fromJson(o.optJSONObject("contest")),
            )
        }
    }
}

data class EventsState(
    val dayKey: String = "",
    val active: List<SeasonEvent> = emptyList(),
    val upcoming: List<SeasonEvent> = emptyList(),
    val primaryDecor: EventDecor? = null,
    val primaryEventId: String? = null,
) {
    companion object {
        fun fromJson(json: JSONObject): EventsState {
            fun list(key: String): List<SeasonEvent> {
                val arr = json.optJSONArray(key) ?: return emptyList()
                return buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        add(SeasonEvent.fromJson(o))
                    }
                }
            }
            return EventsState(
                dayKey = json.optString("dayKey"),
                active = list("active"),
                upcoming = list("upcoming"),
                primaryDecor = json.optJSONObject("primaryDecor")?.let { EventDecor.fromJson(it) },
                primaryEventId = json.optString("primaryEventId").takeIf { it.isNotBlank() },
            )
        }
    }
}

object EventSession {
    private val _state = MutableStateFlow<EventsState?>(null)
    val state: StateFlow<EventsState?> = _state.asStateFlow()

    fun update(s: EventsState?) {
        _state.value = s
    }

    val activeDecor: EventDecor?
        get() = _state.value?.primaryDecor?.takeIf { it.particles != "none" || it.bannerText.isNotBlank() }

    /** Menü-Akzentfarbe vom aktiven Event (sonst null). */
    fun menuAccentOrNull(): androidx.compose.ui.graphics.Color? {
        val hex = _state.value?.primaryDecor?.accentHex?.trim().orEmpty()
        if (hex.isBlank()) return null
        return runCatching {
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
        }.getOrNull()
    }

    /** Kleines Schmuck-Emoji fürs Hauptmenü (neben +). */
    fun menuGlyphOrNull(): String? {
        val s = _state.value ?: return null
        val decor = s.primaryDecor ?: return null
        val fromOrnament = when (decor.ornaments) {
            "wreath" -> "🎄"
            "hearts" -> "💕"
            "spark" -> "✨"
            else -> null
        }
        if (fromOrnament != null) return fromOrnament
        val emoji = s.active.firstOrNull { it.id == s.primaryEventId }?.emoji
            ?: s.active.firstOrNull()?.emoji
        return emoji?.takeIf { it.isNotBlank() }
    }

    fun primaryEventForLobby(): SeasonEvent? {
        val s = _state.value ?: return null
        return s.active.firstOrNull { it.canCreateLobby }
            ?: s.active.firstOrNull { it.lobbyEnabled }
    }
}
