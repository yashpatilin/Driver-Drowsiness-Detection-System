package com.example.drowsiness.data

import kotlinx.coroutines.flow.Flow

class MainRepository(private val dao: DrowsinessDao) {

    val recentLogs: Flow<List<DrowsinessLog>> = dao.getRecentLogsFlow()

    suspend fun insertLog(log: DrowsinessLog) {
        dao.insertLog(log)
    }
}
