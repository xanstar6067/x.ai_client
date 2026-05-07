package com.adam.xai_client.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.adam.xai_client.data.local.entity.ModelRoleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelRoleDao {
    @Query("SELECT * FROM model_roles ORDER BY isBuiltIn DESC, name COLLATE NOCASE ASC")
    fun observeRoles(): Flow<List<ModelRoleEntity>>

    @Query("SELECT * FROM model_roles WHERE id = :roleId")
    suspend fun getRole(roleId: Long): ModelRoleEntity?

    @Query("SELECT * FROM model_roles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultRole(): ModelRoleEntity?

    @Query("SELECT * FROM model_roles WHERE isBuiltIn = 1 LIMIT 1")
    suspend fun getBuiltInRole(): ModelRoleEntity?

    @Insert
    suspend fun insertRole(role: ModelRoleEntity): Long

    @Update
    suspend fun updateRole(role: ModelRoleEntity)

    @Delete
    suspend fun deleteRole(role: ModelRoleEntity)

    @Query("UPDATE model_roles SET isDefault = CASE WHEN id = :roleId THEN 1 ELSE 0 END")
    suspend fun setDefaultRole(roleId: Long)
}
