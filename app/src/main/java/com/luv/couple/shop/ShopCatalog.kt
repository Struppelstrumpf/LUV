package com.luv.couple.shop

import com.luv.couple.R
import com.luv.couple.net.ShopPack

object ShopCatalog {
    const val MAX_BAR = 8
    val DEFAULT_BAR: List<String> = listOf("👍", "❌", "❤️", "😂", "😱", "😡", "😭")

    /** Standard-Begleiter — jeder startet damit ausgerüstet. */
    const val DEFAULT_PET = "🐣"

    /** Alle kaufbaren Reaktions-Emojis mit fairem Coin-Preis. */
    val EMOJIS: List<ShopEmoji> = listOf(
        ShopEmoji("👍", 5), ShopEmoji("👎", 5), ShopEmoji("❌", 5), ShopEmoji("❤️", 8),
        ShopEmoji("🧡", 8), ShopEmoji("💛", 8), ShopEmoji("💚", 8), ShopEmoji("💙", 8),
        ShopEmoji("💜", 8), ShopEmoji("🖤", 8), ShopEmoji("🤍", 8), ShopEmoji("🤎", 8),
        ShopEmoji("💔", 10), ShopEmoji("❣️", 10), ShopEmoji("💕", 12), ShopEmoji("💖", 12),
        ShopEmoji("💗", 12), ShopEmoji("💘", 14), ShopEmoji("💝", 14), ShopEmoji("💞", 12),
        ShopEmoji("💟", 10), ShopEmoji("❤️‍🔥", 16), ShopEmoji("❤️‍🩹", 14),
        ShopEmoji("😂", 8), ShopEmoji("🤣", 10), ShopEmoji("😊", 6), ShopEmoji("🙂", 5),
        ShopEmoji("😉", 6), ShopEmoji("😍", 10), ShopEmoji("🥰", 12), ShopEmoji("😘", 10),
        ShopEmoji("😗", 8), ShopEmoji("😙", 8), ShopEmoji("😚", 8), ShopEmoji("☺️", 8),
        ShopEmoji("😜", 8), ShopEmoji("🤪", 10), ShopEmoji("😝", 8), ShopEmoji("😛", 6),
        ShopEmoji("😎", 12), ShopEmoji("🤩", 12), ShopEmoji("🥳", 12), ShopEmoji("😏", 8),
        ShopEmoji("😱", 8), ShopEmoji("😨", 8), ShopEmoji("😰", 8), ShopEmoji("😥", 8),
        ShopEmoji("😢", 8), ShopEmoji("😭", 10), ShopEmoji("😡", 8), ShopEmoji("🤬", 12),
        ShopEmoji("😤", 8), ShopEmoji("😠", 8), ShopEmoji("😳", 8), ShopEmoji("🥺", 12),
        ShopEmoji("🥹", 12), ShopEmoji("😴", 6), ShopEmoji("🥱", 8), ShopEmoji("🤤", 8),
        ShopEmoji("🤔", 8), ShopEmoji("🙄", 8), ShopEmoji("😬", 8), ShopEmoji("🤐", 8),
        ShopEmoji("🤫", 8), ShopEmoji("🤭", 8), ShopEmoji("🫡", 10), ShopEmoji("🤝", 10),
        ShopEmoji("🙏", 8), ShopEmoji("💪", 10), ShopEmoji("👀", 8), ShopEmoji("🙈", 10),
        ShopEmoji("🙉", 10), ShopEmoji("🙊", 10), ShopEmoji("😺", 10), ShopEmoji("😸", 10),
        ShopEmoji("😹", 10), ShopEmoji("😻", 12), ShopEmoji("😼", 10), ShopEmoji("😽", 10),
        ShopEmoji("🔥", 10), ShopEmoji("✨", 8), ShopEmoji("⭐", 8), ShopEmoji("🌟", 10),
        ShopEmoji("💫", 10), ShopEmoji("⚡", 8), ShopEmoji("💥", 10), ShopEmoji("💯", 12),
        ShopEmoji("🎉", 10), ShopEmoji("🎊", 10), ShopEmoji("🎈", 8), ShopEmoji("🎁", 12),
        ShopEmoji("🌹", 12), ShopEmoji("🌸", 8), ShopEmoji("🍀", 8), ShopEmoji("🌈", 10),
        ShopEmoji("☀️", 6), ShopEmoji("🌙", 6), ShopEmoji("☄️", 12), ShopEmoji("🎯", 10),
        ShopEmoji("🏆", 14), ShopEmoji("👑", 16), ShopEmoji("💎", 18), ShopEmoji("🥇", 16),
        ShopEmoji("🐱", 12), ShopEmoji("🐶", 12), ShopEmoji("🐻", 12), ShopEmoji("🐼", 14),
        ShopEmoji("🦊", 14), ShopEmoji("🐰", 12), ShopEmoji("🦄", 18), ShopEmoji("🐸", 10),
        ShopEmoji("🐧", 12), ShopEmoji("🦋", 10), ShopEmoji("🐝", 8), ShopEmoji("🐢", 10),
        ShopEmoji("🍕", 8), ShopEmoji("🍩", 8), ShopEmoji("☕", 6), ShopEmoji("🍷", 10),
        ShopEmoji("🍺", 8), ShopEmoji("🧁", 8), ShopEmoji("🍓", 6), ShopEmoji("🍑", 10),
        ShopEmoji("🍪", 8), ShopEmoji("🍫", 8), ShopEmoji("🍿", 8), ShopEmoji("🧋", 10),
        ShopEmoji("🎵", 8), ShopEmoji("🎶", 8), ShopEmoji("📱", 6), ShopEmoji("💡", 6),
        ShopEmoji("📎", 5), ShopEmoji("✏️", 5), ShopEmoji("📌", 5), ShopEmoji("🔔", 8),
        ShopEmoji("💀", 12), ShopEmoji("☠️", 14), ShopEmoji("👻", 12), ShopEmoji("🤖", 14),
        ShopEmoji("👽", 14), ShopEmoji("👾", 14), ShopEmoji("💩", 10), ShopEmoji("🤡", 12),
        ShopEmoji("😈", 14), ShopEmoji("👿", 12), ShopEmoji("😇", 12), ShopEmoji("🫠", 12),
        ShopEmoji("🫢", 10), ShopEmoji("🫣", 10), ShopEmoji("🫡", 10), ShopEmoji("🥶", 10),
        ShopEmoji("🥵", 10), ShopEmoji("🥴", 10), ShopEmoji("😵", 10), ShopEmoji("🤯", 12),
        ShopEmoji("🫶", 14), ShopEmoji("🫰", 12), ShopEmoji("✌️", 8), ShopEmoji("🤞", 8),
        ShopEmoji("🤟", 8), ShopEmoji("🤘", 8), ShopEmoji("👏", 8), ShopEmoji("🙌", 10),
        ShopEmoji("👋", 6), ShopEmoji("🫡", 10), ShopEmoji("💅", 10), ShopEmoji("🤳", 8),
        ShopEmoji("🫂", 12), ShopEmoji("💤", 6), ShopEmoji("💢", 8), ShopEmoji("💬", 6),
        ShopEmoji("🫡", 10),
        // Premium → extrem teuer
        ShopEmoji("💋", 22), ShopEmoji("🫦", 28), ShopEmoji("💐", 30), ShopEmoji("🥂", 32),
        ShopEmoji("🍾", 36), ShopEmoji("🪽", 90), ShopEmoji("🦢", 70), ShopEmoji("🪷", 55),
        ShopEmoji("🫧", 40), ShopEmoji("🪄", 95), ShopEmoji("🪞", 60), ShopEmoji("🕯️", 45),
        ShopEmoji("⚔️", 130), ShopEmoji("🛡️", 125), ShopEmoji("🗡️", 115), ShopEmoji("🏹", 90),
        ShopEmoji("💠", 220), ShopEmoji("🔮", 280), ShopEmoji("🧿", 300), ShopEmoji("🛸", 2000),
        ShopEmoji("🚀", 1800), ShopEmoji("🛰️", 1700), ShopEmoji("🧬", 1600), ShopEmoji("🧠", 1600),
        ShopEmoji("🫀", 1500), ShopEmoji("🎭", 1200), ShopEmoji("🏆", 1100), ShopEmoji("🥇", 1300),
        ShopEmoji("🎖️", 800), ShopEmoji("♾️", 6000), ShopEmoji("🔱", 4500), ShopEmoji("⚛️", 4000),
        ShopEmoji("🐉", 10000), ShopEmoji("🐲", 1400), ShopEmoji("🐆", 600), ShopEmoji("🐅", 700),
        ShopEmoji("🦁", 750), ShopEmoji("🦅", 550), ShopEmoji("🐺", 480), ShopEmoji("🐯", 850),
        ShopEmoji("🐘", 900), ShopEmoji("🐋", 1000), ShopEmoji("🦈", 950), ShopEmoji("🦑", 700),
        ShopEmoji("🏯", 2000), ShopEmoji("🗼", 1800), ShopEmoji("🗽", 2200), ShopEmoji("⛪", 1500),
        ShopEmoji("🌌", 450), ShopEmoji("🪐", 520), ShopEmoji("☄️", 380), ShopEmoji("🌠", 400),
        ShopEmoji("💎", 650), ShopEmoji("👑", 800), ShopEmoji("🦄", 1200), ShopEmoji("⚜️", 5000),
        ShopEmoji("💖", 400), ShopEmoji("💕", 380), ShopEmoji("💗", 420), ShopEmoji("💘", 500),
        ShopEmoji("💝", 550), ShopEmoji("💞", 480), ShopEmoji("❤️‍🔥", 900), ShopEmoji("💯", 600)
    ).distinctBy { it.emoji }

