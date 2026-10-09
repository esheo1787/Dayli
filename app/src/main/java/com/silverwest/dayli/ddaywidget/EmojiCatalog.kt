package com.silverwest.dayli.ddaywidget

import android.content.Context
import android.graphics.Paint
import androidx.core.graphics.PaintCompat
import androidx.emoji2.text.EmojiCompat

/**
 * 유니코드 표준 이모지 전체 목록 (assets/emoji_ko.tsv, Unicode 15.1 + CLDR 한국어 이름/키워드)
 * 형식: 카테고리 인덱스 \t 이모지 \t 피부색 지원(0/1) \t 한국어 키워드(| 구분)
 */
object EmojiCatalog {

    data class Entry(val emoji: String, val skinnable: Boolean, val keywords: List<String>)
    data class Category(val icon: String, val name: String, val entries: List<Entry>)

    // TSV 카테고리 인덱스 순서와 동일해야 함
    private val CATEGORY_META = listOf(
        "😀" to "표정", "🧑" to "사람", "👋" to "손·몸", "🐶" to "동물", "🍎" to "음식",
        "⚽" to "활동", "🚗" to "여행", "💡" to "사물", "❤️" to "기호", "🏁" to "깃발"
    )

    @Volatile private var cached: List<Category>? = null

    /** 메인 스레드 밖에서 호출 (asset 읽기 + 글리프 검사) */
    fun load(context: Context): List<Category> {
        cached?.let { return it }
        val text = try {
            context.assets.open("emoji_ko.tsv").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.e("EmojiCatalog", "이모지 목록 로드 실패", e)
            return emptyList()
        }
        val (canRender, final) = renderChecker()
        // 플랫 이모지 파일 목록도 여기(IO 스레드)서 미리 읽어둠 → 화면에서 첫 표시 때 메인 스레드 지연 방지
        FlatEmoji.has(context, "😀")
        val result = parse(text, canRender)
        // EmojiCompat이 아직 로딩 중일 때 만든 목록은 임시 (로딩 끝나면 다음 호출에서 다시 만듦)
        if (final) cached = result
        return result
    }

    internal fun parse(tsv: String, canRender: (String) -> Boolean = { true }): List<Category> {
        val buckets = List(CATEGORY_META.size) { mutableListOf<Entry>() }
        tsv.lineSequence().forEach { line ->
            val cols = line.split('\t')
            if (cols.size < 3) return@forEach
            val index = cols[0].toIntOrNull()?.takeIf { it in buckets.indices } ?: return@forEach
            val emoji = cols[1]
            if (emoji.isEmpty() || !canRender(emoji)) return@forEach
            val keywords = cols.getOrNull(3).orEmpty().split('|').filter { it.isNotBlank() }
            buckets[index].add(Entry(emoji, cols[2] == "1", keywords))
        }
        return CATEGORY_META.mapIndexed { i, (icon, name) -> Category(icon, name, buckets[i]) }
            .filter { it.entries.isNotEmpty() }
    }

    /**
     * EmojiCompat(최신 이모지 폰트)이 로드됐으면 전부 표시, 아니면 기기 폰트에 글리프가 있는 것만 표시
     * → 구형 기기에서 □(두부) 이모지가 목록에 섞이지 않게
     */
    /** @return (렌더링 가능 판정 함수, 결과를 캐시해도 되는지 — EmojiCompat 로딩 중이면 false) */
    private fun renderChecker(): Pair<(String) -> Boolean, Boolean> {
        val state = try {
            EmojiCompat.get().loadState
        } catch (_: IllegalStateException) { EmojiCompat.LOAD_STATE_FAILED }  // EmojiCompat 미사용 환경
        if (state == EmojiCompat.LOAD_STATE_SUCCEEDED) return Pair({ _: String -> true }, true)
        val paint = Paint()
        val final = state != EmojiCompat.LOAD_STATE_LOADING && state != EmojiCompat.LOAD_STATE_DEFAULT
        return Pair({ e: String -> PaintCompat.hasGlyph(paint, e) }, final)
    }

    /** 검색: 공백 무시, 이모지 자체 또는 한국어 키워드 부분 일치 */
    fun search(categories: List<Category>, query: String): List<Entry> {
        val q = query.replace(" ", "").lowercase()
        if (q.isEmpty()) return emptyList()
        val all = categories.asSequence().flatMap { it.entries.asSequence() }
        val byEmoji = all.associateBy { it.emoji }
        // 일정 단어 힌트 먼저 ("생일" → 🎂🎉🎁) — CLDR 키워드만으로는 결과가 1~2개뿐인 경우가 많음
        val hinted = SCHEDULE_HINTS
            .filter { (word, _) -> word.contains(q) || (word.length >= 2 && q.contains(word)) }
            .flatMap { it.second }
            .mapNotNull { byEmoji[it] }
        val keywordHits = all.filter { entry ->
            entry.emoji == query.trim() ||
                entry.keywords.any { it.replace(" ", "").lowercase().contains(q) }
        }
        return (hinted + keywordHits).distinctBy { it.emoji }
    }

