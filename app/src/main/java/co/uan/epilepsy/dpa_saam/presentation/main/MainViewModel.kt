package co.uan.epilepsy.dpa_saam.presentation.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.uan.epilepsy.dpa_saam.core.di.AppContainer
import co.uan.epilepsy.dpa_saam.core.result.AppResult
import co.uan.epilepsy.dpa_saam.data.ble.BleConstants
import co.uan.epilepsy.dpa_saam.data.notification.BraceletForegroundService
import co.uan.epilepsy.dpa_saam.domain.model.BraceletReading
import co.uan.epilepsy.dpa_saam.domain.model.ConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val bleRepository = container.bleRepository
    private val alertRepository = container.alertRepository
    private val notificationManager = container.notificationManager
    private val appContext = container.appContext

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var lowBatteryNotified = false
    private var hasConnectedOnce = false

    init {
        observeConnectionStatus()
        observeAlertHistory()
        observeBatteryInfo()
        observeReadings()
    }

    fun onPermissionsReady() {
        _uiState.update { it.copy(permissionsReady = true) }
        bleRepository.startAutoConnect()
    }

    fun onEvent(event: MainUiEvent) {
        when (event) {
            MainUiEvent.RetryConnection -> bleRepository.retryConnection()
            MainUiEvent.DismissSnackbar -> _uiState.update { it.copy(snackbarMessage = null) }
            MainUiEvent.ConfirmBatteryOpt -> requestBatteryOptimizationExemption(appContext)
            MainUiEvent.DismissBatteryOptDialog -> _uiState.update { it.copy(showBatteryOptDialog = false) }
            MainUiEvent.DismissNotificationDialog -> _uiState.update { it.copy(showNotificationPermissionDialog = false) }
            MainUiEvent.SimulateAlert -> bleRepository.simulateReading()
        }
    }

    fun showNotificationPermissionDialog() {
        _uiState.update { it.copy(showNotificationPermissionDialog = true) }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            showSnackbar("No se pudo abrir la configuración de la app")
        }
    }

    fun requestBatteryOptimizationExemption(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
            _uiState.update { it.copy(showBatteryOptDialog = false) }
        } catch (e: Exception) {
            showSnackbar("No se pudo solicitar exención de batería")
        }
    }

    private fun observeConnectionStatus() {
        viewModelScope.launch {
            bleRepository.connectionStatus
                .catch { e ->
                    showSnackbar("Error de conexión BLE: ${e.message ?: "desconocido"}")
                }
                .collect { status ->
                    _uiState.update {
                        it.copy(
                            connectionStatus = status,
                            isLoading = status == ConnectionStatus.Scanning ||
                                status == ConnectionStatus.Connecting,
                        )
                    }

                    when (status) {
                        ConnectionStatus.Connected -> {
                            BraceletForegroundService.start(appContext)
                            if (!hasConnectedOnce) {
                                hasConnectedOnce = true
                                _uiState.update { it.copy(showBatteryOptDialog = true) }
                            }
                        }

                        ConnectionStatus.Failed -> {
                            BraceletForegroundService.stop(appContext)
                        }

                        else -> Unit
                    }
                }
        }
    }

    private fun observeAlertHistory() {
        viewModelScope.launch {
            alertRepository.observeAll()
                .catch { e ->
                    showSnackbar("Error al cargar historial: ${e.message ?: "desconocido"}")
                }
                .collect { history ->
                    _uiState.update {
                        it.copy(
                            alertHistory = history,
                            latestReading = history.firstOrNull(),
                        )
                    }
                }
        }
    }

    private fun observeBatteryInfo() {
        viewModelScope.launch {
            bleRepository.batteryInfo
                .catch { e ->
                    showSnackbar("Error de batería: ${e.message ?: "desconocido"}")
                }
                .collect { battery ->
                    _uiState.update { it.copy(batteryInfo = battery) }
                    handleLowBattery(battery.percent)
                }
        }
    }

    private fun observeReadings() {
        viewModelScope.launch {
            bleRepository.readings
                .catch { e ->
                    showSnackbar("Error al recibir lectura: ${e.message ?: "desconocido"}")
                }
                .collect { reading ->
                    persistAndNotify(reading)
                }
        }
    }

    private suspend fun persistAndNotify(reading: BraceletReading) {
        when (val result = alertRepository.insert(reading)) {
            is AppResult.Success -> {
                notificationManager.showAlertNotification(reading)
                reading.batteryPercent?.let { handleLowBattery(it) }
            }

            is AppResult.Error -> showSnackbar(result.message)
        }
    }

    private fun handleLowBattery(percent: Int?) {
        if (percent == null) return
        if (percent <= BleConstants.LOW_BATTERY_THRESHOLD && !lowBatteryNotified) {
            lowBatteryNotified = true
            notificationManager.showLowBatteryNotification(percent)
        } else if (percent > BleConstants.LOW_BATTERY_THRESHOLD) {
            lowBatteryNotified = false
        }
    }
}

class MainViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(container) as T
        }
        throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
    }
}