    /** Profil-Hintergründe (meadow ist Starter, gratis). */
    val THEMES: List<ShopTheme> = listOf(
        ShopTheme("meadow", "Wiese", "🌿", 0),
        ShopTheme("forest", "Wald", "🌲", 18),
        ShopTheme("sunset", "Abendrot", "🌅", 20),
        ShopTheme("night", "Nacht", "🌙", 20),
        ShopTheme("snow", "Schnee", "❄️", 18),
        ShopTheme("blossom", "Blüte", "🌸", 22),
        ShopTheme("ocean", "Meer", "🌊", 22),
        ShopTheme("rain", "Regen", "🌧️", 18),
        ShopTheme("autumn", "Herbst", "🍂", 20),
        ShopTheme("stars", "Sterne", "✨", 24),
        ShopTheme("cabin", "Hütte", "🏠", 22),
        ShopTheme("lake", "See", "🏞️", 22),
        ShopTheme("lavender", "Lavendel", "💜", 24),
        ShopTheme("hearth", "Kamin", "🔥", 24),
        ShopTheme("dawn", "Morgengrauen", "🌄", 22),
        ShopTheme("desert", "Wüste", "🏜️", 22),
        ShopTheme("bamboo", "Bambus", "🎋", 24),
        ShopTheme("mist", "Nebel", "🌫️", 24),
        ShopTheme("golden", "Goldstunde", "☀️", 26),
        ShopTheme("iceberg", "Eisberg", "🧊", 26),
        ShopTheme("sakura", "Sakura", "💮", 28),
        ShopTheme("coral", "Koralle", "🪸", 28),
        ShopTheme("vineyard", "Weinberg", "🍇", 28),
        ShopTheme("storm", "Gewitter", "⛈️", 30),
        ShopTheme("candy", "Candy", "🍭", 30),
        ShopTheme("ember", "Glut", "🧡", 32),
        ShopTheme("volcano", "Vulkan", "🌋", 34),
        ShopTheme("aurora", "Nordlicht", "🌌", 36),
        ShopTheme("meteor", "Sternschnuppen", "☄️", 38),
        ShopTheme("galaxy", "Galaxie", "🪐", 40),
        ShopTheme("twilight", "Dämmerung", "🌆", 45),
        ShopTheme("neon", "Neon", "💠", 55),
        ShopTheme("abyss", "Abgrund", "🕳️", 60),
        ShopTheme("cherry", "Kirschgarten", "🍒", 48),
        ShopTheme("moss", "Moos", "🪴", 42),
        ShopTheme("sandstorm", "Sandsturm", "🏜️", 50),
        ShopTheme("frostfire", "Frostfeuer", "🧊", 70),
        ShopTheme("prism", "Prisma", "🔮", 85),
        ShopTheme("void", "Leere", "⬛", 120),
        ShopTheme("royal", "Königlich", "👑", 150),
        ShopTheme("celestial", "Himmlisch", "👼", 200),
        ShopTheme("inferno", "Inferno", "🔥", 280),
        ShopTheme("paradise", "Paradies", "🏝️", 350),
        ShopTheme("mythic", "Mythisch", "🐉", 500),
        ShopTheme("eternity", "Ewigkeit", "♾️", 1200),
        ShopTheme("cosmos", "Kosmos", "🛸", 2500),
        ShopTheme("legend", "Legende", "🏆", 5000),
        ShopTheme("divine", "Göttlich", "✨", 10000)
    )