    /**
     * 일정에 자주 쓰는 단어 → 이모지 (CLDR 키워드보다 우선). 앞에 있을수록 우선순위 높음
     * GeminiParser 프롬프트의 이모지 가이드와 맞춤
     */
    private val SCHEDULE_HINTS: List<Pair<String, List<String>>> = listOf(
        "생일" to listOf("🎂", "🎉", "🎁"), "기념일" to listOf("💕", "🥂", "💐"), "데이트" to listOf("💕", "🌹"),
        "결혼" to listOf("💍", "💒"), "돌잔치" to listOf("👶", "🎂"), "100일" to listOf("💯", "💕"),
        "시험" to listOf("📝", "📚", "✏️"), "퀴즈" to listOf("📝", "✏️"), "과제" to listOf("📄", "✏️"),
        "레포트" to listOf("📄", "💻"), "중간고사" to listOf("📝", "📚"), "기말고사" to listOf("📝", "📚"),
        "수능" to listOf("📝", "🍀"), "토익" to listOf("📝", "📖"), "자격증" to listOf("📜", "📝"),
        "공부" to listOf("📚", "✏️"), "수업" to listOf("🏫", "📚"), "개강" to listOf("🏫", "🎒"),
        "졸업" to listOf("🎓", "💐"), "입학" to listOf("🏫", "🎒"), "수강신청" to listOf("🖥️", "⏰"),
        "치과" to listOf("🦷", "🏥"), "병원" to listOf("🏥", "🩺"), "진료" to listOf("🩺", "🏥"),
        "검진" to listOf("🩺", "🏥"), "예방접종" to listOf("💉", "🏥"), "주사" to listOf("💉"),
        "수술" to listOf("🏥", "🩺"), "예약" to listOf("📅", "⏰"), "약국" to listOf("💊"), "약" to listOf("💊"), "미용실" to listOf("💇", "✂️"), "헤어" to listOf("💇"), "네일" to listOf("💅"),
        "여행" to listOf("✈️", "🧳", "🏝️"), "비행기" to listOf("✈️"), "캠핑" to listOf("🏕️", "⛺"),
        "등산" to listOf("⛰️", "🥾"), "바다" to listOf("🏖️", "🌊"), "수영" to listOf("🏊", "🏖️"),
        "영화" to listOf("🎬", "🍿"), "콘서트" to listOf("🎤", "🎶"), "공연" to listOf("🎭", "🎤"),
        "전시" to listOf("🖼️", "🎨"), "운동" to listOf("💪", "🏋️"), "헬스" to listOf("🏋️", "💪"),
        "러닝" to listOf("🏃", "👟"), "마라톤" to listOf("🏃", "🏅"), "축구" to listOf("⚽"), "야구" to listOf("⚾"),
        "요가" to listOf("🧘"), "필라테스" to listOf("🧘"), "회의" to listOf("💼", "📊"), "미팅" to listOf("💼", "🤝"),
        "면접" to listOf("👔", "💼"), "출장" to listOf("🧳", "💼"), "마감" to listOf("⏰", "📄"),
        "제출" to listOf("⏰", "📄"), "발표" to listOf("📢", "📊"), "월급" to listOf("💰", "💵"),
        "급여" to listOf("💰"), "카드" to listOf("💳"), "납부" to listOf("💳", "🧾"), "공과금" to listOf("🧾"),
        "세금" to listOf("🏛️", "🧾"), "적금" to listOf("🏦", "💰"), "보험" to listOf("🛡️"),
        "이사" to listOf("📦", "🏠"), "택배" to listOf("🚚", "📦"), "청소" to listOf("🧹"), "빨래" to listOf("🧺"),
        "장보기" to listOf("🛒", "🛍️"), "쇼핑" to listOf("🛍️", "🛒"), "요리" to listOf("🍳"),
        "자동차" to listOf("🚗"), "정비" to listOf("🔧", "🚗"), "주유" to listOf("⛽"),
        "강아지" to listOf("🐶"), "고양이" to listOf("🐱"), "산책" to listOf("🚶", "🐕"), "식물" to listOf("🪴"),
        "약속" to listOf("🤝", "☕"), "모임" to listOf("🤝", "🍻"), "회식" to listOf("🍻", "🍖"),
        "술" to listOf("🍻", "🍺"), "맛집" to listOf("🍽️"), "점심" to listOf("🍱"), "저녁" to listOf("🍽️"),
        "카페" to listOf("☕"), "독서" to listOf("📖"), "책" to listOf("📖"), "예매" to listOf("🎫"),
        "티켓" to listOf("🎫"), "구독" to listOf("🔄", "💳"), "휴가" to listOf("🌴", "🏖️"), "연차" to listOf("🌴"),
        "명절" to listOf("🌕"), "추석" to listOf("🌕", "🍡"), "설날" to listOf("🧧", "🥟"),
        "크리스마스" to listOf("🎄", "🎅"), "새해" to listOf("🎆", "🎉"), "할로윈" to listOf("🎃"),
        "엄마" to listOf("👩"), "아빠" to listOf("👨"), "가족" to listOf("👨‍👩‍👧"), "아기" to listOf("👶")
    )

