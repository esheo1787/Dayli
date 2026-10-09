package com.silverwest.dayli.ddaywidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.test.assertCountEquals
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
