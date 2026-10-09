package com.silverwest.dayli.ddaywidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FlatEmojiTest {

    @Test
    fun fileName_dropsFe0fAndJoinsCodePoints() {
        assertEquals("1f44d.webp", FlatEmoji.fileName("👍"))
        assertEquals("2708.webp", FlatEmoji.fileName("✈️"))
        assertEquals("31_20e3.webp", FlatEmoji.fileName("1️⃣"))
        assertEquals("a9.webp", FlatEmoji.fileName("©️"))
        assertEquals("1f44d_1f3fd.webp", FlatEmoji.fileName("👍🏽"))
        assertEquals("1f9d1_200d_2695.webp", FlatEmoji.fileName("🧑‍⚕️"))
    }

    @Test
    fun bundledImages_coverRecommendedAndSuggestedEmoji() {
        val files = File("src/main/assets/emoji_flat").list()!!.toSet()
        val missing = EmojiCatalog.RECOMMENDED.filter { FlatEmoji.fileName(it) !in files }
        assertTrue("플랫 이미지 없는 추천 이모지: $missing", missing.isEmpty())
        // 기본값 이모지와 AI가 자주 쓰는 이모지
        listOf("📌", "✅", "📋", "📁", "🏫", "📝", "📄").forEach {
            assertTrue("$it 플랫 이미지 없음", FlatEmoji.fileName(it) in files)
        }
    }

    @Test
    fun bundledImages_skinToneVariantsMatchPickerRule() {
        // 피커의 applySkinTone 결과 파일명이 실제 에셋에 존재해야 함
        val files = File("src/main/assets/emoji_flat").list()!!.toSet()
        val tone = "🏽"
        listOf("👍", "🧑‍⚕️", "🏃", "✋").forEach {
            val toned = EmojiCatalog.applySkinTone(it, tone)
            assertTrue("$toned 플랫 이미지 없음", FlatEmoji.fileName(toned) in files)
        }
    }
}
