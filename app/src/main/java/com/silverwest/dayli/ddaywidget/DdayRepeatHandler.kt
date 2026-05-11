package com.silverwest.dayli.ddaywidget

import java.util.Calendar
import java.util.Date

/**
 * 체크/서브태스크 토글에 대한 결과 타입.
 * 호출자(앱/위젯)는 이 결과로 새로고침 여부와 UX 분기를 결정한다.
 */
sealed class ToggleCheckResult {
    /** DB write 완료. 일반 체크/해제, 서브태스크 진행, 반복인데 다음 회차 계산 실패 시 일반 완료 등 모두 포함. */
    data class Updated(
        val itemId: Int,
        val reason: Reason = Reason.NORMAL
    ) : ToggleCheckResult()

    /** 반복 항목 완료 처리로 hidden 상태로 이동. nextShowDate는 effective 값(주기 - advanceDays). */
    data class HiddenUntil(
        val itemId: Int,
        val nextShowDate: Long
    ) : ToggleCheckResult()

    /** 아무 DB write 없음. */
    data class NoOp(val reason: Reason) : ToggleCheckResult()

    enum class Reason {
        NORMAL,                     // 일반 체크/해제
        SUBTASK_PROGRESS,           // 서브태스크 일부만 토글, parent 아직 미완료
        REPEAT_NO_NEXT_OCCURRENCE,  // 반복인데 getNextOccurrenceDate() == null → 일반 완료 처리
        INVALID_SUBTASK_INDEX,      // subTaskIndex가 범위 밖 → NoOp
        ITEM_NOT_FOUND              // DB 조회 실패 → NoOp
    }
}

/**
 * 체크 처리 공통 로직.
 *
 * - Android UI 의존성 없음. `dao`, `item`, `nowMillis`만 받는다.
 * - 앱(`DdayViewModel`)과 위젯(`DdayWidgetProvider`)이 같은 함수를 호출한다.
 * - 모든 DB write는 이 핸들러 내부에서 끝난다.
 *
 * 테스트 결정성을 위해 `nowMillis`를 항상 주입받는다.
 */
object DdayRepeatHandler {

    /**
     * 반복 항목의 effective `nextShowDate` 계산.
     *
     * - 기본: `nextDate - advanceDays` (미리 표시 일수만큼 당겨서 표시)
     * - 단, 그 값이 이미 과거면 `unhideReadyItems`에 의해 즉시 풀려버리므로
     *   `nextDate.time`(발생일 자체)으로 미룬다.
     *
     * 핸들러, `DdayViewModel.loadHiddenDdays()`, `RemoteViewsFactory`의 stale 보정이
     * 같은 식을 쓰도록 이 함수를 단일 소스로 사용한다.
     */
    fun computeEffectiveShowDate(item: DdayItem, nextDate: Date, nowMillis: Long): Long {
        val advanceDays = item.getAdvanceDays()
        val showDate = Calendar.getInstance().apply {
            time = nextDate
            add(Calendar.DAY_OF_YEAR, -advanceDays)
        }.timeInMillis
        return if (showDate <= nowMillis) nextDate.time else showDate
    }

    /**
     * 단일 아이템 체크 토글 (체크박스 1번 클릭).
     *
     * - 반복 항목 + 체크 진입: 다음 회차 계산해서 hidden 처리 → [HiddenUntil].
     * - 반복인데 nextOccurrence 못 구함: 일반 완료로 fallback → [Updated] with reason=REPEAT_NO_NEXT_OCCURRENCE.
     * - 그 외(일반 체크/해제): [Updated] with reason=NORMAL.
     */
    suspend fun toggleChecked(
        dao: DdayDao,
        item: DdayItem,
        nowMillis: Long
    ): ToggleCheckResult {
        val newChecked = !item.isChecked

        // 반복 진입(체크)이 아니면 일반 처리
        if (!newChecked || !item.isRepeating()) {
            val checkedAt = if (newChecked) nowMillis else null
            dao.updateChecked(item.id, newChecked, checkedAt)
            return ToggleCheckResult.Updated(item.id, ToggleCheckResult.Reason.NORMAL)
        }

        // 반복 항목 체크: 다음 회차 계산
        val nextDate = item.getNextOccurrenceDate(nowMillis)
            ?: run {
                // 다음 회차를 못 구함 → 일반 완료로 fallback
                dao.updateChecked(item.id, true, nowMillis)
                return ToggleCheckResult.Updated(
                    item.id,
                    ToggleCheckResult.Reason.REPEAT_NO_NEXT_OCCURRENCE
                )
            }

        val effectiveShowDate = computeEffectiveShowDate(item, nextDate, nowMillis)

        val resetSubTasks = item.getSubTaskList().map { it.copy(isChecked = false) }
        val updated = item.copy(
            date = if (item.isDday()) nextDate else item.date,
            subTasks = if (resetSubTasks.isEmpty()) item.subTasks
                       else DdayItem.subTasksToJson(resetSubTasks),
            isChecked = false,
            checkedAt = null,
            isHidden = true,
            nextShowDate = effectiveShowDate
        )
        dao.update(updated)
        return ToggleCheckResult.HiddenUntil(item.id, effectiveShowDate)
    }

