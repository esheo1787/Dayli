package com.silverwest.dayli.ddaywidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmojiCatalogTest {

    private val sample = """
        0	😀	0	활짝 웃는 얼굴|얼굴|웃음
        2	👍	1	올린 엄지|따봉|좋아요|최고
        4	🎂	0	생일 케이크|생일케이크
        2	🕵️	1	탐정|형사
        broken line
        99	❓	0	범위 밖 카테고리
    """.trimIndent()

    @Test
    fun parse_groupsByCategoryAndSkipsInvalidLines() {
        val cats = EmojiCatalog.parse(sample)
        assertEquals(listOf("표정", "손·몸", "음식"), cats.map { it.name })
        assertEquals(listOf("👍", "🕵️"), cats[1].entries.map { it.emoji })
        assertTrue(cats[1].entries[0].skinnable)
    }

    @Test
    fun parse_dropsUnrenderableEmoji() {
        val cats = EmojiCatalog.parse(sample) { it != "🎂" }
        assertTrue(cats.none { c -> c.entries.any { it.emoji == "🎂" } })
    }

    @Test
    fun search_matchesKoreanKeywordsIgnoringSpaces() {
        val cats = EmojiCatalog.parse(sample)
        assertEquals(listOf("🎂"), EmojiCatalog.search(cats, "케이크").map { it.emoji })
        assertEquals(listOf("🎂"), EmojiCatalog.search(cats, "생일 케 이크").map { it.emoji })
        assertEquals(listOf("👍"), EmojiCatalog.search(cats, "따봉").map { it.emoji })
        assertTrue(EmojiCatalog.search(cats, "  ").isEmpty())
    }

    @Test
    fun applySkinTone_insertsAfterFirstCodePointAndDropsFe0f() {
        val tone = "🏻"
        assertEquals("👍$tone", EmojiCatalog.applySkinTone("👍", tone))
        // 🕵️ (U+1F575 U+FE0F) → U+1F575 U+1F3FB
        assertEquals("🕵$tone", EmojiCatalog.applySkinTone("🕵️", tone))
        // 🧑‍⚕️ → 🧑🏻‍⚕️
        assertEquals("🧑$tone‍⚕️", EmojiCatalog.applySkinTone("🧑‍⚕️", tone))
        assertEquals("👍", EmojiCatalog.applySkinTone("👍", ""))
    }

    private val full by lazy {
        EmojiCatalog.parse(File("src/main/assets/emoji_ko.tsv").readText(Charsets.UTF_8))
    }

    @Test
    fun suggest_scheduleWords() {
        assertEquals("🦷", EmojiCatalog.suggest(full, "치과 예약").first())
        assertEquals("🎂", EmojiCatalog.suggest(full, "엄마 생일").first())
        assertEquals("📝", EmojiCatalog.suggest(full, "회계정보시스템 5주차 퀴즈").first())
        assertEquals("✈️", EmojiCatalog.suggest(full, "오사카 여행").first())
    }

    @Test
    fun suggest_shortHintInsideLongerWord_ignored() {
        // "약속"의 "약", "수술"의 "술"로 💊/🍻가 뜨면 안 됨
        assertEquals("🤝", EmojiCatalog.suggest(full, "친구 약속").first())
        assertTrue("💊" !in EmojiCatalog.suggest(full, "친구 약속"))
        assertTrue("🍻" !in EmojiCatalog.suggest(full, "무릎 수술"))
    }

    @Test
    fun search_includesScheduleHints() {
        val results = EmojiCatalog.search(full, "생일").map { it.emoji }
        assertEquals("🎂", results.first())
        assertTrue(results.containsAll(listOf("🎉", "🎁")))
        assertEquals(results.size, results.distinct().size)
    }

    @Test
    fun suggest_singleCharHintsOnlyAtWordStart() {
        assertTrue("💊" !in EmojiCatalog.suggest(full, "전세 계약 만료"))
        assertTrue("🍻" !in EmojiCatalog.suggest(full, "미술 학원"))
        assertTrue("📖" !in EmojiCatalog.suggest(full, "책임자 회의"))
        assertEquals("💼", EmojiCatalog.suggest(full, "책임자 회의").first())
        assertTrue("📦" !in EmojiCatalog.suggest(full, "이사회 참석"))
        // 단어 첫머리면 인정
        assertEquals("🍻", EmojiCatalog.suggest(full, "술자리").first())
        assertEquals("💊", EmojiCatalog.suggest(full, "약 먹기").first())
    }

    @Test
    fun suggest_fallsBackToCldrKeywords() {
        // 힌트 목록에 없는 단어도 CLDR 키워드로 추천
        assertTrue(EmojiCatalog.suggest(full, "피자 파티").contains("🍕"))
        assertTrue(EmojiCatalog.suggest(full, "").isEmpty())
        assertTrue(EmojiCatalog.suggest(full, "피자 파티").size <= 6)
    }

    @Test
    fun recommended_allExistInCatalog() {
        val all = full.flatMap { c -> c.entries.map { it.emoji } }.toSet()
        val missing = EmojiCatalog.RECOMMENDED.filter { it !in all }
        assertTrue("카탈로그에 없는 추천 이모지: $missing", missing.isEmpty())
    }

    @Test
    fun bundledAsset_parsesToFullCatalog() {
        val asset = File("src/main/assets/emoji_ko.tsv")
        val cats = EmojiCatalog.parse(asset.readText(Charsets.UTF_8))
        assertEquals(10, cats.size)
        assertTrue(cats.sumOf { it.entries.size } > 1800)
        assertTrue(EmojiCatalog.search(cats, "치과").any { it.emoji == "🦷" })
    }
}
