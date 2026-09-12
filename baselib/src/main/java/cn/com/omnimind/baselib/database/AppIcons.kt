package cn.com.omnimind.baselib.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "app_icons")
data class AppIcons(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val packageName: String,
    val icon_base64: String,
    val icon_path: String,
    val createdAt: Long = Date().time,
    val updatedAt: Long = Date().time
)