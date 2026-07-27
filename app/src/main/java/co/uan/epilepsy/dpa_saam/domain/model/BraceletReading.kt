package co.uan.epilepsy.dpa_saam.domain.model

import java.time.Instant

data class BraceletReading(
    val id: Long = 0,
    val spo2: Float,
    val bpm: Int,
    val batteryPercent: Int?,
    val receivedAt: Instant,
    val deviceAddress: String,
)
