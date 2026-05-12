package com.silverwest.dayli.ddaywidget

import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * 백업/복원용 JSON 직렬화 (Android 의존성 0, 순수 Kotlin).
 *
 * 책임: BackupSnapshot ↔ JSON 문자열 변환. IO/SAF는 [BackupRepository]에서.
 *
 * 포맷 버전:
 *  - v1: 최초 (2026-05). DdayItem, TodoTemplate, 그룹 순서/이모지, 정렬 옵션 포함.
 *
 * 향후 필드 추가는 lower version에서도 안전하게 옵셔널 처리할 것.
 */
object BackupSerializer {

    const val CURRENT_VERSION = 1

    /** 백업 대상 데이터 한 묶음. */
    data class BackupSnapshot(
        val ddayItems: List<DdayItem>,
        val templates: List<TodoTemplate>,
        val groupOrder: List<String>,
        val groupEmojis: Map<String, String>,
        val ddaySort: String,
        val todoSort: String,
    )

    /** 가져오기 전 사용자에게 보여줄 요약. */
    data class BackupPreview(
        val formatVersion: Int,
        val ddayCount: Int,
        val todoCount: Int,
        val templateCount: Int,
        val exportedAt: Long,
        val appVersionCode: Int,
    )

    enum class ImportMode { OVERWRITE, MERGE }

    /**
     * 스냅샷 + 메타 → JSON 문자열.
     * id는 보존하지만 가져오기에서 [ImportMode.MERGE]면 새 ID 부여됨.
     */
    fun serialize(
        snapshot: BackupSnapshot,
        appVersionCode: Int,
        nowMillis: Long,
    ): String {
        val root = JSONObject().apply {
            put("formatVersion", CURRENT_VERSION)
            put("exportedAt", nowMillis)
            put("appVersionCode", appVersionCode)
            put("ddayItems", JSONArray().apply {
                snapshot.ddayItems.forEach { put(ddayItemToJson(it)) }
            })
            put("templates", JSONArray().apply {
                snapshot.templates.forEach { put(templateToJson(it)) }
            })
            put("groupOrder", JSONArray(snapshot.groupOrder))
            put("groupEmojis", JSONObject(snapshot.groupEmojis as Map<*, *>))
            put("ddaySort", snapshot.ddaySort)
            put("todoSort", snapshot.todoSort)
        }
        return root.toString()
    }

    /**
     * JSON 문자열 → 스냅샷. 파싱 실패 또는 미지원 포맷 버전이면 null.
     */
    fun deserialize(json: String): BackupSnapshot? {
        return try {
            val root = JSONObject(json)
            val version = root.optInt("formatVersion", 0)
            if (version < 1 || version > CURRENT_VERSION) return null

            val items = root.requiredObjectArray("ddayItems") { ddayItemFromJson(it) }
                ?: return null

            val templates = root.requiredObjectArray("templates") { templateFromJson(it) }
                ?: return null

            val groupOrder = root.optJSONArray("groupOrder")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }
            } ?: emptyList()

            val groupEmojis = root.optJSONObject("groupEmojis")?.let { obj ->
                val keys = obj.keys()
                buildMap {
                    while (keys.hasNext()) {
                        val k = keys.next()
                        put(k, obj.optString(k))
                    }
                }
            } ?: emptyMap()

