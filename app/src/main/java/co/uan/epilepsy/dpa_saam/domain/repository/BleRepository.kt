package co.uan.epilepsy.dpa_saam.domain.repository

import co.uan.epilepsy.dpa_saam.domain.model.BatteryInfo
import co.uan.epilepsy.dpa_saam.domain.model.BraceletReading
import co.uan.epilepsy.dpa_saam.domain.model.ConnectionStatus
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BleRepository {
    val connectionStatus: StateFlow<ConnectionStatus>
    val readings: SharedFlow<BraceletReading>
    val batteryInfo: StateFlow<BatteryInfo>

    fun startAutoConnect()
    fun retryConnection()
    fun disconnect()
    fun simulateReading()
}
