package com.xmu.course.data

import androidx.room.withTransaction
import com.xmu.course.data.local.*
import com.xmu.course.parser.ExamSchedule
import com.xmu.course.domain.Exam
import kotlinx.coroutines.flow.map

data class ExamImportPreview(
    val schedule: ExamSchedule,
    val previous: List<ExamEntity>,
    val semesterId: Long?,
) {
    val added = schedule.exams - previous.map { it.exam }.toSet()
    val removed = if (schedule.skippedRows == 0) previous.filter { it.exam !in schedule.exams } else emptyList()
}

class ExamRepository(private val db: AppDatabase) {
    fun observe(semesterId: Long) = db.examDao().observe(semesterId).map { rows -> rows.map { it.exam } }

    suspend fun remove(semesterId: Long, exam: Exam) = db.withTransaction {
        db.examDao().delete(db.examDao().list(semesterId).filter { it.exam == exam })
    }

    suspend fun preview(schedule: ExamSchedule): ExamImportPreview = db.withTransaction {
        require(schedule.exams.isNotEmpty())
        val semester = db.semesterDao().getByCode(schedule.semester.code)
        ExamImportPreview(schedule, semester?.let { db.examDao().list(it.id) }.orEmpty(), semester?.id)
    }

    suspend fun commit(preview: ExamImportPreview): Long = db.withTransaction {
        val schedule = preview.schedule
        require(schedule.exams.isNotEmpty())
        val term = schedule.semester
        val semester = db.semesterDao().getByCode(term.code)
        check(semester?.id == preview.semesterId &&
            semester?.let { db.examDao().list(it.id) }.orEmpty() == preview.previous) {
            "本地日程已变化，请重新预览"
        }
        val id = semester?.id ?: db.semesterDao().insert(SemesterEntity(code = term.code, name = term.name))
        db.examDao().delete(preview.removed)
        db.examDao().insert(preview.added.map { ExamEntity(semesterId = id, exam = it) })
        val timetables = db.timetableDao()
        timetables.getBySemesterId(id)?.id ?: timetables.insert(
            TimetableEntity(name = term.name, semesterId = id, startDate = semester?.startDate,
                createdTime = System.currentTimeMillis())
        ).also { timetables.upsertConfig(TimetableConfigEntity(timetableId = it, showSaturday = true, showSunday = true)) }
    }
}
