package com.example.taskora.ui.lists

import com.example.taskora.domain.model.TaskList

/** ARGB colors offered for lists; the first is the Inbox default. */
val ListColors: List<Int> = listOf(
    TaskList.DEFAULT_COLOR,
    0xFF0B8A8F.toInt(), // teal
    0xFF2E7D32.toInt(), // green
    0xFFB8860B.toInt(), // amber
    0xFFE0662F.toInt(), // orange
    0xFFC62828.toInt(), // red
    0xFFAD1457.toInt(), // pink
    0xFF6A1B9A.toInt(), // purple
    0xFF546E7A.toInt(), // slate
)