    /**
     * Profil-Sticker zum Kaufen (auf die Leinwand kleben).
     * Getrennt von Begleitern und Reaktions-Emojis.
     */
    val STICKERS: List<ShopEmoji> = listOf(
        ShopEmoji("🌙", 5), ShopEmoji("❄️", 5), ShopEmoji("🌎", 5), ShopEmoji("🌛", 5), ShopEmoji("🌪️", 5),
        ShopEmoji("🌼", 5), ShopEmoji("🍔", 5), ShopEmoji("🍥", 5), ShopEmoji("🍰", 5),
        ShopEmoji("🍺", 5), ShopEmoji("🎉", 5), ShopEmoji("🎲", 5), ShopEmoji("🐀", 5),
        ShopEmoji("🐖", 5), ShopEmoji("🐙", 5), ShopEmoji("🐸", 5), ShopEmoji("👓", 5),
        ShopEmoji("💽", 5), ShopEmoji("📯", 5), ShopEmoji("🥅", 5), ShopEmoji("🥎", 5),
        ShopEmoji("🥗", 5), ShopEmoji("🦃", 5), ShopEmoji("🦝", 5), ShopEmoji("🧅", 5),
        ShopEmoji("🩰", 5), ShopEmoji("🩲", 5), ShopEmoji("🪘", 5), ShopEmoji("🪱", 5),
        ShopEmoji("🫐", 5), ShopEmoji("☃️", 6), ShopEmoji("⛳", 6), ShopEmoji("🌋", 6),
        ShopEmoji("🌜", 6), ShopEmoji("🌫️", 6), ShopEmoji("🍄", 6), ShopEmoji("🍟", 6),
        ShopEmoji("🍻", 6), ShopEmoji("🍿", 6), ShopEmoji("🎊", 6), ShopEmoji("🏀", 6),
        ShopEmoji("🐈", 6), ShopEmoji("🐊", 6), ShopEmoji("🐔", 6), ShopEmoji("🐗", 6),
        ShopEmoji("🐚", 6), ShopEmoji("🐹", 6), ShopEmoji("👢", 6), ShopEmoji("💮", 6),
        ShopEmoji("💾", 6), ShopEmoji("📱", 6), ShopEmoji("🔔", 6), ShopEmoji("🕶️", 6),
        ShopEmoji("🥝", 6), ShopEmoji("🥧", 6), ShopEmoji("🥮", 6), ShopEmoji("🦠", 6),
        ShopEmoji("🧸", 6), ShopEmoji("🩳", 6), ShopEmoji("⛄", 7), ShopEmoji("⛸️", 7),
        ShopEmoji("🌚", 7), ShopEmoji("🌦️", 7), ShopEmoji("🍅", 7), ShopEmoji("🍇", 7),
        ShopEmoji("🍕", 7), ShopEmoji("🍡", 7), ShopEmoji("🍫", 7), ShopEmoji("🎋", 7),
        ShopEmoji("🏐", 7), ShopEmoji("🏵️", 7), ShopEmoji("🐓", 7), ShopEmoji("🐢", 7),
        ShopEmoji("🐰", 7), ShopEmoji("🐽", 7), ShopEmoji("👒", 7), ShopEmoji("👙", 7),
        ShopEmoji("💿", 7), ShopEmoji("📲", 7), ShopEmoji("🔕", 7), ShopEmoji("🗻", 7),
        ShopEmoji("🥃", 7), ShopEmoji("🥜", 7), ShopEmoji("🥽", 7), ShopEmoji("🦁", 7),
        ShopEmoji("🧈", 7), ShopEmoji("🪆", 7), ShopEmoji("🪸", 7), ShopEmoji("☎️", 8),
        ShopEmoji("♠️", 8), ShopEmoji("🌕", 8), ShopEmoji("🌧️", 8), ShopEmoji("🌭", 8),
        ShopEmoji("🍈", 8), ShopEmoji("🍬", 8), ShopEmoji("🎍", 8), ShopEmoji("🎣", 8),
        ShopEmoji("🎩", 8), ShopEmoji("🎼", 8), ShopEmoji("🏈", 8), ShopEmoji("🏔️", 8),
        ShopEmoji("🐇", 8), ShopEmoji("🐌", 8), ShopEmoji("🐏", 8), ShopEmoji("🐣", 8),
        ShopEmoji("🐯", 8), ShopEmoji("👚", 8), ShopEmoji("💧", 8), ShopEmoji("📀", 8),
        ShopEmoji("🥀", 8), ShopEmoji("🥟", 8), ShopEmoji("🥼", 8), ShopEmoji("🦎", 8),
        ShopEmoji("🧂", 8), ShopEmoji("🫒", 8), ShopEmoji("🫗", 8), ShopEmoji("🫘", 8),
        ShopEmoji("♥️", 9), ShopEmoji("⛈️", 9), ShopEmoji("⛰️", 9), ShopEmoji("🌖", 9),
        ShopEmoji("🌲", 9), ShopEmoji("🌳", 9), ShopEmoji("🍉", 9), ShopEmoji("🍞", 9), ShopEmoji("🍭", 9),
        ShopEmoji("🎎", 9), ShopEmoji("🎓", 9), ShopEmoji("🎵", 9), ShopEmoji("🏉", 9),
        ShopEmoji("🐅", 9), ShopEmoji("🐍", 9), ShopEmoji("🐑", 9), ShopEmoji("🐛", 9),
        ShopEmoji("🐤", 9), ShopEmoji("🐿️", 9), ShopEmoji("👛", 9), ShopEmoji("💦", 9),
        ShopEmoji("📞", 9), ShopEmoji("🤿", 9), ShopEmoji("🥠", 9), ShopEmoji("🥤", 9),
        ShopEmoji("🥥", 9), ShopEmoji("🥪", 9), ShopEmoji("🥫", 9), ShopEmoji("🦺", 9),
        ShopEmoji("🧮", 9), ShopEmoji("☔", 10), ShopEmoji("♦️", 10), ShopEmoji("🌗", 10),
        ShopEmoji("🌩️", 10), ShopEmoji("🌮", 10), ShopEmoji("🌴", 10), ShopEmoji("🍊", 10),
        ShopEmoji("🍮", 10), ShopEmoji("🍱", 10), ShopEmoji("🎏", 10), ShopEmoji("🎥", 10),
        ShopEmoji("🎶", 10), ShopEmoji("🎽", 10), ShopEmoji("🎾", 10), ShopEmoji("🏕️", 10),
        ShopEmoji("🐆", 10), ShopEmoji("🐐", 10), ShopEmoji("🐜", 10), ShopEmoji("🐥", 10),
        ShopEmoji("🐲", 10), ShopEmoji("👔", 10), ShopEmoji("👜", 10), ShopEmoji("📟", 10),
        ShopEmoji("🥐", 10), ShopEmoji("🥑", 10), ShopEmoji("🥡", 10), ShopEmoji("🦫", 10),
        ShopEmoji("🧃", 10), ShopEmoji("🧢", 10), ShopEmoji("☂️", 11), ShopEmoji("♣️", 11),
        ShopEmoji("🌘", 11), ShopEmoji("🌨️", 11), ShopEmoji("🌯", 11), ShopEmoji("🌵", 11),
        ShopEmoji("🍆", 11), ShopEmoji("🍋", 11), ShopEmoji("🍘", 11), ShopEmoji("🍯", 11),
        ShopEmoji("🎐", 11), ShopEmoji("🎙️", 11), ShopEmoji("🎿", 11), ShopEmoji("🏖️", 11),
        ShopEmoji("🐝", 11), ShopEmoji("🐦", 11), ShopEmoji("🐪", 11), ShopEmoji("🐴", 11),
        ShopEmoji("👕", 11), ShopEmoji("👝", 11), ShopEmoji("📠", 11), ShopEmoji("🥏", 11),
        ShopEmoji("🥖", 11), ShopEmoji("🦀", 11), ShopEmoji("🦇", 11), ShopEmoji("🦕", 11),
        ShopEmoji("🧉", 11), ShopEmoji("🪖", 11), ShopEmoji("♟️", 12), ShopEmoji("⛑️", 12),
        ShopEmoji("🌊", 12), ShopEmoji("🌑", 12), ShopEmoji("🌬️", 12), ShopEmoji("🌾", 12),
        ShopEmoji("🍌", 12), ShopEmoji("🍙", 12), ShopEmoji("🍼", 12), ShopEmoji("🎀", 12),
        ShopEmoji("🎚️", 12), ShopEmoji("🎳", 12), ShopEmoji("🏜️", 12), ShopEmoji("🐎", 12),
        ShopEmoji("🐧", 12), ShopEmoji("🐫", 12), ShopEmoji("🐻", 12), ShopEmoji("👖", 12),
        ShopEmoji("🔋", 12), ShopEmoji("🥔", 12), ShopEmoji("🦖", 12), ShopEmoji("🦞", 12),
        ShopEmoji("🧊", 12), ShopEmoji("🪲", 12), ShopEmoji("🫓", 12), ShopEmoji("🫔", 12),
        ShopEmoji("🛍️", 12), ShopEmoji("🛷", 12), ShopEmoji("☄️", 13), ShopEmoji("🃏", 13),
        ShopEmoji("🌄", 13), ShopEmoji("🌒", 13), ShopEmoji("🍍", 13), ShopEmoji("🍚", 13),
        ShopEmoji("🎁", 13), ShopEmoji("🎒", 13), ShopEmoji("🎛️", 13), ShopEmoji("🏏", 13),
        ShopEmoji("🐒", 13), ShopEmoji("🐞", 13), ShopEmoji("🐨", 13), ShopEmoji("🐳", 13),
        ShopEmoji("💐", 13), ShopEmoji("📿", 13), ShopEmoji("🥌", 13), ShopEmoji("🥕", 13),
        ShopEmoji("🥙", 13), ShopEmoji("🥛", 13), ShopEmoji("🥢", 13), ShopEmoji("🥯", 13),
        ShopEmoji("🦅", 13), ShopEmoji("🦐", 13), ShopEmoji("🦓", 13), ShopEmoji("🦙", 13),
        ShopEmoji("🧣", 13), ShopEmoji("🪫", 13), ShopEmoji("☀️", 14), ShopEmoji("☕", 14),
        ShopEmoji("🀄", 14), ShopEmoji("🌃", 14), ShopEmoji("🌓", 14), ShopEmoji("🌱", 14),
        ShopEmoji("🌽", 14), ShopEmoji("🍛", 14), ShopEmoji("🍽️", 14), ShopEmoji("🎗️", 14),
        ShopEmoji("🎤", 14), ShopEmoji("🎯", 14), ShopEmoji("🏑", 14), ShopEmoji("🐋", 14),
        ShopEmoji("🐼", 14), ShopEmoji("💄", 14), ShopEmoji("🔌", 14), ShopEmoji("🥞", 14),
        ShopEmoji("🥭", 14), ShopEmoji("🦆", 14), ShopEmoji("🦌", 14), ShopEmoji("🦍", 14),
        ShopEmoji("🦑", 14), ShopEmoji("🦒", 14), ShopEmoji("🦗", 14), ShopEmoji("🧆", 14),
        ShopEmoji("🧤", 14), ShopEmoji("🩴", 14), ShopEmoji("☘️", 15), ShopEmoji("🌔", 15),
        ShopEmoji("🌤️", 15), ShopEmoji("🌶️", 15), ShopEmoji("🍎", 15), ShopEmoji("🍜", 15),
        ShopEmoji("🍴", 15), ShopEmoji("🎟️", 15), ShopEmoji("🎧", 15), ShopEmoji("🎴", 15),
        ShopEmoji("🏒", 15), ShopEmoji("🏙️", 15), ShopEmoji("🐘", 15), ShopEmoji("🐬", 15),
        ShopEmoji("👞", 15), ShopEmoji("💻", 15), ShopEmoji("🔇", 15), ShopEmoji("🥚", 15),
        ShopEmoji("🦢", 15), ShopEmoji("🦥", 15), ShopEmoji("🦧", 15), ShopEmoji("🦪", 15),
        ShopEmoji("🦬", 15), ShopEmoji("🧇", 15), ShopEmoji("🧥", 15), ShopEmoji("🪀", 15),
        ShopEmoji("🪳", 15), ShopEmoji("🫖", 15), ShopEmoji("⛅", 16), ShopEmoji("⭐", 16),
        ShopEmoji("🌆", 16), ShopEmoji("🍁", 16), ShopEmoji("🍏", 16), ShopEmoji("🍝", 16),
        ShopEmoji("🍦", 16), ShopEmoji("🍳", 16), ShopEmoji("🍵", 16), ShopEmoji("🎫", 16),
        ShopEmoji("🐕", 16), ShopEmoji("🐮", 16), ShopEmoji("👟", 16), ShopEmoji("📻", 16),
        ShopEmoji("🔈", 16), ShopEmoji("🕷️", 16), ShopEmoji("🖥️", 16), ShopEmoji("🖼️", 16),
        ShopEmoji("🥄", 16), ShopEmoji("🥍", 16), ShopEmoji("🦉", 16), ShopEmoji("🦣", 16),
        ShopEmoji("🦦", 16), ShopEmoji("🦭", 16), ShopEmoji("🧀", 16), ShopEmoji("🧦", 16),
        ShopEmoji("🪁", 16), ShopEmoji("🫑", 16), ShopEmoji("✨", 17), ShopEmoji("🌇", 17),
        ShopEmoji("🌥️", 17), ShopEmoji("🍂", 17), ShopEmoji("🍐", 17), ShopEmoji("🍖", 17),
        ShopEmoji("🍠", 17), ShopEmoji("🍧", 17), ShopEmoji("🍶", 17), ShopEmoji("🎱", 17),
        ShopEmoji("🏓", 17), ShopEmoji("🐂", 17), ShopEmoji("🐟", 17), ShopEmoji("🐩", 17),
        ShopEmoji("👗", 17), ShopEmoji("🔉", 17), ShopEmoji("🔪", 17), ShopEmoji("🕸️", 17),
        ShopEmoji("🖨️", 17), ShopEmoji("🥈", 17), ShopEmoji("🥒", 17), ShopEmoji("🥘", 17),
        ShopEmoji("🥾", 17), ShopEmoji("🦏", 17), ShopEmoji("🦤", 17), ShopEmoji("🦨", 17),
        ShopEmoji("🧵", 17), ShopEmoji("🪗", 17), ShopEmoji("⌨️", 18), ShopEmoji("☁️", 18),
        ShopEmoji("⚡", 18), ShopEmoji("🌉", 18), ShopEmoji("🌰", 18), ShopEmoji("🍑", 18),
        ShopEmoji("🍗", 18), ShopEmoji("🍢", 18), ShopEmoji("🍨", 18), ShopEmoji("🍲", 18),
        ShopEmoji("🍷", 18), ShopEmoji("🎮", 18), ShopEmoji("🎺", 18), ShopEmoji("🏸", 18),
        ShopEmoji("🐃", 18), ShopEmoji("🐠", 18), ShopEmoji("👘", 18), ShopEmoji("🔊", 18),
        ShopEmoji("🥉", 18), ShopEmoji("🥬", 18), ShopEmoji("🥿", 18), ShopEmoji("🦂", 18),
        ShopEmoji("🦘", 18), ShopEmoji("🦛", 18), ShopEmoji("🦮", 18), ShopEmoji("🪡", 18),
        ShopEmoji("🪶", 18), ShopEmoji("🫙", 18), ShopEmoji("⚽", 19), ShopEmoji("🌁", 19),
        ShopEmoji("🌏", 19), ShopEmoji("🌝", 19), ShopEmoji("🍒", 19), ShopEmoji("🍣", 19),
        ShopEmoji("🍩", 19), ShopEmoji("🍸", 19), ShopEmoji("🏺", 19), ShopEmoji("🐄", 19),
        ShopEmoji("🐡", 19), ShopEmoji("🐭", 19), ShopEmoji("🐺", 19), ShopEmoji("👠", 19),
        ShopEmoji("📢", 19), ShopEmoji("🔥", 19), ShopEmoji("🕹️", 19), ShopEmoji("🖱️", 19),
        ShopEmoji("🥊", 19), ShopEmoji("🥦", 19), ShopEmoji("🥩", 19), ShopEmoji("🥻", 19),
        ShopEmoji("🦚", 19), ShopEmoji("🦟", 19), ShopEmoji("🦡", 19), ShopEmoji("🧶", 19),
        ShopEmoji("🪕", 19), ShopEmoji("🫕", 19), ShopEmoji("⚾", 20), ShopEmoji("🌀", 20),
        ShopEmoji("🌍", 20), ShopEmoji("🌞", 20), ShopEmoji("🍓", 20), ShopEmoji("🍤", 20),
        ShopEmoji("🍪", 20), ShopEmoji("🍹", 20), ShopEmoji("🎈", 20), ShopEmoji("🎰", 20),
        ShopEmoji("🐁", 20), ShopEmoji("🐷", 20), ShopEmoji("🐾", 20), ShopEmoji("👡", 20),
        ShopEmoji("💥", 20), ShopEmoji("📣", 20), ShopEmoji("🖲️", 20), ShopEmoji("🥁", 20),
        ShopEmoji("🥋", 20), ShopEmoji("🥓", 20), ShopEmoji("🥣", 20), ShopEmoji("🦈", 20),
        ShopEmoji("🦊", 20), ShopEmoji("🦜", 20), ShopEmoji("🧄", 20), ShopEmoji("🩱", 20),
        ShopEmoji("🪢", 20), ShopEmoji("🪰", 20), ShopEmoji("🦋", 70), ShopEmoji("🥂", 75),
        ShopEmoji("🍾", 80), ShopEmoji("🏝️", 85), ShopEmoji("🧋", 88), ShopEmoji("🌅", 90),
        ShopEmoji("🚀", 95), ShopEmoji("🛸", 100), ShopEmoji("🧁", 105), ShopEmoji("🎭", 110),
        ShopEmoji("🎹", 115), ShopEmoji("🎻", 120), ShopEmoji("🎷", 125), ShopEmoji("🎸", 130),
        ShopEmoji("🎂", 135), ShopEmoji("🧩", 140), ShopEmoji("🔮", 145), ShopEmoji("💫", 150),
        ShopEmoji("🌟", 155), ShopEmoji("💖", 160), ShopEmoji("🗨️", 165), ShopEmoji("💘", 170),
        ShopEmoji("💝", 180), ShopEmoji("🪩", 190), ShopEmoji("🪅", 195), ShopEmoji("🧨", 200),
        ShopEmoji("🎇", 205), ShopEmoji("🎆", 210), ShopEmoji("🎖️", 215), ShopEmoji("🏅", 220),
        ShopEmoji("👽", 280), ShopEmoji("👾", 300), ShopEmoji("🤖", 320), ShopEmoji("👻", 340),
        ShopEmoji("💀", 360), ShopEmoji("☠️", 380), ShopEmoji("👸", 390), ShopEmoji("🎃", 400),
        ShopEmoji("🎄", 420), ShopEmoji("🎅", 440), ShopEmoji("🥷", 450), ShopEmoji("🤶", 460),
        ShopEmoji("🧞", 480), ShopEmoji("🧙", 500), ShopEmoji("🧚", 520), ShopEmoji("🧛", 540),
        ShopEmoji("🧜", 560), ShopEmoji("🧝", 580), ShopEmoji("🦸", 600), ShopEmoji("🦹", 620),
        ShopEmoji("🏆", 900), ShopEmoji("🥇", 1000), ShopEmoji("💍", 1100), ShopEmoji("🐉", 1200),
        ShopEmoji("❤️‍🔥", 1300), ShopEmoji("🌠", 1400), ShopEmoji("🕊️", 1500), ShopEmoji("🦩", 1600),
        ShopEmoji("😶‍🌫️", 1800), ShopEmoji("🗯️", 1980), ShopEmoji("💎", 2800), ShopEmoji("👑", 3500),
        ShopEmoji("🪐", 4200), ShopEmoji("🌌", 5000), ShopEmoji("🦄", 5500), ShopEmoji("⚜️", 11000)
    ).distinctBy { it.emoji }

