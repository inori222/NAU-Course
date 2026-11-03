package com.xmu.course.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SkippedCourseDao {

    @Query("SELECT courseId FROM skipped_courses WHERE week = 0")
    fun observeSkippedCourseIds(): Flow<List<Long>>

    @Query("SELECT courseId FROM skipped_courses WHERE week = 0")
    suspend fun getSkippedCourseIds(): List<Long>

    @Query("SELECT * FROM skipped_courses")
    fun observeAll(): Flow<List<SkippedCourseEntity>>

    @Query("SELECT DISTINCT s.courseId FROM skipped_courses s WHERE s.week = :week OR (s.week = 0 AND NOT EXISTS (SELECT 1 FROM skipped_courses x WHERE x.courseId = s.courseId AND x.week = -:week))")
    suspend fun getSkippedCourseIdsForWeek(week: Int): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM skipped_courses WHERE courseId = :courseId AND week = 0)")
    suspend fun hasGlobalMark(courseId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<SkippedCourseEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: SkippedCourseEntity)

    @Query("DELETE FROM skipped_courses WHERE week <= 0 AND courseId IN (:courseIds)")
    suspend fun deleteByCourseIds(courseIds: List<Long>)

    @Query("DELETE FROM skipped_courses WHERE courseId = :courseId AND week <= 0")
    suspend fun deleteByCourseId(courseId: Long)

    @Query("DELETE FROM skipped_courses WHERE courseId = :courseId AND week = :week")
    suspend fun deleteForWeek(courseId: Long, week: Int)
}
