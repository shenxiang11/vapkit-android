package com.vapkit.demo

data class GiftItem(
    val id: String,
    val name: String,
    val price: Int,
    val assetName: String?,
    val emoji: String,
) {
    val isReady: Boolean get() = assetName != null
}

object GiftCatalog {
    val all: List<GiftItem> = listOf(
        GiftItem("space_rabbit", "星际兔", 199, "gifts/user_246106.mp4", "🐰"),
        GiftItem("moon_jade_rabbit", "月下玉兔", 299, "gifts/user_245341.mp4", "🌙"),
        GiftItem("spark_fist", "热血一拳", 99, "gifts/user_2390.mp4", "✊"),
        GiftItem("glow_cheer", "星光应援", 520, "gifts/user_3123.mp4", "🌟"),
        GiftItem("love_petals", "告白花语", 199, "gifts/user_3179.mp4", "💗"),
        GiftItem("coming_rose", "星愿玫瑰", 99, null, "🌹"),
        GiftItem("coming_car", "梦幻跑车", 520, null, "🚗"),
        GiftItem("coming_castle", "水晶城堡", 1314, null, "🏰"),
        GiftItem("coming_firework", "星河烟花", 299, null, "🎆"),
        GiftItem("coming_crown", "加冕皇冠", 888, null, "👑"),
        GiftItem("coming_rocket", "冲天火箭", 666, null, "🚀"),
        GiftItem("coming_yacht", "海上游艇", 1888, null, "🛥️"),
    )

    val defaultSelection: GiftItem
        get() = all.firstOrNull { it.isReady } ?: all.first()
}
