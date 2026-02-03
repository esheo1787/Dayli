package com.silverwest.dayli.ddaywidget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DdayListItem(
    item: DdayItem,
    onToggle: (DdayItem) -> Unit,
    onLongPress: (DdayItem) -> Unit = {},
    onSubTaskToggle: (DdayItem, Int) -> Unit = { _, _ -> },
    isExpanded: Boolean = false,
    onExpandToggle: () -> Unit = {},
    showCheckbox: Boolean = true,
    infoText: String? = null
) {
    val context = LocalContext.current
    val formattedDate = item.date?.let { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(it) }
    val ddayText = item.date?.let { calculateDday(it) }
    val ddayDiff = item.date?.let { calculateDdayDiff(it) }

    // 체크리스트 상태
    val subTasks = item.getSubTaskList()
    val hasSubTasks = subTasks.isNotEmpty()
    val completedCount = subTasks.count { it.isChecked }
    val totalCount = subTasks.size

    // 커스텀 색상 또는 카테고리 기본 색상 사용
    val itemColor = item.getColorLong().toComposeColor()

    // 설정에서 배경 투명도 가져오기
    val backgroundEnabled = DdaySettings.isBackgroundEnabled(context)
    val bgOpacity = DdaySettings.getBackgroundOpacity(context) / 100f
    val iconBgOpacity = DdaySettings.getIconBgOpacity(context) / 100f

    // 앱 글씨 크기 배율
    val fontScale = DdaySettings.getAppFontScale(context)

    // 체크 시 스타일
    val textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
    // 다크모드 대응: 체크되면 회색, 아니면 기본 텍스트 색상 (다크모드에서 자동으로 흰색)
    val primaryTextColor = if (item.isChecked) Color.Gray else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (item.isChecked) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant

    // 배경색 (설정에 따라 적용)
    val backgroundColor = if (backgroundEnabled) {
        val alpha = if (item.isChecked) bgOpacity * 0.5f else bgOpacity
        itemColor.copy(alpha = alpha)
    } else {
        Color.Transparent
    }

    // 아이콘 배경색
    val iconBgColor = if (backgroundEnabled) {
        val alpha = if (item.isChecked) iconBgOpacity * 0.6f else iconBgOpacity
        itemColor.copy(alpha = alpha)
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (hasSubTasks) onExpandToggle() },
                    onLongClick = { onLongPress(item) }
                )
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        // 이모지 아이콘
        val itemEmoji = item.getEmoji()
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = itemEmoji,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 내용
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = (16 * fontScale).sp,
                    textDecoration = textDecoration,
                    color = primaryTextColor,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // 체크리스트 진행률 표시 (To-Do만)
                if (hasSubTasks) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$completedCount/$totalCount",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = (12 * fontScale).sp,
                        color = secondaryTextColor
                    )
                    // 펼치기/접기 버튼
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "접기" else "펼치기",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onExpandToggle() },
                        tint = secondaryTextColor
                    )
                }
                // 반복 태그 표시 (D-Day와 To-Do 모두)
                if (item.isRepeating()) {
                    val tagText = if (item.isDday()) {
                        item.getRepeatTagText()
                    } else {
                        // To-Do는 간단한 반복 태그
                        when (item.repeatTypeEnum()) {
                            RepeatType.DAILY -> "🔁매일"
                            RepeatType.WEEKLY -> "🔁매주"
                            RepeatType.MONTHLY -> "🔁매월"
                            else -> null
                        }
                    }
                    tagText?.let { text ->
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (item.isChecked) Color.Gray.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = text,
                                fontSize = (12 * fontScale).sp,
                                color = primaryTextColor
                            )
                        }
                    }
                }
                // 날짜 표시 (D-Day만)
                formattedDate?.let { dateStr ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = (12 * fontScale).sp,
                        color = secondaryTextColor,
                        textDecoration = textDecoration
                    )
                }
            }

            if (!item.memo.isNullOrBlank()) {
                Text(
                    text = item.memo,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = (12 * fontScale).sp,
                    modifier = Modifier.padding(top = 2.dp),
                    textDecoration = textDecoration,
                    color = secondaryTextColor
                )
            }
            infoText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = (11 * fontScale).sp,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // D-Day + 체크박스 (D-Day) 또는 체크박스만 (To-Do)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
            // D-Day 텍스트는 D-Day 아이템일 때만 표시
            ddayText?.let { text ->
                // D-Day 숫자 색상: D-3~D-2 파란색, D-1/D-Day/D+N 빨간색, D-4 이상 기본색
                val ddayColor = if (item.isChecked) {
                    Color.Gray
                } else {
                    when {
                        ddayDiff == 2 || ddayDiff == 3 -> Color(0xFF2F6BFF)  // 파란색
                        ddayDiff != null && ddayDiff <= 1 -> Color(0xFFE53935)  // 빨간색
                        else -> MaterialTheme.colorScheme.onSurface  // 기본색 (D-4 이상)
                    }
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = (16 * fontScale).sp,
                    modifier = Modifier.padding(end = 4.dp),
                    textDecoration = textDecoration,
                    color = ddayColor
                )
            }
            // 하위 체크리스트가 있는 To-Do는 상위 체크박스 숨김 (자동 완료로 처리)
            if (!hasSubTasks && showCheckbox) {
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = { onToggle(item) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = itemColor,
                        uncheckedColor = Color(0xFF888888)
                    )
                )
            }
        }
        }

        // 체크리스트 펼치기 (To-Do 전용)
        AnimatedVisibility(visible = isExpanded && hasSubTasks) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 50.dp, end = 10.dp, bottom = 4.dp)
            ) {
                subTasks.forEachIndexed { index, subTask ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSubTaskToggle(item, index) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = subTask.isChecked,
                            onCheckedChange = { onSubTaskToggle(item, index) },
                            modifier = Modifier.size(20.dp),
                            colors = CheckboxDefaults.colors(
                                checkedColor = itemColor,
                                uncheckedColor = Color(0xFF888888)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = subTask.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = (14 * fontScale).sp,
                            textDecoration = if (subTask.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (subTask.isChecked) Color.Gray else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

fun calculateDday(date: Date): String {
    val diff = calculateDdayDiff(date)
    return when {
        diff > 0 -> "D-$diff"
        diff == 0 -> "D-DAY"
        else -> "D+${-diff}"
    }
}

fun calculateDdayDiff(date: Date): Int {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    val targetDate = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    return ((targetDate.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
}
