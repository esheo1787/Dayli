package com.silverwest.dayli.ddaywidget

import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onSettingsChanged: () -> Unit = {},
    onThemeChanged: (DdaySettings.ThemeMode) -> Unit = {},
    onFlatEmojiChanged: (Boolean) -> Unit = {},
    viewModel: DdayViewModel = viewModel()
) {
    val context = LocalContext.current
    var flatEmoji by remember { mutableStateOf(DdaySettings.isFlatEmojiEnabled(context)) }

    // 설정 상태
    var heroEnabled by remember { mutableStateOf(DdaySettings.isHeroCardEnabled(context)) }
    var heroColor by remember { mutableStateOf(DdaySettings.getHeroCardColor(context)) }
    var widgetHeroEnabled by remember { mutableStateOf(DdaySettings.isWidgetHeroEnabled(context)) }
    var backgroundEnabled by remember {
        mutableStateOf(DdaySettings.isBackgroundEnabled(context))
    }
    var backgroundOpacity by remember {
        mutableStateOf(DdaySettings.getBackgroundOpacity(context))
    }
    var iconBgOpacity by remember {
        mutableStateOf(DdaySettings.getIconBgOpacity(context))
    }
    var widgetBgOpacity by remember {
        mutableStateOf(DdaySettings.getWidgetBgOpacity(context))
    }
    var widgetFontSize by remember {
        mutableStateOf(DdaySettings.getWidgetFontSize(context))
    }
    var appFontSize by remember {
        mutableStateOf(DdaySettings.getAppFontSize(context))
    }

    // 테마 설정 상태
    var themeMode by remember {
        mutableStateOf(DdaySettings.getThemeModeEnum(context))
    }

    // 알림 설정 상태
    var notifyDayBefore by remember {
        mutableStateOf(DdaySettings.isNotifyDayBeforeEnabled(context))
    }
    var notifySameDay by remember {
        mutableStateOf(DdaySettings.isNotifySameDayEnabled(context))
    }
    var notifyHour by remember {
        mutableStateOf(DdaySettings.getNotifyHour(context))
    }
    var notifyMinute by remember {
        mutableStateOf(DdaySettings.getNotifyMinute(context))
    }
    var notifySound by remember {
        mutableStateOf(DdaySettings.isNotifySoundEnabled(context))
    }
    var notifyVibrate by remember {
        mutableStateOf(DdaySettings.isNotifyVibrateEnabled(context))
    }
    var showTimePicker by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    // 정확한 알람 권한 상태: 시스템 설정에서 돌아올 때 다시 확인하고, 새로 켜졌으면 알람 재등록
    var exactAlarmOk by remember { mutableStateOf(ExactAlarms.canSchedule(context)) }
    val settingsLifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(settingsLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val now = ExactAlarms.canSchedule(context)
                if (now && !exactAlarmOk) ExactAlarms.rescheduleAll(context)
                exactAlarmOk = now
            }
        }
        settingsLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { settingsLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val aiCallsThisMonth = remember {
        DdaySettings.getAiCallsThisMonth(context)
    }

    // 다크 모드 여부 (위젯 미리보기용)
    val isDark = when (themeMode) {
        DdaySettings.ThemeMode.DARK -> true
        DdaySettings.ThemeMode.LIGHT -> false
        DdaySettings.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        SettingsSectionLabel("알림")
        SettingsCard {

        // POST_NOTIFICATIONS 권한 보유 여부 (Android 12 이하는 항상 true)
        // 신규 사용자가 권한 없는 상태에서 SharedPrefs 기본값이 ON이어도
        // 표시상 OFF로 보이게 해서 "켜져있는데 알림 안 옴" 불일치 방지.
        var hasNotificationPermission by remember {
            mutableStateOf(
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
            )
        }

        // 알림 권한 요청 launcher — 토글 켤 때 권한 없으면 호출.
        // 권한 허용 시 pendingNotificationAction 실행.
        var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasNotificationPermission = granted
            if (granted) {
                pendingNotificationAction?.invoke()
            } else {
                Toast.makeText(
                    context,
                    "알림 권한이 거부되어 알림을 받을 수 없습니다",
                    Toast.LENGTH_LONG
                ).show()
            }
            pendingNotificationAction = null
        }

        /**
         * 알림 토글을 켤 때 권한이 필요하면 launcher 호출 후 [action] 수행, 아니면 즉시 수행.
         * 끄는 경우(enabled=false)는 권한 무관하게 바로 [action] 수행.
         */
        fun guardWithNotificationPermission(enabled: Boolean, action: () -> Unit) {
            if (!enabled) { action(); return }
            val needPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            if (needPermission) {
                pendingNotificationAction = action
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                action()
            }
        }

        // D-1 (하루 전) 알림
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "하루 전 알림 (D-1)",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "D-Day 하루 전에 알림",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                // 권한 없으면 저장된 값과 무관하게 표시상 OFF.
                // 사용자가 ON으로 바꾸면 guardWithNotificationPermission이 권한 요청.
                checked = notifyDayBefore && hasNotificationPermission,
                onCheckedChange = { enabled ->
                    guardWithNotificationPermission(enabled) {
                        notifyDayBefore = enabled
                        DdaySettings.setNotifyDayBeforeEnabled(context, enabled)
                        NotificationScheduler.updateSchedule(context)
                    }
                }
            )
        }

        // D-Day (당일) 알림
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "당일 알림 (D-Day)",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "D-Day 당일에 알림",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                // 권한 없으면 표시상 OFF — UI/실제 불일치 방지
                checked = notifySameDay && hasNotificationPermission,
                onCheckedChange = { enabled ->
                    guardWithNotificationPermission(enabled) {
                        notifySameDay = enabled
                        DdaySettings.setNotifySameDayEnabled(context, enabled)
                        NotificationScheduler.updateSchedule(context)
                    }
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 알림 시간 설정
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showTimePicker = true }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "알림 시간",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "매일 이 시간에 알림 확인",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Text(
                text = DdaySettings.getNotifyTimeString(context),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 정확한 시간에 알림 (Android 12+: 꺼져 있으면 알림이 늦게 올 수 있음)
        if (ExactAlarms.isPermissionNeeded()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { ExactAlarms.openSettings(context) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "정확한 시간에 알림", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (exactAlarmOk) "켜짐 · 정한 시각에 알림이 와요"
                        else "꺼짐 · 알림이 늦게 올 수 있어요 (절전 중에는 더 늦어질 수 있음)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (exactAlarmOk) Color.Gray else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (exactAlarmOk) "켜짐" else "켜기 ›",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 소리 설정
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "알림 소리",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "알림 시 소리 재생",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = notifySound,
                onCheckedChange = { enabled ->
                    notifySound = enabled
                    DdaySettings.setNotifySoundEnabled(context, enabled)
                }
            )
        }

        // 진동 설정
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "알림 진동",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "알림 시 진동",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = notifyVibrate,
                onCheckedChange = { enabled ->
                    notifyVibrate = enabled
                    DdaySettings.setNotifyVibrateEnabled(context, enabled)
                }
            )
        }

        }

        SettingsSectionLabel("화면")
        SettingsCard {

        // 테마 모드 선택
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "화면 모드",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "앱과 위젯의 색상 테마를 선택합니다",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DdaySettings.ThemeMode.entries.forEach { mode ->
                    SegmentChip(
                        selected = themeMode == mode,
                        onClick = {
                            themeMode = mode
                            DdaySettings.setThemeModeEnum(context, mode)
                            onThemeChanged(mode)  // 테마 즉시 적용
                            onSettingsChanged()
                        },
                        label = {
                            Text(
                                text = when (mode) {
                                    DdaySettings.ThemeMode.SYSTEM -> "시스템"
                                    DdaySettings.ThemeMode.LIGHT -> "라이트"
                                    DdaySettings.ThemeMode.DARK -> "다크"
                                }
                            )
                        },
                        leadingIcon = {
                            Text(
                                text = when (mode) {
                                    DdaySettings.ThemeMode.SYSTEM -> "📱"
                                    DdaySettings.ThemeMode.LIGHT -> "☀️"
                                    DdaySettings.ThemeMode.DARK -> "🌙"
                                },
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 이모지 스타일 (기본 / 플랫)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "이모지 스타일",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "앱과 위젯의 이모지 모양을 선택합니다",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(false to "기본", true to "플랫").forEach { (flat, label) ->
                    SegmentChip(
                        selected = flatEmoji == flat,
                        onClick = {
                            if (flatEmoji == flat) return@SegmentChip
                            flatEmoji = flat
                            DdaySettings.setFlatEmojiEnabled(context, flat)
                            onFlatEmojiChanged(flat)
                            onSettingsChanged()  // 위젯 새로고침
                        },
                        label = { Text(label) },
                        leadingIcon = {
                            // 각 스타일 미리보기 (현재 설정과 무관하게 해당 스타일로 표시)
                            CompositionLocalProvider(LocalFlatEmoji provides flat) {
                                Row {
                                    listOf("😊", "🎂", "✈️").forEach { EmojiText(it, fontSize = 14.sp) }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                text = "플랫 이모지: Microsoft Fluent Emoji (MIT License). 국기 등 일부는 기본 이모지로 표시돼요",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 가장 가까운 일정 카드 (켜기/끄기 + 카드 색: 프리셋 · 색상환 + 미리보기)
        HeroCardSettings(
            enabled = heroEnabled,
            onEnabledChange = { enabled ->
                heroEnabled = enabled
                DdaySettings.setHeroCardEnabled(context, enabled)
                // 위젯 스위치를 직접 정한 적 없으면 앱 설정을 따르므로 화면도 다시 읽음
                widgetHeroEnabled = DdaySettings.isWidgetHeroEnabled(context)
                onSettingsChanged()
            },
            color = heroColor,
            onColorCommit = { value ->
                heroColor = value
                DdaySettings.setHeroCardColor(context, value)
                onSettingsChanged()
            },
            fontScale = DdaySettings.getAppFontScale(context)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 항목 배경: 흰 카드 / 항목 색 + 진하기 (항목 색 = backgroundOpacity, 흰 카드 = iconBgOpacity)
        ItemBackgroundSettings(
            tinted = backgroundEnabled,
            onTintedChange = { tinted ->
                backgroundEnabled = tinted
                DdaySettings.setBackgroundEnabled(context, tinted)
                onSettingsChanged()
            },
            strength = if (backgroundEnabled) backgroundOpacity else iconBgOpacity,
            onStrengthChange = { value ->
                // 미리보기가 바로 바뀌도록 드래그 중에도 저장 (위젯 갱신은 손을 뗄 때)
                if (backgroundEnabled) {
                    backgroundOpacity = value
                    DdaySettings.setBackgroundOpacity(context, value)
                } else {
                    iconBgOpacity = value
                    DdaySettings.setIconBgOpacity(context, value)
                }
            },
            onStrengthCommit = { onSettingsChanged() }
        )

        // 앱 글씨 크기
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "앱 글씨 크기",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "앱에 표시되는 텍스트 크기",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("작게" to 0, "보통" to 1, "크게" to 2).forEach { (label, value) ->
                    SegmentChip(
                        selected = appFontSize == value,
                        onClick = {
                            appFontSize = value
                            DdaySettings.setAppFontSize(context, value)
                            onSettingsChanged()
                        },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 미리보기: 실제 목록 카드와 똑같이
        SettingsListPreview(refreshKey = listOf(backgroundEnabled, backgroundOpacity, iconBgOpacity, appFontSize))
        }

        SettingsSectionLabel("위젯")
        SettingsCard {

        // 위젯 배경 투명도 슬라이더
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "위젯 배경 투명도",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${widgetBgOpacity}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "위젯 전체 배경 (글래스모피즘)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = widgetBgOpacity.toFloat(),
                onValueChange = { value ->
                    widgetBgOpacity = (value / 5f).roundToInt() * 5  // 5% 단위
                },
                onValueChangeFinished = {
                    DdaySettings.setWidgetBgOpacity(context, widgetBgOpacity)
                    onSettingsChanged()
                },
                valueRange = 0f..100f,
                steps = 0  // 점 없이 매끄럽게 (값은 5% 단위로 반올림)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 위젯 글씨 크기
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "위젯 글씨 크기",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "위젯에 표시되는 텍스트 크기",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("작게" to 0, "보통" to 1, "크게" to 2).forEach { (label, value) ->
                    SegmentChip(
                        selected = widgetFontSize == value,
                        onClick = {
                            widgetFontSize = value
                            DdaySettings.setWidgetFontSize(context, value)
                            onSettingsChanged()
                        },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 위젯의 가장 가까운 일정 카드 (앱 목록과 따로)
        SettingsSwitchRow(
            title = "가장 가까운 일정 카드",
            subtitle = "위젯 맨 위에 다음 일정을 크게 보여줘요 (D-Day·통합 위젯)",
            checked = widgetHeroEnabled,
            onCheckedChange = { enabled ->
                widgetHeroEnabled = enabled
                DdaySettings.setWidgetHeroEnabled(context, enabled)
                onSettingsChanged()
            }
        )

        // 위젯 미리보기
        Text(
            text = "미리보기",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        WidgetPreviewItem(
            widgetBgOpacity = widgetBgOpacity,
            backgroundEnabled = backgroundEnabled,
            backgroundOpacity = backgroundOpacity,
            iconBgOpacity = iconBgOpacity,
            widgetFontSize = widgetFontSize,
            isDark = isDark,
            heroEnabled = widgetHeroEnabled,
            heroColor = heroColor
        )

        }

        SettingsSectionLabel("프리미엄")
        PremiumSettingsCard()

        SettingsSectionLabel("캘린더 연동")
        CalendarSyncSettingsCard(onChanged = onSettingsChanged)

        SettingsSectionLabel("데이터")
        SettingsCard {

        // 가져오기 미리보기 다이얼로그 상태 (preview + uri 보관)
        var importPreviewState by remember {
            mutableStateOf<Pair<BackupSerializer.BackupPreview, android.net.Uri>?>(null)
        }

        // 내보내기 launcher (SAF: CreateDocument)
        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                viewModel.exportBackup(uri) { result ->
                    val msg = when (result) {
                        is BackupRepository.Result.Success ->
                            "내보내기 완료: ${result.count}개 항목"
                        is BackupRepository.Result.Failure ->
                            "내보내기 실패: ${result.reason}"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }

        // 가져오기 launcher (SAF: OpenDocument)
        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                viewModel.previewBackup(uri) { preview ->
                    if (preview != null) {
                        importPreviewState = preview to uri
                    } else {
                        Toast.makeText(
                            context,
                            "올바른 백업 파일이 아닙니다.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        Text(
            text = "데이터를 파일로 저장하거나 복원합니다. 기기 변경 전이나 정기적으로 백업해두세요.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 내보내기 버튼
        OutlinedButton(
            onClick = {
                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
                    .format(Date())
                exportLauncher.launch("dayli-backup-$timestamp.json")
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text("내보내기 (파일로 저장)")
        }

        // 가져오기 버튼
        OutlinedButton(
            onClick = {
                importLauncher.launch(arrayOf("application/json", "*/*"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("가져오기 (파일에서 복원)")
        }

        // 가져오기 미리보기 다이얼로그
        importPreviewState?.let { (preview, uri) ->
            ImportPreviewDialog(
                preview = preview,
                onCancel = { importPreviewState = null },
                onConfirm = { mode ->
                    importPreviewState = null
                    viewModel.importBackup(uri, mode) { result ->
                        val msg = when (result) {
                            is BackupRepository.Result.Success ->
                                "가져오기 완료: ${result.count}개 항목"
                            is BackupRepository.Result.Failure ->
                                "가져오기 실패: ${result.reason}"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        onSettingsChanged()
                    }
                }
            )
        }

        }

        SettingsSectionLabel("정보")
        SettingsCard {

        // 앱 버전
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "앱 버전",
                style = MaterialTheme.typography.bodyLarge
            )
            val versionName = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
                } catch (_: Exception) { "1.0" }
            }
            Text(
                text = versionName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        // AI 사용량
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "이번 달 AI 사용",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${aiCallsThisMonth}회",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        // 개인정보처리방침
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(PRIVACY_POLICY_URL)
                    )
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            "브라우저를 열 수 없습니다",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "개인정보처리방침",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray
            )
        }

        // 오픈소스 라이선스
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showLicenses = true }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "오픈소스 라이선스", style = MaterialTheme.typography.bodyLarge)
            Text(text = "›", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        }

        // 문의하기
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    // 디버깅에 도움이 되도록 앱/기기 정보 본문에 자동 포함
                    val versionName = try {
                        context.packageManager
                            .getPackageInfo(context.packageName, 0).versionName ?: "?"
                    } catch (_: Exception) { "?" }
                    val body = buildString {
                        append("\n\n---\n")
                        append("아래는 자동으로 첨부된 환경 정보입니다 (수정 가능).\n")
                        append("앱 버전: $versionName\n")
                        append("기기: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                        append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                    }
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        // 수신자를 URI에 직접 박는 게 더 호환성 좋음
                        // (일부 메일 앱이 EXTRA_EMAIL을 안 읽음).
                        data = Uri.parse("mailto:$CONTACT_EMAIL")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT_EMAIL))
                        putExtra(Intent.EXTRA_SUBJECT, "[Dayli 문의] ")
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            "메일 앱을 열 수 없습니다. $CONTACT_EMAIL 으로 보내주세요",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "문의하기",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray
            )
        }

        }

        Spacer(modifier = Modifier.height(16.dp))

        // 기본값으로 복원 버튼
        OutlinedButton(
            onClick = {
                backgroundEnabled = true
                backgroundOpacity = 15  // 글래스모피즘 기본값
                iconBgOpacity = 20
                widgetBgOpacity = 20
                widgetFontSize = 1  // 보통
                appFontSize = 1  // 보통
                DdaySettings.setBackgroundEnabled(context, true)
                DdaySettings.setBackgroundOpacity(context, 15)
                DdaySettings.setIconBgOpacity(context, 20)
                DdaySettings.setWidgetBgOpacity(context, 20)
                DdaySettings.setWidgetFontSize(context, 1)
                DdaySettings.setAppFontSize(context, 1)
                heroEnabled = true
                heroColor = DdaySettings.DEFAULT_HERO_COLOR
                DdaySettings.setHeroCardEnabled(context, true)
                widgetHeroEnabled = true
                DdaySettings.setWidgetHeroEnabled(context, true)
                DdaySettings.setHeroCardColor(context, DdaySettings.DEFAULT_HERO_COLOR)
                onSettingsChanged()
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("기본값으로 복원")
        }
    }

    if (showLicenses) {
        OpenSourceLicensesDialog(onDismiss = { showLicenses = false })
    }

    // 시간 선택 다이얼로그
    if (showTimePicker) {
        TimePickerDialog(
            initialHour = notifyHour,
            initialMinute = notifyMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                notifyHour = hour
                notifyMinute = minute
                DdaySettings.setNotifyHour(context, hour)
                DdaySettings.setNotifyMinute(context, minute)
                NotificationScheduler.updateSchedule(context)
                showTimePicker = false
            }
        )
    }
}

/** 위젯 미리보기: 실제 위젯(RemoteViewsFactory)과 같은 WidgetStyle 색 계산 사용 */
@Composable
private fun WidgetPreviewItem(
    widgetBgOpacity: Int,
    backgroundEnabled: Boolean,
    backgroundOpacity: Int,
    iconBgOpacity: Int,
    widgetFontSize: Int,
    isDark: Boolean,
    heroEnabled: Boolean,
    heroColor: String
) {
    // 폰트 크기 (RemoteViewsFactory와 동일한 배율)
    val fm = when (widgetFontSize) {
        0 -> 0.85f  // 작게
        2 -> 1.15f  // 크게
        else -> 1f  // 보통
    }
    val inTwoDays = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 2) }.time }
    val ddaySample = Color(0xFF81D4FA)
    val todoSample = Color(0xFFA5D6A7)
    // 위젯 머리글: 바탕이 옅으면 흰 글자 + 그림자, 진하면 테마 글자 (RemoteViewsFactory와 같은 판단)
    @Composable
    fun HeaderLabel(text: String) {
        val onWallpaper = WidgetStyle.headerOnWallpaper(isDark, widgetBgOpacity)
        Text(
            text, fontSize = (16f * fm).sp, fontWeight = FontWeight.Bold,
            color = WidgetStyle.headerText(isDark, onWallpaper),
            style = if (onWallpaper) androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = WidgetStyle.HEADER_WALLPAPER_SHADOW,
                    offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                    blurRadius = 8f
                )
            ) else androidx.compose.ui.text.TextStyle.Default,
            modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 2.dp)
        )
    }

    // 배경화면 느낌의 바탕 (위젯 전체 배경 투명도가 보이도록)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    if (isDark) listOf(Color(0xFF2E3A46), Color(0xFF3B2F3A)) else listOf(Color(0xFFB9D3E6), Color(0xFFE9C9C0))
                )
            )
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(WidgetStyle.containerBase(isDark).copy(alpha = WidgetStyle.containerAlpha(widgetBgOpacity) / 255f))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (heroEnabled) {
                val hc = WidgetStyle.heroColors(heroColor, ddaySample)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(hc.bg)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).background(hc.tile),
                        contentAlignment = Alignment.Center
                    ) { EmojiText("📚", fontSize = (20f * fm).sp) }
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                        Text("가장 가까운 일정", fontSize = (11f * fm).sp, color = hc.sub, maxLines = 1)
                        Text("시험 공부", fontSize = (15f * fm).sp, fontWeight = FontWeight.Bold, color = hc.text, maxLines = 1)
                        Text(DayliUi.koreanDate(inTwoDays), fontSize = (11f * fm).sp, color = hc.sub, maxLines = 1)
                    }
                    Text("D-2", fontSize = (26f * fm).sp, fontWeight = FontWeight.Bold, color = hc.text, maxLines = 1)
                }
            }

            HeaderLabel("▎D-Day")
            WidgetPreviewCard(
                emoji = "📚", title = "시험 공부", sub = DayliUi.koreanDate(inTwoDays), dday = "D-2", ddayDiff = 2,
                itemColor = ddaySample, backgroundEnabled = backgroundEnabled, backgroundOpacity = backgroundOpacity,
                iconBgOpacity = iconBgOpacity, isDark = isDark, fm = fm
            )
            HeaderLabel("▎To-Do")
            WidgetPreviewCard(
                emoji = "🏃", title = "운동하기", sub = null, dday = null, ddayDiff = null,
                itemColor = todoSample, backgroundEnabled = backgroundEnabled, backgroundOpacity = backgroundOpacity,
                iconBgOpacity = iconBgOpacity, isDark = isDark, fm = fm
            )
        }
    }
}