    /** Tab-Labels — Inventar (Menü) und Itemshop müssen übereinstimmen. */
    val SHOP_TAB_LABELS: List<String> = listOf(
        "Sticker", "Hintergründe", "Begleiter", "Emojis", "Lootbox"
    )

    const val LOOTBOX_PRICE_COINS = 10
    const val LOOTBOX_TAP_COUNT = 6

    /** Nur Tiere — Küken ist Starter. Preise nach Seltenheit. */
    val PETS: List<ShopPet> = listOf(
        ShopPet("🐣", "Küken", 0),
        ShopPet("🐤", "Küken gelb", 12),
        ShopPet("🐥", "Küken frontal", 12),
        ShopPet("🐦", "Vogel", 14),
        ShopPet("🐔", "Huhn", 16),
        ShopPet("🐓", "Hahn", 16),
        ShopPet("🦆", "Ente", 16),
        ShopPet("🐸", "Frosch", 16),
        ShopPet("🐹", "Hamster", 18),
        ShopPet("🐭", "Maus", 18),
        ShopPet("🦋", "Schmetterling", 18),
        ShopPet("🐝", "Biene", 18),
        ShopPet("🐛", "Raupe", 16),
        ShopPet("🐞", "Marienkäfer", 16),
        ShopPet("🐜", "Ameise", 14),
        ShopPet("🐌", "Schnecke", 16),
        ShopPet("🐶", "Hund", 20),
        ShopPet("🐕", "Hund stehend", 20),
        ShopPet("🐩", "Pudel", 22),
        ShopPet("🦮", "Blindenhund", 24),
        ShopPet("🐕‍🦺", "Assistenzhund", 24),
        ShopPet("🐱", "Katze", 20),
        ShopPet("🐈", "Katze stehend", 20),
        ShopPet("🐈‍⬛", "Schwarze Katze", 26),
        ShopPet("🐰", "Hase", 22),
        ShopPet("🐇", "Kaninchen", 22),
        ShopPet("🐮", "Kuh", 20),
        ShopPet("🐂", "Stier", 22),
        ShopPet("🐃", "Wasserbüffel", 22),
        ShopPet("🐄", "Kuh stehend", 20),
        ShopPet("🐷", "Schwein", 20),
        ShopPet("🐖", "Schwein stehend", 20),
        ShopPet("🐗", "Wildschwein", 24),
        ShopPet("🐏", "Widder", 20),
        ShopPet("🐑", "Schaf", 20),
        ShopPet("🐐", "Ziege", 20),
        ShopPet("🦙", "Lama", 24),
        ShopPet("🦌", "Hirsch", 24),
        ShopPet("🐴", "Pferd", 22),
        ShopPet("🐎", "Pferd galopp", 24),
        ShopPet("🐧", "Pinguin", 22),
        ShopPet("🐢", "Schildkröte", 22),
        ShopPet("🦎", "Eidechse", 20),
        ShopPet("🐍", "Schlange", 24),
        ShopPet("🐊", "Krokodil", 28),
        ShopPet("🦉", "Eule", 24),
        ShopPet("🦇", "Fledermaus", 22),
        ShopPet("🐺", "Wolf", 28),
        ShopPet("🐻", "Bär", 24),
        ShopPet("🐻‍❄️", "Eisbär", 32),
        ShopPet("🦊", "Fuchs", 26),
        ShopPet("🐼", "Panda", 28),
        ShopPet("🐨", "Koala", 26),
        ShopPet("🐯", "Tiger", 30),
        ShopPet("🐅", "Tiger stehend", 32),
        ShopPet("🐆", "Leopard", 30),
        ShopPet("🦁", "Löwe", 32),
        ShopPet("🦓", "Zebra", 28),
        ShopPet("🦒", "Giraffe", 30),
        ShopPet("🦘", "Känguru", 28),
        ShopPet("🐘", "Elefant", 30),
        ShopPet("🦣", "Mammut", 36),
        ShopPet("🦏", "Nashorn", 30),
        ShopPet("🦛", "Nilpferd", 28),
        ShopPet("🐪", "Dromedar", 24),
        ShopPet("🐫", "Kamel", 24),
        ShopPet("🦬", "Bison", 28),
        ShopPet("🦍", "Gorilla", 34),
        ShopPet("🦧", "Orang-Utan", 32),
        ShopPet("🐵", "Affe", 22),
        ShopPet("🙈", "Nichts sehen", 20),
        ShopPet("🙉", "Nichts hören", 20),
        ShopPet("🙊", "Nichts sagen", 20),
        ShopPet("🦝", "Waschbär", 26),
        ShopPet("🦨", "Stinktier", 22),
        ShopPet("🦡", "Dachs", 22),
        ShopPet("🦫", "Biber", 24),
        ShopPet("🦦", "Otter", 26),
        ShopPet("🦥", "Faultier", 28),
        ShopPet("🐁", "Feldmaus", 16),
        ShopPet("🐀", "Ratte", 16),
        ShopPet("🐿️", "Streifenhörnchen", 20),
        ShopPet("🦔", "Igel", 24),
        ShopPet("🦅", "Adler", 28),
        ShopPet("🕊️", "Taube", 20),
        ShopPet("🦢", "Schwan", 26),
        ShopPet("🦩", "Flamingo", 28),
        ShopPet("🦚", "Pfau", 32),
        ShopPet("🦜", "Papagei", 28),
        ShopPet("🦤", "Dodo", 34),
        ShopPet("🦃", "Truthahn", 20),
        ShopPet("🐟", "Fisch", 16),
        ShopPet("🐠", "Tropenfisch", 18),
        ShopPet("🐡", "Kugelfisch", 20),
        ShopPet("🐬", "Delfin", 28),
        ShopPet("🐳", "Wal spritz", 30),
        ShopPet("🐋", "Wal", 30),
        ShopPet("🦈", "Hai", 32),
        ShopPet("🦭", "Robbe", 26),
        ShopPet("🐙", "Oktopus", 28),
        ShopPet("🦑", "Tintenfisch", 24),
        ShopPet("🦐", "Garnele", 16),
        ShopPet("🦞", "Hummer", 22),
        ShopPet("🦀", "Krabbe", 18),
        ShopPet("🦟", "Mücke", 12),
        ShopPet("🦗", "Grille", 14),
        ShopPet("🕷️", "Spinne", 18),
        ShopPet("🦂", "Skorpion", 22),
        ShopPet("🦖", "T-Rex", 38),
        ShopPet("🦕", "Sauropode", 36),
        ShopPet("🐉", "Drache", 42),
        ShopPet("🐲", "Drachenkopf", 40),
        ShopPet("🦄", "Einhorn", 44),
        ShopPet("🧙", "Hexe", 500),
        ShopPet("🐾", "Pfoten", 14)
    ).distinctBy { it.emoji }

