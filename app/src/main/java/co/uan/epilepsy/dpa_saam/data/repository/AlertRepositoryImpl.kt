package co.uan.epilepsy.dpa_saam.data.repository

import android.database.sqlite.SQLiteException
import co.uan.epilepsy.dpa_saam.core.result.AppResult
import co.uan.epilepsy.dpa_saam.data.local.room.AlertDao
import co.uan.epilepsy.dpa_saam.data.local.room.AlertEntity
import co.uan.epilepsy.dpa_saam.domain.model.BraceletReading
import co.uan.epilepsy.dpa_saam.domain.repository.AlertRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlertRepositoryImpl(
    private val alertDao: AlertDao,
) : AlertRepository {

    override fun observeAll(): Flow<List<BraceletReading>> {
        return alertDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insert(reading: BraceletReading): AppResult<Unit> {
        return try {
            alertDao.insert(reading.toEntity())
            AppResult.Success(Unit)
        } catch (e: SQLiteException) {
            AppResult.Error("No se pudo guardar la alerta", e)
        } catch (e: Exception) {
            AppResult.Error("Error al guardar la alerta", e)
        }
    }

    private fun AlertEntity.toDomain(): BraceletReading {
        return BraceletReading(
            id = id,
            spo2 = spo2,
            bpm = bpm,
            batteryPercent = batteryPercent,
            receivedAt = Instant.ofEpochMilli(receivedAtEpochMillis),
            deviceAddress = deviceAddress,
        )
    }

    private fun BraceletReading.toEntity(): AlertEntity {
        return AlertEntity(
            id = id,
            spo2 = spo2,
            bpm = bpm,
            batteryPercent = batteryPercent,
            receivedAtEpochMillis = receivedAt.toEpochMilli(),
            deviceAddress = deviceAddress,
        )
    }
}
