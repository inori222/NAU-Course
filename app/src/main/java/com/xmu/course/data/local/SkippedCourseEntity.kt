package com.xmu.course.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * 用户本地“翘课”标记。
 *
 * week=0 保留旧版全周标记；正数表示指定周标记，负数表示对应周对全周标记的例外。
 */
@Entity(
    tableName = "skipped_courses",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["courseId", "week"], unique = true)],
)
data class SkippedCourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val courseId: Long,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val week: Int = 0,
)
