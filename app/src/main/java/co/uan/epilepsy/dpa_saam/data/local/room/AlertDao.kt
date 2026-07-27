package co.uan.epilepsy.dpa_saam.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AlertEntity)

    @Query("SELECT * FROM alerts ORDER BY receivedAtEpochMillis DESC")
    fun observeAll(): Flow<List<AlertEntity>>
}
