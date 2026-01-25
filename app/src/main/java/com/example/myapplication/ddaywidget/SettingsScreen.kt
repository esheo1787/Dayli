package com.example.myapplication.ddaywidget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onSettingsChanged: () -> Unit = {}
) {
    val context = LocalContext.current

    // 설정 상태
    var backgroundEnabled by remember {
        mutableStateOf(DdaySettings.isBackgroundEnabled(context))
    }
    var backgroundOpacity by remember {
        mutableStateOf(DdaySettings.getBackgroundOpacity(context))
    }
    var iconBgOpacity by remember {
        mutableStateOf(DdaySettings.getIconBgOpacity(context))
    }
    var widgetBgOpacity by remember {
        mutableStateOf(DdaySettings.getWidgetBgOpacity(context))
    }
    var widgetFontSize by remember {
        mutableStateOf(DdaySettings.getWidgetFontSize(context))
    }

    // 테마 설정 상태
    var themeMode by remember {
        mutableStateOf(DdaySettings.getThemeModeEnum(context))
    }

    // 알림 설정 상태
    var notifyDayBefore by remember {
        mutableStateOf(DdaySettings.isNotifyDayBeforeEnabled(context))
    }
    var notifySameDay by remember {
        mutableStateOf(DdaySettings.isNotifySameDayEnabled(context))
    }
    var notifyHour by remember {
        mutableStateOf(DdaySettings.getNotifyHour(context))
    }
    var notifyMinute by remember {
        mutableStateOf(DdaySettings.getNotifyMinute(context))
    }
    var notifySound by remember {
        mutableStateOf(DdaySettings.isNotifySoundEnabled(context))
    }
    var notifyVibrate by remember {
        mutableStateOf(DdaySettings.isNotifyVibrateEnabled(context))
    }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // ===== 테마 설정 섹션 =====
        Text(
            text = "테마 설정",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 테마 모드 선택
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "화면 모드",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "앱과 위젯의 색상 테마를 선택합니다",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DdaySettings.ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = {
                            themeMode = mode
                            DdaySettings.setThemeModeEnum(context, mode)
                            onSettingsChanged()
                        },
                        label = {
                            Text(
                                text = when (mode) {
                                    DdaySettings.ThemeMode.SYSTEM -> "시스템"
                                    DdaySettings.ThemeMode.LIGHT -> "라이트"
                                    DdaySettings.ThemeMode.DARK -> "다크"
                                }
                            )
                        },
                        leadingIcon = {
                            Text(
                                text = when (mode) {
                                    DdaySettings.ThemeMode.SYSTEM -> "📱"
                                    DdaySettings.ThemeMode.LIGHT -> "☀️"
                                    DdaySettings.ThemeMode.DARK -> "🌙"
                                },
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // ===== 배경 설정 섹션 =====
        Text(
            text = "배경 설정",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 배경 색상 ON/OFF
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "배경 색상 표시",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "아이템별 색상을 배경에 적용",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = backgroundEnabled,
                onCheckedChange = { enabled ->
                    backgroundEnabled = enabled
                    DdaySettings.setBackgroundEnabled(context, enabled)
                    onSettingsChanged()
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 배경 투명도 슬라이더
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "아이템 배경 투명도",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${backgroundOpacity}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "리스트와 위젯의 아이템 배경 색상 강도",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = backgroundOpacity.toFloat(),
                onValueChange = { value ->
                    backgroundOpacity = value.toInt()
                },
                onValueChangeFinished = {
                    DdaySettings.setBackgroundOpacity(context, backgroundOpacity)
                    onSettingsChanged()
                },
                valueRange = 0f..100f,
                steps = 19,  // 5% 단위
                enabled = backgroundEnabled
            )
        }

        // 아이콘 배경 투명도 슬라이더
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "아이콘 배경 투명도",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${iconBgOpacity}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "이모지 아이콘 배경 색상 강도",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = iconBgOpacity.toFloat(),
                onValueChange = { value ->
                    iconBgOpacity = value.toInt()
                },
                onValueChangeFinished = {
                    DdaySettings.setIconBgOpacity(context, iconBgOpacity)
                    onSettingsChanged()
                },
                valueRange = 0f..100f,
                steps = 19,  // 5% 단위
                enabled = backgroundEnabled
            )
        }

        // 위젯 배경 투명도 슬라이더
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "위젯 배경 투명도",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${widgetBgOpacity}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "위젯 전체 배경 (글래스모피즘)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = widgetBgOpacity.toFloat(),
                onValueChange = { value ->
                    widgetBgOpacity = value.toInt()
                },
                onValueChangeFinished = {
                    DdaySettings.setWidgetBgOpacity(context, widgetBgOpacity)
                    onSettingsChanged()
                },
                valueRange = 0f..100f,
                steps = 19,  // 5% 단위
                enabled = true  // 위젯 배경은 항상 조절 가능
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 위젯 글씨 크기
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "위젯 글씨 크기",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "위젯에 표시되는 텍스트 크기",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("작게" to 0, "보통" to 1, "크게" to 2).forEach { (label, value) ->
                    FilterChip(
                        selected = widgetFontSize == value,
                        onClick = {
                            widgetFontSize = value
                            DdaySettings.setWidgetFontSize(context, value)
                            onSettingsChanged()
                        },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // ===== 알림 설정 섹션 =====
        Text(
            text = "알림 설정",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // D-1 (하루 전) 알림
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "하루 전 알림 (D-1)",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "D-Day 하루 전에 알림",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = notifyDayBefore,
                onCheckedChange = { enabled ->
                    notifyDayBefore = enabled
                    DdaySettings.setNotifyDayBeforeEnabled(context, enabled)
                    NotificationScheduler.updateSchedule(context)
                }
            )
        }

        // D-Day (당일) 알림
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "당일 알림 (D-Day)",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "D-Day 당일에 알림",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = notifySameDay,
                onCheckedChange = { enabled ->
                    notifySameDay = enabled
                    DdaySettings.setNotifySameDayEnabled(context, enabled)
                    NotificationScheduler.updateSchedule(context)
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 알림 시간 설정
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showTimePicker = true }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "알림 시간",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "매일 이 시간에 알림 확인",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Text(
                text = DdaySettings.getNotifyTimeString(context),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 소리 설정
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "알림 소리",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "알림 시 소리 재생",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = notifySound,
                onCheckedChange = { enabled ->
                    notifySound = enabled
                    DdaySettings.setNotifySoundEnabled(context, enabled)
                }
            )
        }

        // 진동 설정
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "알림 진동",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "알림 시 진동",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = notifyVibrate,
                onCheckedChange = { enabled ->
                    notifyVibrate = enabled
                    DdaySettings.setNotifyVibrateEnabled(context, enabled)
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // 미리보기
        Text(
            text = "미리보기",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // 샘플 아이템 미리보기
        PreviewItem(
            emoji = "📚",
            title = "시험 공부",
            dday = "D-7",
            color = Color(0xFFE53935),
            backgroundEnabled = backgroundEnabled,
            backgroundOpacity = backgroundOpacity,
            iconBgOpacity = iconBgOpacity
        )

        Spacer(modifier = Modifier.height(8.dp))

        PreviewItem(
            emoji = "✈️",
            title = "여행 출발",
            dday = "D-14",
            color = Color(0xFF1E88E5),
            backgroundEnabled = backgroundEnabled,
            backgroundOpacity = backgroundOpacity,
            iconBgOpacity = iconBgOpacity
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 기본값 복원 버튼
        OutlinedButton(
            onClick = {
                backgroundEnabled = true
                backgroundOpacity = 15  // 글래스모피즘 기본값
                iconBgOpacity = 20
                widgetBgOpacity = 20
                widgetFontSize = 1  // 보통
                DdaySettings.setBackgroundEnabled(context, true)
                DdaySettings.setBackgroundOpacity(context, 15)
                DdaySettings.setIconBgOpacity(context, 20)
                DdaySettings.setWidgetBgOpacity(context, 20)
                DdaySettings.setWidgetFontSize(context, 1)
                onSettingsChanged()
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("기본값으로 복원")
        }
    }

    // 시간 선택 다이얼로그
    if (showTimePicker) {
        TimePickerDialog(
            initialHour = notifyHour,
            initialMinute = notifyMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                notifyHour = hour
                notifyMinute = minute
                DdaySettings.setNotifyHour(context, hour)
                DdaySettings.setNotifyMinute(context, minute)
                NotificationScheduler.updateSchedule(context)
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun PreviewItem(
    emoji: String,
    title: String,
    dday: String,
    color: Color,
    backgroundEnabled: Boolean,
    backgroundOpacity: Int,
    iconBgOpacity: Int
) {
    val bgAlpha = if (backgroundEnabled) backgroundOpacity / 100f else 0f
    val iconAlpha = if (backgroundEnabled) iconBgOpacity / 100f else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = bgAlpha))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 이모지 아이콘
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = iconAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 22.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = dday,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    // 12시간제로 변환
    val initialIsAm = initialHour < 12
    val initialHour12 = when {
        initialHour == 0 -> 12
        initialHour > 12 -> initialHour - 12
        else -> initialHour
    }
    var isAm by remember { mutableStateOf(initialIsAm) }
    var selectedHour12 by remember { mutableStateOf(initialHour12) }
    var selectedMinute by remember { mutableStateOf(initialMinute) }

    // 직접 입력 다이얼로그 상태
    var showHourInput by remember { mutableStateOf(false) }
    var showMinuteInput by remember { mutableStateOf(false) }

    // 24시간제로 변환
    fun get24Hour(): Int {
        return when {
            isAm && selectedHour12 == 12 -> 0
            !isAm && selectedHour12 == 12 -> 12
            !isAm -> selectedHour12 + 12
            else -> selectedHour12
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "알림 시간",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTime(get24Hour(), selectedMinute),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 오전/오후 선택
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    AmPmButton(
                        text = "오전",
                        isSelected = isAm,
                        onClick = { isAm = true }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    AmPmButton(
                        text = "오후",
                        isSelected = !isAm,
                        onClick = { isAm = false }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 휠 피커
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 시간 휠
                    WheelPicker(
                        items = (1..12).map { "$it" },
                        selectedIndex = selectedHour12 - 1,
                        onSelectedChange = { selectedHour12 = it + 1 },
                        onCenterClick = { showHourInput = true },
                        modifier = Modifier.width(80.dp)
                    )

                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // 분 휠 (1분 단위)
                    WheelPicker(
                        items = (0..59).map { String.format("%02d", it) },
                        selectedIndex = selectedMinute,
                        onSelectedChange = { selectedMinute = it },
                        onCenterClick = { showMinuteInput = true },
                        modifier = Modifier.width(80.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "숫자를 탭하면 직접 입력할 수 있습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(get24Hour(), selectedMinute) }
            ) {
                Text("확인", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )

    // 시간 직접 입력 다이얼로그
    if (showHourInput) {
        NumberInputDialog(
            title = "시간 입력",
            currentValue = selectedHour12,
            minValue = 1,
            maxValue = 12,
            onDismiss = { showHourInput = false },
            onConfirm = { value ->
                selectedHour12 = value
                showHourInput = false
            }
        )
    }

    // 분 직접 입력 다이얼로그
    if (showMinuteInput) {
        NumberInputDialog(
            title = "분 입력",
            currentValue = selectedMinute,
            minValue = 0,
            maxValue = 59,
            onDismiss = { showMinuteInput = false },
            onConfirm = { value ->
                selectedMinute = value
                showMinuteInput = false
            }
        )
    }
}

@Composable
private fun AmPmButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    onCenterClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val itemHeight = 48.dp
    val visibleItems = 3
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }

    // 스냅 동작
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // 초기 스크롤 위치 설정
    LaunchedEffect(Unit) {
        listState.scrollToItem(selectedIndex)
    }

    // 외부에서 selectedIndex가 변경되면 스크롤
    LaunchedEffect(selectedIndex) {
        if (listState.firstVisibleItemIndex != selectedIndex) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    // 스크롤 완료 시 선택 항목 업데이트 및 정렬
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            // 가장 가까운 아이템 계산
            val offset = listState.firstVisibleItemScrollOffset
            val currentIndex = listState.firstVisibleItemIndex
            val snapToNext = offset > itemHeightPx / 2
            val targetIndex = if (snapToNext) currentIndex + 1 else currentIndex
            val newIndex = targetIndex.coerceIn(0, items.lastIndex)

            if (newIndex != selectedIndex) {
                onSelectedChange(newIndex)
            }

            // 정확히 가운데 정렬
            if (offset != 0) {
                listState.animateScrollToItem(newIndex)
            }
        }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleItems),
        contentAlignment = Alignment.Center
    ) {
        // 선택 영역 하이라이트
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = itemHeight),
            flingBehavior = snapFlingBehavior
        ) {
            items(items.size) { index ->
                val isCenter = index == selectedIndex
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .clickable {
                            if (isCenter) {
                                // 중앙 아이템 클릭 시 직접 입력
                                onCenterClick()
                            } else {
                                // 다른 아이템 클릭 시 해당 위치로 스크롤
                                scope.launch {
                                    listState.animateScrollToItem(index)
                                    onSelectedChange(index)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCenter)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberInputDialog(
    title: String,
    currentValue: Int,
    minValue: Int,
    maxValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = currentValue.toString(),
                selection = TextRange(0, currentValue.toString().length)
            )
        )
    }
    var isError by remember { mutableStateOf(false) }

    // 자동 포커스
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        // 숫자만 허용
                        val filtered = newValue.text.filter { it.isDigit() }
                        if (filtered.length <= 2) {
                            textFieldValue = newValue.copy(text = filtered)
                            isError = false
                        }
                    },
                    modifier = Modifier
                        .width(100.dp)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val value = textFieldValue.text.toIntOrNull()
                            if (value != null && value in minValue..maxValue) {
                                onConfirm(value)
                            } else {
                                isError = true
                            }
                        }
                    ),
                    isError = isError
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$minValue ~ $maxValue 사이 값을 입력하세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = textFieldValue.text.toIntOrNull()
                    if (value != null && value in minValue..maxValue) {
                        onConfirm(value)
                    } else {
                        isError = true
                    }
                }
            ) {
                Text("확인", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

private fun formatTime(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "오전" else "오후"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return if (minute == 0) {
        "$amPm ${displayHour}시"
    } else {
        "$amPm ${displayHour}시 ${minute}분"
    }
}
