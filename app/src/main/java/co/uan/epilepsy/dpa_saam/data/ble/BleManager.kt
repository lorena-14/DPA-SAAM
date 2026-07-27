package co.uan.epilepsy.dpa_saam.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import co.uan.epilepsy.dpa_saam.core.result.AppResult
import co.uan.epilepsy.dpa_saam.domain.model.BatteryInfo
import co.uan.epilepsy.dpa_saam.domain.model.BraceletReading
import co.uan.epilepsy.dpa_saam.domain.model.ConnectionStatus
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("MissingPermission")
class BleManager(
    private val context: Context,
    private val payloadParser: BlePayloadParser,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.Idle)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _readings = MutableSharedFlow<BraceletReading>(extraBufferCapacity = 16)
    val readings: SharedFlow<BraceletReading> = _readings.asSharedFlow()

    private val _batteryInfo = MutableStateFlow(BatteryInfo(percent = null, isLow = false))
    val batteryInfo: StateFlow<BatteryInfo> = _batteryInfo.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDeviceAddress: String? = null
    private var isScanning = false
    private var scanTimeoutRunnable: Runnable? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                val device = result.device ?: return
                stopScan()
                connectToDevice(device)
            } catch (e: Exception) {
                emitFailed("Error durante el escaneo BLE")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            emitFailed("Escaneo BLE fallido (código $errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            try {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    emitFailed("Conexión GATT fallida (código $status)")
                    closeGatt()
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectedDeviceAddress = gatt.device.address
                        _connectionStatus.value = ConnectionStatus.Connected
                        gatt.discoverServices()
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedDeviceAddress = null
                        _connectionStatus.value = ConnectionStatus.Failed
                        closeGatt()
                    }
                }
            } catch (e: Exception) {
                emitFailed("Error en cambio de estado GATT")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            try {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    emitFailed("Descubrimiento de servicios fallido")
                    return
                }

                val alertCharacteristic = gatt
                    .getService(BleConstants.SERVICE_UUID)
                    ?.getCharacteristic(BleConstants.ALERT_CHAR_UUID)

                if (alertCharacteristic == null) {
                    emitFailed("Característica de alerta no encontrada")
                    return
                }

                gatt.setCharacteristicNotification(alertCharacteristic, true)
                val descriptor = alertCharacteristic.getDescriptor(
                    UUID.fromString(BleConstants.CCCD_UUID),
                )
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                }

                readBatteryLevel(gatt)
            } catch (e: Exception) {
                emitFailed("Error al configurar notificaciones BLE")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleCharacteristicValue(gatt.device.address, value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            handleCharacteristicValue(gatt.device.address, characteristic.value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            try {
                if (status != BluetoothGatt.GATT_SUCCESS) return
                if (characteristic.uuid == BleConstants.BATTERY_LEVEL_CHAR_UUID && value.isNotEmpty()) {
                    updateBattery(value[0].toInt() and 0xFF)
                }
            } catch (_: Exception) {
                // Batería opcional: ignorar silenciosamente
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            try {
                if (status != BluetoothGatt.GATT_SUCCESS) return
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                if (characteristic.uuid == BleConstants.BATTERY_LEVEL_CHAR_UUID && value.isNotEmpty()) {
                    updateBattery(value[0].toInt() and 0xFF)
                }
            } catch (_: Exception) {
                // Batería opcional: ignorar silenciosamente
            }
        }
    }

    fun startAutoConnect() {
        try {
            if (_connectionStatus.value == ConnectionStatus.Connected ||
                _connectionStatus.value == ConnectionStatus.Connecting ||
                _connectionStatus.value == ConnectionStatus.Scanning
            ) {
                return
            }

            val adapter = bluetoothAdapter
            if (adapter == null || !adapter.isEnabled) {
                emitFailed("Bluetooth no disponible o desactivado")
                return
            }

            _connectionStatus.value = ConnectionStatus.Scanning
            startScan()
        } catch (e: Exception) {
            emitFailed("No se pudo iniciar la conexión automática")
        }
    }

    fun retryConnection() {
        try {
            disconnect()
            startAutoConnect()
        } catch (e: Exception) {
            emitFailed("No se pudo reintentar la conexión")
        }
    }

    fun disconnect() {
        try {
            stopScan()
            closeGatt()
            connectedDeviceAddress = null
            _connectionStatus.value = ConnectionStatus.Failed
        } catch (_: Exception) {
            _connectionStatus.value = ConnectionStatus.Failed
        }
    }

    fun simulateReading() {
        try {
            val reading = BraceletReading(
                spo2 = 97.5f,
                bpm = 72,
                batteryPercent = null,
                receivedAt = java.time.Instant.now(),
                deviceAddress = connectedDeviceAddress ?: "SIMULATED",
            )
            _readings.tryEmit(reading)
        } catch (_: Exception) {
            // Simulación no debe crashear
        }
    }

    private fun startScan() {
        try {
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            if (scanner == null) {
                emitFailed("Escáner BLE no disponible")
                return
            }

            val filters = listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
                    .build(),
            )
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            isScanning = true
            scanner.startScan(filters, settings, scanCallback)

            scanTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            scanTimeoutRunnable = Runnable {
                if (isScanning) {
                    stopScan()
                    emitFailed("No se encontró la manilla ESP32")
                }
            }.also { mainHandler.postDelayed(it, BleConstants.SCAN_TIMEOUT_MS) }
        } catch (e: SecurityException) {
            emitFailed("Permisos BLE insuficientes")
        } catch (e: Exception) {
            emitFailed("Error al escanear dispositivos BLE")
        }
    }

    private fun stopScan() {
        try {
            scanTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            scanTimeoutRunnable = null
            if (isScanning) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
                isScanning = false
            }
        } catch (_: Exception) {
            isScanning = false
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        try {
            _connectionStatus.value = ConnectionStatus.Connecting
            closeGatt()
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            emitFailed("Permisos BLE insuficientes para conectar")
        } catch (e: Exception) {
            emitFailed("Error al conectar con la manilla")
        }
    }

    private fun readBatteryLevel(gatt: BluetoothGatt) {
        try {
            val batteryChar = gatt
                .getService(BleConstants.BATTERY_SERVICE_UUID)
                ?.getCharacteristic(BleConstants.BATTERY_LEVEL_CHAR_UUID)
                ?: return
            gatt.readCharacteristic(batteryChar)
        } catch (_: Exception) {
            // Característica futura: ignorar si no existe
        }
    }

    private fun handleCharacteristicValue(deviceAddress: String, value: ByteArray?) {
        try {
            val payload = value?.toString(Charsets.UTF_8)?.trim().orEmpty()
            if (payload.isEmpty()) return

            when (val result = payloadParser.parse(payload, deviceAddress)) {
                is AppResult.Success -> {
                    result.data.batteryPercent?.let { updateBattery(it) }
                    _readings.tryEmit(result.data)
                }

                is AppResult.Error -> {
                    // El ViewModel mostrará el error vía snackbar si se propaga
                }
            }
        } catch (_: Exception) {
            // Payload inválido: no crashear
        }
    }

    private fun updateBattery(percent: Int) {
        _batteryInfo.value = BatteryInfo(
            percent = percent,
            isLow = percent <= BleConstants.LOW_BATTERY_THRESHOLD,
        )
    }

    private fun closeGatt() {
        try {
            bluetoothGatt?.close()
        } catch (_: Exception) {
            // Ignorar
        } finally {
            bluetoothGatt = null
        }
    }

    private fun emitFailed(message: String) {
        stopScan()
        _connectionStatus.value = ConnectionStatus.Failed
    }
}