    /** 힌트 단어별 오탐 단어: 제목에 이 단어가 있으면 해당 힌트를 쓰지 않음 */
    private val HINT_FALSE_POSITIVES: Map<String, List<String>> = mapOf(
        "책" to listOf("책임", "책상"),
        "약" to listOf("약간", "약점"),
        "술" to listOf("술래"),
        "이사" to listOf("이사회", "이사장", "이사님"),
        "카드" to listOf("카드뉴스"),
        "주사" to listOf("주사위")
    )

    /** 피커 '추천' 탭: 일정·기념일에 자주 쓰는 이모지 */
    val RECOMMENDED: List<String> = listOf(
        "📌", "⭐", "❤️", "🎂", "🎉", "🎁", "💕", "💍", "📝", "📚",
        "✏️", "📄", "🏫", "🎓", "💼", "📊", "⏰", "📢", "🏥", "🦷",
        "💊", "💉", "💇", "✈️", "🧳", "🏖️", "🏕️", "🎬", "🎤", "🎫",
        "💪", "🏃", "⚽", "🧘", "💰", "💳", "🧾", "🏦", "📦", "🚚",
        "🧹", "🛒", "🍳", "🍽️", "☕", "🍻", "🤝", "🚗", "🐶", "🐱",
        "🪴", "📖", "🌴", "🎄", "🌕", "🎆", "🔥", "✅", "🍀", "💯"
    )

    /**
     * 제목에 어울리는 이모지 추천 (오프라인, AI 미사용)
     * 1) 일정용 힌트 단어 2) CLDR 한국어 키워드 일치 순
     */
    fun suggest(categories: List<Category>, title: String, limit: Int = 6): List<String> {
        val compact = title.replace(Regex("\\s+"), "").lowercase()
        if (compact.isEmpty()) return emptyList()
        val tokens = title.lowercase().split(Regex("[\\s,./()\\[\\]!?~·-]+")).filter { it.isNotEmpty() }

        val scores = LinkedHashMap<String, Int>()
        fun add(emoji: String, score: Int) {
            if ((scores[emoji] ?: Int.MIN_VALUE) < score) scores[emoji] = score
        }

        // 한 글자 힌트("약","술","책")는 단어 첫머리에서만 인정 ("계약"·"미술"·"정책" 오탐 방지)
        // 오탐이 잦은 단어가 들어 있으면 해당 힌트 제외 ("책임자"의 책, "이사회"의 이사)
        val matchedHints = SCHEDULE_HINTS.withIndex().filter { (_, hint) ->
            val word = hint.first
            val hit = if (word.length == 1) tokens.any { it.startsWith(word) } else compact.contains(word)
            hit && HINT_FALSE_POSITIVES[word]?.none { compact.contains(it) } != false
        }
        matchedHints.forEach { (order, hint) ->
            val (word, emojis) = hint
            val subsumed = matchedHints.any { other ->
                other.value.first.length > word.length && other.value.first.contains(word)
            }
            if (!subsumed) emojis.forEachIndexed { i, e -> add(e, 1000 - order * 3 - i) }
        }

        val available = HashSet<String>()
        categories.forEach { cat ->
            cat.entries.forEach { entry ->
                available.add(entry.emoji)
                var best = 0
                entry.keywords.forEachIndexed { ki, raw ->
                    val k = raw.replace(" ", "").lowercase()
                    val s = when {
                        tokens.any { it == k } -> 30
                        k.length >= 2 && compact.contains(k) -> 20
                        tokens.any { it.length >= 2 && k.startsWith(it) } -> 10
                        else -> 0
                    }
                    // 첫 키워드(공식 이름) 일치 가산점
                    if (s > 0) best = maxOf(best, s + if (ki == 0) 5 else 0)
                }
                if (best > 0) add(entry.emoji, best)
            }
        }

        return scores.entries
            .filter { categories.isEmpty() || it.key in available }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
    }

    /** 피부색 적용: 첫 코드포인트 뒤에 삽입하고 바로 뒤 FE0F는 제거 (유니코드 RGI 시퀀스 규칙) */
    fun applySkinTone(emoji: String, modifier: String): String {
        if (modifier.isEmpty() || emoji.isEmpty()) return emoji
        val firstLen = Character.charCount(emoji.codePointAt(0))
        var rest = emoji.substring(firstLen)
        if (rest.startsWith("️")) rest = rest.substring(1)
        return emoji.substring(0, firstLen) + modifier + rest
    }
}