    fun priceOf(emoji: String): Int =
        EMOJIS.firstOrNull { it.emoji == emoji }?.priceCoins ?: 10

    fun playfulPackTitle(pack: ShopPack): String = when {
        pack.id.contains("intro", ignoreCase = true) || pack.coins in 90..110 -> "Säckchen Glück"
        pack.id == "pack_50" || pack.coins <= 60 -> "Handvoll Coins"
        pack.id == "pack_150" || pack.coins in 120..180 -> "Beutel voll Coins"
        pack.id == "pack_400" || pack.coins in 300..500 -> "Schatztruhe"
        pack.id == "pack_900" || pack.coins in 700..1200 -> "Münzhaufen"
        pack.id == "pack_2000" || pack.coins in 1500..3000 -> "Goldschatz"
        pack.id == "pack_5000" || pack.coins >= 4000 -> "Schatzkammer"
        else -> pack.label.ifBlank { "${pack.coins} Coins" }
    }

    fun packImageRes(pack: ShopPack): Int = when {
        pack.id.contains("intro", ignoreCase = true) || pack.coins in 90..110 ->
            R.drawable.shop_coins_pouch
        pack.id == "pack_50" || pack.coins <= 60 -> R.drawable.shop_coins_handful
        pack.id == "pack_150" || pack.coins in 120..180 -> R.drawable.shop_coins_chest
        pack.id == "pack_400" || pack.coins in 300..500 -> R.drawable.shop_coins_crate
        pack.id == "pack_900" || pack.coins in 700..1200 -> R.drawable.shop_coins_hoard
        pack.id == "pack_2000" || pack.coins in 1500..3000 -> R.drawable.shop_coins_vault
        pack.id == "pack_5000" || pack.coins >= 4000 -> R.drawable.shop_coins_treasure
        else -> R.drawable.shop_coins_treasure
    }
}

