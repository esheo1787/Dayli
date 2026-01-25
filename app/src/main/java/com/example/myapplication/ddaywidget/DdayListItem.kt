package com.example.myapplication.ddaywidget

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    onLongPress: (DdayItem) -> Unit = {}
) {
    val context = LocalContext.current
    val formattedDate = item.date?.let { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(it) }
    val ddayText = item.date?.let { calculateDday(it) }

    // 커스텀 색상 또는 카테고리 기본 색상 사용
    val itemColor = item.getColorLong().toComposeColor()

    // 설정에서 배경 투명도 가져오기
    val backgroundEnabled = DdaySettings.isBackgroundEnabled(context)
    val bgOpacity = DdaySettings.getBackgroundOpacity(context) / 100f
    val iconBgOpacity = DdaySettings.getIconBgOpacity(context) / 100f

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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = { },
                onLongClick = { onLongPress(item) }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 이모지 아이콘
        val itemEmoji = item.getEmoji()
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = itemEmoji,
                fontSize = 22.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 내용
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = textDecoration,
                    color = primaryTextColor,
                    modifier = Modifier.weight(1f, fill = false)
                )
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
                                fontSize = 10.sp,
                                color = secondaryTextColor
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
                        color = secondaryTextColor,
                        textDecoration = textDecoration
                    )
                }
            }

            if (!item.memo.isNullOrBlank()) {
                Text(
                    text = item.memo,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                    textDecoration = textDecoration,
                    color = secondaryTextColor
                )
            }
        }

        // D-Day + 체크박스 (D-Day) 또는 체크박스만 (To-Do)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // D-Day 텍스트는 D-Day 아이템일 때만 표시
            ddayText?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 4.dp),
                    textDecoration = textDecoration,
                    color = primaryTextColor
                )
            }
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = { onToggle(item) },
                colors = CheckboxDefaults.colors(
                    checkedColor = itemColor,
                    uncheckedColor = itemColor.copy(alpha = 0.6f)
                )
            )
        }
    }
}

fun calculateDday(date: Date): String {
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

    val diff = ((targetDate.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
    return when {
        diff > 0 -> "D-$diff"
        diff == 0 -> "D-DAY"
        else -> "D+${-diff}"
    }
}
