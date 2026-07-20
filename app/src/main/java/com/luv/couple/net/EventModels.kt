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
}
