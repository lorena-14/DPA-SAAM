package co.uan.epilepsy.dpa_saam.presentation.main

import co.uan.epilepsy.dpa_saam.domain.model.BatteryInfo
import co.uan.epilepsy.dpa_saam.domain.model.BraceletReading
import co.uan.epilepsy.dpa_saam.domain.model.ConnectionStatus

data class MainUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Idle,
    val latestReading: BraceletReading? = null,
    val alertHistory: List<BraceletReading> = emptyList(),
    val batteryInfo: BatteryInfo = BatteryInfo(null, false),
    val permissionsReady: Boolean = false,
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null,
    val showBatteryOptDialog: Boolean = false,
    val showNotificationPermissionDialog: Boolean = false,
)
