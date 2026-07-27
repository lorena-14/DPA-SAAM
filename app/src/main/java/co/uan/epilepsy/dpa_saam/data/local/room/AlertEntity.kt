package co.uan.epilepsy.dpa_saam.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spo2: Float,
    val bpm: Int,
    val batteryPercent: Int?,
    val receivedAtEpochMillis: Long,
    val deviceAddress: String,
)
