package com.silverwest.dayli.ddaywidget

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 백업 파일 입출력 + DB 반영.
 *
 * Storage Access Framework가 준 [Uri]에 대해 [Context.contentResolver]로 읽고 쓴다.
 * [BackupSerializer]를 통해 JSON ↔ 데이터 변환.
 */
object BackupRepository {

    sealed class Result {
        data class Success(val count: Int) : Result()
        data class Failure(val reason: String) : Result()
    }

    /**
     * 현재 DB 상태를 백업 JSON으로 직렬화해서 [uri]에 쓴다.
     */
    suspend fun export(
        context: Context,
        uri: Uri,
        dao: DdayDao,
        templateDao: TodoTemplateDao,
    ): Result = withContext(Dispatchers.IO) {
        try {
            val items = dao.getAll()
            val templates = templateDao.getAll()
            val snapshot = BackupSerializer.BackupSnapshot(
                ddayItems = items,
                templates = templates,
                groupOrder = DdaySettings.getGroupOrder(context),
                groupEmojis = items.mapNotNull { it.groupName }
                    .toSet()
                    .associateWith { DdaySettings.getGroupEmoji(context, it) },
                ddaySort = DdaySettings.getDdaySort(context),
                todoSort = DdaySettings.getTodoSort(context),
            )

            val versionCode = try {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                @Suppress("DEPRECATION")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    info.longVersionCode.toInt()
                } else info.versionCode
            } catch (_: Exception) { 0 }

            val json = BackupSerializer.serialize(snapshot, versionCode, System.currentTimeMillis())

            context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: return@withContext Result.Failure("파일 쓰기 실패: 위치를 다시 선택해주세요.")

            Result.Success(items.size + templates.size)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "내보내기 실패")
        }
    }

    /**
     * [uri] 파일을 읽어 미리보기만 반환. DB 변경 없음.
     */
    suspend fun preview(context: Context, uri: Uri): BackupSerializer.BackupPreview? =
        withContext(Dispatchers.IO) {
            try {
                val json = readUriAsString(context, uri) ?: return@withContext null
                BackupSerializer.preview(json)
            } catch (_: Exception) {
                null
            }
        }

    /**
     * [uri]에서 백업 읽어 DB에 반영.
     * - [BackupSerializer.ImportMode.OVERWRITE]: 기존 D-Day/To-Do + 템플릿 전부 삭제 후 복원
     * - [BackupSerializer.ImportMode.MERGE]: 백업 항목을 새 ID로 INSERT (기존과 공존)
     *
     * 트랜잭션으로 묶어 중간 실패 시 rollback.
     */
    suspend fun import(
        context: Context,
        uri: Uri,
        db: DdayDatabase,
        mode: BackupSerializer.ImportMode,
    ): Result = withContext(Dispatchers.IO) {
        try {
            val json = readUriAsString(context, uri)
                ?: return@withContext Result.Failure("파일을 읽을 수 없습니다.")
            val snapshot = BackupSerializer.deserialize(json)
                ?: return@withContext Result.Failure("백업 파일 형식이 올바르지 않거나 지원하지 않는 버전입니다.")

            val dao = db.ddayDao()
            val templateDao = db.todoTemplateDao()

            db.withTransaction {
                if (mode == BackupSerializer.ImportMode.OVERWRITE) {
                    // 기존 데이터 전부 삭제
                    dao.getAll().forEach { dao.delete(it) }
                    templateDao.getAll().forEach { templateDao.delete(it) }
                }
                // INSERT: id=0으로 넣어 현재 DB에서 새 ID를 발급한다.
                // 템플릿 참조는 새 템플릿 ID로 다시 연결한다.
                val templateIdMap = mutableMapOf<Int, Int>()
                snapshot.templates.forEach { template ->
                    val insertedId = templateDao.insert(template.copy(id = 0)).toInt()
                    if (template.id != 0) templateIdMap[template.id] = insertedId
                }

                snapshot.ddayItems.forEach { item ->
                    val remappedTemplateId = item.templateId?.let { templateIdMap[it] }
                    dao.insert(item.copy(id = 0, templateId = remappedTemplateId))
                }
            }

            // 설정 복원 (그룹 순서/이모지/정렬)
            DdaySettings.setGroupOrder(context, snapshot.groupOrder)
            snapshot.groupEmojis.forEach { (group, emoji) ->
                DdaySettings.setGroupEmoji(context, group, emoji)
            }
            DdaySettings.setDdaySort(context, snapshot.ddaySort)
            DdaySettings.setTodoSort(context, snapshot.todoSort)

            Result.Success(snapshot.ddayItems.size + snapshot.templates.size)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "가져오기 실패")
        }
    }

    private fun readUriAsString(context: Context, uri: Uri): String? {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        }
    }
}