/** 위젯 항목 카드 한 줄 (item_dday_widget.xml과 같은 배치) */
@Composable
private fun WidgetPreviewCard(
    emoji: String,
    title: String,
    sub: String?,
    dday: String?,
    ddayDiff: Int?,
    itemColor: Color,
    backgroundEnabled: Boolean,
    backgroundOpacity: Int,
    iconBgOpacity: Int,
    isDark: Boolean,
    fm: Float
) {
    val c = WidgetStyle.itemColors(itemColor, isDark, backgroundEnabled, backgroundOpacity, iconBgOpacity, checked = false, ddayDiff = ddayDiff)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.card)
            .padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(c.tile),
            contentAlignment = Alignment.Center
        ) { EmojiText(emoji, fontSize = (20f * fm).sp) }
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 6.dp)) {
            Text(title, fontSize = (15f * fm).sp, fontWeight = FontWeight.Bold, color = c.title, maxLines = 1)
            if (sub != null) Text(sub, fontSize = (12f * fm).sp, color = c.sub, maxLines = 1)
        }
        if (dday != null) {
            Box(
                Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp)).background(c.pillBg)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(dday, fontSize = (13f * fm).sp, fontWeight = FontWeight.Bold, color = c.pillText, maxLines = 1)
            }
        } else {
            // 위젯 체크박스 모양 (빈 칸)
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                        .border(2.dp, c.sub.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                )
            }
        }
    }
}

