package com.silverwest.dayli.ddaywidget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

// ─── 내부 데이터 클래스 ───
private class E(val e: String, val s: Boolean = false)
private class Cat(val icon: String, val name: String, val items: List<E>)

// ─── 피부색 수정자 (Fitzpatrick) ───
private val SKIN_TONES = listOf(
    "", "\uD83C\uDFFB", "\uD83C\uDFFC", "\uD83C\uDFFD", "\uD83C\uDFFE", "\uD83C\uDFFF"
)
private val SKIN_DISPLAY = listOf("✋", "✋🏻", "✋🏼", "✋🏽", "✋🏾", "✋🏿")

private fun applySkinTone(emoji: String, mod: String): String {
    if (mod.isEmpty()) return emoji
    val sb = StringBuilder()
    val cp = emoji.codePointAt(0)
    sb.appendCodePoint(cp)
    sb.append(mod)
    var i = Character.charCount(cp)
    while (i < emoji.length) {
        val next = emoji.codePointAt(i)
        sb.appendCodePoint(next)
        i += Character.charCount(next)
    }
    return sb.toString()
}

// ─── 카테고리 데이터 (성별 변형 포함) ───
private val categories = listOf(
    // 1. 표정
    Cat("😀", "표정", listOf(
        E("😀"), E("😃"), E("😄"), E("😁"), E("😆"), E("😅"), E("🤣"), E("😂"),
        E("🙂"), E("🙃"), E("😉"), E("😊"), E("😇"), E("🥰"), E("😍"), E("🤩"),
        E("😘"), E("😗"), E("😋"), E("😛"), E("😜"), E("🤪"), E("😝"), E("🤑"),
        E("🤗"), E("🤭"), E("🤫"), E("🤔"), E("😐"), E("😑"), E("😶"), E("😏"),
        E("😒"), E("🙄"), E("😬"), E("😌"), E("😔"), E("😪"), E("🤤"), E("😴"),
        E("😷"), E("🤒"), E("🤕"), E("🤢"), E("🤮"), E("🥵"), E("🥶"), E("🤯"),
        E("🥳"), E("🤠"), E("🥸"), E("😎"), E("🤓"), E("🧐"), E("😕"), E("😟"),
        E("🙁"), E("😮"), E("😯"), E("😲"), E("😳"), E("🥺"), E("😦"), E("😧"),
        E("😨"), E("😰"), E("😥"), E("😢"), E("😭"), E("😱"), E("😖"), E("😣"),
        E("😞"), E("😓"), E("😩"), E("😫"), E("🥱"), E("😤"), E("😡"), E("😠"),
        E("🤬"), E("😈"), E("👿"), E("💀"), E("☠️"), E("💩"), E("🤡"), E("👹"),
        E("👺"), E("👻"), E("👽"), E("👾"), E("🤖"), E("🙈"), E("🙉"), E("🙊")
    )),

    // 2. 사람 (성별 변형 모두 표시)
    Cat("🧑", "사람", listOf(
        // 기본 사람
        E("👶", true), E("🧒", true), E("👦", true), E("👧", true),
        E("🧑", true), E("👨", true), E("👩", true),
        E("🧓", true), E("👴", true), E("👵", true),
        // 수염
        E("🧔", true), E("🧔‍♂️", true), E("🧔‍♀️", true),
        // 머리스타일 (중립/남성/여성)
        E("🧑‍🦰", true), E("👨‍🦰", true), E("👩‍🦰", true),
        E("🧑‍🦱", true), E("👨‍🦱", true), E("👩‍🦱", true),
        E("🧑‍🦳", true), E("👨‍🦳", true), E("👩‍🦳", true),
        E("🧑‍🦲", true), E("👨‍🦲", true), E("👩‍🦲", true),
        // 직업 (중립/남성/여성)
        E("🧑‍⚕️", true), E("👨‍⚕️", true), E("👩‍⚕️", true),
        E("🧑‍🎓", true), E("👨‍🎓", true), E("👩‍🎓", true),
        E("🧑‍🏫", true), E("👨‍🏫", true), E("👩‍🏫", true),
        E("🧑‍⚖️", true), E("👨‍⚖️", true), E("👩‍⚖️", true),
        E("🧑‍🌾", true), E("👨‍🌾", true), E("👩‍🌾", true),
        E("🧑‍🍳", true), E("👨‍🍳", true), E("👩‍🍳", true),
        E("🧑‍🔧", true), E("👨‍🔧", true), E("👩‍🔧", true),
        E("🧑‍🏭", true), E("👨‍🏭", true), E("👩‍🏭", true),
        E("🧑‍💼", true), E("👨‍💼", true), E("👩‍💼", true),
        E("🧑‍🔬", true), E("👨‍🔬", true), E("👩‍🔬", true),
        E("🧑‍💻", true), E("👨‍💻", true), E("👩‍💻", true),
        E("🧑‍🎤", true), E("👨‍🎤", true), E("👩‍🎤", true),
        E("🧑‍🎨", true), E("👨‍🎨", true), E("👩‍🎨", true),
        E("🧑‍✈️", true), E("👨‍✈️", true), E("👩‍✈️", true),
        E("🧑‍🚀", true), E("👨‍🚀", true), E("👩‍🚀", true),
        E("🧑‍🚒", true), E("👨‍🚒", true), E("👩‍🚒", true),
        // 제복/모자
        E("👮", true), E("👮‍♂️", true), E("👮‍♀️", true),
        E("🕵️", true), E("🕵️‍♂️", true), E("🕵️‍♀️", true),
        E("💂", true), E("💂‍♂️", true), E("💂‍♀️", true),
        E("🥷", true),
        E("👷", true), E("👷‍♂️", true), E("👷‍♀️", true),
        // 왕관/격식
        E("🫅", true), E("🤴", true), E("👸", true),
        E("👳", true), E("👳‍♂️", true), E("👳‍♀️", true),
        E("🧕", true),
        E("🤵", true), E("🤵‍♂️", true), E("🤵‍♀️", true),
        E("👰", true), E("👰‍♂️", true), E("👰‍♀️", true),
        // 임신/아기
        E("🤰", true), E("🫃", true), E("🫄", true),
        E("🤱", true), E("👼", true),
        // 산타
        E("🎅", true), E("🤶", true), E("🧑‍🎄", true),
        // 판타지 (중립/남성/여성)
        E("🦸", true), E("🦸‍♂️", true), E("🦸‍♀️", true),
        E("🦹", true), E("🦹‍♂️", true), E("🦹‍♀️", true),
        E("🧙", true), E("🧙‍♂️", true), E("🧙‍♀️", true),
        E("🧚", true), E("🧚‍♂️", true), E("🧚‍♀️", true),
        E("🧛", true), E("🧛‍♂️", true), E("🧛‍♀️", true),
        E("🧜", true), E("🧜‍♂️", true), E("🧜‍♀️", true),
        E("🧝", true), E("🧝‍♂️", true), E("🧝‍♀️", true),
        E("🧞"), E("🧞‍♂️"), E("🧞‍♀️"),
        E("🧟"), E("🧟‍♂️"), E("🧟‍♀️"),
        // 표현 (중립/남성/여성)
        E("💆", true), E("💆‍♂️", true), E("💆‍♀️", true),
        E("💇", true), E("💇‍♂️", true), E("💇‍♀️", true),
        E("🚶", true), E("🚶‍♂️", true), E("🚶‍♀️", true),
        E("🧍", true), E("🧍‍♂️", true), E("🧍‍♀️", true),
        E("🧎", true), E("🧎‍♂️", true), E("🧎‍♀️", true),
        E("🏃", true), E("🏃‍♂️", true), E("🏃‍♀️", true),
        E("💃", true), E("🕺", true),
        E("🕴️", true),
        E("🧖", true), E("🧖‍♂️", true), E("🧖‍♀️", true),
        E("🧗", true), E("🧗‍♂️", true), E("🧗‍♀️", true),
        E("🧘", true), E("🧘‍♂️", true), E("🧘‍♀️", true),
        // 제스처 (중립/남성/여성)
        E("🙅", true), E("🙅‍♂️", true), E("🙅‍♀️", true),
        E("🙆", true), E("🙆‍♂️", true), E("🙆‍♀️", true),
        E("🙋", true), E("🙋‍♂️", true), E("🙋‍♀️", true),
        E("🙇", true), E("🙇‍♂️", true), E("🙇‍♀️", true),
        E("🤷", true), E("🤷‍♂️", true), E("🤷‍♀️", true),
        E("🤦", true), E("🤦‍♂️", true), E("🤦‍♀️", true),
        E("💁", true), E("💁‍♂️", true), E("💁‍♀️", true),
        E("🙍", true), E("🙍‍♂️", true), E("🙍‍♀️", true),
        E("🙎", true), E("🙎‍♂️", true), E("🙎‍♀️", true)
    )),

    // 3. 손·몸
    Cat("👋", "손·몸", listOf(
        E("👋", true), E("🤚", true), E("🖐️", true), E("✋", true),
        E("🖖", true), E("🫱", true), E("🫲", true), E("🫳", true),
        E("🫴", true), E("👌", true), E("🤌", true), E("🤏", true),
        E("✌️", true), E("🤞", true), E("🫰", true), E("🤟", true),
        E("🤘", true), E("🤙", true), E("👈", true), E("👉", true),
        E("👆", true), E("🖕", true), E("👇", true), E("☝️", true),
        E("🫵", true), E("👍", true), E("👎", true), E("✊", true),
        E("👊", true), E("🤛", true), E("🤜", true), E("👏", true),
        E("🙌", true), E("🫶", true), E("👐", true), E("🤲", true),
        E("🤝", true), E("🙏", true), E("✍️", true), E("💅", true),
        E("🤳", true), E("💪", true),
        E("🦾"), E("🦿"), E("🦵", true), E("🦶", true),
        E("👂", true), E("🦻", true), E("👃", true),
        E("🧠"), E("🫀"), E("🫁"), E("🦷"), E("🦴"),
        E("👀"), E("👁️"), E("👅"), E("👄"), E("🫦"),
        E("👣"), E("🫂"), E("💏"), E("💑"), E("👪")
    )),

    // 4. 동물
    Cat("🐶", "동물", listOf(
        E("🐶"), E("🐱"), E("🐭"), E("🐹"), E("🐰"), E("🦊"), E("🐻"), E("🐼"),
        E("🐨"), E("🐯"), E("🦁"), E("🐮"), E("🐷"), E("🐸"), E("🐵"), E("🙈"),
        E("🙉"), E("🙊"), E("🐔"), E("🐧"), E("🐦"), E("🐤"), E("🦆"), E("🦅"),
        E("🦉"), E("🦇"), E("🐺"), E("🐗"), E("🐴"), E("🦄"), E("🐝"), E("🐛"),
        E("🦋"), E("🐌"), E("🐞"), E("🐜"), E("🐢"), E("🐍"), E("🦎"), E("🦖"),
        E("🐙"), E("🦑"), E("🦐"), E("🦀"), E("🐠"), E("🐟"), E("🐡"), E("🐬"),
        E("🐳"), E("🐋"), E("🦈"), E("🐊"), E("🐅"), E("🐆"), E("🦓"), E("🦍"),
        E("🦧"), E("🐘"), E("🦛"), E("🦏"), E("🐪"), E("🐫"), E("🦒"), E("🦘")
    )),

    // 5. 음식
    Cat("🍎", "음식", listOf(
        E("🍎"), E("🍐"), E("🍊"), E("🍋"), E("🍌"), E("🍉"), E("🍇"), E("🍓"),
        E("🍈"), E("🍒"), E("🍑"), E("🥭"), E("🍍"), E("🥥"), E("🥝"), E("🍅"),
        E("🍆"), E("🥑"), E("🥦"), E("🥬"), E("🥒"), E("🌶"), E("🌽"), E("🥕"),
        E("🥔"), E("🍠"), E("🍞"), E("🧀"), E("🍖"), E("🍗"), E("🥩"), E("🌭"),
        E("🍔"), E("🍟"), E("🍕"), E("🥪"), E("🌮"), E("🌯"), E("🥙"), E("🍣"),
        E("🍰"), E("🍩"), E("🍪"), E("🎂"), E("☕"), E("🍵"), E("🍺"), E("🥤")
    )),

    // 6. 활동 (스포츠 성별 변형 포함)
    Cat("⚽", "활동", listOf(
        E("⚽"), E("🏀"), E("🏈"), E("⚾"), E("🥎"), E("🎾"), E("🏐"), E("🏉"),
        E("🎱"), E("🏓"), E("🏸"), E("🏒"), E("🥍"), E("🏏"), E("⛳"), E("🎣"),
        E("🥊"), E("🥋"), E("🎽"), E("🛹"), E("🛼"), E("🛷"), E("⛸"), E("🥌"),
        E("🎿"), E("🏂", true),
        // 스포츠 (중립/남성/여성)
        E("🏌️", true), E("🏌️‍♂️", true), E("🏌️‍♀️", true),
        E("🏄", true), E("🏄‍♂️", true), E("🏄‍♀️", true),
        E("🚣", true), E("🚣‍♂️", true), E("🚣‍♀️", true),
        E("🏊", true), E("🏊‍♂️", true), E("🏊‍♀️", true),
        E("⛹️", true), E("⛹️‍♂️", true), E("⛹️‍♀️", true),
        E("🏋️", true), E("🏋️‍♂️", true), E("🏋️‍♀️", true),
        E("🚴", true), E("🚴‍♂️", true), E("🚴‍♀️", true),
        E("🚵", true), E("🚵‍♂️", true), E("🚵‍♀️", true),
        E("🤸", true), E("🤸‍♂️", true), E("🤸‍♀️", true),
        E("🤼"), E("🤼‍♂️"), E("🤼‍♀️"),
        E("🤽", true), E("🤽‍♂️", true), E("🤽‍♀️", true),
        E("🤾", true), E("🤾‍♂️", true), E("🤾‍♀️", true),
        E("🤹", true), E("🤹‍♂️", true), E("🤹‍♀️", true),
        E("💪", true), E("🎮"), E("🎲"), E("🎯"), E("🎳"), E("🎪"), E("🎨"), E("🎬")
    )),

    // 7. 여행
    Cat("🚗", "여행", listOf(
        E("🚗"), E("🚕"), E("🚙"), E("🚌"), E("🚎"), E("🏎"), E("🚓"), E("🚑"),
        E("🚒"), E("🚐"), E("🚚"), E("🚛"), E("🚜"), E("🛵"), E("🏍"), E("🚲"),
        E("🛴"), E("🚏"), E("🚅"), E("🚆"), E("🚇"), E("🚊"), E("🚉"), E("✈️"),
        E("🛫"), E("🛬"), E("🚀"), E("🛸"), E("🚁"), E("🛶"), E("⛵"), E("🚤"),
        E("🛥"), E("🛳"), E("🚢"), E("⚓"), E("🏖"), E("🏝"), E("🏔"), E("⛰"),
        E("🌋"), E("🗻"), E("🏕"), E("🏠"), E("🏡"), E("🏢"), E("🏣"), E("🏥")
    )),

    // 8. 사물
    Cat("💼", "사물", listOf(
        E("💼"), E("📱"), E("💻"), E("⌨️"), E("🖥"), E("🖨"), E("💾"), E("📀"),
        E("🎥"), E("📷"), E("📸"), E("📹"), E("🔍"), E("🔎"), E("💡"), E("🔦"),
        E("📔"), E("📕"), E("📖"), E("📗"), E("📘"), E("📙"), E("📚"), E("📓"),
        E("📒"), E("📃"), E("📄"), E("📰"), E("📑"), E("🔖"), E("🏷"), E("💰"),
        E("💵"), E("💳"), E("🧾"), E("✉"), E("📧"), E("📦"), E("🔑"), E("🔒"),
        E("🔓"), E("🛒"), E("💎"), E("⏰"), E("⌚"), E("📌"), E("📎"), E("✂️")
    )),

    // 9. 기호
    Cat("❤️", "기호", listOf(
        E("❤️"), E("🧡"), E("💛"), E("💚"), E("💙"), E("💜"), E("🖤"), E("🤍"),
        E("🤎"), E("💔"), E("❣"), E("💕"), E("💞"), E("💟"), E("💗"), E("💖"),
        E("💝"), E("💘"), E("✅"), E("❌"), E("⭕"), E("❗"), E("❓"), E("⚡"),
        E("🔥"), E("💥"), E("✨"), E("⭐"), E("🌟"), E("💫"), E("🎵"), E("🎶"),
        E("🔔"), E("📣"), E("📢"), E("🏁"), E("☮"), E("☯"), E("♻"), E("⚜"),
        E("🔰"), E("💠"), E("🔷"), E("🔶"), E("🔵"), E("🟢"), E("🔴"), E("🟡")
    )),

    // 10. 깃발
    Cat("🏁", "깃발", listOf(
        E("🏳"), E("🏴"), E("🏁"), E("🚩"), E("🎌"), E("🏴‍☠️"), E("🇰🇷"), E("🇺🇸"),
        E("🇯🇵"), E("🇨🇳"), E("🇬🇧"), E("🇫🇷"), E("🇩🇪"), E("🇮🇹"), E("🇪🇸"), E("🇷🇺"),
        E("🇧🇷"), E("🇦🇺"), E("🇨🇦"), E("🇲🇽"), E("🇮🇳"), E("🇮🇩"), E("🇹🇷"), E("🇸🇦"),
        E("🇦🇪"), E("🇹🇭"), E("🇻🇳"), E("🇵🇭"), E("🇲🇾"), E("🇸🇬"), E("🇳🇿"), E("🇨🇭"),
        E("🇸🇪"), E("🇳🇴"), E("🇩🇰"), E("🇫🇮"), E("🇳🇱"), E("🇧🇪"), E("🇵🇱"), E("🇦🇹")
    ))
)

