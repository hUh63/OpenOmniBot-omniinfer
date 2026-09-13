package cn.com.omnimind.baselib.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AppIconsDao {
    @Insert
    suspend fun insert(appIcon: AppIcons): Long

    @Update
    suspend fun update(appIcon: AppIcons)

    @Query("SELECT * FROM app_icons WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): AppIcons?

    @Query("SELECT * FROM app_icons WHERE packageName IN (:packageNames)")
    suspend fun getByPackageNames(packageNames: List<String>): List<AppIcons>
}