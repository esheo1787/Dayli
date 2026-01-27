package com.silverwest.dayli.ddaywidget

enum class ItemType(val displayName: String) {
    DDAY("D-Day"),
    TODO("To-Do");

    companion object {
        fun fromName(name: String): ItemType {
            return entries.find { it.name == name } ?: DDAY
        }
    }
}
