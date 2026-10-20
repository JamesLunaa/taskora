package com.example.taskora.data.repository

import com.example.taskora.data.local.dao.TaskListDao
import com.example.taskora.data.local.entity.toDomain
import com.example.taskora.data.local.entity.toEntity
import com.example.taskora.domain.model.TaskList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface TaskListRepository {
    fun observeLists(): Flow<List<TaskList>>
    fun observeList(id: Long): Flow<TaskList?>
    suspend fun addList(name: String, color: Int): Long
    suspend fun updateList(list: TaskList)

    /** Deletes the list and all its tasks. The Inbox can't be deleted. */
    suspend fun deleteList(id: Long)
}

@Singleton
class OfflineTaskListRepository @Inject constructor(
    private val dao: TaskListDao,
) : TaskListRepository {
    override fun observeLists(): Flow<List<TaskList>> =
        dao.observeAll().map { lists -> lists.map { it.toDomain() } }

    override fun observeList(id: Long): Flow<TaskList?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun addList(name: String, color: Int): Long =
        dao.insert(TaskList(name = name.trim(), color = color, sortOrder = dao.nextSortOrder()).toEntity())

    override suspend fun updateList(list: TaskList) = dao.update(list.toEntity())

    override suspend fun deleteList(id: Long) {
        require(id != TaskList.INBOX_ID) { "The Inbox can't be deleted" }
        dao.deleteById(id)
    }
}
