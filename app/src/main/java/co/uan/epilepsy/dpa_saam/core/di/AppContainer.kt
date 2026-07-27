package co.uan.epilepsy.dpa_saam.core.di

import android.content.Context
import co.uan.epilepsy.dpa_saam.data.ble.BleManager
import co.uan.epilepsy.dpa_saam.data.ble.BlePayloadParser
import co.uan.epilepsy.dpa_saam.data.local.room.DpaSaamDatabase
import co.uan.epilepsy.dpa_saam.data.notification.LocalNotificationManager
import co.uan.epilepsy.dpa_saam.data.repository.AlertRepositoryImpl
import co.uan.epilepsy.dpa_saam.data.repository.BleRepositoryImpl
import co.uan.epilepsy.dpa_saam.domain.repository.AlertRepository
import co.uan.epilepsy.dpa_saam.domain.repository.BleRepository

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    private val database: DpaSaamDatabase by lazy {
        DpaSaamDatabase.getInstance(appContext)
    }

    private val bleManager: BleManager by lazy {
        BleManager(appContext, BlePayloadParser())
    }

    val alertRepository: AlertRepository by lazy {
        AlertRepositoryImpl(database.alertDao())
    }

    val bleRepository: BleRepository by lazy {
        BleRepositoryImpl(bleManager)
    }

    val notificationManager: LocalNotificationManager by lazy {
        LocalNotificationManager(appContext)
    }
}
