package ro.infotb.modern.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query("SELECT * FROM dashboard_groups ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM dashboard_groups ORDER BY sortOrder ASC")
    suspend fun getAll(): List<GroupEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<GroupEntity>)

    @Query("DELETE FROM dashboard_groups")
    suspend fun deleteAll()

    @Update
    suspend fun update(entity: GroupEntity)

    @Query("DELETE FROM dashboard_groups WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM dashboard_groups")
    suspend fun nextSortOrder(): Int
}
