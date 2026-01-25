package com.example.myapplication.ddaywidget

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TodoInputScreen(viewModel: DdayViewModel) {
    var title by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("✅") }  // To-Do 기본 이모지
    var selectedColor by remember { mutableStateOf(0xFFA8C5DAL) }  // 기본 색상 (Pastel Blue)
    var showEmojiPicker by remember { mutableStateOf(false) }

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
            label = { Text("할 일") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("할 일을 입력하세요") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it },
            label = { Text("메모 (선택)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (title.isNotBlank()) {
                    Log.d("DDAY_WIDGET", "✅ To-Do 저장: title=$title, memo=$memo, emoji=$selectedEmoji, color=$selectedColor")

                    viewModel.insertTodo(title, memo.ifBlank { null }, selectedEmoji, selectedColor)

                    // 입력 초기화
                    title = ""
                    memo = ""
                    selectedEmoji = "✅"
                    selectedColor = 0xFFA8C5DAL
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("저장")
        }
    }
}
