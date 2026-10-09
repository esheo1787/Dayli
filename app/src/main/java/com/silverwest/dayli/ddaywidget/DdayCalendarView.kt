package com.silverwest.dayli.ddaywidget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

@Composable
fun DdayCalendarView(
    ddayItems: List<DdayItem>,
    onToggle: (DdayItem) -> Unit,
    onLongPress: (DdayItem) -> Unit = {},
    onSubTaskToggle: (DdayItem, Int) -> Unit = { _, _ -> },
    expandedSubTaskIds: Set<Int> = emptySet(),
    onExpandSubTask: (Int) -> Unit = {},
    searchQuery: String = "",
    onAddForDate: (Date) -> Unit = {},
    // 보고 있는 월·선택 날짜. 1단↔2단 전환(폴드 접기/펼치기)에도 유지되도록 상위에서 넘겨받음
    state: CalendarState = rememberCalendarState()
) {
    val context = LocalContext.current
    val fontScale = DdaySettings.getAppFontScale(context)

    // 오늘 날짜
    val today = remember { Calendar.getInstance() }
    val todayYear = today.get(Calendar.YEAR)
    val todayMonth = today.get(Calendar.MONTH)
    val todayDay = today.get(Calendar.DAY_OF_MONTH)

    // 현재 표시 중인 년/월, 선택된 날짜 (year, month, day) — 처음엔 오늘
    var currentYear by state::year
    var currentMonth by state::month
    var selectedDate by state::selected
    var showShareDialog by remember { mutableStateOf(false) }

    // 이번 달에 해당하는 D-Day 아이템을 일자별 그룹핑
    val itemsByDay = remember(ddayItems, currentYear, currentMonth) {
        val map = mutableMapOf<Int, MutableList<DdayItem>>()
        val cal = Calendar.getInstance()
        ddayItems.forEach { item ->
            item.date?.let { date ->
                cal.time = date
                if (cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth) {
                    val day = cal.get(Calendar.DAY_OF_MONTH)
                    map.getOrPut(day) { mutableListOf() }.add(item)
                }
            }
        }
        map
    }
    val monthPendingCount = itemsByDay.values.sumOf { list -> list.count { !it.isChecked } }

    // 선택된 날짜의 아이템 필터링
    val itemsForSelectedDate = remember(selectedDate, ddayItems) {
        val sel = selectedDate ?: return@remember emptyList()
        val cal = Calendar.getInstance()
        ddayItems.filter { item ->
            item.date?.let { date ->
                cal.time = date
                cal.get(Calendar.YEAR) == sel.first &&
                    cal.get(Calendar.MONTH) == sel.second &&
                    cal.get(Calendar.DAY_OF_MONTH) == sel.third
            } == true
        }
    }

    // 그리드 계산: 이번 달 첫날 요일, 총 일수
    val firstDayOfWeek = remember(currentYear, currentMonth) {
        Calendar.getInstance().apply { set(currentYear, currentMonth, 1) }
            .get(Calendar.DAY_OF_WEEK) - 1 // 0=일, 1=월, ..., 6=토
    }
    val daysInMonth = remember(currentYear, currentMonth) {
        Calendar.getInstance().apply { set(currentYear, currentMonth, 1) }
            .getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val rows = (firstDayOfWeek + daysInMonth + 6) / 7
    val isCurrentMonth = currentYear == todayYear && currentMonth == todayMonth

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sundayColor = if (dark) Color(0xFFF2A49E) else Color(0xFFC2564F)
    val saturdayColor = if (dark) Color(0xFFA8C3EE) else Color(0xFF3E6FB0)

    fun moveMonth(delta: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            add(Calendar.MONTH, delta)
        }
        currentYear = cal.get(Calendar.YEAR)
        currentMonth = cal.get(Calendar.MONTH)
        selectedDate = if (currentYear == todayYear && currentMonth == todayMonth) Triple(todayYear, todayMonth, todayDay) else null
    }

    // 글자가 커져도 일정 목록이 가려지지 않도록 화면 전체를 한 번에 스크롤
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── 월 헤더 ──
        item(key = "month_header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "이번 달 일정 ${monthPendingCount}개",
                        fontSize = (13 * fontScale).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${currentYear}년 ${currentMonth + 1}월",
                        fontSize = (22 * fontScale).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                CalendarNavButton(onClick = { moveMonth(-1) }, description = "이전 달") {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                }
                Spacer(Modifier.width(4.dp))
                CalendarNavButton(onClick = { moveMonth(1) }, description = "다음 달") {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
                Spacer(Modifier.width(4.dp))
                CalendarNavButton(
                    onClick = {
                        currentYear = todayYear
                        currentMonth = todayMonth
                        selectedDate = Triple(todayYear, todayMonth, todayDay)
                    },
                    description = "오늘로 이동",
                    wide = true
                ) {
                    Text("오늘", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // ── 달력 카드 ──
        item(key = "month_grid_${currentYear}_$currentMonth") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 6.dp, vertical = 10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { index, name ->
                        Text(
                            text = name,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = (12 * fontScale).sp,
                            color = when (index) {
                                0 -> sundayColor
                                6 -> saturdayColor
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val dayNumber = row * 7 + col - firstDayOfWeek + 1
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                if (dayNumber in 1..daysInMonth) {
                                    val isSelected = selectedDate?.let {
                                        it.first == currentYear && it.second == currentMonth && it.third == dayNumber
                                    } == true
                                    val pending = itemsByDay[dayNumber]?.filter { !it.isChecked }.orEmpty()
                                    CalendarDayCell(
                                        day = dayNumber,
                                        isToday = isCurrentMonth && dayNumber == todayDay,
                                        isSelected = isSelected,
                                        markerEmoji = pending.firstOrNull()?.getEmoji(),
                                        extraCount = (pending.size - 1).coerceAtLeast(0),
                                        dayColor = when (col) {
                                            0 -> sundayColor
                                            6 -> saturdayColor
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        fontScale = fontScale,
                                        onClick = { selectedDate = Triple(currentYear, currentMonth, dayNumber) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 선택된 날짜의 D-Day 목록 ──
        val sel = selectedDate
        if (sel == null) {
            item(key = "pick_hint") {
                Text(
                    text = "날짜를 누르면 그날 일정을 볼 수 있어요",
                    fontSize = (14 * fontScale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
            }
        } else {
            val selDate = Calendar.getInstance().apply { clear(); set(sel.first, sel.second, sel.third) }.time
            item(key = "selected_header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DayliUi.koreanDate(selDate).replace(Regex("\\((.)\\)$"), "$1요일"),
                        fontSize = (16 * fontScale).sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${itemsForSelectedDate.size}개",
                        fontSize = (13 * fontScale).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    if (itemsForSelectedDate.isNotEmpty()) {
                        IconButton(onClick = { showShareDialog = true }) {
                            Icon(Icons.Default.Share, contentDescription = "이 날짜 일정 공유", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            items(itemsForSelectedDate, key = { it.id }) { item ->
                DdayListItem(
                    item = item,
                    onToggle = onToggle,
                    onLongPress = onLongPress,
                    onSubTaskToggle = onSubTaskToggle,
                    isExpanded = item.id in expandedSubTaskIds,
                    onExpandToggle = { onExpandSubTask(item.id) },
                    searchQuery = searchQuery
                )
            }
            item(key = "add_for_date") {
                Row(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .clickable { onAddForDate(selDate) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("이 날짜에 D-Day 추가", fontSize = (14 * fontScale).sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }

    // 공유 방식 선택 다이얼로그
    if (showShareDialog && selectedDate != null) {
        val (sy, sm, sd) = selectedDate!!
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("공유") },
            text = {
                Column {
                    Surface(
                        onClick = {
                            DdayShareHelper.shareDateItems(
                                context, sy, sm, sd, itemsForSelectedDate
                            )
                            showShareDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text("이미지로 공유") },
                            leadingContent = {
                                Icon(Icons.Default.Image, contentDescription = null)
                            }
                        )
                    }
                    Surface(
                        onClick = {
                            DdayShareHelper.shareDateText(
                                context, sy, sm, sd, itemsForSelectedDate
                            )
                            showShareDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text("텍스트로 공유") },
                            leadingContent = {
                                Icon(Icons.Default.Share, contentDescription = null)
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showShareDialog = false }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun CalendarNavButton(
    onClick: () -> Unit,
    description: String,
    wide: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .then(if (wide) Modifier.heightIn(min = 40.dp).widthIn(min = 52.dp) else Modifier.size(40.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** 날짜 칸: 숫자(선택=강조색 채움, 오늘=연한 배경) + 그날 첫 일정 이모지(+N) */
@Composable
private fun CalendarDayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    markerEmoji: String?,
    extraCount: Int,
    dayColor: Color,
    fontScale: Float,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = (54 * fontScale).dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${day}일" + (if (markerEmoji != null) ", 일정 ${extraCount + 1}개" else "") +
                    (if (isToday) ", 오늘" else "")
            }
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size((34 * fontScale).dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    when {
                        isSelected -> accent
                        isToday -> MaterialTheme.colorScheme.surfaceVariant
                        else -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$day",
                fontSize = (15 * fontScale).sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else dayColor,
                textAlign = TextAlign.Center
            )
        }
        // 이모지 유무와 관계없이 줄 높이를 고정 (일정 있는 주만 칸이 커지지 않게)
        val markerHeight = with(LocalDensity.current) { (11 * fontScale).sp.toDp() } * 1.5f
        Row(
            modifier = Modifier.height(markerHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (markerEmoji != null) {
                EmojiText(markerEmoji, fontSize = (11 * fontScale).sp)
                if (extraCount > 0) {
                    Text("+$extraCount", fontSize = (9 * fontScale).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * 캘린더가 보고 있는 월과 선택한 날짜.
 * 화면 회전·폴드 접기/펼치기로 Activity가 다시 만들어져도 유지 (rememberSaveable)
 */
@Stable
class CalendarState(year: Int, month: Int, selected: Triple<Int, Int, Int>?) {
    var year by mutableIntStateOf(year)
    var month by mutableIntStateOf(month)
    var selected by mutableStateOf(selected)

    companion object {
        fun today(): CalendarState {
            val c = Calendar.getInstance()
            val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH)
            return CalendarState(y, m, Triple(y, m, c.get(Calendar.DAY_OF_MONTH)))
        }

        val Saver = listSaver<CalendarState, Int>(
            save = { listOf(it.year, it.month, it.selected?.first ?: -1, it.selected?.second ?: -1, it.selected?.third ?: -1) },
            restore = { CalendarState(it[0], it[1], if (it[2] < 0) null else Triple(it[2], it[3], it[4])) }
        )
    }
}

@Composable
fun rememberCalendarState(): CalendarState = rememberSaveable(saver = CalendarState.Saver) { CalendarState.today() }
