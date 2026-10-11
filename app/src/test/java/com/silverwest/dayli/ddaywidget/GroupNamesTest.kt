package com.silverwest.dayli.ddaywidget

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupNamesTest {

    // ── 이름 변경 검사 ──

    @Test
    fun rename_toExistingName_isBlocked() {
        val groups = listOf("A", "B")
        assertEquals(GroupNames.RenameCheck.Duplicate, GroupNames.checkRename("A", "B", groups))
        // 앞뒤 공백을 제거한 뒤 비교
        assertEquals(GroupNames.RenameCheck.Duplicate, GroupNames.checkRename("A", "  B ", groups))
    }

    @Test
    fun rename_toSameName_isUnchanged() {
        val groups = listOf("A", "B")
        assertEquals(GroupNames.RenameCheck.Unchanged, GroupNames.checkRename("A", "A", groups))
        assertEquals(GroupNames.RenameCheck.Unchanged, GroupNames.checkRename("A", " A  ", groups))
    }

    @Test
    fun rename_blank_isRejected() {
        assertEquals(GroupNames.RenameCheck.Blank, GroupNames.checkRename("A", "   ", listOf("A")))
    }

    @Test
    fun rename_toNewName_isTrimmed() {
        assertEquals(GroupNames.RenameCheck.Ok("C"), GroupNames.checkRename("A", " C ", listOf("A", "B")))
    }

    // ── 그룹 순서 ──

    @Test
    fun renameInOrder_neverProducesDuplicates() {
        // 화면·ViewModel 검사를 지나쳐도 저장 순서에 같은 키가 두 번 생기지 않음
        assertEquals(listOf("B"), GroupNames.renameInOrder(listOf("A", "B"), "A", "B"))
        assertEquals(listOf("C", "B"), GroupNames.renameInOrder(listOf("A", "B"), "A", "C"))
    }

    // ── 이모지 이동 ──

    @Test
    fun moveEmoji_aToBToA_keepsCurrentEmoji() {
        var emojis = mapOf("A" to "🏫")
        emojis = GroupNames.moveEmoji(emojis, "A", "B")
        assertEquals(mapOf("B" to "🏫"), emojis)
        emojis = emojis + ("B" to "✈️")  // B일 때 이모지 변경
        emojis = GroupNames.moveEmoji(emojis, "B", "A")
        assertEquals(mapOf("A" to "✈️"), emojis)  // A의 옛 이모지(🏫)가 되살아나지 않음
    }

    @Test
    fun moveEmoji_sameName_keepsKey() {
        val emojis = mapOf("A" to "🏫")
        assertEquals(emojis, GroupNames.moveEmoji(emojis, "A", "A"))
    }

    @Test
    fun moveEmoji_withoutEmoji_clearsStaleTarget() {
        // A는 기본 이모지, B 이름에 예전 값이 남아 있었던 경우 → 기본(📁)으로 보이게 지움
        assertEquals(emptyMap<String, String>(), GroupNames.moveEmoji(mapOf("B" to "🎮"), "A", "B"))
    }

    // ── 위젯의 가장 가까운 일정 카드 ──

    @Test
    fun widgetHero_beforeWidgetSetting_followsAppSwitch() {
        assertEquals(true, GroupNames.widgetHeroEnabled(null, appHeroEnabled = true))
        assertEquals(false, GroupNames.widgetHeroEnabled(null, appHeroEnabled = false))
    }

    @Test
    fun widgetHero_afterWidgetSetting_ignoresAppSwitch() {
        assertEquals(true, GroupNames.widgetHeroEnabled(true, appHeroEnabled = false))
        assertEquals(false, GroupNames.widgetHeroEnabled(false, appHeroEnabled = true))
    }
}
