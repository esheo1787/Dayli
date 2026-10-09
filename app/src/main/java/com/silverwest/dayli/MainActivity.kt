package com.silverwest.dayli

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.silverwest.dayli.ddaywidget.*
import com.silverwest.dayli.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import java.util.Calendar

class MainActivity : ComponentActivity() {

    // 앱 내 업데이트: 다운로드 완료 시 재시작 안내 다이얼로그 표시
    private var updateReadyToInstall by mutableStateOf(false)
    private lateinit var inAppUpdater: InAppUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        inAppUpdater = InAppUpdater(this) { updateReadyToInstall = true }
        inAppUpdater.checkForUpdate()

        // 알림 채널 생성
        NotificationHelper.createNotificationChannel(this)

        // 알림 스케줄 초기화
        NotificationScheduler.updateSchedule(this)

        // AdMob 초기화
        MobileAds.initialize(this) {}

        // 알림 권한 요청은 사용자가 알림 토글을 켤 때만 (Android best practice).
        // 첫 실행 즉시 권한 요청을 띄우지 않는다.

        setContent {
            // 스플래시 화면 상태
            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(1200L)
                showSplash = false
            }

            // 테마 모드 상태 관리 (즉시 적용을 위해)
            var currentThemeMode by remember {
                mutableStateOf(DdaySettings.getThemeModeEnum(this))
            }

            // 이모지 스타일 (기본/플랫) — 설정 변경 즉시 앱 전체 반영
            var flatEmoji by remember {
                mutableStateOf(DdaySettings.isFlatEmojiEnabled(this))
            }