data class ShopEmoji(
    val emoji: String,
    val priceCoins: Int,
    val compareAtPrice: Int? = null,
    val remainingMs: Long? = null,
    val searchText: String = "",
    /** Custom-Bild (img_*) für Sticker/Emoji — relativer oder absoluter URL. */
    val imageUrl: String? = null,
    /** Anzeigename (Admin/Composer) — nie raw img_-ID. */
    val label: String = ""
)

data class ThemeVisualConfig(
    val skyTop: String = "#7EB8D8",
    val skyBottom: String = "#B8D4E8",
    val groundTop: String = "#2F5D2E",
    val groundBottom: String = "#1E3D1E",
    val emojis: List<String> = listOf("✨"),
    val motion: String = "fall",
    val coverage: String = "full",
    val speed: Float = 1f,
    val density: Float = 0.7f,
    val size: Float = 1f
)

data class ShopTheme(
    val id: String,
    val label: String,
    val emoji: String,
    val priceCoins: Int,
    val compareAtPrice: Int? = null,
    val remainingMs: Long? = null,
    val searchText: String = "",
    val visualConfig: ThemeVisualConfig? = null
)

data class ShopPet(
    val emoji: String,
    val label: String,
    val priceCoins: Int,
    val compareAtPrice: Int? = null,
    val remainingMs: Long? = null,
    val searchText: String = "",
    /** Relativer oder absoluter URL für Custom-Begleiter-PNG (Alpha). */
    val imageUrl: String? = null
)