@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    // 12시간제로 변환
    val initialIsAm = initialHour < 12
    val initialHour12 = when {
        initialHour == 0 -> 12
        initialHour > 12 -> initialHour - 12
        else -> initialHour
    }
    var isAm by remember { mutableStateOf(initialIsAm) }
    var selectedHour12 by remember { mutableStateOf(initialHour12) }
    var selectedMinute by remember { mutableStateOf(initialMinute) }

    // 직접 입력 다이얼로그 상태
    var showHourInput by remember { mutableStateOf(false) }
    var showMinuteInput by remember { mutableStateOf(false) }

    // 24시간제로 변환
    fun get24Hour(): Int {
        return when {
            isAm && selectedHour12 == 12 -> 0
            !isAm && selectedHour12 == 12 -> 12
            !isAm -> selectedHour12 + 12
            else -> selectedHour12
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "알림 시간",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTime(get24Hour(), selectedMinute),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 오전/오후 선택
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    AmPmButton(
                        text = "오전",
                        isSelected = isAm,
                        onClick = { isAm = true }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    AmPmButton(
                        text = "오후",
                        isSelected = !isAm,
                        onClick = { isAm = false }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 휠 피커
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 시간 휠
                    WheelPicker(
                        items = (1..12).map { "$it" },
                        selectedIndex = selectedHour12 - 1,
                        onSelectedChange = { selectedHour12 = it + 1 },
                        onCenterClick = { showHourInput = true },
                        modifier = Modifier.width(80.dp)
                    )

                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // 분 휠 (1분 단위)
                    WheelPicker(
                        items = (0..59).map { String.format("%02d", it) },
                        selectedIndex = selectedMinute,
                        onSelectedChange = { selectedMinute = it },
                        onCenterClick = { showMinuteInput = true },
                        modifier = Modifier.width(80.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "숫자를 탭하면 직접 입력할 수 있습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(get24Hour(), selectedMinute) }
            ) {
                Text("확인", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )

    // 시간 직접 입력 다이얼로그
    if (showHourInput) {
        NumberInputDialog(
            title = "시간 입력",
            currentValue = selectedHour12,
            minValue = 1,
            maxValue = 12,
            onDismiss = { showHourInput = false },
            onConfirm = { value ->
                selectedHour12 = value
                showHourInput = false
            }
        )
    }

    // 분 직접 입력 다이얼로그
    if (showMinuteInput) {
        NumberInputDialog(
            title = "분 입력",
            currentValue = selectedMinute,
            minValue = 0,
            maxValue = 59,
            onDismiss = { showMinuteInput = false },
            onConfirm = { value ->
                selectedMinute = value
                showMinuteInput = false
            }
        )
    }
}

@Composable
private fun AmPmButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    onCenterClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val itemHeight = 48.dp
    val visibleItems = 3
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }

    // 스냅 동작
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // 초기 스크롤 위치 설정
    LaunchedEffect(Unit) {
        listState.scrollToItem(selectedIndex)
    }

    // 외부에서 selectedIndex가 변경되면 스크롤
    LaunchedEffect(selectedIndex) {
        if (listState.firstVisibleItemIndex != selectedIndex) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    // 스크롤 완료 시 선택 항목 업데이트 및 정렬
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            // 가장 가까운 아이템 계산
            val offset = listState.firstVisibleItemScrollOffset
            val currentIndex = listState.firstVisibleItemIndex
            val snapToNext = offset > itemHeightPx / 2
            val targetIndex = if (snapToNext) currentIndex + 1 else currentIndex
            val newIndex = targetIndex.coerceIn(0, items.lastIndex)

            if (newIndex != selectedIndex) {
                onSelectedChange(newIndex)
            }

            // 정확히 가운데 정렬
            if (offset != 0) {
                listState.animateScrollToItem(newIndex)
            }
        }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleItems),
        contentAlignment = Alignment.Center
    ) {
        // 선택 영역 하이라이트
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = itemHeight),
            flingBehavior = snapFlingBehavior
        ) {
            items(items.size) { index ->
                val isCenter = index == selectedIndex
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .clickable {
                            if (isCenter) {
                                // 중앙 아이템 클릭 시 직접 입력
                                onCenterClick()
                            } else {
                                // 다른 아이템 클릭 시 해당 위치로 스크롤
                                scope.launch {
                                    listState.animateScrollToItem(index)
                                    onSelectedChange(index)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCenter)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberInputDialog(
    title: String,
    currentValue: Int,
    minValue: Int,
    maxValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = currentValue.toString(),
                selection = TextRange(0, currentValue.toString().length)
            )
        )
    }
    var isError by remember { mutableStateOf(false) }

    // 자동 포커스
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        // 숫자만 허용
                        val filtered = newValue.text.filter { it.isDigit() }
                        if (filtered.length <= 2) {
                            textFieldValue = newValue.copy(text = filtered)
                            isError = false
                        }
                    },
                    modifier = Modifier
                        .width(100.dp)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val value = textFieldValue.text.toIntOrNull()
                            if (value != null && value in minValue..maxValue) {
                                onConfirm(value)
                            } else {
                                isError = true
                            }
                        }
                    ),
                    isError = isError
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$minValue ~ $maxValue 사이 값을 입력하세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = textFieldValue.text.toIntOrNull()
                    if (value != null && value in minValue..maxValue) {
                        onConfirm(value)
                    } else {
                        isError = true
                    }
                }
            ) {
                Text("확인", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

private fun formatTime(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "오전" else "오후"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return if (minute == 0) {
        "$amPm ${displayHour}시"
    } else {
        "$amPm ${displayHour}시 ${minute}분"
    }
}

@Composable
private fun ImportPreviewDialog(
    preview: BackupSerializer.BackupPreview,
    onCancel: () -> Unit,
    onConfirm: (BackupSerializer.ImportMode) -> Unit
) {
    var mode by remember { mutableStateOf(BackupSerializer.ImportMode.MERGE) }
    val exportedAtText = remember(preview.exportedAt) {
        if (preview.exportedAt > 0L) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(preview.exportedAt))
        } else "알 수 없음"
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("백업 파일 가져오기") },
        text = {
            Column {
                Text(
                    text = "파일 정보",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "• D-Day ${preview.ddayCount}개\n" +
                            "• To-Do ${preview.todoCount}개\n" +
                            "• 템플릿 ${preview.templateCount}개\n" +
                            "• 내보낸 시각: $exportedAtText",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = "처리 방법",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mode = BackupSerializer.ImportMode.MERGE }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == BackupSerializer.ImportMode.MERGE,
                        onClick = { mode = BackupSerializer.ImportMode.MERGE }
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text("병합 (추천)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "기존 데이터에 백업 항목 추가",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mode = BackupSerializer.ImportMode.OVERWRITE }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == BackupSerializer.ImportMode.OVERWRITE,
                        onClick = { mode = BackupSerializer.ImportMode.OVERWRITE }
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text("덮어쓰기", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "기존 데이터 삭제 후 백업으로 복원",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(mode) }) {
                Text("가져오기", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("취소")
            }
        }
    )
}

// ===== 외부 링크 상수 =====
private const val PRIVACY_POLICY_URL = "https://esheo1787.github.io/Dayli/privacy-policy.html"
private const val CONTACT_EMAIL = "heunseo1787@gmail.com"