            if (showSplash) {
                SplashContent()
            } else {
                MyApplicationTheme(themeMode = currentThemeMode) {
                    CompositionLocalProvider(LocalFlatEmoji provides flatEmoji) {
                        MainDdayScreen(
                            onThemeChanged = { newMode ->
                                currentThemeMode = newMode
                            },
                            onFlatEmojiChanged = { flatEmoji = it }
                        )
                    }

                    if (updateReadyToInstall) {
                        AlertDialog(
                            onDismissRequest = { updateReadyToInstall = false },
                            title = { Text("업데이트 준비 완료") },
                            text = { Text("새 버전을 다운로드했어요. 지금 재시작해서 설치할까요?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    updateReadyToInstall = false
                                    inAppUpdater.completeUpdate()
                                }) { Text("재시작") }
                            },
                            dismissButton = {
                                TextButton(onClick = { updateReadyToInstall = false }) { Text("나중에") }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        inAppUpdater.onResume()
    }

    override fun onDestroy() {
        inAppUpdater.release()
        super.onDestroy()
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDdayScreen(
    viewModel: DdayViewModel = viewModel(),
    onThemeChanged: (DdaySettings.ThemeMode) -> Unit = {},
    onFlatEmojiChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    // 검색 상태
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    // 검색 모드에서 뒤로가기 시 검색 닫기
    BackHandler(enabled = isSearchActive) {
        searchQuery = ""
        isSearchActive = false
    }

    // 상단 날짜: 앱으로 돌아올 때와 자정에 갱신 (켜둔 채 날짜가 바뀌어도 어제 날짜로 남지 않게)
    var todayText by remember { mutableStateOf(todayLabel()) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) todayText = todayLabel()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(todayText) {
        delay(millisUntilNextMidnight() + 1_000L)
        todayText = todayLabel()
    }

    // 설정 변경 시 리스트 새로고침을 위한 키
    var settingsKey by remember { mutableStateOf(0) }
    // 현재 탭 (0: D-Day, 1: To-Do)
    var selectedTab by remember { mutableStateOf(0) }

    // 캘린더 뷰 토글 상태
    var isCalendarView by remember { mutableStateOf(DdaySettings.isCalendarView(context)) }
    // 넓은 화면(폴드 펼침·태블릿·가로 태블릿): 목록 + 캘린더 2단. 창 크기 기준이라 화면 분할에서도 맞게 바뀜
    val twoPane = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= TWO_PANE_MIN_WIDTH_DP
    LaunchedEffect(isCalendarView) {
        DdaySettings.setCalendarView(context, isCalendarView)
    }

    // 추가/수정 바텀시트 상태
    var showAddSheet by remember { mutableStateOf(false) }
    var addInitialDate by remember { mutableStateOf<java.util.Date?>(null) }
    var editItem by remember { mutableStateOf<DdayItem?>(null) }

    // 기존 그룹 목록
    val existingGroups by viewModel.existingGroups.observeAsState(emptyList())

    // 템플릿 목록
    val templates by viewModel.templates.observeAsState(emptyList())

    // 첫 항목 추가 직후 위젯 추가 유도 다이얼로그
    val widgetPromptEvent by viewModel.widgetPromptEvent.observeAsState()
    var showWidgetPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(widgetPromptEvent) {
        if (widgetPromptEvent == true) {
            // 위젯이 이미 설치된 사용자에게는 보여줄 필요 없음
            if (!isAnyWidgetInstalled(context)) {
                showWidgetPrompt = true
            }
            viewModel.consumeWidgetPromptEvent()
            DdaySettings.setWidgetPromptShown(context)
        }
    }
    if (showWidgetPrompt) {
        WidgetPinPromptDialog(
            onAdd = {
                showWidgetPrompt = false
                requestPinAppWidget(context)
            },
            onDismiss = { showWidgetPrompt = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                // 날짜 + 제목 두 줄: 시스템 글자 크기가 커지면 상단바도 같이 높아지게 (200%에서 제목 잘림 방지)
                expandedHeight = maxOf(64f, 12f + 51f * LocalDensity.current.fontScale).dp,
                title = {
                    if (isSearchActive) {
                        LaunchedEffect(Unit) {
                            searchFocusRequester.requestFocus()
                        }
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("검색...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester)
                        )
                    } else {
                        Column {
                            Text(
                                text = todayText,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (selectedTab == 0) "D-Day" else "To-Do",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            searchQuery = ""
                            isSearchActive = false
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "검색 닫기"
                            )
                        }
                    } else {
                        // D-Day 탭일 때만 캘린더/리스트 토글 아이콘 표시 (2단에서는 캘린더가 늘 보여 불필요)
                        if (selectedTab == 0 && !twoPane) {
                            IconButton(onClick = { isCalendarView = !isCalendarView }) {
                                Icon(
                                    imageVector = if (isCalendarView)
                                        Icons.AutoMirrored.Filled.List
                                    else
                                        Icons.Default.CalendarMonth,
                                    contentDescription = if (isCalendarView) "리스트 보기" else "캘린더 보기"
                                )
                            }
                        }
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "검색"
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "설정"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editItem = null
                    addInitialDate = null
                    showAddSheet = true
                },
                shape = RoundedCornerShape(14.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                text = { Text("추가", fontWeight = FontWeight.Bold) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // settingsKey를 key로 사용하여 설정 변경 시 리컴포지션
            Box(modifier = Modifier.weight(1f)) {
                key(settingsKey) {
                    DdayScreen(
                        viewModel = viewModel,
                        onTabChanged = { tabIndex -> selectedTab = tabIndex },
                        onEditItem = { item ->
                            editItem = item
                            showAddSheet = true
                        },
                        searchQuery = searchQuery,
                        isCalendarView = isCalendarView,
                        onAddForDate = { date ->
                            editItem = null
                            addInitialDate = date
                            showAddSheet = true
                        },
                        twoPane = twoPane
                    )
                }
            }
            // 하단 배너 광고
            BannerAd()
        }
    }

    // 추가/수정 바텀시트
    AddEditBottomSheet(
        isVisible = showAddSheet,
        // 캘린더의 "이 날짜에 D-Day 추가"는 항상 D-Day (2단 화면에서는 To-Do 탭을 연 채로 누를 수 있음)
        itemType = if (selectedTab == 0 || addInitialDate != null) ItemType.DDAY else ItemType.TODO,
        editItem = editItem,
        initialDate = addInitialDate,
        existingGroups = existingGroups,
        templates = templates,
        onDismiss = {
            showAddSheet = false
            editItem = null
            addInitialDate = null
        },
        onSave = { title, memo, date, emoji, color, repeatType, itemType, subTasks, groupName, repeatDay, advanceDisplayDays, templateId, timeHour, timeMinute, notificationRules ->
            if (editItem != null) {
                // 수정 모드: 전달된 repeatDay 사용, 없으면 날짜에서 계산
                val finalRepeatDay = repeatDay ?: when (repeatType) {
                    RepeatType.WEEKLY -> date?.let { 1 shl (Calendar.getInstance().apply { time = it }.get(Calendar.DAY_OF_WEEK) - 1) }
                    RepeatType.MONTHLY -> date?.let { Calendar.getInstance().apply { time = it }.get(Calendar.DAY_OF_MONTH) }
                    else -> null
                }
                val updatedItem = editItem!!.copy(
                    title = title,
                    memo = memo,
                    date = date,
                    iconName = emoji,
                    customColor = color,
                    repeatType = repeatType.name,
                    repeatDay = finalRepeatDay,
                    subTasks = DdayItem.subTasksToJson(subTasks),
                    groupName = groupName,
                    advanceDisplayDays = advanceDisplayDays,
                    timeHour = timeHour,
                    timeMinute = timeMinute,
                    notifications = DdayItem.notificationRulesToJson(notificationRules)
                )
                viewModel.updateItem(updatedItem)
            } else {
                // 추가 모드
                if (itemType == ItemType.DDAY) {
                    viewModel.insertDday(title, memo ?: "", date!!, emoji, color, repeatType, groupName, advanceDisplayDays, timeHour, timeMinute, notificationRules)
                } else {
                    viewModel.insertTodo(title, memo, emoji, color, repeatType, subTasks, repeatDay, advanceDisplayDays, templateId)
                }
            }
            showAddSheet = false
            editItem = null
            addInitialDate = null
        },
        onSaveAsTemplate = { name, iconName, customColor, subTasks ->
            viewModel.saveAsTemplate(name, iconName, customColor, subTasks)
        }
    )

    // 설정 다이얼로그
    if (showSettings) {
        // 전체 화면 설정 페이지 (뒤로가기 / ← 버튼으로 닫기)
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSettings = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                        navigationIcon = {
                            IconButton(onClick = { showSettings = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                            }
                        },
                        title = { Text("설정", fontWeight = FontWeight.Bold) }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 넓은 화면에서 설정 줄이 끝까지 늘어나지 않게 최대 폭 제한
                    Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    SettingsScreen(
                        onSettingsChanged = {
                            // 위젯 전체 새로고침 (배경 등 설정 반영)
                            DdayWidgetProvider.refreshAllWidgetsFull(context)
                            // 앱 리스트 새로고침
                            settingsKey++
                        },
                        onThemeChanged = onThemeChanged,
                        onFlatEmojiChanged = onFlatEmojiChanged
                    )
                    }
                }
            }
        }
    }
}

/** 스토어용 스크린샷을 찍을 때만 false (테스트에서 설정). 앱에서는 항상 true */
@androidx.annotation.VisibleForTesting
var bannerAdsEnabled = true

@Composable
fun BannerAd() {
    if (!bannerAdsEnabled) return
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-1913317477324858/1275802348"
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

/** Dayli의 위젯(통합/D-Day/To-Do) 중 하나라도 홈 화면에 설치되어 있으면 true. */
private fun isAnyWidgetInstalled(context: android.content.Context): Boolean {
    val mgr = AppWidgetManager.getInstance(context)
    val providers = listOf(
        DdayWidgetProvider::class.java,
        DdayOnlyWidgetProvider::class.java,
        TodoOnlyWidgetProvider::class.java,
    )
    return providers.any { cls ->
        mgr.getAppWidgetIds(ComponentName(context, cls)).isNotEmpty()
    }
}

/**
 * 사용자가 홈 화면에 통합 위젯을 한 번 클릭으로 추가하도록 시스템 다이얼로그를 호출.
 * Android 8.0+(API 26)에서만 동작. Dayli minSdk 24이므로 24/25 기기는 fallback 토스트.
 */
private fun requestPinAppWidget(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        android.widget.Toast.makeText(
            context,
            "홈 화면을 길게 눌러 위젯 메뉴에서 Dayli를 추가해주세요",
            android.widget.Toast.LENGTH_LONG
        ).show()
        return
    }
    val mgr = AppWidgetManager.getInstance(context)
    if (!mgr.isRequestPinAppWidgetSupported) {
        android.widget.Toast.makeText(
            context,
            "이 런처는 자동 추가를 지원하지 않아요. 홈 화면을 길게 눌러 위젯을 추가해주세요",
            android.widget.Toast.LENGTH_LONG
        ).show()
        return
    }
    val component = ComponentName(context, DdayWidgetProvider::class.java)
    val successCallback = PendingIntent.getBroadcast(
        context, 0, Intent(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    mgr.requestPinAppWidget(component, null, successCallback)
}

@Composable
private fun WidgetPinPromptDialog(onAdd: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("홈 화면에 위젯 추가") },
        text = {
            Text(
                "Dayli의 핵심은 위젯입니다. 홈 화면에 추가하면 앱을 열지 않고도 " +
                "D-Day와 할 일을 바로 확인할 수 있어요."
            )
        },
        confirmButton = {
            TextButton(onClick = onAdd) {
                Text("추가하기", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("나중에") }
        }
    )
}

@Composable
private fun SplashContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFDDD8F3)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.splash_icon),
                contentDescription = null,
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Dayli",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5B5680)
            )
        }
    }
}

/** 다음 자정까지 남은 시간(ms) */
/** 이 폭(dp) 이상이면 목록 + 캘린더 2단 (Material 기준 Medium 창 크기 시작점) */
const val TWO_PANE_MIN_WIDTH_DP = 600

private fun millisUntilNextMidnight(): Long {
    val now = Calendar.getInstance()
    val midnight = (now.clone() as Calendar).apply {
        add(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return midnight.timeInMillis - now.timeInMillis
}

/** 상단 날짜 표시: "10월 9일 금요일" */
private fun todayLabel(): String {
    val cal = Calendar.getInstance()
    val days = arrayOf("일", "월", "화", "수", "목", "금", "토")
    return "${cal.get(Calendar.MONTH) + 1}월 ${cal.get(Calendar.DAY_OF_MONTH)}일 ${days[cal.get(Calendar.DAY_OF_WEEK) - 1]}요일"
}