/**
 * Remote-Katalog vom Server (Angebote, Timer).
 * Wichtig: Remote **ergänzt** lokale Listen, ersetzt sie nicht —
 * sonst verschwinden Items aus dem Sortiment, wenn der Server kurz unvollständig ist.
 */
object LiveShopCatalog {
    @Volatile var remoteEmojis: List<ShopEmoji>? = null
    @Volatile var remoteStickers: List<ShopEmoji>? = null
    @Volatile var remoteThemes: List<ShopTheme>? = null
    @Volatile var remotePets: List<ShopPet>? = null

    fun emojis(): List<ShopEmoji> =
        mergeEmojiLists(ShopCatalog.EMOJIS, remoteEmojis)

    fun stickers(): List<ShopEmoji> =
        mergeEmojiLists(ShopCatalog.STICKERS, remoteStickers)

    fun themes(): List<ShopTheme> {
        val local = ShopCatalog.THEMES.filter { it.priceCoins > 0 || it.id == "meadow" }
        val remote = remoteThemes
        if (remote.isNullOrEmpty()) return local.sortedBy { it.priceCoins }
        val byId = LinkedHashMap<String, ShopTheme>()
        local.forEach { byId[it.id] = it }
        remote.forEach { byId[it.id] = it } // Remote gewinnt bei Preis/Angebot
        return byId.values.sortedBy { it.priceCoins }
    }

