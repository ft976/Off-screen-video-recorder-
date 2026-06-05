package com.example.data

import kotlinx.coroutines.flow.Flow

class RecordingRepository(private val recordingDao: RecordingDao) {
    val allLogs: Flow<List<RecordingLog>> = recordingDao.getAllLogs()

    suspend fun insertLog(log: RecordingLog): Long {
        return recordingDao.insertLog(log)
    }

    suspend fun updateLog(log: RecordingLog) {
        recordingDao.updateLog(log)
    }

    suspend fun deleteLogById(id: Long) {
        recordingDao.deleteLogById(id)
    }

    suspend fun getLogById(id: Long): RecordingLog? {
        return recordingDao.getLogById(id)
    }
}
