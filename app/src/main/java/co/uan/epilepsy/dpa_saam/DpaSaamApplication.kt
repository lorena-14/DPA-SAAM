package co.uan.epilepsy.dpa_saam

import android.app.Application
import co.uan.epilepsy.dpa_saam.core.di.AppContainer

class DpaSaamApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
