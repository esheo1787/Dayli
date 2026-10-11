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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val itemColor = item.getColorLong().toComposeColor()

    // 항목 배경 방식: 켜짐 = 카드 전체에 항목 색 / 꺼짐 = 흰 카드 + 이모지 칸에만 항목 색
    val tinted = DdaySettings.isBackgroundEnabled(context)
    val bgOpacity = DdaySettings.getBackgroundOpacity(context) / 100f
    val iconBgOpacity = DdaySettings.getIconBgOpacity(context) / 100f
    val fontScale = DdaySettings.getAppFontScale(context)

    val surface = MaterialTheme.colorScheme.surface
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // 위젯·설정 미리보기와 같은 계산
    val colors = DayliUi.cardColors(itemColor, surface, dark, tinted, bgOpacity, iconBgOpacity, item.isChecked)
    val cardBg = colors.card
    val tileBg = colors.tile
    val lightCard = colors.lightCard
    val primaryTextColor = colors.text
    val secondaryTextColor = colors.subText
    val textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None

    val meta = buildList {
        item.date?.let { add(DayliUi.koreanDate(it)) }
        timeLabel?.let { add(it) }
        if (item.isRepeating()) item.getRepeatTagText()?.let { add(it.trim('[', ']')) }
    }.joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (tinted) 0.dp else 1.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (hasSubTasks) onExpandToggle() },
                    onLongClick = { onLongPress(item) }
                )
                .heightIn(min = 64.dp)
                .padding(start = 12.dp, end = if (showCheckbox) 2.dp else 12.dp, top = 8.dp, bottom = 8.dp)
                .alpha(if (item.isChecked) 0.6f else 1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 이모지 칸
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(tileBg),
                contentAlignment = Alignment.Center
            ) {
                EmojiText(item.getEmoji(), fontSize = 21.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 내용
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HighlightedText(
                        text = item.title,
                        highlight = searchQuery,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = (15.5f * fontScale).sp,
                        fontWeight = FontWeight.SemiBold,
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
                            fontSize = (12.5f * fontScale).sp,
                            color = secondaryTextColor
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "접기" else "펼치기",
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { onExpandToggle() },
                            tint = secondaryTextColor
                        )
                    }
                    if (item.getNotificationRules().isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("🔔", fontSize = (10 * fontScale).sp)
                    }
                }

                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        fontSize = (12.5f * fontScale).sp,
                        color = secondaryTextColor,
                        textDecoration = textDecoration,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (!item.memo.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            text = item.memo,
                            fontSize = (12 * fontScale).sp,
                            color = secondaryTextColor,
                            maxLines = if (memoExpanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            onTextLayout = { result ->
                                if (!memoExpanded) memoOverflows = result.hasVisualOverflow
                            }
                        )
                        if (memoOverflows || memoExpanded) {
                            Text(
                                text = if (memoExpanded) "▲" else "▼",
                                fontSize = (10 * fontScale).sp,
                                color = secondaryTextColor,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .clickable { memoExpanded = !memoExpanded }
                            )
                        }
                    }
                }

                if (hasSubTasks) {
                    LinearProgressIndicator(
                        progress = { if (totalCount == 0) 0f else completedCount.toFloat() / totalCount },
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = DayliUi.deepen(itemColor),
                        trackColor = if (lightCard) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.12f),
                        drawStopIndicator = {}
                    )
                }

                infoText?.let {
                    Text(
                        text = it,
                        fontSize = (11 * fontScale).sp,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            ddayLabel?.let { text ->
                Spacer(modifier = Modifier.width(8.dp))
                DdayPill(label = text, diff = ddayDiff, checked = item.isChecked, onTinted = tinted, fontScale = fontScale, card = colors.card)
            }

            if (showCheckbox && (forceCheckbox || !hasSubTasks)) {
                DayliCheckbox(checked = item.isChecked, color = itemColor, onClick = { onToggle(item) })
            }
        }

        // 체크리스트 펼치기 (To-Do 전용)
        AnimatedVisibility(visible = isExpanded && hasSubTasks) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 54.dp, end = 10.dp, bottom = 6.dp)
            ) {
                subTasks.forEachIndexed { index, subTask ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSubTaskToggle(item, index) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DayliCheckbox(
                            checked = subTask.isChecked,
                            color = itemColor,
                            onClick = { onSubTaskToggle(item, index) },
                            size = 20
                        )
                        HighlightedText(
                            text = subTask.title,
                            highlight = searchQuery,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = (14 * fontScale).sp,
                            textDecoration = if (subTask.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (subTask.isChecked) secondaryTextColor else primaryTextColor
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
    overflow: TextOverflow = TextOverflow.Clip,
    fontWeight: FontWeight? = null
) {
    if (highlight.isBlank()) {
        Text(text, modifier, color, fontSize, fontWeight = fontWeight, textDecoration = textDecoration, style = style, maxLines = maxLines, overflow = overflow)
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

    Text(annotated, modifier, color, fontSize, fontWeight = fontWeight, textDecoration = textDecoration, style = style, maxLines = maxLines, overflow = overflow)
}
