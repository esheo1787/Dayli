package com.silverwest.dayli.ddaywidget

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
 * @param onCreateGroup 새 그룹 만들기 콜백 (D-Day가 없어도 그룹 목록에 남김)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBottomSheet(
    isVisible: Boolean,
    itemType: ItemType,
    editItem: DdayItem? = null,
    initialDate: Date? = null,  // 캘린더의 "이 날짜에 D-Day 추가"에서 날짜 미리 채우기
    existingGroups: List<String> = emptyList(),
    templates: List<TodoTemplate> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (title: String, memo: String?, date: Date?, emoji: String, color: Long, repeatType: RepeatType, itemType: ItemType, subTasks: List<SubTask>, groupName: String?, repeatDay: Int?, advanceDisplayDays: Int?, templateId: Int?, timeHour: Int?, timeMinute: Int?, notificationRules: List<NotificationRule>) -> Unit,
    onSaveAsTemplate: ((name: String, iconName: String, customColor: Long, subTasks: List<SubTask>) -> Unit)? = null,
    onCreateGroup: ((name: String, emoji: String) -> Unit)? = null
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
    var selectedDate by remember(editItem, initialDate) { mutableStateOf(editItem?.date ?: initialDate ?: Date()) }
    // 추가 모드: 시트가 열릴 때마다 날짜를 다시 채움 (저장 후 Date()로 되돌린 뒤 같은 날짜로 다시 열어도 반영)
    LaunchedEffect(isVisible) {
        if (isVisible && editItem == null) selectedDate = initialDate ?: Date()
    }
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
    var showExactAlarmPrompt by remember { mutableStateOf(false) }

    // 제목 기반 이모지 추천 (오프라인 키워드 매칭)
    val defaultEmoji = if (actualItemType == ItemType.TODO) "✅" else "📌"
    // 시트를 열 때마다 확인 (앱 시작 직후 EmojiCompat 로딩 중에 만든 임시 목록을 갱신)
    val emojiCatalog by produceState<List<EmojiCatalog.Category>>(initialValue = emptyList(), isVisible) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { EmojiCatalog.load(context) }
    }
    val emojiSuggestions = remember(title, emojiCatalog) {
        if (emojiCatalog.isEmpty()) emptyList() else EmojiCatalog.suggest(emojiCatalog, title)
    }
    // 사용자가 이모지를 직접 정했는지 (피커·추천 칩·템플릿·AI 결과). 값 비교가 아니라 별도 기록 —
    // 직접 고른 이모지가 기본값(📌/✅)과 같아도 자동 추천이 덮어쓰지 않게
    var emojiManuallyChosen by remember(editItem) { mutableStateOf(false) }
    LaunchedEffect(emojiSuggestions) {
        if (isEditMode || emojiManuallyChosen) return@LaunchedEffect
        selectedEmoji = emojiSuggestions.firstOrNull() ?: defaultEmoji
    }
    var showRepeatPicker by remember { mutableStateOf(false) }

    // 템플릿 관련 상태 (To-Do 전용)
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var showLoadTemplateDialog by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var selectedTemplateId by remember(editItem) { mutableStateOf<Int?>(editItem?.templateId) }

    // 자동 입력 (OCR + Gemini) 상태
    var showAutoInputDialog by remember { mutableStateOf(false) }
    var showAiConsentDialog by remember { mutableStateOf(false) }
    var autoInputText by remember { mutableStateOf("") }
    var isParsingText by remember { mutableStateOf(false) }
    var isOcrProcessing by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }
    // 첨부 이미지: 기기 OCR 없이 Gemini가 원본 이미지를 직접 읽음 (OCR 오타 방지)
    var attachedImages by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    // AI가 제안한 새 그룹의 이모지 (그룹명 to 이모지). 저장 시 그 그룹이 선택돼 있을 때만 기록
    var pendingNewGroupEmoji by remember(editItem) { mutableStateOf<Pair<String, String>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // 이미지 선택 런처 (갤러리)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            isOcrProcessing = true
            parseError = null
            coroutineScope.launch {
                val bitmaps = GeminiParser.loadImagesForAi(context, it)
                if (bitmaps.isNotEmpty()) attachedImages = bitmaps else parseError = "이미지 로드 실패"
                isOcrProcessing = false
            }
        }
    }

    // 음성 입력 런처 (시스템 음성 인식)
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (!spoken.isNullOrBlank()) {
            // 기존 텍스트가 있으면 줄바꿈으로 이어붙임 (여러 번 말하기 지원)
            autoInputText = if (autoInputText.isBlank()) spoken else autoInputText + "\n" + spoken
        }
    }

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

    // 정확한 알람 안내 (N분·N시간 전 알림을 처음 추가할 때 한 번)
    if (showExactAlarmPrompt) {
        AlertDialog(
            onDismissRequest = {
                showExactAlarmPrompt = false
                DdaySettings.setExactAlarmPromptShown(context)
            },
            title = { Text("정확한 시간에 알림 받기") },
            text = {
                Text("지금은 \"알람 및 리마인더\" 권한이 꺼져 있어서 \"분·시간 전\" 알림이 정한 시각보다 늦게 올 수 있어요.\n\n설정에서 Dayli의 권한을 켜면 정확한 시간에 알려드려요. 나중에 Dayli 설정 > 알림에서도 켤 수 있어요.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showExactAlarmPrompt = false
                    DdaySettings.setExactAlarmPromptShown(context)
                    ExactAlarms.openSettings(context)
                }) { Text("설정으로 이동") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExactAlarmPrompt = false
                    DdaySettings.setExactAlarmPromptShown(context)
                }) { Text("나중에") }
            }
        )
    }

    // AI 자동 입력 최초 사용 안내 다이얼로그
    if (showAiConsentDialog) {
        AlertDialog(
            onDismissRequest = { showAiConsentDialog = false },
            title = { Text("AI 자동 입력 안내") },
            text = {
                Text("선택한 이미지, 음성 인식 결과 또는 텍스트가 Google Gemini AI 서버로 전송되어 일정을 자동으로 파싱합니다.\n\n전송된 데이터는 별도로 저장되지 않습니다.")
            },
            confirmButton = {
                TextButton(onClick = {
                    DdaySettings.setAiConsentShown(context)
                    showAiConsentDialog = false
                    autoInputText = ""
                    attachedImages = emptyList()
                    parseError = null
                    showAutoInputDialog = true
                }) {
                    Text("확인 후 사용")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiConsentDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // AI 사용 횟수 (무료 월 제공분 + 광고로 받은 횟수). 창을 열 때마다 다시 읽음
    var aiQuota by remember { mutableStateOf(DdaySettings.getAiQuota(context)) }
    LaunchedEffect(showAutoInputDialog) {
        if (showAutoInputDialog) aiQuota = DdaySettings.getAiQuota(context)
    }
    val aiRemaining = AiQuota.totalRemaining(aiQuota)
    // 남은 횟수가 적으면 광고를 미리 불러 둠 (다 썼을 때 바로 볼 수 있게)
    LaunchedEffect(showAutoInputDialog, aiRemaining) {
        if (showAutoInputDialog && aiRemaining <= 3) RewardedAds.preload(context)
    }

    // 자동 입력 다이얼로그 (OCR + Gemini)
    if (showAutoInputDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isParsingText && !isOcrProcessing) {
                    showAutoInputDialog = false
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("AI로 자동 입력")
                }
            },
            text = {
                Column {
                    Text(
                        "말하거나, 사진을 고르거나, 글을 붙여넣으면 AI가 일정으로 바꿔줘요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    AiQuotaBar(
                        quota = aiQuota,
                        enabled = !isParsingText && !isOcrProcessing,
                        adStatus = RewardedAds.status,
                        onWatchAd = {
                            parseError = null
                            val shown = RewardedAds.show(
                                context,
                                onRewarded = {
                                    DdaySettings.addAiAdReward(context)
                                    aiQuota = DdaySettings.getAiQuota(context)
                                },
                                onClosed = { aiQuota = DdaySettings.getAiQuota(context) }
                            )
                            if (!shown) RewardedAds.preload(context)
                        },
                        onRetryAd = { RewardedAds.preload(context) }
                    )
                    Spacer(Modifier.height(14.dp))

                    // 음성 + 이미지 입력 (둘 다 된다는 걸 한눈에)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiInputModeTile(
                            icon = Icons.Default.Mic,
                            title = "말로 입력",
                            subtitle = "말하면 받아써요",
                            enabled = !isOcrProcessing && !isParsingText,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "일정을 말해주세요")
                                }
                                try {
                                    speechLauncher.launch(intent)
                                } catch (e: ActivityNotFoundException) {
                                    parseError = "이 기기에서 음성 인식을 사용할 수 없습니다"
                                }
                            }
                        )
                        AiInputModeTile(
                            icon = Icons.Default.Image,
                            title = if (isOcrProcessing) "불러오는 중..." else "사진으로 입력",
                            subtitle = "캡처·사진 속 일정을 읽어요",
                            enabled = !isOcrProcessing && !isParsingText,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }

                    // 첨부 이미지 미리보기
                    attachedImages.firstOrNull()?.let { bitmap ->
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp)
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "첨부한 이미지",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (attachedImages.size > 1) "긴 캡처 · ${attachedImages.size}장으로 나눠 분석" else "이미지 첨부됨",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { attachedImages = emptyList() },
                                enabled = !isParsingText
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "이미지 제거")
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 텍스트 입력 필드 (말로 입력한 내용도 여기에 들어옴)
                    OutlinedTextField(
                        value = autoInputText,
                        onValueChange = { autoInputText = it },
                        label = { Text(if (attachedImages.isNotEmpty()) "추가 설명 (선택)" else "또는 글로 입력·붙여넣기") },
                        placeholder = {
                            Text(
                                if (attachedImages.isNotEmpty()) "예: 퀴즈만 넣어줘"
                                else "예: 다음 주 금요일 오후 3시 치과"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp),
                        maxLines = 8,
                        enabled = !isParsingText
                    )

                    // 로딩 인디케이터
                    if (isParsingText) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("AI 분석 중...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // 에러 메시지
                    parseError?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (autoInputText.isBlank() && attachedImages.isEmpty()) return@TextButton
                        // 분석 요청 한 번 = 1회 (일정 여러 개를 뽑아도 1회). 누르는 즉시 차감해 연속 클릭으로 초과 사용 방지
                        val quotaSource = DdaySettings.consumeAiQuota(context)
                        aiQuota = DdaySettings.getAiQuota(context)
                        if (quotaSource == null) {
                            parseError = "이번 달 무료 횟수를 다 썼어요."
                            return@TextButton
                        }
                        isParsingText = true
                        parseError = null
                        coroutineScope.launch {
                            try {
                                val results = GeminiParser.parse(context, autoInputText, attachedImages, existingGroups)
                                    .map { parsed ->
                                        // 기존 그룹 철자로 맞춤. 새 그룹 이모지는 실제 저장할 때만 기록 (취소 시 흔적 안 남김)
                                        val (group, isNew) = GeminiParser.resolveGroupName(parsed.groupName, existingGroups)
                                        parsed.copy(groupName = group, groupEmoji = if (isNew) parsed.groupEmoji ?: "📁" else null)
                                    }
                                if (results.size > 1) {
                                    // 복수 일정 → 일괄 생성 (날짜 없는 항목은 D-Day로 만들 수 없어 제외)
                                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    val dated = results.mapNotNull { parsed ->
                                        val eventDate = parsed.date?.let {
                                            try { dateFormat.parse(it) } catch (_: Exception) { null }
                                        }
                                        eventDate?.let { parsed to it }
                                    }
                                    if (dated.isEmpty()) {
                                        parseError = "날짜를 찾지 못했습니다"
                                        return@launch
                                    }
                                    dated.forEach { (parsed, _) ->
                                        if (parsed.groupName != null && parsed.groupEmoji != null) {
                                            DdaySettings.setGroupEmojiIfAbsent(context, parsed.groupName, parsed.groupEmoji)
                                        }
                                    }
                                    dated.forEach { (parsed, eventDate) ->
                                        onSave(
                                            parsed.title ?: "",
                                            parsed.memo,
                                            eventDate,
                                            parsed.emoji ?: "📌",
                                            selectedColor,
                                            parsed.repeatType?.let {
                                                try { RepeatType.valueOf(it) } catch (_: Exception) { RepeatType.NONE }
                                            } ?: RepeatType.NONE,
                                            ItemType.DDAY,
                                            emptyList(),
                                            parsed.groupName, null, null, null,
                                            parsed.timeHour,
                                            parsed.timeMinute,
                                            emptyList()
                                        )
                                    }
                                    val groupNames = dated.mapNotNull { it.first.groupName }.distinct()
                                    val groupSuffix = if (groupNames.size == 1) " (${groupNames[0]} 그룹)" else ""
                                    android.widget.Toast.makeText(
                                        context, "${dated.size}개 D-Day가 추가되었습니다$groupSuffix", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    showAutoInputDialog = false
                                    attachedImages = emptyList()  // 저장 성공 후에만 해제 (날짜 못 찾으면 이미지 유지해 재시도)
                                } else {
                                    // 단일 일정 → 폼 자동완성
                                    val parsed = results.firstOrNull() ?: GeminiParser.ParsedEvent()
                                    parsed.title?.let { title = it }
                                    parsed.date?.let { dateStr ->
                                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                            .parse(dateStr)?.let { selectedDate = it }
                                    }
                                    parsed.timeHour?.let { h ->
                                        selectedHour = h
                                        selectedMinute = parsed.timeMinute ?: 0
                                        hasTime = true
                                    }
                                    parsed.repeatType?.let {
                                        try { selectedRepeatType = RepeatType.valueOf(it) } catch (_: Exception) {}
                                    }
                                    if (parsed.subTasks.isNotEmpty()) {
                                        subTasks = parsed.subTasks.map { SubTask(title = it) }
                                    }
                                    parsed.emoji?.let {
                                        selectedEmoji = it
                                        emojiManuallyChosen = true
                                    }
                                    parsed.memo?.let { memo = it }
                                    if (actualItemType == ItemType.DDAY) {
                                        parsed.groupName?.let { selectedGroupName = it }
                                        pendingNewGroupEmoji = parsed.groupName?.let { g -> parsed.groupEmoji?.let { g to it } }
                                    }
                                    showAutoInputDialog = false
                                    attachedImages = emptyList()  // 폼 적용 성공 후에만 해제 (실패 시 이미지로 재시도 가능)
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                DdaySettings.refundAiQuota(context, quotaSource)
                                throw e
                            } catch (e: Exception) {
                                // 분석 실패 → 쓴 횟수 돌려줌. 원인별 안내 (지출 상한 / 잠깐의 요청 제한 / 네트워크)
                                DdaySettings.refundAiQuota(context, quotaSource)
                                Log.w("DDAY_WIDGET", "AI 분석 실패", e)
                                parseError = AiErrors.userMessage(AiErrors.kindOf(e))
                            } finally {
                                aiQuota = DdaySettings.getAiQuota(context)
                                isParsingText = false
                            }
                        }
                    },
                    enabled = (autoInputText.isNotBlank() || attachedImages.isNotEmpty()) && !isParsingText && !isOcrProcessing &&
                        aiRemaining > 0
                ) { Text("분석") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAutoInputDialog = false
                        attachedImages = emptyList()
                    },
                    enabled = !isParsingText && !isOcrProcessing
                ) { Text("취소") }
            }
        )
    }

    // 이모지 선택 다이얼로그
    if (showEmojiPicker) {
        EmojiPickerDialog(
            currentEmoji = selectedEmoji,
            categoryColor = selectedColor.toComposeColor(),
            onEmojiSelected = { emoji ->
                selectedEmoji = emoji
                emojiManuallyChosen = true
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
        val trimmedGroupName = newGroupName.trim()
        val isDuplicateGroup = existingGroups.any { it == trimmedGroupName }
        AlertDialog(
            onDismissRequest = {
                showGroupDialog = false
                newGroupName = ""
                newGroupEmoji = "📁"
            },
            title = { Text("새 그룹") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 이모지 선택
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { showGroupEmojiPicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            EmojiText(newGroupEmoji, fontSize = 26.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            label = { Text("그룹 이름") },
                            placeholder = { Text("예: 학교, 여행") },
                            singleLine = true,
                            isError = isDuplicateGroup,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = if (isDuplicateGroup) "이미 있는 그룹이에요" else "이모지를 누르면 바꿀 수 있어요",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDuplicateGroup) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (onCreateGroup != null) {
                            onCreateGroup(trimmedGroupName, newGroupEmoji)
                        } else {
                            DdaySettings.setGroupEmoji(context, trimmedGroupName, newGroupEmoji)
                        }
                        selectedGroupName = trimmedGroupName
                        newGroupName = ""
                        newGroupEmoji = "📁"
                        showGroupDialog = false
                    },
                    enabled = trimmedGroupName.isNotEmpty() && !isDuplicateGroup
                ) {
                    Text("만들기")
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
                                        emojiManuallyChosen = true
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
                                    EmojiText(template.iconName, fontSize = 24.sp)
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

                // AI 자동 입력 (추가 모드에서만) — 말·사진·글 모두 된다는 걸 바로 보이게
                if (!isEditMode) {
                    Surface(
                        onClick = {
                            // 네트워크 체크
                            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                            val isOnline = cm?.getNetworkCapabilities(cm.activeNetwork)
                                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                            if (!isOnline) {
                                Toast.makeText(context, "인터넷 연결이 필요합니다. 연결 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                                return@Surface
                            }
                            // 최초 사용 동의
                            if (!DdaySettings.isAiConsentShown(context)) {
                                showAiConsentDialog = true
                            } else {
                                autoInputText = ""
                                attachedImages = emptyList()
                                parseError = null
                                showAutoInputDialog = true
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "AI로 빠르게 입력",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "말하기 · 사진 · 글 모두 돼요",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(Icons.Default.Mic, Icons.Default.Image, Icons.Default.TextFields).forEach { icon ->
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 제목 입력 (이모지 버튼을 왼쪽에 붙여 한 줄에서 정함)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(selectedColor.toComposeColor().copy(alpha = 0.25f))
                            .clickable { showEmojiPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        EmojiText(selectedEmoji, fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(if (actualItemType == ItemType.TODO) "할 일" else "제목") },
                        placeholder = { Text(if (actualItemType == ItemType.TODO) "할 일을 입력하세요" else "제목을 입력하세요") },
                        modifier = Modifier.weight(1f),
                        maxLines = 2
                    )
                }

                // 제목에 어울리는 이모지 추천 칩
                if (emojiSuggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "추천",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        emojiSuggestions.forEach { emoji ->
                            val isSel = emoji == selectedEmoji
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSel) selectedColor.toComposeColor().copy(alpha = 0.35f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                    .clickable {
                                        selectedEmoji = emoji
                                        emojiManuallyChosen = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                EmojiText(emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 메모 입력
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 색상 팔레트
                ColorPalette(
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it }
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

                Spacer(modifier = Modifier.height(16.dp))

                // 일정 설정 카드: 줄 전체를 눌러 바꿈
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp)
                ) {
                    if (actualItemType == ItemType.DDAY) {
                        // 날짜
                        FormRow(
                            icon = Icons.Outlined.CalendarToday,
                            label = "날짜",
                            value = SimpleDateFormat("yyyy.MM.dd (E)", Locale.getDefault()).format(selectedDate),
                            onClick = { datePickerDialog.show() }
                        )
                        FormDivider()

                        // 시간
                        val timeText = if (hasTime) {
                            val amPm = if (selectedHour < 12) "오전" else "오후"
                            val displayHour = when {
                                selectedHour == 0 -> 12
                                selectedHour > 12 -> selectedHour - 12
                                else -> selectedHour
                            }
                            if (selectedMinute == 0) "$amPm ${displayHour}시" else "$amPm ${displayHour}시 ${selectedMinute}분"
                        } else "없음"
                        FormRow(
                            icon = Icons.Outlined.Schedule,
                            label = "시간",
                            value = timeText,
                            valueActive = hasTime,
                            onClick = { timePickerDialog.show() },
                            trailing = if (hasTime) {
                                {
                                    IconButton(
                                        onClick = {
                                            hasTime = false
                                            // 시간 제거 시 분/시간 단위 알림 제거
                                            notificationRules = notificationRules.filter { it.type == "days" }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "시간 삭제",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else null
                        )
                        FormDivider()

                        // 그룹: 칩으로 바로 선택
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("그룹", style = MaterialTheme.typography.bodyLarge)
                        }
                        val groupChoices = (existingGroups + listOfNotNull(selectedGroupName)).distinct()
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 32.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedGroupName == null,
                                onClick = { selectedGroupName = null },
                                label = { Text("없음") }
                            )
                            groupChoices.forEach { group ->
                                FilterChip(
                                    selected = selectedGroupName == group,
                                    onClick = { selectedGroupName = group },
                                    label = {
                                        EmojiText(DdaySettings.getGroupEmoji(context, group), fontSize = 14.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Text(group)
                                    }
                                )
                            }
                            AssistChip(
                                onClick = { showGroupDialog = true },
                                label = { Text("새 그룹") },
                                leadingIcon = {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                        FormDivider()
                    }

                    // 반복 (D-Day와 To-Do 모두 지원)
                    FormRow(
                        icon = Icons.Outlined.Repeat,
                        label = "반복",
                        value = if (selectedRepeatType == RepeatType.NONE) "없음" else selectedRepeatType.displayName,
                        valueActive = selectedRepeatType != RepeatType.NONE,
                        onClick = { showRepeatPicker = true }
                    )

                    // 매주 요일 선택
                    if (selectedRepeatType == RepeatType.WEEKLY) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
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
                        FormDivider()
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
                        Box {
                            FormRow(
                                icon = Icons.Outlined.Visibility,
                                label = "미리 표시",
                                value = currentLabel,
                                onClick = { advanceDropdownExpanded = true }
                            )
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

                    // 개별 알림 (D-Day 전용)
                    if (actualItemType == ItemType.DDAY) {
                        var showNotifDropdown by remember { mutableStateOf(false) }
                        FormDivider()
                        Box {
                            FormRow(
                                icon = Icons.Outlined.NotificationsNone,
                                label = "알림",
                                value = if (notificationRules.isEmpty()) "없음" else "${notificationRules.size}개",
                                valueActive = notificationRules.isNotEmpty(),
                                onClick = { showNotifDropdown = true },
                                trailing = {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "알림 추가",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            DropdownMenu(
                                expanded = showNotifDropdown,
                                onDismissRequest = { showNotifDropdown = false }
                            ) {
                                // 시간 설정 시 분/시간 단위 옵션 표시
                                if (hasTime) {
                                    listOf(
                                        NotificationRule("minutes", 10),
                                        NotificationRule("minutes", 30),
                                        NotificationRule("hours", 1),
                                        NotificationRule("hours", 2)
                                    ).filter { rule -> rule !in notificationRules }.forEach { rule ->
                                        DropdownMenuItem(
                                            text = { Text(rule.displayText()) },
                                            onClick = {
                                                notificationRules = notificationRules + rule
                                                showNotifDropdown = false
                                                // 분·시간 전 알림은 정확한 알람이 필요 → 꺼져 있으면 한 번 안내
                                                if (!ExactAlarms.canSchedule(context) && !DdaySettings.isExactAlarmPromptShown(context)) {
                                                    showExactAlarmPrompt = true
                                                }
                                            }
                                        )
                                    }
                                }
                                // 일 단위 옵션은 항상 표시
                                listOf(
                                    NotificationRule("days", 1),
                                    NotificationRule("days", 3),
                                    NotificationRule("days", 7),
                                    NotificationRule("days", 14),
                                    NotificationRule("days", 30)
                                ).filter { rule -> rule !in notificationRules }.forEach { rule ->
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

                        // 추가된 알림 칩 (누르면 삭제)
                        if (notificationRules.isNotEmpty()) {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 32.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                notificationRules.forEachIndexed { index, rule ->
                                    FilterChip(
                                        selected = true,
                                        onClick = {
                                            notificationRules = notificationRules.toMutableList().apply { removeAt(index) }
                                        },
                                        label = { Text("🔔 ${rule.displayText()}", style = MaterialTheme.typography.bodySmall) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "삭제",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 저장 + 초기화 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 초기화 버튼 (추가 모드에서만)
                    if (!isEditMode) {
                        OutlinedButton(
                            onClick = {
                                title = ""
                                memo = ""
                                selectedDate = Date()
                                selectedEmoji = if (actualItemType == ItemType.TODO) "✅" else "📌"
                                emojiManuallyChosen = false
                                selectedColor = 0xFFA8C5DAL
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
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Text("초기화", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    // 저장 버튼
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
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
                                pendingNewGroupEmoji?.let { (group, emoji) ->
                                    if (actualItemType == ItemType.DDAY && selectedGroupName == group) {
                                        DdaySettings.setGroupEmojiIfAbsent(context, group, emoji)
                                    }
                                }
                                pendingNewGroupEmoji = null
                                // 추천 칩·자동 선택으로 정한 이모지도 최근 사용에 반영 (기본 이모지는 제외)
                                if (selectedEmoji != defaultEmoji) DdaySettings.addRecentEmoji(context, selectedEmoji)
                                title = ""
                                memo = ""
                                selectedDate = Date()
                                selectedEmoji = if (actualItemType == ItemType.TODO) "✅" else "📌"
                                emojiManuallyChosen = false
                                selectedColor = 0xFFA8C5DAL
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
                            .weight(if (isEditMode) 1f else 2f)
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
}

/** 일정 설정 카드의 한 줄: 줄 전체를 누르면 바꿈 */
@Composable
private fun FormRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    valueActive: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (valueActive) FontWeight.Medium else FontWeight.Normal,
            color = if (valueActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FormDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/** AI 입력 창의 입력 방식 타일 (말로 입력 / 사진으로 입력) */
@Composable
private fun AiInputModeTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (enabled) 0.6f else 0.3f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** AI 입력 창의 남은 횟수 줄. 다 쓰면 광고 보고 받기 (광고 단위가 없으면 다음 달 안내) */
@Composable
private fun AiQuotaBar(
    quota: AiQuota.State,
    enabled: Boolean,
    adStatus: RewardedAds.Status,
    onWatchAd: () -> Unit,
    onRetryAd: () -> Unit
) {
    val free = AiQuota.freeRemaining(quota)
    val total = AiQuota.totalRemaining(quota)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = buildString {
                append("이번 달 무료 $free/${AiQuota.FREE_PER_MONTH}회 남음")
                if (quota.adCredits > 0) append(" · 광고로 받은 ${quota.adCredits}회")
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (total > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
        )
        if (total == 0) {
            Spacer(Modifier.height(6.dp))
            // 광고 상태별: 준비됨 → 보기 / 불러오는 중 → 기다림 / 없음·실패 → 이유 + 다시 시도
            when (adStatus) {
                RewardedAds.Status.NOT_CONFIGURED -> Text(
                    "다음 달 1일에 ${AiQuota.FREE_PER_MONTH}회가 다시 채워져요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RewardedAds.Status.READY -> Button(
                    onClick = onWatchAd,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("광고 보고 ${AiQuota.AD_REWARD}회 받기")
                }
                RewardedAds.Status.IDLE, RewardedAds.Status.LOADING -> Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("광고 불러오는 중…")
                }
                else -> {
                    Text(
                        when (adStatus) {
                            RewardedAds.Status.NO_FILL -> "지금은 볼 수 있는 광고가 없어요."
                            RewardedAds.Status.NETWORK_ERROR -> "인터넷 연결을 확인해 주세요."
                            else -> "광고를 불러오지 못했어요."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onRetryAd,
                        enabled = enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text("다시 시도")
                    }
                }
            }
            Text(
                "직접 입력은 횟수 제한 없이 쓸 수 있어요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
