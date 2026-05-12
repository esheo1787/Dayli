package com.silverwest.dayli.ddaywidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class BackupSerializerTest {

    private fun sampleSnapshot(): BackupSerializer.BackupSnapshot {
        val items = listOf(
            DdayItem(
                id = 1, title = "시험", date = Date(1735000000000L),
                memo = "수능", category = DdayCategory.OTHER.name,
                iconName = "📝", customColor = 0xFFE53935L,
                repeatType = RepeatType.NONE.name,
                itemType = ItemType.DDAY.name,
                groupName = "학업"
            ),
            DdayItem(
                id = 2, title = "운동", date = null,
                repeatType = RepeatType.DAILY.name,
                itemType = ItemType.TODO.name,
                subTasks = DdayItem.subTasksToJson(
                    listOf(SubTask("스쿼트", false), SubTask("푸시업", true))
                ),
            ),
            DdayItem(
                id = 3, title = "월간 점검", date = Date(1735000000000L),
                repeatType = RepeatType.MONTHLY.name,
                repeatDay = 15,
                itemType = ItemType.DDAY.name,
                advanceDisplayDays = 7,
                isHidden = true,
                nextShowDate = 1736000000000L,
                timeHour = 9, timeMinute = 30,
            )
        )
        val templates = listOf(
            TodoTemplate(
                id = 1, name = "아침 루틴",
                iconName = "🌅", customColor = 0xFFA8C5DAL,
                subTasks = TodoTemplate.subTasksToJson(
                    listOf(SubTask("물 한 잔", false), SubTask("스트레칭", false))
                ),
                createdAt = 1735000000000L
            )
        )
        return BackupSerializer.BackupSnapshot(
            ddayItems = items,
            templates = templates,
            groupOrder = listOf("학업", "건강"),
            groupEmojis = mapOf("학업" to "📚", "건강" to "💪"),
            ddaySort = "NEAREST",
            todoSort = "MY_ORDER",
        )
    }

    @Test
    fun `serialize and deserialize round-trips all fields`() {
        val original = sampleSnapshot()
        val json = BackupSerializer.serialize(original, appVersionCode = 13, nowMillis = 1735000000000L)
        val restored = BackupSerializer.deserialize(json)

        assertNotNull(restored)
        val r = restored!!
        assertEquals(original.ddayItems.size, r.ddayItems.size)
        assertEquals(original.templates.size, r.templates.size)
        assertEquals(original.groupOrder, r.groupOrder)
        assertEquals(original.groupEmojis, r.groupEmojis)
        assertEquals(original.ddaySort, r.ddaySort)
        assertEquals(original.todoSort, r.todoSort)

        // 각 필드 보존 확인
        val first = r.ddayItems[0]
        assertEquals("시험", first.title)
        assertEquals(1735000000000L, first.date?.time)
        assertEquals("수능", first.memo)
        assertEquals("📝", first.iconName)
        assertEquals(0xFFE53935L, first.customColor)
        assertEquals(ItemType.DDAY.name, first.itemType)
        assertEquals("학업", first.groupName)

        val todo = r.ddayItems[1]
        assertEquals(null, todo.date)
        assertEquals(RepeatType.DAILY.name, todo.repeatType)
        assertEquals(ItemType.TODO.name, todo.itemType)
        val subs = todo.getSubTaskList()
        assertEquals(2, subs.size)
        assertEquals("스쿼트", subs[0].title)
        assertEquals(true, subs[1].isChecked)

        val monthly = r.ddayItems[2]
        assertEquals(15, monthly.repeatDay)
        assertEquals(7, monthly.advanceDisplayDays)
        assertEquals(true, monthly.isHidden)
        assertEquals(1736000000000L, monthly.nextShowDate)
        assertEquals(9, monthly.timeHour)
        assertEquals(30, monthly.timeMinute)
    }

    @Test
    fun `preview reports correct counts without full parse`() {
        val original = sampleSnapshot()
        val json = BackupSerializer.serialize(original, 13, 1735000000000L)
        val preview = BackupSerializer.preview(json)

        assertNotNull(preview)
        val p = preview!!
        assertEquals(1, p.formatVersion)
        assertEquals(2, p.ddayCount) // 시험 + 월간 점검
        assertEquals(1, p.todoCount) // 운동
        assertEquals(1, p.templateCount)
        assertEquals(1735000000000L, p.exportedAt)
        assertEquals(13, p.appVersionCode)
    }

    @Test
    fun `deserialize returns null for invalid json`() {
        assertNull(BackupSerializer.deserialize("not a json"))
        assertNull(BackupSerializer.deserialize("{broken"))
    }

    @Test
    fun `deserialize returns null for missing format version`() {
        val invalid = """{"ddayItems":[]}"""
        assertNull(BackupSerializer.deserialize(invalid))
    }

    @Test
    fun `deserialize returns null for missing required arrays`() {
        val invalid = """{"formatVersion":1}"""
        assertNull(BackupSerializer.deserialize(invalid))
        assertNull(BackupSerializer.preview(invalid))
    }

    @Test
    fun `deserialize returns null for unsupported future version`() {
        val future = """{"formatVersion":9999,"ddayItems":[]}"""
        assertNull(BackupSerializer.deserialize(future))
    }

    @Test
    fun `preview returns null for unsupported future version`() {
        val future = """{"formatVersion":9999,"ddayItems":[]}"""
        assertNull(BackupSerializer.preview(future))
    }

    @Test
    fun `deserialize returns null when item is missing required title`() {
        val invalid = """{"formatVersion":1,"ddayItems":[{"id":1}],"templates":[]}"""
        assertNull(BackupSerializer.deserialize(invalid))
    }

    @Test
    fun `deserialize returns null when template is missing required name`() {
        val invalid = """{"formatVersion":1,"ddayItems":[],"templates":[{"id":1}]}"""
        assertNull(BackupSerializer.deserialize(invalid))
    }

    @Test
    fun `deserialize rejects item missing itemType (preview-import consistency)`() {
        // itemType 누락이면 preview는 0개로 카운트하는데, deserialize가 default DDAY로 받으면
        // 미리보기와 실제 가져오기 결과가 다름. itemType 필수화로 해당 불일치 방지.
        val invalid = """{"formatVersion":1,"ddayItems":[{"id":1,"title":"x"}],"templates":[]}"""
        assertNull(BackupSerializer.deserialize(invalid))
    }

    @Test
    fun `deserialize rejects item with invalid itemType value`() {
        val invalid = """
            {"formatVersion":1,
             "ddayItems":[{"id":1,"title":"x","itemType":"UNKNOWN"}],
             "templates":[]}
        """.trimIndent()
        assertNull(BackupSerializer.deserialize(invalid))
    }

    @Test
    fun `deserialize handles empty snapshot gracefully`() {
        val empty = BackupSerializer.BackupSnapshot(
            ddayItems = emptyList(), templates = emptyList(),
            groupOrder = emptyList(), groupEmojis = emptyMap(),
            ddaySort = "NEAREST", todoSort = "MY_ORDER"
        )
        val json = BackupSerializer.serialize(empty, 1, 100L)
        val restored = BackupSerializer.deserialize(json)
        assertNotNull(restored)
        assertTrue(restored!!.ddayItems.isEmpty())
        assertTrue(restored.templates.isEmpty())
    }

    @Test
    fun `serialize then preview is consistent for empty data`() {
        val empty = BackupSerializer.BackupSnapshot(
            ddayItems = emptyList(), templates = emptyList(),
            groupOrder = emptyList(), groupEmojis = emptyMap(),
            ddaySort = "NEAREST", todoSort = "MY_ORDER"
        )
        val json = BackupSerializer.serialize(empty, 1, 100L)
        val preview = BackupSerializer.preview(json)
        assertNotNull(preview)
        assertEquals(0, preview!!.ddayCount)
        assertEquals(0, preview.todoCount)
        assertEquals(0, preview.templateCount)
    }

    @Test
    fun `serialize preserves null fields correctly`() {
        val item = DdayItem(
            id = 5, title = "최소 항목",
            date = null, memo = null, iconName = null, customColor = null,
            repeatDay = null, subTasks = null, nextShowDate = null,
            advanceDisplayDays = null, groupName = null, templateId = null,
            timeHour = null, timeMinute = null, notifications = null,
        )
        val snap = BackupSerializer.BackupSnapshot(
            listOf(item), emptyList(), emptyList(), emptyMap(),
            "NEAREST", "MY_ORDER"
        )
        val json = BackupSerializer.serialize(snap, 1, 100L)
        val restored = BackupSerializer.deserialize(json)!!
        val r = restored.ddayItems[0]
        assertEquals(null, r.date)
        assertEquals(null, r.memo)
        assertEquals(null, r.iconName)
        assertEquals(null, r.customColor)
        assertEquals(null, r.repeatDay)
        assertEquals(null, r.subTasks)
        assertEquals(null, r.nextShowDate)
        assertEquals(null, r.advanceDisplayDays)
        assertEquals(null, r.groupName)
        assertEquals(null, r.templateId)
        assertEquals(null, r.timeHour)
        assertEquals(null, r.timeMinute)
        assertEquals(null, r.notifications)
    }
}
