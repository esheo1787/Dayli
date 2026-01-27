package com.silverwest.dayli.ddaywidget

/**
 * 반복 일정 타입
 */
enum class RepeatType(val displayName: String, val icon: String) {
    NONE("반복 안 함", ""),
    DAILY("매일", "🔁"),
    WEEKLY("매주", "🔁"),
    MONTHLY("매월", "🔁"),
    YEARLY("매년", "🔁");

    companion object {
        fun fromName(name: String): RepeatType {
            return entries.find { it.name == name } ?: NONE
        }
    }
}
