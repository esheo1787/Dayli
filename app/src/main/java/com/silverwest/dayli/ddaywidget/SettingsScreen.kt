package com.silverwest.dayli.ddaywidget

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
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onSettingsChanged: () -> Unit = {},
    onThemeChanged: (DdaySettings.ThemeMode) -> Unit = {},
    viewModel: DdayViewModel = viewModel()
) {
    val context = LocalContext.current

    // 설정 상태
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
            .padding(16.dp)
    ) {
        // ===== 🔔 알림 섹션 =====
        Text(
            text = "🔔 알림",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // ===== 🎨 테마 & 배경 섹션 =====
        Text(
            text = "🎨 테마 & 배경",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

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
                    FilterChip(
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 배경 색상 ON/OFF
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "배경 색상 표시",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "아이템별 색상을 배경에 적용",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = backgroundEnabled,
                onCheckedChange = { enabled ->
                    backgroundEnabled = enabled
                    DdaySettings.setBackgroundEnabled(context, enabled)
                    onSettingsChanged()
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 아이템 배경 투명도 슬라이더
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
                    text = "아이템 배경 투명도",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${backgroundOpacity}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "리스트와 위젯의 아이템 배경 색상 강도",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = backgroundOpacity.toFloat(),
                onValueChange = { value ->
                    backgroundOpacity = value.toInt()
                },
                onValueChangeFinished = {
                    DdaySettings.setBackgroundOpacity(context, backgroundOpacity)
                    onSettingsChanged()
                },
                valueRange = 0f..100f,
                steps = 19,  // 5% 단위
                enabled = backgroundEnabled
            )
        }

        // 아이콘 배경 투명도 슬라이더
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
                    text = "아이콘 배경 투명도",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${iconBgOpacity}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "이모지 아이콘 배경 색상 강도",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = iconBgOpacity.toFloat(),
                onValueChange = { value ->
                    iconBgOpacity = value.toInt()
                },
                onValueChangeFinished = {
                    DdaySettings.setIconBgOpacity(context, iconBgOpacity)
                    onSettingsChanged()
                },
                valueRange = 0f..100f,
                steps = 19,  // 5% 단위
                enabled = backgroundEnabled
            )
        }

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
                    FilterChip(
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 앱 미리보기
        Text(
            text = "미리보기",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        PreviewItem(
            emoji = "📚",
            title = "시험 공부",
            dday = "D-7",
            color = Color(0xFFE53935),
            backgroundEnabled = backgroundEnabled,
            backgroundOpacity = backgroundOpacity,
            iconBgOpacity = iconBgOpacity,
            appFontSize = appFontSize
        )

        Spacer(modifier = Modifier.height(8.dp))

        PreviewItem(
            emoji = "✈️",
            title = "여행 출발",
            dday = "D-14",
            color = Color(0xFF1E88E5),
            backgroundEnabled = backgroundEnabled,
            backgroundOpacity = backgroundOpacity,
            iconBgOpacity = iconBgOpacity,
            appFontSize = appFontSize
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // ===== 🧩 위젯 섹션 =====
        Text(
            text = "🧩 위젯",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

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
                    widgetBgOpacity = value.toInt()
                },
                onValueChangeFinished = {
                    DdaySettings.setWidgetBgOpacity(context, widgetBgOpacity)
                    onSettingsChanged()
                },
                valueRange = 0f..100f,
                steps = 19  // 5% 단위
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
                    FilterChip(
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
            isDark = isDark
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // ===== 💾 백업/복원 섹션 =====
        Text(
            text = "💾 백업/복원",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // ===== ℹ️ 정보 섹션 =====
        Text(
            text = "ℹ️ 정보",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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

@Composable
private fun WidgetPreviewItem(
    widgetBgOpacity: Int,
    backgroundEnabled: Boolean,
    backgroundOpacity: Int,
    iconBgOpacity: Int,
    widgetFontSize: Int,
    isDark: Boolean
) {
    // 위젯 배경색 (DdayWidgetProvider와 동일한 로직)
    val bgAlpha = (widgetBgOpacity / 100f).coerceIn(0f, 1f)
    val widgetBgColor = if (isDark) Color(0xFF2A2A3E) else Color(0xFFFFFDF5)

    // 폰트 크기 (RemoteViewsFactory와 동일한 배율)
    val fontMultiplier = when (widgetFontSize) {
        0 -> 0.85f  // 작게
        2 -> 1.15f  // 크게
        else -> 1f  // 보통
    }
    val titleSize = (15f * fontMultiplier).sp
    val dateSize = (12f * fontMultiplier).sp
    val ddaySize = (16f * fontMultiplier).sp

    val itemBgAlpha = if (backgroundEnabled) backgroundOpacity / 100f else 0f
    val iconAlpha = if (backgroundEnabled) iconBgOpacity / 100f else 0f

    val textColor = if (isDark) Color.White.copy(alpha = 0.87f) else Color.Black.copy(alpha = 0.87f)
    val subTextColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.53f)

    // 위젯 컨테이너
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(widgetBgColor.copy(alpha = bgAlpha))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // D-Day 예시 카드
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE53935).copy(alpha = itemBgAlpha))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE53935).copy(alpha = iconAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Text("📚", fontSize = (18f * fontMultiplier).sp)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 6.dp)
            ) {
                Text(
                    text = "시험 공부",
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1
                )
                Text(
                    text = "2025.06.15",
                    fontSize = dateSize,
                    color = subTextColor
                )
            }
            Text(
                text = "D-7",
                fontSize = ddaySize,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE53935)
            )
        }

        // To-Do 예시 카드
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF43A047).copy(alpha = itemBgAlpha))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF43A047).copy(alpha = iconAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Text("✅", fontSize = (18f * fontMultiplier).sp)
            }
            Text(
                text = "운동하기",
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 6.dp)
            )
            Checkbox(
                checked = true,
                onCheckedChange = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun PreviewItem(
    emoji: String,
    title: String,
    dday: String,
    color: Color,
    backgroundEnabled: Boolean,
    backgroundOpacity: Int,
    iconBgOpacity: Int,
    appFontSize: Int = 1
) {
    val bgAlpha = if (backgroundEnabled) backgroundOpacity / 100f else 0f
    val iconAlpha = if (backgroundEnabled) iconBgOpacity / 100f else 0f

    // 앱 글씨 크기 배율 (DdaySettings.getAppFontScale과 동일)
    val fontScale = when (appFontSize) {
        0 -> 0.85f  // 작게
        2 -> 1.2f   // 크게
        else -> 1f  // 보통
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = bgAlpha))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 이모지 아이콘
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = iconAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = (22 * fontScale).sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = (16 * fontScale).sp,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = dday,
            style = MaterialTheme.typography.titleMedium,
            fontSize = (16 * fontScale).sp,
            color = color,
            fontWeight = FontWeight.Bold
        )
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
