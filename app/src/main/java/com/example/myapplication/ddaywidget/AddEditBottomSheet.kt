package com.example.myapplication.ddaywidget

import android.app.DatePickerDialog
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * 통합 바텀시트: 추가/수정 모두 지원
 * @param isVisible 바텀시트 표시 여부
 * @param itemType 추가할 아이템 타입 (DDAY / TODO)
 * @param editItem 수정할 아이템 (null이면 추가 모드)
 * @param onDismiss 닫기 콜백
 * @param onSave 저장 콜백
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBottomSheet(
    isVisible: Boolean,
    itemType: ItemType,
    editItem: DdayItem? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, memo: String?, date: Date?, emoji: String, color: Long, repeatType: RepeatType, itemType: ItemType) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 수정 모드 여부
    val isEditMode = editItem != null
    val actualItemType = editItem?.let {
        if (it.isTodo()) ItemType.TODO else ItemType.DDAY
    } ?: itemType

    // 입력 상태
    var title by remember(editItem) { mutableStateOf(editItem?.title ?: "") }
    var memo by remember(editItem) { mutableStateOf(editItem?.memo ?: "") }
    var selectedDate by remember(editItem) { mutableStateOf(editItem?.date ?: Date()) }
    var selectedEmoji by remember(editItem) {
        mutableStateOf(editItem?.getEmoji() ?: if (actualItemType == ItemType.TODO) "✅" else "📌")
    }
    var selectedColor by remember(editItem) {
        mutableStateOf(editItem?.getColorLong() ?: 0xFF757575L)
    }
    var selectedRepeatType by remember(editItem) {
        mutableStateOf(editItem?.repeatTypeEnum() ?: RepeatType.NONE)
    }

    var showEmojiPicker by remember { mutableStateOf(false) }
    var showRepeatPicker by remember { mutableStateOf(false) }

    // DatePicker
    val calendar = Calendar.getInstance().apply { time = selectedDate }
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            calendar.set(year, month, day)
            selectedDate = calendar.time
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // 이모지 선택 다이얼로그
    if (showEmojiPicker) {
        EmojiPickerDialog(
            currentEmoji = selectedEmoji,
            categoryColor = selectedColor.toComposeColor(),
            onEmojiSelected = { emoji ->
                selectedEmoji = emoji
            },
            onDismiss = { showEmojiPicker = false }
        )
    }

    // 반복 선택 다이얼로그
    if (showRepeatPicker) {
        RepeatPickerDialog(
            currentType = selectedRepeatType,
            onRepeatSelected = { repeatType ->
                selectedRepeatType = repeatType
                showRepeatPicker = false
            },
            onDismiss = { showRepeatPicker = false }
        )
    }

    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 제목
                Text(
                    text = if (isEditMode) {
                        if (actualItemType == ItemType.TODO) "To-Do 수정" else "D-Day 수정"
                    } else {
                        if (actualItemType == ItemType.TODO) "새 To-Do" else "새 D-Day"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // 아이콘 + 색상 섹션
                Text(
                    text = "아이콘",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 이모지 미리보기
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(selectedColor.toComposeColor().copy(alpha = 0.2f))
                            .clickable { showEmojiPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedEmoji,
                            fontSize = 28.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    TextButton(onClick = { showEmojiPicker = true }) {
                        Text("이모지 변경")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 색상 팔레트
                ColorPalette(
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 제목 입력
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (actualItemType == ItemType.TODO) "할 일" else "제목") },
                    placeholder = { Text(if (actualItemType == ItemType.TODO) "할 일을 입력하세요" else "제목을 입력하세요") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 메모 입력
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // D-Day일 때만 날짜 선택 표시
                if (actualItemType == ItemType.DDAY) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "날짜",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = SimpleDateFormat("yyyy.MM.dd (E)", Locale.getDefault()).format(selectedDate),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = { datePickerDialog.show() }) {
                                Text("변경")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 반복 설정 (D-Day와 To-Do 모두 지원)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "반복",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (selectedRepeatType == RepeatType.NONE) "없음" else selectedRepeatType.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { showRepeatPicker = true }) {
                            Text("설정")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 저장 버튼
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            Log.d("DDAY_WIDGET", "✅ 저장: title=$title, type=$actualItemType, repeat=$selectedRepeatType")
                            onSave(
                                title,
                                memo.ifBlank { null },
                                if (actualItemType == ItemType.DDAY) selectedDate else null,
                                selectedEmoji,
                                selectedColor,
                                selectedRepeatType,
                                actualItemType
                            )
                            // 입력 초기화
                            title = ""
                            memo = ""
                            selectedDate = Date()
                            selectedEmoji = if (actualItemType == ItemType.TODO) "✅" else "📌"
                            selectedColor = 0xFF757575L
                            selectedRepeatType = RepeatType.NONE
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = title.isNotBlank()
                ) {
                    Text(
                        text = if (isEditMode) "저장" else "추가",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
