package com.silverwest.dayli

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverwest.dayli.ddaywidget.*
import com.silverwest.dayli.ui.theme.MyApplicationTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            android.util.Log.d("DDAY_NOTIFICATION", "✅ 알림 권한 허용됨")
        } else {
            android.util.Log.d("DDAY_NOTIFICATION", "❌ 알림 권한 거부됨")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 알림 채널 생성
        NotificationHelper.createNotificationChannel(this)

        // 알림 스케줄 초기화
        NotificationScheduler.updateSchedule(this)

        // Android 13+ 알림 권한 요청
        requestNotificationPermission()

        setContent {
            // 테마 모드 상태 관리 (즉시 적용을 위해)
            var currentThemeMode by remember {
                mutableStateOf(DdaySettings.getThemeModeEnum(this))
            }

            MyApplicationTheme(themeMode = currentThemeMode) {
                MainDdayScreen(
                    onThemeChanged = { newMode ->
                        currentThemeMode = newMode
                    }
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 이미 권한 있음
                    android.util.Log.d("DDAY_NOTIFICATION", "✅ 알림 권한 이미 있음")
                }
                else -> {
                    // 권한 요청
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDdayScreen(
    viewModel: DdayViewModel = viewModel(),
    onThemeChanged: (DdaySettings.ThemeMode) -> Unit = {}
) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    // 설정 변경 시 리스트 새로고침을 위한 키
    var settingsKey by remember { mutableStateOf(0) }
    // 현재 탭 (0: D-Day, 1: To-Do)
    var selectedTab by remember { mutableStateOf(0) }

    // 추가/수정 바텀시트 상태
    var showAddSheet by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<DdayItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedTab == 0) "D-Day" else "To-Do",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "설정"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editItem = null
                    showAddSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "추가"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // settingsKey를 key로 사용하여 설정 변경 시 리컴포지션
            key(settingsKey) {
                DdayScreen(
                    viewModel = viewModel,
                    onTabChanged = { tabIndex -> selectedTab = tabIndex },
                    onEditItem = { item ->
                        editItem = item
                        showAddSheet = true
                    }
                )
            }
        }
    }

    // 추가/수정 바텀시트
    AddEditBottomSheet(
        isVisible = showAddSheet,
        itemType = if (selectedTab == 0) ItemType.DDAY else ItemType.TODO,
        editItem = editItem,
        onDismiss = {
            showAddSheet = false
            editItem = null
        },
        onSave = { title, memo, date, emoji, color, repeatType, itemType, subTasks ->
            if (editItem != null) {
                // 수정 모드
                val calendar = Calendar.getInstance().apply { date?.let { time = it } }
                val repeatDay = when (repeatType) {
                    RepeatType.WEEKLY -> calendar.get(Calendar.DAY_OF_WEEK)
                    RepeatType.MONTHLY -> calendar.get(Calendar.DAY_OF_MONTH)
                    else -> null
                }
                viewModel.updateItem(
                    editItem!!.copy(
                        title = title,
                        memo = memo,
                        date = date,
                        iconName = emoji,
                        customColor = color,
                        repeatType = repeatType.name,
                        repeatDay = repeatDay,
                        subTasks = DdayItem.subTasksToJson(subTasks)
                    )
                )
            } else {
                // 추가 모드
                if (itemType == ItemType.DDAY) {
                    viewModel.insertDday(title, memo ?: "", date!!, emoji, color, repeatType)
                } else {
                    viewModel.insertTodo(title, memo, emoji, color, repeatType, subTasks)
                }
            }
            showAddSheet = false
            editItem = null
        }
    )

    // 설정 다이얼로그
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("설정") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    SettingsScreen(
                        onSettingsChanged = {
                            // 위젯 새로고침
                            DdayWidgetProvider.refreshAllWidgets(context)
                            // 앱 리스트 새로고침
                            settingsKey++
                        },
                        onThemeChanged = onThemeChanged
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("닫기")
                }
            }
        )
    }
}

