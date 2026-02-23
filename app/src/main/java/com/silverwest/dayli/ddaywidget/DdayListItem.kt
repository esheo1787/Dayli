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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
    forceCheckbox: Boolean = false,
    infoText: String? = null,
    searchQuery: String = ""
) {
    val context = LocalContext.current
    val formattedDate = item.date?.let { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(it) }
    val ddayLabel = item.date?.let { calculateDday(it) }
    val timeLabel = item.getTimeString()
    val ddayDiff = item.date?.let { calculateDdayDiff(it) }

    // 체크리스트 상태
    val subTasks = item.getSubTaskList()
    val hasSubTasks = subTasks.isNotEmpty()
    val completedCount = subTasks.count { it.isChecked }
    val totalCount = subTasks.size

    // 메모 펼치기 상태
    var memoExpanded by remember { mutableStateOf(false) }
    var memoOverflows by remember { mutableStateOf(false) }

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
    // 배경 밝기에 따른 텍스트 색 자동 조절 (밝은 배경 → 진한 텍스트, 어두운 배경 → 흰색)
    val isLightBg = (0.2126f * itemColor.red + 0.7152f * itemColor.green + 0.0722f * itemColor.blue) > 0.5f
    val primaryTextColor = if (item.isChecked) {
        Color.Gray
    } else if (backgroundEnabled && bgOpacity > 0.3f) {
        if (isLightBg) Color(0xFF1A1A1A) else Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryTextColor = if (item.isChecked) {
        Color.Gray
    } else if (backgroundEnabled && bgOpacity > 0.3f) {
        if (isLightBg) Color(0xFF555555) else Color(0xFFCCCCCC)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

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
            // 첫째 줄: [제목 + 태그] ... [D-Day] 양쪽 끝 정렬
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 왼쪽: 제목 + 태그 (남은 공간 차지)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HighlightedText(
                        text = item.title,
                        highlight = searchQuery,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = (16 * fontScale).sp,
                        textDecoration = textDecoration,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (hasSubTasks) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$completedCount/$totalCount",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = (12 * fontScale).sp,
                            color = secondaryTextColor
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "접기" else "펼치기",
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onExpandToggle() },
                            tint = secondaryTextColor
                        )
                    }
                    if (item.isRepeating()) {
                        val tagText = item.getRepeatTagText()
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
                                Text(text = text, fontSize = (12 * fontScale).sp, color = primaryTextColor)
                            }
                        }
                    }
                    if (item.getNotificationRules().isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("🔔", fontSize = (10 * fontScale).sp)
                    }
                }
                // 오른쪽: D-Day (항상 우측 끝)
                ddayLabel?.let { text ->
                    val ddayColor = if (item.isChecked) {
                        Color.Gray
                    } else {
                        when {
                            ddayDiff == 2 || ddayDiff == 3 -> Color(0xFF2F6BFF)
                            ddayDiff != null && ddayDiff <= 1 -> Color(0xFFE53935)
                            else -> primaryTextColor
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = (16 * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = textDecoration,
                        color = ddayColor
                    )
                }
            }

            // 둘째 줄: [날짜 + 메모] ... [시간] 양쪽 끝 정렬
            if (formattedDate != null || !item.memo.isNullOrBlank() || timeLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // 왼쪽: 날짜 + 메모
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        formattedDate?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = (12 * fontScale).sp,
                                color = secondaryTextColor,
                                textDecoration = textDecoration
                            )
                        }
                        if (!item.memo.isNullOrBlank()) {
                            if (formattedDate != null) Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "[메모: ${item.memo}]",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = (12 * fontScale).sp,
                                color = secondaryTextColor,
                                textDecoration = textDecoration,
                                maxLines = if (memoExpanded) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                                onTextLayout = { result ->
                                    if (!memoExpanded) {
                                        memoOverflows = result.hasVisualOverflow
                                    }
                                }
                            )
                            if (memoOverflows || memoExpanded) {
                                Text(
                                    text = if (memoExpanded) "▲" else "▼",
                                    fontSize = (10 * fontScale).sp,
                                    color = secondaryTextColor,
                                    modifier = Modifier
                                        .padding(start = 2.dp)
                                        .clickable { memoExpanded = !memoExpanded }
                                )
                            }
                        }
                    }
                    // 오른쪽: 시간 (항상 우측 끝)
                    timeLabel?.let {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = (12 * fontScale).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textDecoration = textDecoration
                        )
                    }
                }
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

        // 체크박스
        if (showCheckbox && (forceCheckbox || !hasSubTasks)) {
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
                        HighlightedText(
                            text = subTask.title,
                            highlight = searchQuery,
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

@Composable
fun HighlightedText(
    text: String,
    highlight: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    color: Color = Color.Unspecified,
    textDecoration: TextDecoration? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    if (highlight.isBlank()) {
        Text(text, modifier, color, fontSize, textDecoration = textDecoration, style = style, maxLines = maxLines, overflow = overflow)
        return
    }

    val highlightColor = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        val lowerText = text.lowercase()
        val lowerQuery = highlight.lowercase()
        var start = 0
        while (start < text.length) {
            val index = lowerText.indexOf(lowerQuery, start)
            if (index == -1) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, index))
            withStyle(SpanStyle(background = highlightColor.copy(alpha = 0.35f))) {
                append(text.substring(index, index + highlight.length))
            }
            start = index + highlight.length
        }
    }

    Text(annotated, modifier, color, fontSize, textDecoration = textDecoration, style = style, maxLines = maxLines, overflow = overflow)
}