    /**
     * 서브태스크 토글.
     *
     * 책임:
     * 1. 해당 인덱스 sub-task isChecked 반전
     * 2. 완료 항목을 하단으로 정렬 (앱/위젯 일관)
     * 3. parent allChecked 판정
     * 4. allChecked && repeating → [toggleChecked]의 반복 로직 재사용 (hidden 처리, sub-task reset)
     * 5. allChecked && !repeating → parent isChecked=true
     * 6. !allChecked → parent isChecked=false (parent 진행 중)
     */
    suspend fun toggleSubTask(
        dao: DdayDao,
        item: DdayItem,
        subTaskIndex: Int,
        nowMillis: Long
    ): ToggleCheckResult {
        val currentSubTasks = item.getSubTaskList().toMutableList()
        if (subTaskIndex !in currentSubTasks.indices) {
            return ToggleCheckResult.NoOp(ToggleCheckResult.Reason.INVALID_SUBTASK_INDEX)
        }

        // 1. 해당 인덱스 토글
        val target = currentSubTasks[subTaskIndex]
        currentSubTasks[subTaskIndex] = target.copy(isChecked = !target.isChecked)

        // 2. 완료 항목 하단 정렬 (안정 정렬 — 동일 isChecked 내 원순서 유지)
        val sorted = currentSubTasks.sortedBy { it.isChecked }

        val allChecked = sorted.all { it.isChecked }

        // 3-5. allChecked && 반복: 반복 회차 처리
        if (allChecked && item.isRepeating()) {
            val nextDate = item.getNextOccurrenceDate(nowMillis)
            if (nextDate != null) {
                val effectiveShowDate = computeEffectiveShowDate(item, nextDate, nowMillis)
                val resetSubTasks = sorted.map { it.copy(isChecked = false) }
                val updated = item.copy(
                    date = if (item.isDday()) nextDate else item.date,
                    subTasks = DdayItem.subTasksToJson(resetSubTasks),
                    isChecked = false,
                    checkedAt = null,
                    isHidden = true,
                    nextShowDate = effectiveShowDate
                )
                dao.update(updated)
                return ToggleCheckResult.HiddenUntil(item.id, effectiveShowDate)
            } else {
                // 반복인데 다음 회차 못 구함 → 일반 완료 fallback
                val updated = item.copy(
                    subTasks = DdayItem.subTasksToJson(sorted),
                    isChecked = true,
                    checkedAt = nowMillis
                )
                dao.update(updated)
                return ToggleCheckResult.Updated(
                    item.id,
                    ToggleCheckResult.Reason.REPEAT_NO_NEXT_OCCURRENCE
                )
            }
        }

        // 5. allChecked && !반복: parent 완료
        // 6. !allChecked: parent 미완료
        val updated = item.copy(
            subTasks = DdayItem.subTasksToJson(sorted),
            isChecked = allChecked,
            checkedAt = if (allChecked) nowMillis else null
        )
        dao.update(updated)
        return ToggleCheckResult.Updated(
            item.id,
            if (allChecked) ToggleCheckResult.Reason.NORMAL
            else ToggleCheckResult.Reason.SUBTASK_PROGRESS
        )
    }
}
