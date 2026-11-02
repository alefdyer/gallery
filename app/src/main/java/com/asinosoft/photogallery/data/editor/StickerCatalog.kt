package com.asinosoft.photogallery.data.editor

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class StickerItem(
    val id: String,
    val emoji: String,
)

data class StickerCategory(
    val id: String,
    val titleResName: String,
    val defaultTitle: String,
    val iconEmoji: String,
    val stickers: List<StickerItem>,
)

@Singleton
class StickerCatalog @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun categories(): List<StickerCategory> {
        val order = categoryOrder()
        val byId = DEFAULT_CATEGORIES.associateBy { it.id }
        val ordered = order.mapNotNull { byId[it] }
        val missing = DEFAULT_CATEGORIES.filter { it.id !in order.toSet() }
        return ordered + missing
    }

    fun categoryOrder(): List<String> {
        val raw = prefs.getString(KEY_ORDER, null)
        if (raw.isNullOrBlank()) return DEFAULT_CATEGORIES.map { it.id }
        return raw.split(',').filter { it.isNotBlank() }
    }

    fun setCategoryOrder(ids: List<String>) {
        prefs.edit { putString(KEY_ORDER, ids.joinToString(",")) }
    }

    companion object {
        private const val PREFS = "editor_stickers"
        private const val KEY_ORDER = "category_order"

        val DEFAULT_CATEGORIES: List<StickerCategory> = listOf(
            StickerCategory(
                id = "special",
                titleResName = "sticker_cat_special",
                defaultTitle = "Special",
                iconEmoji = "✨",
                stickers = listOf(
                    StickerItem("sp1", "✨"),
                    StickerItem("sp2", "🌟"),
                    StickerItem("sp3", "💫"),
                    StickerItem("sp4", "⭐"),
                    StickerItem("sp5", "🔥"),
                    StickerItem("sp6", "💥"),
                ),
            ),
            StickerCategory(
                id = "dates",
                titleResName = "sticker_cat_dates",
                defaultTitle = "Dates & shapes",
                iconEmoji = "📅",
                stickers = listOf(
                    StickerItem("d1", "📅"),
                    StickerItem("d2", "🗓️"),
                    StickerItem("d3", "⬜"),
                    StickerItem("d4", "⬛"),
                    StickerItem("d5", "⭕"),
                    StickerItem("d6", "🔴"),
                    StickerItem("d7", "➡️"),
                    StickerItem("d8", "✅"),
                ),
            ),
            StickerCategory(
                id = "emoji",
                titleResName = "sticker_cat_emoji",
                defaultTitle = "Emoji",
                iconEmoji = "😀",
                stickers = listOf(
                    StickerItem("e1", "😀"),
                    StickerItem("e2", "😂"),
                    StickerItem("e3", "😍"),
                    StickerItem("e4", "🥳"),
                    StickerItem("e5", "😎"),
                    StickerItem("e6", "🤔"),
                    StickerItem("e7", "😭"),
                    StickerItem("e8", "👍"),
                ),
            ),
            StickerCategory(
                id = "hearts",
                titleResName = "sticker_cat_hearts",
                defaultTitle = "Hearts & stars",
                iconEmoji = "❤️",
                stickers = listOf(
                    StickerItem("h1", "❤️"),
                    StickerItem("h2", "💕"),
                    StickerItem("h3", "💗"),
                    StickerItem("h4", "💖"),
                    StickerItem("h5", "⭐"),
                    StickerItem("h6", "🌟"),
                    StickerItem("h7", "💜"),
                    StickerItem("h8", "🖤"),
                ),
            ),
            StickerCategory(
                id = "words",
                titleResName = "sticker_cat_words",
                defaultTitle = "Words",
                iconEmoji = "💬",
                stickers = listOf(
                    StickerItem("w1", "Hi"),
                    StickerItem("w2", "Love"),
                    StickerItem("w3", "OK"),
                    StickerItem("w4", "Wow"),
                    StickerItem("w5", "Yes"),
                    StickerItem("w6", "Cool"),
                ),
            ),
        )
    }
}
