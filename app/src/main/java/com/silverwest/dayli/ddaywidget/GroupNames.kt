package com.silverwest.dayli.ddaywidget

/** D-Day 그룹 이름 규칙 (만들기·이름 변경 공통). 저장소와 분리해 단위 테스트 가능 */
object GroupNames {

    sealed class RenameCheck {
        data class Ok(val newName: String) : RenameCheck()
        object Unchanged : RenameCheck()  // 공백 제거 후 현재 이름과 같음 → 변경 없이 종료
        object Blank : RenameCheck()
        object Duplicate : RenameCheck()  // 다른 그룹과 이름이 같음 → "이미 있는 그룹이에요"
    }

    fun checkRename(oldName: String, rawNewName: String, existingGroups: List<String>): RenameCheck {
        val newName = rawNewName.trim()
        return when {
            newName.isEmpty() -> RenameCheck.Blank
            newName == oldName -> RenameCheck.Unchanged
            existingGroups.any { it == newName } -> RenameCheck.Duplicate
            else -> RenameCheck.Ok(newName)
        }
    }

    /** 그룹 순서에서 이름을 바꾸고 중복 제거 (같은 키가 두 번 들어가면 목록이 깨짐) */
    fun renameInOrder(order: List<String>, oldName: String, newName: String): List<String> =
        order.map { if (it == oldName) newName else it }.distinct()

    /**
     * 그룹 이모지를 새 이름으로 옮김: 현재 이모지를 새 이름에 덮어쓰고 이전 이름 키는 삭제.
     * 이전 이름에 이모지가 없으면 새 이름에 남아 있던 옛 값도 지움 (기본 📁로 보이게)
     */
    fun moveEmoji(emojis: Map<String, String>, oldName: String, newName: String): Map<String, String> {
        if (oldName == newName) return emojis
        val result = emojis.toMutableMap()
        val current = result.remove(oldName)
        if (current != null) result[newName] = current else result.remove(newName)
        return result
    }

    /** 위젯 카드 표시 여부: 위젯에서 직접 정한 적 없으면 앱 카드 설정을 따름 */
    fun widgetHeroEnabled(widgetSetting: Boolean?, appHeroEnabled: Boolean): Boolean =
        widgetSetting ?: appHeroEnabled
}