    fun pets(): List<ShopPet> {
        val remote = remotePets
        if (remote.isNullOrEmpty()) return ShopCatalog.PETS.sortedBy { it.priceCoins }
        val byId = LinkedHashMap<String, ShopPet>()
        ShopCatalog.PETS.forEach { byId[it.emoji] = it }
        remote.forEach { byId[it.emoji] = it }
        return byId.values.sortedBy { it.priceCoins }
    }

    private fun mergeEmojiLists(
        local: List<ShopEmoji>,
        remote: List<ShopEmoji>?
    ): List<ShopEmoji> {
        if (remote.isNullOrEmpty()) return local.sortedBy { it.priceCoins }
        val byId = LinkedHashMap<String, ShopEmoji>()
        local.forEach { byId[it.emoji] = it }
        remote.forEach { byId[it.emoji] = it }
        return byId.values.sortedBy { it.priceCoins }
    }

    fun matchesQuery(query: String, emoji: String, label: String = "", searchText: String = ""): Boolean {
        val tokens = normalizeSearch(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        val catalogBits = buildString {
            append(EmojiSearchKeywords.forEmoji(emoji))
            append(' ')
            remoteEmojis?.firstOrNull { it.emoji == emoji }?.searchText?.let { append(it); append(' ') }
            remoteStickers?.firstOrNull { it.emoji == emoji }?.searchText?.let { append(it); append(' ') }
            remotePets?.firstOrNull { it.emoji == emoji }?.let {
                append(it.label); append(' '); append(it.searchText); append(' ')
            }
            remoteThemes?.firstOrNull { it.id == emoji || it.emoji == emoji }?.let {
                append(it.id); append(' '); append(it.label); append(' '); append(it.searchText); append(' ')
            }
            ShopCatalog.PETS.firstOrNull { it.emoji == emoji }?.let {
                append(it.label); append(' '); append(it.searchText); append(' ')
            }
            ShopCatalog.THEMES.firstOrNull { it.id == emoji || it.emoji == emoji }?.let {
                append(it.id); append(' '); append(it.label); append(' ')
            }
            ShopCatalog.EMOJIS.firstOrNull { it.emoji == emoji }?.searchText?.let { append(it); append(' ') }
            ShopCatalog.STICKERS.firstOrNull { it.emoji == emoji }?.searchText?.let { append(it); append(' ') }
        }
        val hayWords = normalizeSearch("$emoji $label $searchText $catalogBits")
            .split(' ')
            .filter { it.isNotEmpty() }
        return tokens.all { token ->
            emoji.contains(token) ||
                hayWords.any { w -> w == token || w.startsWith(token) }
        }
    }

    private fun normalizeSearch(s: String): String =
        s.lowercase()
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
