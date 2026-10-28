package io.github.jameslunaa.taskora.domain.model

/** [MANUAL] is the user's drag order; the others are computed and can be reversed per list. */
enum class TaskSortMode { MANUAL, CREATED, DUE_DATE, PRIORITY, TITLE }
