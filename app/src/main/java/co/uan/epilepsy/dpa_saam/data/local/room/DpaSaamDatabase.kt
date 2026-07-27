package co.uan.epilepsy.dpa_saam.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AlertEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DpaSaamDatabase : RoomDatabase() {

    abstract fun alertDao(): AlertDao

    companion object {
        @Volatile
        private var instance: DpaSaamDatabase? = null

        fun getInstance(context: Context): DpaSaamDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DpaSaamDatabase::class.java,
                    "dpa_saam.db",
                ).build().also { instance = it }
            }
        }
    }
}
