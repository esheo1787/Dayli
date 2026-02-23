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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
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
    searchQuery: String = ""
) {
    val context = LocalContext.current
    val fontScale = DdaySettings.getAppFontScale(context)

    // 현재 표시 중인 년/월
    var currentYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var currentMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }

    // 선택된 날짜 (year, month, day) — null이면 미선택
    var selectedDate by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var showShareDialog by remember { mutableStateOf(false) }

    // 오늘 날짜
    val today = remember { Calendar.getInstance() }
    val todayYear = today.get(Calendar.YEAR)
    val todayMonth = today.get(Calendar.MONTH)
    val todayDay = today.get(Calendar.DAY_OF_MONTH)

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

    // 선택된 날짜의 아이템 필터링
    val itemsForSelectedDate = remember(selectedDate, ddayItems) {
        if (selectedDate == null) return@remember emptyList()
        val (year, month, day) = selectedDate!!
        val cal = Calendar.getInstance()
        ddayItems.filter { item ->
            item.date?.let { date ->
                cal.time = date
                cal.get(Calendar.YEAR) == year &&
                    cal.get(Calendar.MONTH) == month &&
                    cal.get(Calendar.DAY_OF_MONTH) == day
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
    val totalCells = firstDayOfWeek + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(modifier = Modifier.fillMaxSize()) {
        // ── 월 네비게이션 헤더 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, currentYear)
                    set(Calendar.MONTH, currentMonth)
                    add(Calendar.MONTH, -1)
                }
                currentYear = cal.get(Calendar.YEAR)
                currentMonth = cal.get(Calendar.MONTH)
                selectedDate = null
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
            }

            Text(
                text = "${currentYear}년 ${currentMonth + 1}월",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = (16 * fontScale).sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    currentYear = todayYear
                    currentMonth = todayMonth
                    selectedDate = Triple(todayYear, todayMonth, todayDay)
                }) {
                    Text("오늘", fontSize = (13 * fontScale).sp)
                }
                IconButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, currentYear)
                        set(Calendar.MONTH, currentMonth)
                        add(Calendar.MONTH, 1)
                    }
                    currentYear = cal.get(Calendar.YEAR)
                    currentMonth = cal.get(Calendar.MONTH)
                    selectedDate = null
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달")
                }
            }
        }

        // ── 요일 헤더 ──
        val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            dayNames.forEachIndexed { index, name ->
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = (12 * fontScale).sp,
                    fontWeight = FontWeight.Medium,
                    color = when (index) {
                        0 -> Color(0xFFE53935) // 일요일
                        6 -> Color(0xFF2F6BFF) // 토요일
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── 캘린더 그리드 ──
        val isCurrentMonth = currentYear == todayYear && currentMonth == todayMonth

        for (row in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - firstDayOfWeek + 1

                    Box(modifier = Modifier.weight(1f)) {
                        if (dayNumber in 1..daysInMonth) {
                            val isToday = isCurrentMonth && dayNumber == todayDay
                            val isSelected = selectedDate?.let {
                                it.first == currentYear && it.second == currentMonth && it.third == dayNumber
                            } == true
                            val hasDday = itemsByDay[dayNumber]?.any { !it.isChecked } == true

                            CalendarDayCell(
                                day = dayNumber,
                                isToday = isToday,
                                isSelected = isSelected,
                                hasDday = hasDday,
                                isSunday = col == 0,
                                isSaturday = col == 6,
                                fontScale = fontScale,
                                onClick = {
                                    selectedDate = Triple(currentYear, currentMonth, dayNumber)
                                }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // ── 선택된 날짜의 D-Day 목록 ──
        if (selectedDate != null) {
            val (selYear, selMonth, selDay) = selectedDate!!

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selMonth + 1}월 ${selDay}일",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = (15 * fontScale).sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "(${itemsForSelectedDate.size}개)",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = (12 * fontScale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (itemsForSelectedDate.isNotEmpty()) {
                    IconButton(
                        onClick = { showShareDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "공유",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (itemsForSelectedDate.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "이 날짜에 등록된 D-Day가 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(itemsForSelectedDate, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RectangleShape,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
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
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "날짜를 선택하면 해당 D-Day를 볼 수 있습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
private fun CalendarDayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    hasDday: Boolean,
    isSunday: Boolean,
    isSaturday: Boolean,
    fontScale: Float,
    onClick: () -> Unit
) {
    val pointColor = MaterialTheme.colorScheme.primary
    val todayOutlineColor = MaterialTheme.colorScheme.onSurface
    val selectedBgColor = pointColor.copy(alpha = 0.15f)
    val circleSize = (38 * fontScale).dp

    // 배경: D-Day → 포인트 컬러 채움 / 선택(D-Day·오늘 아님) → 연한 포인트 / 그 외 → 투명
    // 테두리: 오늘 → 항상 진한 테두리 (배경 유무 무관)
    val bgColor = when {
        hasDday -> pointColor
        isSelected && !isToday -> selectedBgColor
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .clip(CircleShape)
                .background(bgColor)
                .then(
                    if (isToday) Modifier.border(
                        width = if (hasDday) 2.5.dp else 2.dp,
                        color = todayOutlineColor,
                        shape = CircleShape
                    )
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$day",
                fontSize = (16 * fontScale).sp,
                fontWeight = if (isToday || hasDday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    hasDday -> Color.White
                    isSunday -> Color(0xFFE53935)
                    isSaturday -> Color(0xFF2F6BFF)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center
            )
        }
    }
}
