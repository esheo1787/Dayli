package com.silverwest.dayli.ddaywidget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

/**
 * 시스템 이모지 키보드를 사용한 이모지 선택 다이얼로그.
 * 텍스트 입력 필드에서 시스템 키보드(이모지 탭)를 열어 선택.
 */
@Composable
fun EmojiPickerDialog(
    currentEmoji: String,
    categoryColor: Color,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedEmoji by remember { mutableStateOf(currentEmoji) }
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 제목
                Text(
                    text = "이모지 선택",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 미리보기
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(categoryColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedEmoji,
                        fontSize = 36.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 안내 텍스트
                Text(
                    text = "키보드에서 이모지를 선택하세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 이모지 입력 필드
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { newText ->
                        if (newText.isNotEmpty()) {
                            val emoji = extractLastGrapheme(newText)
                            if (emoji != null) {
                                selectedEmoji = emoji
                            }
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("이모지 입력...", fontSize = 14.sp) },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("취소")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onEmojiSelected(selectedEmoji)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = categoryColor)
                    ) {
                        Text("선택")
                    }
                }
            }
        }
    }

    // 자동 포커스로 키보드 표시
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
}

/**
 * 텍스트에서 마지막 그래핌 클러스터(이모지 단위) 추출
 */
private fun extractLastGrapheme(text: String): String? {
    if (text.isEmpty()) return null
    val iterator = java.text.BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val end = iterator.last()
    val start = iterator.previous()
    return if (start != java.text.BreakIterator.DONE) {
        text.substring(start, end)
    } else {
        text
    }
}
