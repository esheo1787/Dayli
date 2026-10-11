package com.silverwest.dayli.ddaywidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 이번 변경 기능을 실제 기기(에뮬레이터) 위에서 확인하는 UI/통합 테스트
 * - 이모지 피커: 추천 탭, 한글 검색, 최근 사용 저장, 플랫 이미지 렌더링
 * - 추가 시트: 제목 입력 → 이모지 자동 선택 → 저장 값
 * - AI 입력용 이미지 로드: 일반 이미지 1장, 긴 캡처는 여러 장으로 분할
 */
@RunWith(AndroidJUnit4::class)
class NewFeaturesUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("dday_widget_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    // ===== 에셋이 APK에 실제로 들어갔는지 =====

    @Test
    fun emojiCatalog_loadsFullListOnDevice() {
        val cats = EmojiCatalog.load(context)
        assertEquals(10, cats.size)
        assertTrue("기기에서 표시 가능한 이모지 수", cats.sumOf { it.entries.size } > 1500)
    }

    @Test
    fun flatEmoji_bitmapsDecodeOnDevice() {
        listOf("🎂", "📝", "👍🏽", "🧑‍⚕️", "1️⃣").forEach {
            val bmp = FlatEmoji.bitmap(context, it)
            assertNotNull("$it 플랫 이미지", bmp)
            assertEquals(72, bmp!!.width)
        }
        // 국기는 플랫 이미지 없음 → null (기본 이모지로 대체)
        assertEquals(null, FlatEmoji.bitmap(context, "🇰🇷"))
    }

    // ===== 이모지 피커 =====

    @Test
    fun picker_searchKorean_selectAndSaveToRecents() {
        var picked: String? = null
        compose.setContent {
            EmojiPickerDialog(
                currentEmoji = "📌",
                categoryColor = ComposeColor(0xFFA8C5DA),
                onEmojiSelected = { picked = it },
                onDismiss = {}
            )
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("추천").fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(hasSetTextAction()).performTextInput("케이크")
        compose.waitUntil(3_000) { compose.onAllNodesWithText("🎂").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("🎂")[0].performClick()
        compose.onNodeWithText("선택").performClick()

        assertEquals("🎂", picked)
        assertEquals("🎂", DdaySettings.getRecentEmojis(context).first())
    }

    @Test
    fun picker_flatStyle_rendersImagesInsteadOfText() {
        compose.setContent {
            CompositionLocalProvider(LocalFlatEmoji provides true) {
                EmojiPickerDialog(
                    currentEmoji = "📌",
                    categoryColor = ComposeColor(0xFFA8C5DA),
                    onEmojiSelected = {},
                    onDismiss = {}
                )
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("추천").fetchSemanticsNodes().isNotEmpty() }
        // 플랫 모드: 이모지가 이미지(contentDescription=이모지)로 그려지고 텍스트 노드는 없음
        compose.onAllNodesWithContentDescription("🎂").fetchSemanticsNodes().let {
            assertTrue("추천 탭의 🎂가 이미지로 표시", it.isNotEmpty())
        }
        compose.onAllNodesWithText("🎂").assertCountEquals(0)
    }

    // ===== 추가 시트: 제목 기반 이모지 자동 선택 =====

    @Test
    fun addSheet_titleAutoPicksEmoji_andSavesIt() {
        var savedTitle: String? = null
        var savedEmoji: String? = null
        compose.setContent {
            AddEditBottomSheet(
                isVisible = true,
                itemType = ItemType.DDAY,
                onDismiss = {},
                onSave = { title, _, _, emoji, _, _, _, _, _, _, _, _, _, _, _ ->
                    savedTitle = title
                    savedEmoji = emoji
                }
            )
        }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("제목")).performTextInput("치과 예약")
        // 추천 칩 표시 + 자동 선택
        compose.waitUntil(5_000) { compose.onAllNodesWithText("🦷").fetchSemanticsNodes().size >= 2 }
        compose.onNodeWithText("추가").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals("치과 예약", savedTitle)
        assertEquals("🦷", savedEmoji)
        assertEquals("🦷", DdaySettings.getRecentEmojis(context).first())
    }

    @Test
    fun addSheet_manuallyPickedDefaultEmoji_notOverwrittenBySuggestion() {
        var savedEmoji: String? = null
        compose.setContent {
            AddEditBottomSheet(
                isVisible = true,
                itemType = ItemType.DDAY,
                onDismiss = {},
                onSave = { _, _, _, emoji, _, _, _, _, _, _, _, _, _, _, _ -> savedEmoji = emoji }
            )
        }
        compose.waitForIdle()
        // 피커에서 기본값과 같은 📌를 직접 선택
        compose.onNodeWithContentDescription("이모지 변경").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("추천").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText("검색", substring = true)).performTextInput("압정")
        compose.waitUntil(3_000) { compose.onAllNodesWithText("📌").fetchSemanticsNodes().size >= 2 }
        // 시트 미리보기·피커 미리보기·검색 결과 중 마지막(검색 결과)을 선택
        compose.onAllNodesWithText("📌")[compose.onAllNodesWithText("📌").fetchSemanticsNodes().size - 1].performClick()
        compose.onNodeWithText("선택").performClick()
        compose.waitForIdle()

        // 그 뒤 제목을 입력해도 🦷로 바뀌면 안 됨
        compose.onNode(hasSetTextAction() and hasText("제목")).performTextInput("치과 예약")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("🦷").fetchSemanticsNodes().isNotEmpty() }  // 추천 칩은 표시
        compose.onNodeWithText("추가").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals("📌", savedEmoji)
    }

    // ===== AI 자동 입력: 날짜를 못 찾아도 첨부 이미지 유지 (Codex 지적 1) =====

    @Test
    fun autoInput_noDatesFound_keepsImageForRetry_thenSuccessSavesAll() {
        DdaySettings.setAiConsentShown(context)
        val imageUri = writePng("pick.png", 400, 600)
        // 사진 선택기 대신 바로 이미지 Uri를 돌려주는 가짜 registry
        val registryOwner = object : androidx.activity.result.ActivityResultRegistryOwner {
            override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
                    input: I,
                    options: androidx.core.app.ActivityOptionsCompat?
                ) {
                    dispatchResult(requestCode, imageUri)
                }
            }
        }
        val saved = mutableListOf<String>()
        // 1차 응답: 날짜 없는 일정 2개 → 실패
        GeminiParser.parseOverrideForTest = { _, images ->
            assertEquals(1, images.size)
            listOf(GeminiParser.ParsedEvent(title = "A"), GeminiParser.ParsedEvent(title = "B"))
        }
        try {
            compose.setContent {
                CompositionLocalProvider(
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides registryOwner
                ) {
                    AddEditBottomSheet(
                        isVisible = true,
                        itemType = ItemType.DDAY,
                        onDismiss = {},
                        onSave = { title, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> saved.add(title) }
                    )
                }
            }
            compose.waitForIdle()
            compose.onNodeWithText("AI로 빠르게 입력").performScrollTo().performClick()
            compose.onNodeWithText("사진으로 입력").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("이미지 첨부됨").fetchSemanticsNodes().isNotEmpty() }

            compose.onNodeWithText("분석").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("날짜를 찾지 못했습니다").fetchSemanticsNodes().isNotEmpty() }
            // 이미지가 남아 있어 바로 재시도 가능해야 함
            compose.onNodeWithText("이미지 첨부됨").assertExists()
            compose.onNodeWithText("분석").assertIsEnabled()
            assertTrue(saved.isEmpty())

            // 2차 응답: 날짜 있는 일정 2개 → 일괄 저장 성공, 다이얼로그 닫힘
            GeminiParser.parseOverrideForTest = { _, images ->
                assertEquals("재시도 때도 이미지가 전달돼야 함", 1, images.size)
                listOf(
                    GeminiParser.ParsedEvent(title = "A", date = "2026-12-01"),
                    GeminiParser.ParsedEvent(title = "B", date = "2026-12-02")
                )
            }
            compose.onNodeWithText("분석").performClick()
            compose.waitUntil(5_000) { saved.size == 2 }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("이미지 첨부됨").fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf("A", "B"), saved)
        } finally {
            GeminiParser.parseOverrideForTest = null
        }
    }

    // ===== AI 횟수: 실제 호출 실패가 원인별 안내까지 이어지고, 쓴 횟수는 돌려줌 =====

    @Test
    fun autoInput_failure_showsCauseMessage_andRefundsQuota() {
        DdaySettings.setAiConsentShown(context)
        val cases = listOf(
            java.io.IOException("Unable to resolve host") to "인터넷 연결을 확인하고 다시 시도해 주세요.",
            Exception("429 RESOURCE_EXHAUSTED: Quota exceeded for generate content requests per minute") to
                "요청이 많아 잠시 막혔어요. 잠시 후 다시 시도해 주세요.",
            Exception("429 RESOURCE_EXHAUSTED: billing spend cap reached") to
                "이번 달 AI 자동 입력이 일시 중지됐어요. 직접 입력은 계속 쓸 수 있어요."
        )
        var failure: Exception = cases[0].first
        GeminiParser.modelCallOverrideForTest = { throw failure }
        try {
            compose.setContent {
                AddEditBottomSheet(
                    isVisible = true,
                    itemType = ItemType.DDAY,
                    onDismiss = {},
                    onSave = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> }
                )
            }
            compose.waitForIdle()
            compose.onNodeWithText("AI로 빠르게 입력").performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasText("또는 글로 입력·붙여넣기")).performTextInput("다음 주 금요일 치과")
            for ((error, message) in cases) {
                failure = error
                compose.onNodeWithText("분석").performClick()
                compose.waitUntil(5_000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
                // 실패한 분석은 횟수를 쓰지 않음
                assertEquals(0, DdaySettings.getAiQuota(context).freeUsed)
            }
            compose.onNodeWithText("이번 달 무료 ${AiQuota.FREE_PER_MONTH}/${AiQuota.FREE_PER_MONTH}회 남음").assertExists()
        } finally {
            GeminiParser.modelCallOverrideForTest = null
        }
    }

    @Test
    fun autoInput_noQuotaLeft_disablesAnalyzeAndOffersAd() {
        DdaySettings.setAiConsentShown(context)
        repeat(AiQuota.FREE_PER_MONTH) { DdaySettings.consumeAiQuota(context) }
        compose.setContent {
            AddEditBottomSheet(
                isVisible = true,
                itemType = ItemType.DDAY,
                onDismiss = {},
                onSave = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> }
            )
        }
        compose.waitForIdle()
        compose.onNodeWithText("AI로 빠르게 입력").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("또는 글로 입력·붙여넣기")).performTextInput("내일 회의")
        compose.onNodeWithText("이번 달 무료 0/${AiQuota.FREE_PER_MONTH}회 남음").assertExists()
        compose.onNodeWithText("분석").assertIsNotEnabled()
        // debug 빌드는 테스트 광고 단위가 있음 → 불러온 뒤 광고 버튼, 못 불러오면 다시 시도 (횟수를 대신 주지 않음)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("광고 보고 ${AiQuota.AD_REWARD}회 받기").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("다시 시도").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, AiQuota.totalRemaining(DdaySettings.getAiQuota(context)))
        // 광고로 받은 횟수가 생기면 다시 분석 가능
        DdaySettings.addAiAdReward(context)
        assertEquals(AiQuota.AD_REWARD, AiQuota.totalRemaining(DdaySettings.getAiQuota(context)))
    }

    @Test
    fun autoInput_noAdAvailable_showsReasonAndRetry_withoutGrantingQuota() {
        DdaySettings.setAiConsentShown(context)
        repeat(AiQuota.FREE_PER_MONTH) { DdaySettings.consumeAiQuota(context) }
        // 실제 광고를 불러오지 않고 "보여줄 광고 없음" 상태를 넣음
        RewardedAds.loadEnabledForTest = false
        RewardedAds.setStatusForTest(RewardedAds.Status.NO_FILL)
        try {
            compose.setContent {
                AddEditBottomSheet(
                    isVisible = true,
                    itemType = ItemType.DDAY,
                    onDismiss = {},
                    onSave = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> }
                )
            }
            compose.waitForIdle()
            compose.onNodeWithText("AI로 빠르게 입력").performScrollTo().performClick()
            compose.onNodeWithText("지금은 볼 수 있는 광고가 없어요.").assertExists()
            compose.onNodeWithText("다시 시도").assertExists()
            compose.onNodeWithText("광고 보고 ${AiQuota.AD_REWARD}회 받기").assertDoesNotExist()
            // 광고가 없다고 횟수를 대신 주지 않음
            compose.onNodeWithText("다시 시도").performClick()
            assertEquals(0, AiQuota.totalRemaining(DdaySettings.getAiQuota(context)))
            // 네트워크 오류는 다른 안내
            RewardedAds.setStatusForTest(RewardedAds.Status.NETWORK_ERROR)
            compose.onNodeWithText("인터넷 연결을 확인해 주세요.").assertExists()
        } finally {
            RewardedAds.loadEnabledForTest = true
            RewardedAds.setStatusForTest(RewardedAds.Status.IDLE)
        }
    }

    // ===== 캘린더 가져오기 기록: 가져온 D-Day를 지우면 다시 가져올 수 있음 =====

    @Test
    fun importedPhoneEvent_onlyWhileItsDdayExists() {
        DdaySettings.addImportedPhoneEvent(context, "10@1000", itemId = 7)
        DdaySettings.addImportedPhoneEvent(context, "11@2000", itemId = 8)
        // 두 D-Day가 모두 있으면 둘 다 "가져옴"
        assertEquals(setOf("10@1000", "11@2000"), DdaySettings.importedPhoneKeys(context, setOf(7, 8, 9)))
        // 7번 D-Day를 지우면 그 일정은 다시 가져올 수 있음
        assertEquals(setOf("11@2000"), DdaySettings.importedPhoneKeys(context, setOf(8, 9)))
        // 같은 일정을 다시 가져오면 새 D-Day 번호로 바뀜
        DdaySettings.addImportedPhoneEvent(context, "10@1000", itemId = 12)
        assertEquals(setOf("10@1000", "11@2000"), DdaySettings.importedPhoneKeys(context, setOf(8, 12)))
    }

    // ===== 1라운드 Codex 지적: 그룹 이동 · 체크박스 접근성 =====

    // 그룹 손잡이 끌기는 Compose 테스트의 가상 터치·테스트 시계로는 재현되지 않아(라이브러리 특성)
    // 실제 앱 + adb input swipe로 확인함. 순서 계산은 단위 테스트(groupNameFromKey)로 검증.

    @Test
    fun calendar_addForSelectedDate_twiceInARow_bothSaveSelectedDate() {
        val today = java.util.Calendar.getInstance()
        val day = if (today.get(java.util.Calendar.DAY_OF_MONTH) == 20) 21 else 20
        val expected = (today.clone() as java.util.Calendar).apply { set(java.util.Calendar.DAY_OF_MONTH, day) }
        val label = java.text.SimpleDateFormat("yyyy.MM.dd (E)", java.util.Locale.getDefault()).format(expected.time)
        // 저장 후 시트만 닫고 날짜 값은 그대로 둠 → 같은 날짜로 다시 열어도 시트가 날짜를 다시 채워야 함
        var sheetVisible by mutableStateOf(false)
        var sheetDate by mutableStateOf<java.util.Date?>(null)
        val savedDates = mutableListOf<java.util.Date?>()
        compose.setContent {
            DdayCalendarView(
                ddayItems = emptyList(),
                onToggle = {},
                onAddForDate = { sheetDate = it; sheetVisible = true }
            )
            AddEditBottomSheet(
                isVisible = sheetVisible,
                itemType = ItemType.DDAY,
                initialDate = sheetDate,
                onDismiss = { sheetVisible = false },
                onSave = { _, _, date, _, _, _, _, _, _, _, _, _, _, _, _ ->
                    savedDates += date
                    sheetVisible = false
                }
            )
        }
        compose.onNodeWithContentDescription("${day}일").performClick()
        repeat(2) { n ->
            compose.onNodeWithText("이 날짜에 D-Day 추가").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithText(label).assertExists()
            compose.onNode(hasSetTextAction() and hasText("제목")).performTextInput("캘린더 일정 ${n + 1}")
            compose.onNodeWithText("추가").performScrollTo().performClick()
            compose.waitForIdle()
        }

        assertEquals(2, savedDates.size)
        savedDates.forEach { date ->
            val saved = java.util.Calendar.getInstance().apply { time = date!! }
            assertEquals(expected.get(java.util.Calendar.YEAR), saved.get(java.util.Calendar.YEAR))
            assertEquals(expected.get(java.util.Calendar.MONTH), saved.get(java.util.Calendar.MONTH))
            assertEquals(day, saved.get(java.util.Calendar.DAY_OF_MONTH))
        }
    }

    // ===== 넓은 화면 2단 (폴드 펼침·태블릿) =====

    private fun mainScreenAtWidth(widthDp: Int) = mainScreenAtWidth(mutableStateOf(widthDp))

    private fun mainScreenAtWidth(widthDp: androidx.compose.runtime.MutableState<Int>) {
        val db = DdayDatabase.getDatabase(context)
        db.clearAllTables()
        runBlocking {
            db.ddayDao().insert(
                DdayItem(title = "치과 예약", date = java.util.Date(System.currentTimeMillis() + 5L * 86_400_000L), iconName = "🦷", customColor = 0xFFC5E1A5L)
            )
        }
        DdaySettings.setCalendarView(context, false)
        compose.setContent {
            // 창 폭만 바꿔서 확인 (실제 기기 폭과 무관하게 분기 로직 검증)
            val conf = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current).apply { screenWidthDp = widthDp.value }
            CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides conf) {
                com.silverwest.dayli.ui.theme.MyApplicationTheme {
                    com.silverwest.dayli.MainDdayScreen()
                }
            }
        }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("치과 예약").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun mainScreen_wide_showsListAndCalendarTogether_noToggle() {
        mainScreenAtWidth(840)
        compose.onAllNodesWithText("이 날짜에 D-Day 추가").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("캘린더 보기").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("리스트 보기").assertCountEquals(0)
    }

    @Test
    fun mainScreen_wide_onTodoTab_calendarAddStillOpensDdaySheet() {
        mainScreenAtWidth(840)
        compose.onAllNodesWithText("To-Do")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("이 날짜에 D-Day 추가").performScrollTo().performClick()
        compose.waitForIdle()
        // D-Day 시트에만 있는 날짜 칸 (오늘 날짜가 채워짐)
        val today = java.text.SimpleDateFormat("yyyy.MM.dd (E)", java.util.Locale.getDefault()).format(java.util.Date())
        compose.onNodeWithText(today).assertExists()
    }

    @Test
    fun mainScreen_narrow_keepsSinglePaneWithToggle() {
        mainScreenAtWidth(411)
        compose.onAllNodesWithText("이 날짜에 D-Day 추가").assertCountEquals(0)
        compose.onNodeWithContentDescription("캘린더 보기").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("이 날짜에 D-Day 추가").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("리스트 보기").assertCountEquals(1)
    }

    /** 다음 달 20일을 고른 상태에서 기대하는 머리글·선택 날짜 글자 */
    private fun nextMonthDay20Labels(): Pair<String, String> {
        val c = java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_MONTH, 1); add(java.util.Calendar.MONTH, 1); set(java.util.Calendar.DAY_OF_MONTH, 20) }
        val header = "${c.get(java.util.Calendar.YEAR)}년 ${c.get(java.util.Calendar.MONTH) + 1}월"
        val selected = DayliUi.koreanDate(c.time).replace(Regex("\\((.)\\)$"), "$1요일")
        return header to selected
    }

    @Test
    fun calendar_monthAndSelectedDate_surviveSingleToTwoPaneSwitch() {
        val width = mutableStateOf(411)
        mainScreenAtWidth(width)
        // 1단: 캘린더 열고 다음 달 20일 선택
        compose.onNodeWithContentDescription("캘린더 보기").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("다음 달").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("다음 달").performClick()
        compose.onNodeWithContentDescription("20일").performClick()
        compose.waitForIdle()
        val (header, selected) = nextMonthDay20Labels()
        compose.onNodeWithText(header).assertExists()
        compose.onNodeWithText(selected).assertExists()

        // 폴드 펼침: 600dp 이상 → 2단. 오른쪽 캘린더가 같은 월·날짜 유지
        width.value = 840
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("캘린더 보기").assertCountEquals(0)
        compose.onNodeWithText(header).assertExists()
        compose.onNodeWithText(selected).assertExists()

        // 다시 접기: 1단 캘린더로 돌아와도 유지
        width.value = 411
        compose.waitForIdle()
        compose.onNodeWithText(header).assertExists()
        compose.onNodeWithText(selected).assertExists()
    }

    @Test
    fun calendarState_survivesActivityRecreation() {
        // 폴드 접기/펼치기는 Activity를 다시 만듦 → 저장·복원 후에도 월·선택 날짜 유지
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {
            DdayCalendarView(ddayItems = emptyList(), onToggle = {}, state = rememberCalendarState())
        }
        compose.onNodeWithContentDescription("다음 달").performClick()
        compose.onNodeWithContentDescription("20일").performClick()
        compose.waitForIdle()
        val (header, selected) = nextMonthDay20Labels()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(header).assertExists()
        compose.onNodeWithText(selected).assertExists()
    }

    @Test
    fun settingsReset_resetsColorWheelToDefault() {
        DdaySettings.setHeroCardEnabled(context, true)
        DdaySettings.setHeroCardColor(context, "#C2185B")
        compose.setContent {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) { SettingsScreen() }
        }
        compose.waitForIdle()
        // 직접 고른 색이면 색상환이 열려 있고 그 색을 표시
        compose.onAllNodesWithText("#C2185B").fetchSemanticsNodes().let { assertTrue("처음엔 직접 고른 색 표시", it.isNotEmpty()) }

        compose.onNodeWithText("기본값으로 복원").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(DdaySettings.DEFAULT_HERO_COLOR, DdaySettings.getHeroCardColor(context))
        // 프리셋으로 돌아왔으니 색상환은 닫힘
        compose.onAllNodesWithContentDescription("색상환: 눌러서 색 고르기").assertCountEquals(0)

        // 다시 직접 고르기를 열면 이전 색이 아니라 기본색에서 시작
        compose.onNodeWithContentDescription("직접 고르기").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("#C2185B").assertCountEquals(0)
        compose.onAllNodesWithText(DdaySettings.DEFAULT_HERO_COLOR).fetchSemanticsNodes().let {
            assertTrue("기본색 표시", it.isNotEmpty())
        }
    }

    @Test
    fun checkbox_exposesCheckedStateToAccessibility() {
        var checked by mutableStateOf(false)
        compose.setContent {
            DayliCheckbox(checked = checked, color = ComposeColor(0xFFA5D6A7), onClick = { checked = !checked })
        }
        compose.onNodeWithContentDescription("완료").assertIsOff()
        compose.onNodeWithContentDescription("완료").performClick()
        compose.onNodeWithContentDescription("완료").assertIsOn()
    }

    // ===== AI 입력용 이미지 로드 =====

    @Test
    fun loadImagesForAi_normalImageIsSingle_tallCaptureIsSliced() = runBlocking {
        val normal = GeminiParser.loadImagesForAi(context, writePng("normal.png", 720, 1280))
        assertEquals(1, normal.size)

        val tall = GeminiParser.loadImagesForAi(context, writePng("tall.png", 720, 6000))
        assertTrue("긴 캡처는 여러 장 (실제 ${tall.size})", tall.size in 2..6)
        tall.forEach { assertEquals("가로 해상도 유지", 720, it.width) }
        // 겹침 포함해 원본 높이를 모두 덮어야 함
        assertTrue(tall.sumOf { it.height } >= 6000)
    }

    private fun writePng(name: String, w: Int, h: Int): Uri {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        Canvas(bmp).drawColor(Color.WHITE)
        val file = File(context.cacheDir, name)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return Uri.fromFile(file)
    }
}