            BackupSnapshot(
                ddayItems = items,
                templates = templates,
                groupOrder = groupOrder,
                groupEmojis = groupEmojis,
                ddaySort = root.optString("ddaySort", "NEAREST"),
                todoSort = root.optString("todoSort", "MY_ORDER"),
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * JSON 문자열 → 미리보기 (전체 파싱 없이 카운트만).
     * 파싱 실패면 null.
     */
    fun preview(json: String): BackupPreview? {
        return try {
            val root = JSONObject(json)
            val version = root.optInt("formatVersion", 0)
            if (version < 1 || version > CURRENT_VERSION) return null

            val items = root.optJSONArray("ddayItems") ?: return null
            val templates = root.optJSONArray("templates") ?: return null

            BackupPreview(
                formatVersion = version,
                ddayCount = (0 until items.length()).count { i ->
                    items.optJSONObject(i)?.optString("itemType") == ItemType.DDAY.name
                },
                todoCount = (0 until items.length()).count { i ->
                    items.optJSONObject(i)?.optString("itemType") == ItemType.TODO.name
                },
                templateCount = templates.length(),
                exportedAt = root.optLong("exportedAt", 0L),
                appVersionCode = root.optInt("appVersionCode", 0),
            )
        } catch (_: Exception) {
            null
        }
    }

    // ===== DdayItem ↔ JSON =====

    private fun ddayItemToJson(item: DdayItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        if (item.date != null) put("date", item.date.time) else put("date", JSONObject.NULL)
        putOrNull("memo", item.memo)
        put("isChecked", item.isChecked)
        if (item.checkedAt != null) put("checkedAt", item.checkedAt) else put("checkedAt", JSONObject.NULL)
        put("category", item.category)
        putOrNull("iconName", item.iconName)
        if (item.customColor != null) put("customColor", item.customColor) else put("customColor", JSONObject.NULL)
        put("repeatType", item.repeatType)
        if (item.repeatDay != null) put("repeatDay", item.repeatDay) else put("repeatDay", JSONObject.NULL)
        put("itemType", item.itemType)
        put("sortOrder", item.sortOrder)
        putOrNull("subTasks", item.subTasks)
        put("isHidden", item.isHidden)
        if (item.nextShowDate != null) put("nextShowDate", item.nextShowDate) else put("nextShowDate", JSONObject.NULL)
        if (item.advanceDisplayDays != null) put("advanceDisplayDays", item.advanceDisplayDays) else put("advanceDisplayDays", JSONObject.NULL)
        putOrNull("groupName", item.groupName)
        if (item.templateId != null) put("templateId", item.templateId) else put("templateId", JSONObject.NULL)
        if (item.timeHour != null) put("timeHour", item.timeHour) else put("timeHour", JSONObject.NULL)
        if (item.timeMinute != null) put("timeMinute", item.timeMinute) else put("timeMinute", JSONObject.NULL)
        putOrNull("notifications", item.notifications)
    }

    private fun ddayItemFromJson(obj: JSONObject): DdayItem = DdayItem(
        id = obj.optInt("id", 0),
        title = obj.requiredString("title"),
        date = obj.optNullableLong("date")?.let { Date(it) },
        memo = obj.optNullableString("memo"),
        isChecked = obj.optBoolean("isChecked", false),
        checkedAt = obj.optNullableLong("checkedAt"),
        category = obj.optString("category", DdayCategory.OTHER.name),
        iconName = obj.optNullableString("iconName"),
        customColor = obj.optNullableLong("customColor"),
        repeatType = obj.optString("repeatType", RepeatType.NONE.name),
        repeatDay = obj.optNullableInt("repeatDay"),
        itemType = obj.requiredString("itemType").also {
            require(it == ItemType.DDAY.name || it == ItemType.TODO.name) {
                "Invalid itemType: $it"
            }
        },
        sortOrder = obj.optInt("sortOrder", 0),
        subTasks = obj.optNullableString("subTasks"),
        isHidden = obj.optBoolean("isHidden", false),
        nextShowDate = obj.optNullableLong("nextShowDate"),
        advanceDisplayDays = obj.optNullableInt("advanceDisplayDays"),
        groupName = obj.optNullableString("groupName"),
        templateId = obj.optNullableInt("templateId"),
        timeHour = obj.optNullableInt("timeHour"),
        timeMinute = obj.optNullableInt("timeMinute"),
        notifications = obj.optNullableString("notifications"),
    )

    // ===== TodoTemplate ↔ JSON =====

    private fun templateToJson(t: TodoTemplate): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("name", t.name)
        put("iconName", t.iconName)
        put("customColor", t.customColor)
        putOrNull("subTasks", t.subTasks)
        put("createdAt", t.createdAt)
    }

    private fun templateFromJson(obj: JSONObject): TodoTemplate = TodoTemplate(
        id = obj.optInt("id", 0),
        name = obj.requiredString("name"),
        iconName = obj.optString("iconName", "📋"),
        customColor = obj.optLong("customColor", 0xFFA8C5DAL),
        subTasks = obj.optNullableString("subTasks"),
        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
    )

    // ===== JSON helpers =====

    private fun JSONObject.putOrNull(key: String, value: String?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun <T> JSONObject.requiredObjectArray(
        key: String,
        convert: (JSONObject) -> T,
    ): List<T>? {
        val arr = optJSONArray(key) ?: return null
        return (0 until arr.length()).map { i ->
            val obj = arr.optJSONObject(i) ?: return null
            convert(obj)
        }
    }

    private fun JSONObject.requiredString(key: String): String {
        if (!has(key) || isNull(key)) throw IllegalArgumentException("Missing $key")
        return getString(key)
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return optLong(key)
    }
}
