package com.silverwest.dayli.ddaywidget

import android.app.DatePickerDialog
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
 * @param templates 템플릿 목록 (To-Do 전용)
 * @param onDismiss 닫기 콜백
 * @param onSave 저장 콜백
 * @param onSaveAsTemplate 템플릿으로 저장 콜백 (To-Do 전용)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBottomSheet(
    isVisible: Boolean,
    itemType: ItemType,
    editItem: DdayItem? = null,
    existingGroups: List<String> = emptyList(),
    templates: List<TodoTemplate> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (title: String, memo: String?, date: Date?, emoji: String, color: Long, repeatType: RepeatType, itemType: ItemType, subTasks: List<SubTask>, groupName: String?, repeatDay: Int?, advanceDisplayDays: Int?, templateId: Int?, timeHour: Int?, timeMinute: Int?, notificationRules: List<NotificationRule>) -> Unit,
    onSaveAsTemplate: ((name: String, iconName: String, customColor: Long, subTasks: List<SubTask>) -> Unit)? = null
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
        mutableStateOf(editItem?.getColorLong() ?: 0xFFA8C5DAL)  // Pastel Blue
    }
    var selectedRepeatType by remember(editItem) {
        mutableStateOf(editItem?.repeatTypeEnum() ?: RepeatType.NONE)
    }
    // 매주 요일 선택
    var selectedWeeklyDays by remember(editItem) {
        mutableStateOf(
            if (editItem?.repeatTypeEnum() == RepeatType.WEEKLY && editItem.repeatDay != null) {
                DdayItem.bitmaskToWeeklyDays(editItem.repeatDay)
            } else {
                emptySet<Int>()
            }
        )
    }

    // 그룹 상태 (D-Day 전용)
    var selectedGroupName by remember(editItem) {
        mutableStateOf(editItem?.groupName)
    }
    var showGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupEmoji by remember { mutableStateOf("📁") }
    var showGroupEmojiPicker by remember { mutableStateOf(false) }
    var groupDropdownExpanded by remember { mutableStateOf(false) }

    // 체크리스트 상태 (To-Do 전용)
    var subTasks by remember(editItem) {
        mutableStateOf(editItem?.getSubTaskList() ?: emptyList())
    }
    var newSubTaskText by remember { mutableStateOf("") }

    // 미리 표시 일수 상태
    var selectedAdvanceDays by remember(editItem) {
        mutableStateOf(editItem?.advanceDisplayDays)
    }
    var advanceDropdownExpanded by remember { mutableStateOf(false) }

    // 시간 상태 (D-Day 전용)
    var hasTime by remember(editItem) { mutableStateOf(editItem?.hasTime() == true) }
    var selectedHour by remember(editItem) { mutableStateOf(editItem?.timeHour ?: 12) }
    var selectedMinute by remember(editItem) { mutableStateOf(editItem?.timeMinute ?: 0) }

    // 개별 알림 상태 (D-Day 전용)
    var notificationRules by remember(editItem) {
        mutableStateOf(editItem?.getNotificationRules() ?: emptyList())
    }

    var showEmojiPicker by remember { mutableStateOf(false) }
    var showRepeatPicker by remember { mutableStateOf(false) }

    // 템플릿 관련 상태 (To-Do 전용)
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var showLoadTemplateDialog by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var selectedTemplateId by remember(editItem) { mutableStateOf<Int?>(editItem?.templateId) }

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

    // TimePicker
    val timePickerDialog = android.app.TimePickerDialog(
        context,
        { _, hour, minute ->
            selectedHour = hour
            selectedMinute = minute
            hasTime = true
        },
        selectedHour,
        selectedMinute,
        false  // 12시간 형식
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
                if (repeatType != RepeatType.WEEKLY) selectedWeeklyDays = emptySet()
                showRepeatPicker = false
            },
            onDismiss = { showRepeatPicker = false }
        )
    }

    // 새 그룹 입력 다이얼로그
    if (showGroupDialog) {
        AlertDialog(
            onDismissRequest = {
                showGroupDialog = false
                newGroupName = ""
                newGroupEmoji = "📁"
            },
            title = { Text("새 그룹") },
            text = {
                Column {
                    // 이모지 선택
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text("이모지: ", style = MaterialTheme.typography.bodyMedium)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { showGroupEmojiPicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(newGroupEmoji, fontSize = 22.sp)
                        }
                    }
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("그룹 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newGroupName.isNotBlank()) {
                            val trimmedName = newGroupName.trim()
                            DdaySettings.setGroupEmoji(context, trimmedName, newGroupEmoji)
                            selectedGroupName = trimmedName
                            newGroupName = ""
                            newGroupEmoji = "📁"
                            showGroupDialog = false
                        }
                    },
                    enabled = newGroupName.isNotBlank()
                ) {
                    Text("추가")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGroupDialog = false
                    newGroupName = ""
                    newGroupEmoji = "📁"
                }) {
                    Text("취소")
                }
            }
        )
    }

    // 그룹 이모지 피커
    if (showGroupEmojiPicker) {
        EmojiPickerDialog(
            currentEmoji = newGroupEmoji,
            categoryColor = MaterialTheme.colorScheme.primary,
            onEmojiSelected = { newGroupEmoji = it },
            onDismiss = { showGroupEmojiPicker = false }
        )
    }

    // 템플릿으로 저장 다이얼로그
    if (showSaveTemplateDialog && onSaveAsTemplate != null) {
        AlertDialog(
            onDismissRequest = {
                showSaveTemplateDialog = false
                templateName = ""
            },
            title = { Text("템플릿으로 저장") },
            text = {
                Column {
                    Text(
                        text = "현재 체크리스트를 템플릿으로 저장합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("템플릿 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (templateName.isNotBlank()) {
                            val validSubTasks = subTasks.filter { it.title.isNotBlank() }
                            onSaveAsTemplate(
                                templateName.trim(),
                                selectedEmoji,
                                selectedColor,
                                validSubTasks
                            )
                            showSaveTemplateDialog = false
                            templateName = ""
                        }
                    },
                    enabled = templateName.isNotBlank()
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveTemplateDialog = false
                    templateName = ""
                }) {
                    Text("취소")
                }
            }
        )
    }

    // 템플릿에서 불러오기 다이얼로그
    if (showLoadTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showLoadTemplateDialog = false },
            title = { Text("템플릿에서 불러오기") },
            text = {
                if (templates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "저장된 템플릿이 없습니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        templates.forEach { template ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // 템플릿에서 값 불러오기
                                        selectedEmoji = template.iconName
                                        selectedColor = template.customColor
                                        subTasks = template.getSubTaskList()
                                        selectedTemplateId = template.id
                                        showLoadTemplateDialog = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = template.customColor.toComposeColor().copy(alpha = 0.15f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = template.iconName,
                                        fontSize = 24.sp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = template.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        val subTaskCount = template.getSubTaskList().size
                                        if (subTaskCount > 0) {
                                            Text(
                                                text = "체크리스트 ${subTaskCount}개 항목",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoadTemplateDialog = false }) {
                    Text("닫기")
                }
            }
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
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 메모 입력
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // 체크리스트 섹션 (To-Do 전용)
                if (actualItemType == ItemType.TODO) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "체크리스트",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    // 기존 서브태스크 목록
                    subTasks.forEachIndexed { index, subTask ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = subTask.isChecked,
                                onCheckedChange = { checked ->
                                    subTasks = subTasks.toMutableList().apply {
                                        this[index] = subTask.copy(isChecked = checked)
                                    }
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            OutlinedTextField(
                                value = subTask.title,
                                onValueChange = { newTitle ->
                                    subTasks = subTasks.toMutableList().apply {
                                        this[index] = subTask.copy(title = newTitle)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    subTasks = subTasks.toMutableList().apply {
                                        removeAt(index)
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "삭제",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // 새 서브태스크 추가
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSubTaskText,
                            onValueChange = { newSubTaskText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("항목 추가...") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        FilledIconButton(
                            onClick = {
                                if (newSubTaskText.isNotBlank()) {
                                    subTasks = subTasks + SubTask(title = newSubTaskText.trim())
                                    newSubTaskText = ""
                                }
                            },
                            enabled = newSubTaskText.isNotBlank(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "추가",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 템플릿 버튼 영역
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 템플릿에서 불러오기
                        OutlinedButton(
                            onClick = { showLoadTemplateDialog = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("📋 템플릿 불러오기", style = MaterialTheme.typography.bodySmall)
                        }

                        // 템플릿으로 저장 (체크리스트가 있을 때만 활성화)
                        if (onSaveAsTemplate != null) {
                            OutlinedButton(
                                onClick = { showSaveTemplateDialog = true },
                                modifier = Modifier.weight(1f),
                                enabled = subTasks.any { it.title.isNotBlank() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("💾 템플릿 저장", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

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

                    // 시간 설정
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "시간",
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (hasTime) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val amPm = if (selectedHour < 12) "오전" else "오후"
                                val displayHour = when {
                                    selectedHour == 0 -> 12
                                    selectedHour > 12 -> selectedHour - 12
                                    else -> selectedHour
                                }
                                val timeText = if (selectedMinute == 0) "$amPm ${displayHour}시"
                                    else "$amPm ${displayHour}시 ${selectedMinute}분"
                                Text(
                                    text = timeText,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(onClick = { timePickerDialog.show() }) {
                                    Text("변경")
                                }
                                TextButton(onClick = {
                                    hasTime = false
                                    // 시간 제거 시 분/시간 단위 알림 제거
                                    notificationRules = notificationRules.filter { it.type == "days" }
                                }) {
                                    Text("삭제", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else {
                            TextButton(onClick = { timePickerDialog.show() }) {
                                Text("시간 추가")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 그룹 선택
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "그룹",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Box {
                            TextButton(onClick = { groupDropdownExpanded = true }) {
                                Text(selectedGroupName ?: "없음")
                            }
                            DropdownMenu(
                                expanded = groupDropdownExpanded,
                                onDismissRequest = { groupDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("없음") },
                                    onClick = {
                                        selectedGroupName = null
                                        groupDropdownExpanded = false
                                    }
                                )
                                existingGroups.forEach { group ->
                                    DropdownMenuItem(
                                        text = { Text(group) },
                                        onClick = {
                                            selectedGroupName = group
                                            groupDropdownExpanded = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("+ 새 그룹") },
                                    onClick = {
                                        groupDropdownExpanded = false
                                        showGroupDialog = true
                                    }
                                )
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

                // 매주 요일 선택
                if (selectedRepeatType == RepeatType.WEEKLY) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            Calendar.MONDAY to "월", Calendar.TUESDAY to "화",
                            Calendar.WEDNESDAY to "수", Calendar.THURSDAY to "목",
                            Calendar.FRIDAY to "금", Calendar.SATURDAY to "토",
                            Calendar.SUNDAY to "일"
                        ).forEach { (day, label) ->
                            FilterChip(
                                selected = day in selectedWeeklyDays,
                                onClick = {
                                    selectedWeeklyDays = if (day in selectedWeeklyDays) {
                                        selectedWeeklyDays - day
                                    } else {
                                        selectedWeeklyDays + day
                                    }
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 미리 표시 설정 (매주/매월/매년일 때만)
                if (selectedRepeatType in listOf(RepeatType.WEEKLY, RepeatType.MONTHLY, RepeatType.YEARLY)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "미리 표시",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Box {
                            val presets = when (selectedRepeatType) {
                                RepeatType.WEEKLY -> listOf(0 to "당일", 1 to "1일 전", 2 to "2일 전", 3 to "3일 전")
                                RepeatType.MONTHLY -> listOf(7 to "1주 전", 14 to "2주 전", 21 to "3주 전")
                                RepeatType.YEARLY -> listOf(14 to "2주 전", 30 to "1달 전", 60 to "2달 전")
                                else -> emptyList()
                            }
                            val defaultDays = when (selectedRepeatType) {
                                RepeatType.WEEKLY -> 2
                                RepeatType.MONTHLY -> 14
                                RepeatType.YEARLY -> 30
                                else -> 0
                            }
                            val currentDays = selectedAdvanceDays ?: defaultDays
                            val currentLabel = presets.find { it.first == currentDays }?.second ?: "${currentDays}일 전"
                            TextButton(onClick = { advanceDropdownExpanded = true }) {
                                Text(currentLabel)
                            }
                            DropdownMenu(
                                expanded = advanceDropdownExpanded,
                                onDismissRequest = { advanceDropdownExpanded = false }
                            ) {
                                presets.forEach { (days, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            selectedAdvanceDays = days
                                            advanceDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 개별 알림 설정 (D-Day 전용)
                if (actualItemType == ItemType.DDAY) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "개별 알림",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 기존 알림 규칙 표시
                    notificationRules.forEachIndexed { index, rule ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🔔 ${rule.displayText()}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(
                                onClick = {
                                    notificationRules = notificationRules.toMutableList().apply { removeAt(index) }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "삭제",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 알림 추가 버튼
                    var showNotifDropdown by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { showNotifDropdown = true }) {
                            Text("+ 알림 추가")
                        }
                        DropdownMenu(
                            expanded = showNotifDropdown,
                            onDismissRequest = { showNotifDropdown = false }
                        ) {
                            if (hasTime) {
                                listOf(
                                    NotificationRule("minutes", 10),
                                    NotificationRule("minutes", 30),
                                    NotificationRule("hours", 1),
                                    NotificationRule("hours", 2)
                                ).forEach { rule ->
                                    DropdownMenuItem(
                                        text = { Text(rule.displayText()) },
                                        onClick = {
                                            notificationRules = notificationRules + rule
                                            showNotifDropdown = false
                                        }
                                    )
                                }
                            } else {
                                listOf(
                                    NotificationRule("days", 1),
                                    NotificationRule("days", 3),
                                    NotificationRule("days", 7),
                                    NotificationRule("days", 14)
                                ).forEach { rule ->
                                    DropdownMenuItem(
                                        text = { Text(rule.displayText()) },
                                        onClick = {
                                            notificationRules = notificationRules + rule
                                            showNotifDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 저장 버튼
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            // 빈 제목의 서브태스크 제거
                            val validSubTasks = subTasks.filter { it.title.isNotBlank() }
                            Log.d("DDAY_WIDGET", "✅ 저장: title=$title, type=$actualItemType, repeat=$selectedRepeatType, subTasks=${validSubTasks.size}")
                            val repeatDayValue = if (selectedRepeatType == RepeatType.WEEKLY && selectedWeeklyDays.isNotEmpty()) {
                                DdayItem.weeklyDaysToBitmask(selectedWeeklyDays)
                            } else null
                            val advanceDaysValue = if (selectedRepeatType in listOf(RepeatType.WEEKLY, RepeatType.MONTHLY, RepeatType.YEARLY)) {
                                selectedAdvanceDays
                            } else null
                            onSave(
                                title,
                                memo.ifBlank { null },
                                if (actualItemType == ItemType.DDAY) selectedDate else null,
                                selectedEmoji,
                                selectedColor,
                                selectedRepeatType,
                                actualItemType,
                                validSubTasks,
                                if (actualItemType == ItemType.DDAY) selectedGroupName else null,
                                repeatDayValue,
                                advanceDaysValue,
                                if (actualItemType == ItemType.TODO) selectedTemplateId else null,
                                if (actualItemType == ItemType.DDAY && hasTime) selectedHour else null,
                                if (actualItemType == ItemType.DDAY && hasTime) selectedMinute else null,
                                if (actualItemType == ItemType.DDAY) notificationRules else emptyList()
                            )
                            // 입력 초기화
                            title = ""
                            memo = ""
                            selectedDate = Date()
                            selectedEmoji = if (actualItemType == ItemType.TODO) "✅" else "📌"
                            selectedColor = 0xFFA8C5DAL  // Pastel Blue
                            selectedRepeatType = RepeatType.NONE
                            selectedWeeklyDays = emptySet()
                            selectedAdvanceDays = null
                            subTasks = emptyList()
                            newSubTaskText = ""
                            selectedGroupName = null
                            selectedTemplateId = null
                            hasTime = false
                            selectedHour = 12
                            selectedMinute = 0
                            notificationRules = emptyList()
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