// ─── 이모지 피커 다이얼로그 ───

@Composable
fun EmojiPickerDialog(
    currentEmoji: String,
    categoryColor: Color,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedEmoji by remember { mutableStateOf(currentEmoji) }
    val pagerState = rememberPagerState(pageCount = { categories.size })
    var skinIndex by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {

                // ── 미리보기 + 피부색 선택 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(categoryColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = selectedEmoji, fontSize = 24.sp)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // 피부색 선택 바
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        SKIN_DISPLAY.forEachIndexed { index, display ->
                            val isSel = index == skinIndex
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSel) categoryColor.copy(alpha = 0.2f)
                                        else Color.Transparent
                                    )
                                    .then(
                                        if (isSel) Modifier.border(
                                            1.5.dp, categoryColor, CircleShape
                                        ) else Modifier
                                    )
                                    .clickable { skinIndex = index },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = display, fontSize = 15.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── 카테고리 탭 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    categories.forEachIndexed { index, cat ->
                        val isSel = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSel) categoryColor.copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                                .then(
                                    if (isSel) Modifier.border(
                                        1.5.dp, categoryColor, RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = cat.icon, fontSize = 18.sp)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                // ── 이모지 그리드 (스와이프로 카테고리 전환) ──
                val skinMod = SKIN_TONES[skinIndex]

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) { page ->
                    val pageItems = categories[page].items
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(pageItems) { item ->
                            val display = if (item.s && skinMod.isNotEmpty()) {
                                applySkinTone(item.e, skinMod)
                            } else {
                                item.e
                            }
                            val isSel = display == selectedEmoji
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSel) categoryColor.copy(alpha = 0.2f)
                                        else Color.Transparent
                                    )
                                    .then(
                                        if (isSel) Modifier.border(
                                            1.5.dp, categoryColor, RoundedCornerShape(6.dp)
                                        ) else Modifier.border(
                                            0.5.dp, Color.Gray.copy(alpha = 0.15f),
                                            RoundedCornerShape(6.dp)
                                        )
                                    )
                                    .clickable { selectedEmoji = display },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = display, fontSize = 20.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
