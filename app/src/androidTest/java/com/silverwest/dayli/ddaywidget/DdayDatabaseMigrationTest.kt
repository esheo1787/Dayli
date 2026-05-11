package com.silverwest.dayli.ddaywidget

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Room 마이그레이션 검증 (instrumentation test, 실기기/에뮬레이터에서 실행).
 *
 * 목적:
 * 1. 현재 schema 버전(v17)이 entity 정의와 일치하는지 검증.
 * 2. 향후 추가될 마이그레이션을 위한 테스트 패턴 제공.
 *
 * 실행: Android Studio → Run > DdayDatabaseMigrationTest
 *      또는 `./gradlew.bat connectedDebugAndroidTest` (실기기 연결 필요).
 *
 * 새 마이그레이션 추가 시 체크리스트:
 *   1. `DdayDatabase`의 `version`을 18로 올림
 *   2. `MIGRATION_17_18` 추가하고 `addMigrations(...)`에 등록
 *   3. 빌드 → `app/schemas/.../18.json` 자동 생성 확인
 *   4. 본 파일에 `migrate17To18()` 테스트 추가 (아래 주석 참고)
 */
@RunWith(AndroidJUnit4::class)
class DdayDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DdayDatabase::class.java
    )

    /**
     * v17 schema 정합성 검증.
     *
     * `createDatabase`가 schemas/17.json을 기반으로 DB를 만들고,
     * Room이 entity 정의와 schema가 일치하는지 자동 검증한다.
     * 불일치하면 IllegalStateException 발생.
     */
    @Test
    @Throws(IOException::class)
    fun validateCurrentSchemaV17() {
        helper.createDatabase(TEST_DB, 17).apply {
            // 데이터 한 건 삽입해서 컬럼 정합성 확인
            execSQL(
                """
                INSERT INTO dday_items
                  (id, title, date, isChecked, category, repeatType, itemType, sortOrder, isHidden)
                VALUES
                  (1, 'test', NULL, 0, 'OTHER', 'NONE', 'TODO', 0, 0)
                """
            )
            close()
        }
    }

    /*
     * 다음 마이그레이션 추가 시 패턴 예시:
     *
     * @Test
     * @Throws(IOException::class)
     * fun migrate17To18() {
     *     helper.createDatabase(TEST_DB, 17).apply {
     *         // v17 시점 데이터 삽입
     *         execSQL("INSERT INTO dday_items (id, title, ...) VALUES (1, 'old', ...)")
     *         close()
     *     }
     *     // 마이그레이션 실행 + schema 검증
     *     val db = helper.runMigrationsAndValidate(
     *         TEST_DB, 18, true,
     *         DdayDatabase.MIGRATION_17_18  // 새 마이그레이션 공개 필요
     *     )
     *     // 새 컬럼 기본값/데이터 보존 확인
     *     db.query("SELECT new_column FROM dday_items WHERE id = 1").use { cursor ->
     *         cursor.moveToFirst()
     *         assertEquals(<expected_default>, cursor.getInt(0))
     *     }
     *     db.close()
     * }
     */

    companion object {
        private const val TEST_DB = "dday-migration-test"
    }
}
