package com.silverwest.dayli.ddaywidget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.emoji2.emojipicker.EmojiPickerView

/**
 * androidx.emoji2 EmojiPickerView를 사용한 전체 이모지 피커 다이얼로그.
 * 카테고리별 탐색 가능 (스마일, 동물, 음식, 여행, 물건 등).
 */
@Composable
fun EmojiPickerDialog(
    currentEmoji: String,
    categoryColor: Color,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedEmoji by remember { mutableStateOf(currentEmoji) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 미리보기
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(categoryColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedEmoji,
                        fontSize = 28.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // EmojiPickerView (AndroidView)
                AndroidView(
                    factory = { context ->
                        EmojiPickerView(context).apply {
                            setOnEmojiPickedListener { emojiViewItem ->
                                selectedEmoji = emojiViewItem.emoji
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

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
}
