package com.silverwest.dayli.ddaywidget

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DdayItem::class], version = 12, exportSchema = false)
@TypeConverters(Converters::class)
abstract class DdayDatabase : RoomDatabase() {
    abstract fun ddayDao(): DdayDao

    companion object {
        @Volatile
        private var INSTANCE: DdayDatabase? = null

        // 마이그레이션: version 1 → 2 (isChecked 컬럼 추가)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN isChecked INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 마이그레이션: version 2 → 3 (checkedAt 컬럼 추가)
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN checkedAt INTEGER DEFAULT NULL")
            }
        }

        // 마이그레이션: version 1 → 3 (한 번에 업그레이드하는 경우)
        private val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN isChecked INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN checkedAt INTEGER DEFAULT NULL")
            }
        }

        // 마이그레이션: version 3 → 4 (category 컬럼 추가)
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
            }
        }

        // 마이그레이션: version 1 → 4 (한 번에 업그레이드하는 경우)
        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN isChecked INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN checkedAt INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
            }
        }

        // 마이그레이션: version 2 → 4 (한 번에 업그레이드하는 경우)
        private val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN checkedAt INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
            }
        }

        // 마이그레이션: version 4 → 5 (iconName 컬럼 추가)
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN iconName TEXT DEFAULT NULL")
            }
        }

        // 마이그레이션: version 1 → 5 (한 번에 업그레이드하는 경우)
        private val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN isChecked INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN checkedAt INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN iconName TEXT DEFAULT NULL")
            }
        }

        // 마이그레이션: version 2 → 5 (한 번에 업그레이드하는 경우)
        private val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN checkedAt INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN iconName TEXT DEFAULT NULL")
            }
        }

        // 마이그레이션: version 3 → 5 (한 번에 업그레이드하는 경우)
        private val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN iconName TEXT DEFAULT NULL")
            }
        }

        // 마이그레이션: version 5 → 6 (customColor 컬럼 추가)
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN customColor INTEGER DEFAULT NULL")
            }
        }

        // 마이그레이션: version 1 → 6 (한 번에 업그레이드하는 경우)
        private val MIGRATION_1_6 = object : Migration(1, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN isChecked INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN checkedAt INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN iconName TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN customColor INTEGER DEFAULT NULL")
            }
        }

        // 마이그레이션: version 2 → 6 (한 번에 업그레이드하는 경우)
        private val MIGRATION_2_6 = object : Migration(2, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN checkedAt INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN iconName TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN customColor INTEGER DEFAULT NULL")
            }
        }

        // 마이그레이션: version 3 → 6 (한 번에 업그레이드하는 경우)
        private val MIGRATION_3_6 = object : Migration(3, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN iconName TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN customColor INTEGER DEFAULT NULL")
            }
        }

        // 마이그레이션: version 4 → 6 (한 번에 업그레이드하는 경우)
        private val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN iconName TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN customColor INTEGER DEFAULT NULL")
            }
        }

        // 마이그레이션: version 6 → 7 (repeatType, repeatDay 컬럼 추가)
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN repeatType TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN repeatDay INTEGER DEFAULT NULL")
            }
        }

        // 마이그레이션: version 1 → 7 (한 번에 업그레이드하는 경우)
        private val MIGRATION_1_7 = object : Migration(1, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN isChecked INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN checkedAt INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN iconName TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN customColor INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN repeatType TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN repeatDay INTEGER DEFAULT NULL")
            }
        }

        // 마이그레이션: version 2 → 7 (한 번에 업그레이드하는 경우)
        private val MIGRATION_2_7 = object : Migration(2, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN checkedAt INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN iconName TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN customColor INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN repeatType TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN repeatDay INTEGER DEFAULT NULL")
            }
        }

        // 마이그레이션: version 3 → 7 (한 번에 업그레이드하는 경우)
        private val MIGRATION_3_7 = object : Migration(3, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN iconName TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN customColor INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN repeatType TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN repeatDay INTEGER DEFAULT NULL")
            }
        }

        // 마이그레이션: version 4 → 7 (한 번에 업그레이드하는 경우)
        private val MIGRATION_4_7 = object : Migration(4, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN iconName TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN customColor INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN repeatType TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN repeatDay INTEGER DEFAULT NULL")
            }
        }

        // 마이그레이션: version 5 → 7 (한 번에 업그레이드하는 경우)
        private val MIGRATION_5_7 = object : Migration(5, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN customColor INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN repeatType TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE dday_items ADD COLUMN repeatDay INTEGER DEFAULT NULL")
            }
        }

        // 마이그레이션: version 7 → 8 (itemType 컬럼 추가 + date 컬럼 nullable로 변경)
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // SQLite는 ALTER COLUMN을 지원하지 않으므로 테이블 재생성 필요
                // 1. 새 테이블 생성 (date nullable)
                database.execSQL("""
                    CREATE TABLE dday_items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        date INTEGER,
                        memo TEXT,
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        checkedAt INTEGER,
                        category TEXT NOT NULL DEFAULT 'OTHER',
                        iconName TEXT,
                        customColor INTEGER,
                        repeatType TEXT NOT NULL DEFAULT 'NONE',
                        repeatDay INTEGER,
                        itemType TEXT NOT NULL DEFAULT 'DDAY'
                    )
                """)
                // 2. 기존 데이터 복사
                database.execSQL("""
                    INSERT INTO dday_items_new (id, title, date, memo, isChecked, checkedAt, category, iconName, customColor, repeatType, repeatDay, itemType)
                    SELECT id, title, date, memo, isChecked, checkedAt, category, iconName, customColor, repeatType, repeatDay, 'DDAY'
                    FROM dday_items
                """)
                // 3. 기존 테이블 삭제
                database.execSQL("DROP TABLE dday_items")
                // 4. 새 테이블 이름 변경
                database.execSQL("ALTER TABLE dday_items_new RENAME TO dday_items")
            }
        }

        // 마이그레이션: version 1 → 8 (한 번에 업그레이드하는 경우)
        private val MIGRATION_1_8 = object : Migration(1, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 테이블 재생성 (date nullable)
                database.execSQL("""
                    CREATE TABLE dday_items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        date INTEGER,
                        memo TEXT,
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        checkedAt INTEGER,
                        category TEXT NOT NULL DEFAULT 'OTHER',
                        iconName TEXT,
                        customColor INTEGER,
                        repeatType TEXT NOT NULL DEFAULT 'NONE',
                        repeatDay INTEGER,
                        itemType TEXT NOT NULL DEFAULT 'DDAY'
                    )
                """)
                database.execSQL("""
                    INSERT INTO dday_items_new (id, title, date, memo, itemType)
                    SELECT id, title, date, memo, 'DDAY' FROM dday_items
                """)
                database.execSQL("DROP TABLE dday_items")
                database.execSQL("ALTER TABLE dday_items_new RENAME TO dday_items")
            }
        }

        // 마이그레이션: version 2 → 8 (한 번에 업그레이드하는 경우)
        private val MIGRATION_2_8 = object : Migration(2, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 테이블 재생성 (date nullable)
                database.execSQL("""
                    CREATE TABLE dday_items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        date INTEGER,
                        memo TEXT,
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        checkedAt INTEGER,
                        category TEXT NOT NULL DEFAULT 'OTHER',
                        iconName TEXT,
                        customColor INTEGER,
                        repeatType TEXT NOT NULL DEFAULT 'NONE',
                        repeatDay INTEGER,
                        itemType TEXT NOT NULL DEFAULT 'DDAY'
                    )
                """)
                database.execSQL("""
                    INSERT INTO dday_items_new (id, title, date, memo, isChecked, itemType)
                    SELECT id, title, date, memo, isChecked, 'DDAY' FROM dday_items
                """)
                database.execSQL("DROP TABLE dday_items")
                database.execSQL("ALTER TABLE dday_items_new RENAME TO dday_items")
            }
        }

        // 마이그레이션: version 3 → 8 (한 번에 업그레이드하는 경우)
        private val MIGRATION_3_8 = object : Migration(3, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 테이블 재생성 (date nullable)
                database.execSQL("""
                    CREATE TABLE dday_items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        date INTEGER,
                        memo TEXT,
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        checkedAt INTEGER,
                        category TEXT NOT NULL DEFAULT 'OTHER',
                        iconName TEXT,
                        customColor INTEGER,
                        repeatType TEXT NOT NULL DEFAULT 'NONE',
                        repeatDay INTEGER,
                        itemType TEXT NOT NULL DEFAULT 'DDAY'
                    )
                """)
                database.execSQL("""
                    INSERT INTO dday_items_new (id, title, date, memo, isChecked, checkedAt, itemType)
                    SELECT id, title, date, memo, isChecked, checkedAt, 'DDAY' FROM dday_items
                """)
                database.execSQL("DROP TABLE dday_items")
                database.execSQL("ALTER TABLE dday_items_new RENAME TO dday_items")
            }
        }

        // 마이그레이션: version 4 → 8 (한 번에 업그레이드하는 경우)
        private val MIGRATION_4_8 = object : Migration(4, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 테이블 재생성 (date nullable)
                database.execSQL("""
                    CREATE TABLE dday_items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        date INTEGER,
                        memo TEXT,
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        checkedAt INTEGER,
                        category TEXT NOT NULL DEFAULT 'OTHER',
                        iconName TEXT,
                        customColor INTEGER,
                        repeatType TEXT NOT NULL DEFAULT 'NONE',
                        repeatDay INTEGER,
                        itemType TEXT NOT NULL DEFAULT 'DDAY'
                    )
                """)
                database.execSQL("""
                    INSERT INTO dday_items_new (id, title, date, memo, isChecked, checkedAt, category, itemType)
                    SELECT id, title, date, memo, isChecked, checkedAt, category, 'DDAY' FROM dday_items
                """)
                database.execSQL("DROP TABLE dday_items")
                database.execSQL("ALTER TABLE dday_items_new RENAME TO dday_items")
            }
        }

        // 마이그레이션: version 5 → 8 (한 번에 업그레이드하는 경우)
        private val MIGRATION_5_8 = object : Migration(5, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 테이블 재생성 (date nullable)
                database.execSQL("""
                    CREATE TABLE dday_items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        date INTEGER,
                        memo TEXT,
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        checkedAt INTEGER,
                        category TEXT NOT NULL DEFAULT 'OTHER',
                        iconName TEXT,
                        customColor INTEGER,
                        repeatType TEXT NOT NULL DEFAULT 'NONE',
                        repeatDay INTEGER,
                        itemType TEXT NOT NULL DEFAULT 'DDAY'
                    )
                """)
                database.execSQL("""
                    INSERT INTO dday_items_new (id, title, date, memo, isChecked, checkedAt, category, iconName, itemType)
                    SELECT id, title, date, memo, isChecked, checkedAt, category, iconName, 'DDAY' FROM dday_items
                """)
                database.execSQL("DROP TABLE dday_items")
                database.execSQL("ALTER TABLE dday_items_new RENAME TO dday_items")
            }
        }

        // 마이그레이션: version 6 → 8 (한 번에 업그레이드하는 경우)
        private val MIGRATION_6_8 = object : Migration(6, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 테이블 재생성 (date nullable)
                database.execSQL("""
                    CREATE TABLE dday_items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        date INTEGER,
                        memo TEXT,
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        checkedAt INTEGER,
                        category TEXT NOT NULL DEFAULT 'OTHER',
                        iconName TEXT,
                        customColor INTEGER,
                        repeatType TEXT NOT NULL DEFAULT 'NONE',
                        repeatDay INTEGER,
                        itemType TEXT NOT NULL DEFAULT 'DDAY'
                    )
                """)
                database.execSQL("""
                    INSERT INTO dday_items_new (id, title, date, memo, isChecked, checkedAt, category, iconName, customColor, itemType)
                    SELECT id, title, date, memo, isChecked, checkedAt, category, iconName, customColor, 'DDAY' FROM dday_items
                """)
                database.execSQL("DROP TABLE dday_items")
                database.execSQL("ALTER TABLE dday_items_new RENAME TO dday_items")
            }
        }

        // 마이그레이션: version 8 → 9 (이미 잘못된 버전 8로 마이그레이션된 사용자용 - date nullable 수정)
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 테이블 재생성 (date nullable 보장)
                database.execSQL("""
                    CREATE TABLE dday_items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        date INTEGER,
                        memo TEXT,
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        checkedAt INTEGER,
                        category TEXT NOT NULL DEFAULT 'OTHER',
                        iconName TEXT,
                        customColor INTEGER,
                        repeatType TEXT NOT NULL DEFAULT 'NONE',
                        repeatDay INTEGER,
                        itemType TEXT NOT NULL DEFAULT 'DDAY'
                    )
                """)
                database.execSQL("""
                    INSERT INTO dday_items_new (id, title, date, memo, isChecked, checkedAt, category, iconName, customColor, repeatType, repeatDay, itemType)
                    SELECT id, title, date, memo, isChecked, checkedAt, category, iconName, customColor, repeatType, repeatDay, COALESCE(itemType, 'DDAY')
                    FROM dday_items
                """)
                database.execSQL("DROP TABLE dday_items")
                database.execSQL("ALTER TABLE dday_items_new RENAME TO dday_items")
            }
        }

        // 마이그레이션: version 9 → 10 (sortOrder 컬럼 추가)
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 마이그레이션: version 10 → 11 (sub_tasks 컬럼 추가 - 체크리스트)
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN sub_tasks TEXT DEFAULT NULL")
            }
        }

        // 마이그레이션: version 11 → 12 (group_name 컬럼 추가 - D-Day 그룹)
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dday_items ADD COLUMN group_name TEXT DEFAULT NULL")
            }
        }

        // 마이그레이션: version 7 → 9 (직접 점프)
        private val MIGRATION_7_9 = object : Migration(7, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 테이블 재생성 (date nullable + itemType 추가)
                database.execSQL("""
                    CREATE TABLE dday_items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        date INTEGER,
                        memo TEXT,
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        checkedAt INTEGER,
                        category TEXT NOT NULL DEFAULT 'OTHER',
                        iconName TEXT,
                        customColor INTEGER,
                        repeatType TEXT NOT NULL DEFAULT 'NONE',
                        repeatDay INTEGER,
                        itemType TEXT NOT NULL DEFAULT 'DDAY'
                    )
                """)
                database.execSQL("""
                    INSERT INTO dday_items_new (id, title, date, memo, isChecked, checkedAt, category, iconName, customColor, repeatType, repeatDay, itemType)
                    SELECT id, title, date, memo, isChecked, checkedAt, category, iconName, customColor, repeatType, repeatDay, 'DDAY'
                    FROM dday_items
                """)
                database.execSQL("DROP TABLE dday_items")
                database.execSQL("ALTER TABLE dday_items_new RENAME TO dday_items")
            }
        }

        fun getDatabase(context: Context): DdayDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    DdayDatabase::class.java,
                    "dday_database"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3,
                        MIGRATION_3_4, MIGRATION_1_4, MIGRATION_2_4,
                        MIGRATION_4_5, MIGRATION_1_5, MIGRATION_2_5, MIGRATION_3_5,
                        MIGRATION_5_6, MIGRATION_1_6, MIGRATION_2_6, MIGRATION_3_6, MIGRATION_4_6,
                        MIGRATION_6_7, MIGRATION_1_7, MIGRATION_2_7, MIGRATION_3_7, MIGRATION_4_7, MIGRATION_5_7,
                        MIGRATION_7_8, MIGRATION_1_8, MIGRATION_2_8, MIGRATION_3_8, MIGRATION_4_8, MIGRATION_5_8, MIGRATION_6_8,
                        MIGRATION_8_9, MIGRATION_7_9,
                        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
 