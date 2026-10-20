package com.example.taskora.domain.model

/** Ordered lowest to highest; [level] is what gets stored and sorted on. */
enum class Priority(val level: Int) {
    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    companion object {
        fun fromLevel(level: Int): Priority = entries.firstOrNull { it.level == level } ?: NONE
    }
}
