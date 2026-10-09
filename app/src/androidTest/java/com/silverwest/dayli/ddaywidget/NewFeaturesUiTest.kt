package com.silverwest.dayli.ddaywidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsEnabled
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
        compose.onNodeWithText("이모지 변경").performClick()
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
            compose.onNodeWithText("텍스트로 자동 입력").performScrollTo().performClick()
            compose.onNodeWithText("이미지").performClick()
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

    // ===== 1라운드 Codex 지적: 그룹 이동 · 체크박스 접근성 =====

    // 그룹 손잡이 끌기는 Compose 테스트의 가상 터치·테스트 시계로는 재현되지 않아(라이브러리 특성)
    // 실제 앱 + adb input swipe로 확인함. 순서 계산은 단위 테스트(groupNameFromKey)로 검증.

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
