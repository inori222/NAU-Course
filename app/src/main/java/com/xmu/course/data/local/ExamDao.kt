package com.xmu.course.data.local

import androidx.room.*
import com.xmu.course.domain.Exam
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "exams",
    foreignKeys = [ForeignKey(entity = SemesterEntity::class, parentColumns = ["id"],
        childColumns = ["semesterId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("semesterId")],
)
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val semesterId: Long,
    @Embedded val exam: Exam,
)

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE semesterId = :semesterId ORDER BY startAt, id")
    fun observe(semesterId: Long): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE semesterId = :semesterId ORDER BY startAt, id")
    suspend fun list(semesterId: Long): List<ExamEntity>

    @Query("SELECT * FROM exams WHERE semesterId IN (SELECT semesterId FROM timetables) ORDER BY id")
    fun observeAll(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE id = :id AND semesterId IN (SELECT semesterId FROM timetables)")
    suspend fun get(id: Long): ExamEntity?

    @Query("SELECT * FROM exams WHERE semesterId = :semesterId AND startAt >= :start AND startAt < :end ORDER BY startAt, id")
    suspend fun onDate(semesterId: Long, start: Long, end: Long): List<ExamEntity>

    @Delete suspend fun delete(rows: List<ExamEntity>)
    @Insert suspend fun insert(rows: List<ExamEntity>)
}
