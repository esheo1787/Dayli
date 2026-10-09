package com.silverwest.dayli.ddaywidget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.silverwest.dayli.ui.theme.MyApplicationTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 화면 확인용 스크린샷 생성 (검증 assert 없음)
 * 결과: /sdcard/Android/data/com.silverwest.dayli/files/screenshots 폴더의 PNG → adb pull 로 확인
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.getSharedPreferences("dday_widget_settings", Context.MODE_PRIVATE).edit().clear().commit()
        listOf("🎂", "📝", "✈️", "🦷", "☕").forEach { DdaySettings.addRecentEmoji(context, it) }
    }

    private fun save(name: String) {
        compose.waitForIdle()
        // 다이얼로그·바텀시트는 별도 창이라 루트가 여러 개 → 모든 루트를 각각 캡처
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val count = compose.onAllNodes(isRoot()).fetchSemanticsNodes().size
        for (i in 0 until count) {
            val bitmap: Bitmap = compose.onAllNodes(isRoot())[i].captureToImage().asAndroidBitmap()
            File(dir, "${name}_w$i.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun picker(flat: Boolean) {
        compose.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalFlatEmoji provides flat) {
                    Box(Modifier.fillMaxSize().background(ComposeColor.White)) {
                        EmojiPickerContent("🎂", ComposeColor(0xFFA8C5DA), {}, {})
                    }
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("최근 사용").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun pickerDefault() { picker(false); save("01_picker_default") }

    @Test fun pickerFlat() { picker(true); save("02_picker_flat") }

    @Test
    fun pickerSearch() {
        picker(true)
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("생일")
        compose.waitForIdle()
        save("03_picker_search_flat")
    }

    // 메인 목록 (테스트 에뮬레이터 전용: 앱 DB를 비우고 샘플 D-Day 삽입)
    private fun mainList(flat: Boolean) {
        val db = DdayDatabase.getDatabase(context)
        db.clearAllTables()
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        kotlinx.coroutines.runBlocking {
            listOf(
                Triple("회계정보시스템 5주차 퀴즈", "📝", 3), Triple("정보보호시스템 과제(레포트)", "📄", 44),
                Triple("엄마 생일", "🎂", 12), Triple("치과 예약", "🦷", 5), Triple("오사카 여행", "✈️", 30)
            ).forEachIndexed { i, (title, emoji, d) ->
                db.ddayDao().insert(
                    DdayItem(
                        title = title, date = java.util.Date(now + d * day), iconName = emoji,
                        customColor = listOf(0xFFA8C5DAL, 0xFFF4C7C3L, 0xFFF9E79FL, 0xFFC5E1A5L, 0xFFD7BDE2L)[i],
                        groupName = if (i < 2) "학교" else null
                    )
                )
            }
        }
        DdaySettings.setGroupEmoji(context, "학교", "🏫")
        compose.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalFlatEmoji provides flat) {
                    com.silverwest.dayli.MainDdayScreen()
                }
            }
        }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("치과 예약").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(500)
    }

    @Test fun listDefault() { mainList(false); save("06_list_default") }

    @Test fun listFlat() { mainList(true); save("07_list_flat") }
}
