package com.silverwest.dayli.ddaywidget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
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
        // 스토어용 스크린샷: 광고 배너 숨김
        com.silverwest.dayli.bannerAdsEnabled = false
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
    private fun mainList(
        flat: Boolean,
        dark: Boolean = false,
        tinted: Boolean = false,
        todoTab: Boolean = false,
        calendar: Boolean = false
    ) {
        seedSampleData()
        DdaySettings.setGroupEmoji(context, "학교", "🏫")
        DdaySettings.setBackgroundEnabled(context, tinted)
        if (tinted) DdaySettings.setBackgroundOpacity(context, 30)
        if (todoTab) {
            // 체크리스트 펼친 상태로 (스토어 스크린샷)
            val todos = kotlinx.coroutines.runBlocking { DdayDatabase.getDatabase(context).ddayDao().getAll() }
            DdaySettings.setExpandedSubTaskIds(context, todos.filter { it.title == "중간고사 공부" || it.title == "장보기" }.map { it.id }.toSet())
        }
        DdaySettings.setLastTab(context, if (todoTab) 1 else 0)
        DdaySettings.setCalendarView(context, calendar)
        compose.setContent {
            MyApplicationTheme(themeMode = if (dark) DdaySettings.ThemeMode.DARK else DdaySettings.ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalFlatEmoji provides flat) {
                    com.silverwest.dayli.MainDdayScreen()
                }
            }
        }
        val anchor = if (todoTab) "장보기" else if (calendar) "이 날짜에 D-Day 추가" else "치과 예약"
        compose.waitUntil(8_000) { compose.onAllNodesWithText(anchor).fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(700)
    }

    private fun seedSampleData() {
        val db = DdayDatabase.getDatabase(context)
        db.clearAllTables()
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        kotlinx.coroutines.runBlocking {
            listOf(
                Triple("수학 쪽지시험", "📝", 3), Triple("과학 실험 보고서", "🔬", 9),
                Triple("엄마 생일", "🎂", 12), Triple("치과 예약", "🦷", 5), Triple("오사카 여행", "✈️", 30)
            ).forEachIndexed { i, (title, emoji, d) ->
                db.ddayDao().insert(
                    DdayItem(
                        title = title, date = java.util.Date(now + d * day), iconName = emoji,
                        customColor = listOf(0xFFA8C5DAL, 0xFFF4C7C3L, 0xFFF9E79FL, 0xFFC5E1A5L, 0xFFD7BDE2L)[i],
                        groupName = if (i < 2) "학교" else null,
                        timeHour = if (i == 0) 12 else if (i == 3) 14 else null,
                        timeMinute = if (i == 0 || i == 3) 0 else null
                    )
                )
            }
            // To-Do 샘플
            db.ddayDao().insert(DdayItem(title = "장보기", iconName = "🛒", customColor = 0xFFC5E1A5L, itemType = ItemType.TODO.name,
                subTasks = DdayItem.subTasksToJson(listOf(SubTask("우유", true), SubTask("계란", true), SubTask("양파"), SubTask("두부"), SubTask("김")))))
            db.ddayDao().insert(DdayItem(title = "운동하기", iconName = "💪", customColor = 0xFFF4C7C3L, itemType = ItemType.TODO.name, repeatType = RepeatType.DAILY.name))
            db.ddayDao().insert(DdayItem(title = "중간고사 공부", iconName = "📚", customColor = 0xFFA8C5DAL, itemType = ItemType.TODO.name,
                subTasks = DdayItem.subTasksToJson(listOf(SubTask("수학", true), SubTask("과학"), SubTask("영어")))))
        }
    }

    /**
     * 위젯 캡처: 실제 위젯과 같은 RemoteViewsFactory가 만든 행(RemoteViews)을 그대로 펼쳐 그림으로 저장.
     * 홈 화면 없이도 위젯 색·배치를 확인하기 위함 (바탕은 배경화면 느낌의 그라데이션)
     */
    private fun widget(name: String, mode: String, dark: Boolean = false, tinted: Boolean = false, bgOpacity: Int = 15, flat: Boolean = false) {
        seedSampleData()
        DdaySettings.setGroupEmoji(context, "학교", "🏫")
        DdaySettings.setBackgroundEnabled(context, tinted)
        DdaySettings.setBackgroundOpacity(context, bgOpacity)
        DdaySettings.setFlatEmojiEnabled(context, flat)
        DdaySettings.setThemeMode(context, if (dark) 2 else 1)
        val factory = RemoteViewsFactory(context, android.content.Intent().putExtra(DdayOnlyWidgetProvider.EXTRA_WIDGET_MODE, mode))
        factory.onDataSetChanged()
        val dp = context.resources.displayMetrics.density
        var bitmap: Bitmap? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val list = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val pad = (6 * dp).toInt()
                setPadding(pad, pad, pad, pad)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 16 * dp
                    setColor(WidgetStyle.containerBase(dark).copy(alpha = WidgetStyle.containerAlpha(DdaySettings.getWidgetBgOpacity(context)) / 255f).toArgb())
                }
            }
            for (i in 0 until factory.count) {
                val row = factory.getViewAt(i).apply(context, list)
                val lp = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                if (i > 0) lp.topMargin = (6 * dp).toInt()
                list.addView(row, lp)
            }
            val root = android.widget.FrameLayout(context).apply {
                val pad = (16 * dp).toInt()
                setPadding(pad, pad, pad, pad)
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    if (dark) intArrayOf(0xFF2E3A46.toInt(), 0xFF3B2F3A.toInt()) else intArrayOf(0xFFB9D3E6.toInt(), 0xFFE9C9C0.toInt())
                )
                addView(list)
            }
            val w = (360 * dp).toInt()
            root.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            bitmap = Bitmap.createBitmap(root.measuredWidth, root.measuredHeight, Bitmap.Config.ARGB_8888).also {
                root.draw(android.graphics.Canvas(it))
            }
        }
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun widgetMixed() = widget("20_widget_mixed", DdayOnlyWidgetProvider.MODE_ALL)

    @Test fun widgetMixedTinted() = widget("21_widget_mixed_tinted", DdayOnlyWidgetProvider.MODE_ALL, tinted = true, bgOpacity = 45)

    @Test fun widgetDdayDark() = widget("22_widget_dday_dark", DdayOnlyWidgetProvider.MODE_DDAY, dark = true, tinted = true, bgOpacity = 30, flat = true)

    @Test fun widgetTodo() = widget("23_widget_todo", DdayOnlyWidgetProvider.MODE_TODO, tinted = true, bgOpacity = 30)

    // 설정 페이지: 길어서 스크롤하며 여러 장 (직접 고른 카드 색 → 색상환 펼친 상태)
    @Test
    fun settingsPages() {
        DdaySettings.setHeroCardEnabled(context, true)
        DdaySettings.setHeroCardColor(context, "#C2185B")
        DdaySettings.setBackgroundEnabled(context, true)
        DdaySettings.setBackgroundOpacity(context, 35)
        var scroll: androidx.compose.foundation.ScrollState? = null
        compose.setContent {
            MyApplicationTheme(themeMode = DdaySettings.ThemeMode.LIGHT) {
                val st = androidx.compose.foundation.rememberScrollState()
                scroll = st
                Box(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
                    androidx.compose.foundation.layout.Column(Modifier.verticalScroll(st)) { SettingsScreen() }
                }
            }
        }
        compose.waitForIdle()
        val page = context.resources.displayMetrics.heightPixels * 0.8f
        for (i in 0 until 6) {
            compose.runOnIdle { kotlinx.coroutines.runBlocking { scroll!!.scrollTo((i * page).toInt()) } }
            compose.waitForIdle()
            Thread.sleep(300)
            save("11_settings_p$i")
        }
    }

    @Test fun listDefault() { mainList(false); save("06_list_default") }

    @Test fun listFlat() { mainList(true); save("07_list_flat") }

    @Test fun listTinted() { mainList(false, tinted = true); save("08_list_tinted") }

    @Test fun listDark() { mainList(false, dark = true); save("09_list_dark") }

    @Test fun listTodo() { mainList(false, todoTab = true); save("10_list_todo") }

    @Test fun calendarLight() { mainList(false, calendar = true); save("12_calendar") }

    @Test fun calendarDark() { mainList(true, dark = true, calendar = true); save("13_calendar_dark_flat") }
}
