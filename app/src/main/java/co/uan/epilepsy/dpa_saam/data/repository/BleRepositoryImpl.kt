package co.uan.epilepsy.dpa_saam.data.repository

import co.uan.epilepsy.dpa_saam.data.ble.BleManager
import co.uan.epilepsy.dpa_saam.domain.model.BatteryInfo
import co.uan.epilepsy.dpa_saam.domain.model.BraceletReading
import co.uan.epilepsy.dpa_saam.domain.model.ConnectionStatus
import co.uan.epilepsy.dpa_saam.domain.repository.BleRepository
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class BleRepositoryImpl(
    private val bleManager: BleManager,
) : BleRepository {

    override val connectionStatus: StateFlow<ConnectionStatus> = bleManager.connectionStatus
    override val readings: SharedFlow<BraceletReading> = bleManager.readings
    override val batteryInfo: StateFlow<BatteryInfo> = bleManager.batteryInfo

    override fun startAutoConnect() = bleManager.startAutoConnect()
    override fun retryConnection() = bleManager.retryConnection()
    override fun disconnect() = bleManager.disconnect()
    override fun simulateReading() = bleManager.simulateReading()
}
