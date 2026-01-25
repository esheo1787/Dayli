package com.example.myapplication.ddaywidget

import android.app.DatePickerDialog
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DdayInputScreen(viewModel: DdayViewModel) {
    val context = LocalContext.current

    var title by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(Date()) }
    var selectedEmoji by remember { mutableStateOf("📌") }  // 기본 이모지
    var selectedColor by remember { mutableStateOf(0xFF757575L) }  // 기본 색상 (회색)
    var showEmojiPicker by remember { mutableStateOf(false) }
    var selectedRepeatType by remember { mutableStateOf(RepeatType.NONE) }
    var showRepeatPicker by remember { mutableStateOf(false) }

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

    Column(modifier = Modifier.padding(16.dp)) {
        // 미리보기 + 이모지 선택
        Text(
            text = "아이콘",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 현재 선택된 이모지 + 색상 미리보기
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

        // 색상 선택
        ColorPalette(
            selectedColor = selectedColor,
            onColorSelected = { selectedColor = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("제목") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it },
            label = { Text("메모") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "날짜: ${SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(selectedDate)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = { datePickerDialog.show() }) {
                Text("변경")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 반복 설정
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedRepeatType == RepeatType.NONE) "반복: 없음" else "반복: ${selectedRepeatType.displayName}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = { showRepeatPicker = true }) {
                Text("설정")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (title.isNotBlank()) {
                    Log.d("DDAY_WIDGET", "✅ 저장 버튼 눌림: title=$title, memo=$memo, emoji=$selectedEmoji, color=$selectedColor, repeat=$selectedRepeatType")

                    viewModel.insertDday(title, memo, selectedDate, selectedEmoji, selectedColor, selectedRepeatType)

                    // 입력 초기화
                    title = ""
                    memo = ""
                    selectedDate = Date()
                    selectedEmoji = "📌"
                    selectedColor = 0xFF757575L
                    selectedRepeatType = RepeatType.NONE
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("저장")
        }
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
}

@Composable
fun RepeatPickerDialog(
    currentType: RepeatType,
    onRepeatSelected: (RepeatType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("반복 설정") },
        text = {
            Column {
                RepeatType.entries.forEach { repeatType ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRepeatSelected(repeatType) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentType == repeatType,
                            onClick = { onRepeatSelected(repeatType) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = repeatType.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

