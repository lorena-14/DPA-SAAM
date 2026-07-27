package co.uan.epilepsy.dpa_saam.domain.repository

import co.uan.epilepsy.dpa_saam.core.result.AppResult
import co.uan.epilepsy.dpa_saam.domain.model.BraceletReading
import kotlinx.coroutines.flow.Flow

interface AlertRepository {
    fun observeAll(): Flow<List<BraceletReading>>
    suspend fun insert(reading: BraceletReading): AppResult<Unit>
}
