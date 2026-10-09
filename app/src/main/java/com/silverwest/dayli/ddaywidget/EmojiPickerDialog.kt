package com.silverwest.dayli.ddaywidget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── 피부색 수정자 (Fitzpatrick) ───
private val SKIN_TONES = listOf(
    "", "🏻", "🏼", "🏽", "🏾", "🏿"
)
private val SKIN_DISPLAY = listOf("✋", "✋🏻", "✋🏼", "✋🏽", "✋🏾", "✋🏿")

private const val RECENT_ICON = "🕘"
private const val RECOMMENDED_ICON = "✨"
private const val FIXED_PAGES = 2  // 최근 사용 + 추천

// ─── 이모지 피커 다이얼로그 ───

@Composable
fun EmojiPickerDialog(
    currentEmoji: String,
    categoryColor: Color,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        EmojiPickerContent(currentEmoji, categoryColor, onEmojiSelected, onDismiss)
    }
}

/** 피커 본문 (다이얼로그 틀과 분리 — 테스트/미리보기에서 일반 화면에 그릴 수 있게) */
@Composable
internal fun EmojiPickerContent(
    currentEmoji: String,
    categoryColor: Color,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedEmoji by remember { mutableStateOf(currentEmoji) }
    var skinIndex by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val recents = remember { DdaySettings.getRecentEmojis(context) }
    val scope = rememberCoroutineScope()

    // 표준 이모지 전체 목록 (assets) — 백그라운드에서 1회 로드
    val categories by produceState<List<EmojiCatalog.Category>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { EmojiCatalog.load(context) }
    }

    // 추천 탭: 이 기기에서 표시 가능한 것만
    val recommended = remember(categories) {
        val available = categories?.flatMap { c -> c.entries.map { it.emoji } }?.toHashSet()
        if (available == null) emptyList() else EmojiCatalog.RECOMMENDED.filter { it in available }
    }

    // 페이지 0 = 최근 사용, 1 = 추천, 2.. = 카테고리
    val pageCount = (categories?.size ?: 0) + FIXED_PAGES
    val pagerState = rememberPagerState { pageCount }
    // 최근 사용이 없으면 추천 탭부터 (목록 로드 후 페이지가 생기므로 그때 이동)
    LaunchedEffect(categories != null) {
        if (categories != null && recents.isEmpty()) pagerState.scrollToPage(1)
    }
    val tabListState = rememberLazyListState()
    LaunchedEffect(pagerState.currentPage) {
        tabListState.animateScrollToItem(pagerState.currentPage)
    }

    val skinMod = SKIN_TONES[skinIndex]
    val confirm = {
        DdaySettings.addRecentEmoji(context, selectedEmoji)
        onEmojiSelected(selectedEmoji)
        onDismiss()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── 미리보기 + 피부색 선택 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(categoryColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    EmojiText(selectedEmoji, fontSize = 28.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SKIN_DISPLAY.forEachIndexed { index, display ->
                        val isSel = index == skinIndex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSel) categoryColor.copy(alpha = 0.22f) else Color.Transparent
                                )
                                .then(
                                    if (isSel) Modifier.border(1.5.dp, categoryColor, CircleShape)
                                    else Modifier
                                )
                                .clickable { skinIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = display, fontSize = 15.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 검색 ──
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("검색 (예: 생일, 치과)", maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "검색어 지우기")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            val gridModifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
            val loaded = categories

            when {
                loaded == null -> Box(gridModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = categoryColor, strokeWidth = 2.dp)
                }

                query.isNotBlank() -> {
                    val results = remember(query, loaded) { EmojiCatalog.search(loaded, query) }
                    if (results.isEmpty()) {
                        EmptyHint("'${query.trim()}'에 맞는 이모지가 없어요", gridModifier)
                    } else {
                        EmojiGrid(
                            emojis = results.map { if (it.skinnable) EmojiCatalog.applySkinTone(it.emoji, skinMod) else it.emoji },
                            selected = selectedEmoji,
                            accent = categoryColor,
                            modifier = gridModifier,
                            onClick = { selectedEmoji = it }
                        )
                    }
                }

                else -> {
                    // ── 카테고리 탭 ──
                    LazyRow(
                        state = tabListState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val icons = listOf(RECENT_ICON, RECOMMENDED_ICON) + loaded.map { it.icon }
                        itemsIndexed(icons) { index, icon ->
                            val isSel = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSel) categoryColor.copy(alpha = 0.22f) else Color.Transparent
                                    )
                                    .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                                contentAlignment = Alignment.Center
                            ) {
                                EmojiText(icon, fontSize = 19.sp)
                            }
                        }
                    }

                    Text(
                        text = when (pagerState.currentPage) {
                            0 -> "최근 사용"
                            1 -> "추천"
                            else -> loaded.getOrNull(pagerState.currentPage - FIXED_PAGES)?.name.orEmpty()
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                    )

                    // ── 이모지 그리드 (스와이프로 카테고리 전환) ──
                    HorizontalPager(state = pagerState, modifier = gridModifier) { page ->
                        if (page == 0) {
                            if (recents.isEmpty()) {
                                EmptyHint("최근 사용한 이모지가 여기에 표시돼요", Modifier.fillMaxSize())
                            } else {
                                EmojiGrid(recents, selectedEmoji, categoryColor, Modifier.fillMaxSize()) {
                                    selectedEmoji = it
                                }
                            }
                        } else if (page == 1) {
                            EmojiGrid(recommended, selectedEmoji, categoryColor, Modifier.fillMaxSize()) {
                                selectedEmoji = it
                            }
                        } else {
                            val entries = loaded[page - FIXED_PAGES].entries
                            EmojiGrid(
                                emojis = entries.map { if (it.skinnable) EmojiCatalog.applySkinTone(it.emoji, skinMod) else it.emoji },
                                selected = selectedEmoji,
                                accent = categoryColor,
                                modifier = Modifier.fillMaxSize(),
                                onClick = { selectedEmoji = it }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 버튼 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("취소")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = confirm,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = categoryColor)
                ) {
                    Text("선택")
                }
            }
        }
    }
}

@Composable
private fun EmojiGrid(
    emojis: List<String>,
    selected: String,
    accent: Color,
    modifier: Modifier,
    onClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 42.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(emojis, key = { it }) { emoji ->
            val isSel = emoji == selected
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) accent.copy(alpha = 0.22f) else Color.Transparent)
                    .then(
                        if (isSel) Modifier.border(1.5.dp, accent, RoundedCornerShape(12.dp))
                        else Modifier
                    )
                    .clickable { onClick(emoji) },
                contentAlignment = Alignment.Center
            ) {
                EmojiText(emoji, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
