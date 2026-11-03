package com.xmu.course

import com.xmu.course.parser.XmuKingosoftParser

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.CourseRepository
import com.xmu.course.data.ImportPreparation
import com.xmu.course.data.ImportResult
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.local.toDomain
import com.xmu.course.domain.Course
import com.xmu.course.domain.CourseSource
import com.xmu.course.domain.Semester
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room + Repository 测试（Robolectric 内存库）。
 * 样本 HTML 为 Phase 3 脱敏样张，保证与真实金智页面结构一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomRepositoryTest {

    @Test fun `完整编辑课程保留身份与备注`() = runTest {
        val html = javaClass.getResourceAsStream("/sample_nau_timetable.html")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val parsed = com.xmu.course.parser.NauCourseParser().parse(html)
        val prep = repository.prepareImport(parsed) as ImportPreparation.Ready
        val result = repository.commitImport(prep.semester, prep.courses, false) as ImportResult.Success
        val original = repository.observeCourses(result.semesterId).first().first()
        repository.updateCourseNote(original.id, "保留备注")
        val updated = original.copy(dayOfWeek = 7, startSection = 11, duration = 3, weeks = setOf(1, 3), note = "保留备注")
        assertTrue(repository.updateCourse(updated))
        val actual = repository.observeCourses(result.semesterId).first().first { it.id == original.id }
        assertEquals(updated, actual)
        assertEquals(18, db.courseDao().countBySemester(result.semesterId))
    }

    @Test fun `南审样本导入自动创建课表并支持安全覆盖`() = runTest {
        val html = javaClass.getResourceAsStream("/sample_nau_timetable.html")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val parser = com.xmu.course.parser.NauCourseParser(com.xmu.course.parser.NauStudentPageAdapter())
        val prep = repository.prepareImport(parser.parse(html)) as ImportPreparation.Ready
        val first = repository.commitImport(prep.semester, prep.courses, false) as ImportResult.Success
        assertEquals(18, db.courseDao().countBySemester(first.semesterId))
        assertEquals(first.timetableId, db.timetableDao().getBySemesterId(first.semesterId)!!.id)
        assertTrue(db.timetableDao().getConfig(first.timetableId) != null)
        val conflict = repository.prepareImport(parser.parse(html)) as ImportPreparation.Conflict
        assertTrue(repository.commitImport(conflict.semester, conflict.pendingCourses, false) is ImportResult.Cancelled)
        assertEquals(18, db.courseDao().countBySemester(first.semesterId))
        val overwrite = repository.commitImport(conflict.semester, conflict.pendingCourses, true) as ImportResult.Success
        assertEquals(first.timetableId, overwrite.timetableId)
        assertEquals(18, db.courseDao().countBySemester(first.semesterId))
    }

    @Test fun `事务失败回滚新增学期和原课程`() = runTest {
        val course = Course(id = 123, name = "测试", dayOfWeek = 1, startSection = 1, duration = 1, weeks = setOf(1))
        val semester = Semester(code = "atomic-test", name = "测试学期")
        val failure = runCatching { repository.commitImport(semester, listOf(course, course), false) }
        assertTrue(failure.isFailure)
        assertTrue(repository.getSemesters().isEmpty())
        val saved = repository.commitImport(semester, listOf(course), false) as ImportResult.Success
        assertTrue(runCatching { repository.commitImport(semester, listOf(course, course), true) }.isFailure)
        assertEquals(1, db.courseDao().countBySemester(saved.semesterId))
    }

    @Test fun `未适配和缺学期结果不能写入数据库`() = runTest {
        val result = XmuKingosoftParser().parse(sampleHtml())
        val rejected = listOf(
            result.copy(failure = com.xmu.course.parser.ParseFailure.ADAPTATION_REQUIRED),
            result.copy(semesterCode = ""),
            result.copy(semesterName = ""),
        )
        rejected.forEach { parsed ->
            val error = runCatching { repository.prepareImport(parsed) }.exceptionOrNull()
            assertTrue(error is com.xmu.course.data.ImportEmptyException)
        }
        assertTrue(repository.getSemesters().isEmpty())
    }

    @Test fun `准备导入保留局部解析警告`() = runTest {
        val parsed = XmuKingosoftParser().parse(sampleHtml()).copy(warnings = listOf("部分课程缺少教师"))
        val prepared = repository.prepareImport(parsed) as ImportPreparation.Ready
        assertEquals(parsed.warnings, prepared.warnings)
        repository.commitImport(prepared.semester, prepared.courses, false)
        val conflict = repository.prepareImport(parsed) as ImportPreparation.Conflict
        assertEquals(parsed.warnings, conflict.warnings)
    }

    private lateinit var db: AppDatabase
    private lateinit var repository: CourseRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CourseRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun sampleHtml(): String =
        javaClass.getResourceAsStream("/sample_timetable.html")!!
            .readBytes().toString(Charsets.UTF_8)

    @Test fun `导入-查询课程`() = runTest {
        val prep = repository.prepareImport(XmuKingosoftParser().parse(sampleHtml())) as ImportPreparation.Ready
        val result = repository.commitImport(prep.semester, prep.courses, overwrite = false)
        assertTrue(result is ImportResult.Success)

        val courses = repository.observeCourses((result as ImportResult.Success).semesterId).first()
        assertEquals(38, courses.size)
        assertTrue(courses.all { it.source == CourseSource.IMPORT })
        assertTrue(courses.all { it.weeks.isNotEmpty() })
    }

    @Test fun `同学期导入触发冲突并覆盖`() = runTest {
        val prep = repository.prepareImport(XmuKingosoftParser().parse(sampleHtml())) as ImportPreparation.Ready
        repository.commitImport(prep.semester, prep.courses, overwrite = false)

        // 再次导入同一学期：应得到 Conflict，含双方课程数。
        val conflict = repository.prepareImport(XmuKingosoftParser().parse(sampleHtml())) as ImportPreparation.Conflict
        assertEquals(38, conflict.existingCount)
        assertEquals(38, conflict.newCount)

        // 覆盖导入：旧数据被删除、新数据写入，总数不变。
        val result = repository.commitImport(conflict.semester, conflict.pendingCourses, overwrite = true)
        val courses = repository.observeCourses(conflict.semester.id).first()
        assertEquals(38, courses.size)
    }

    @Test fun `多学期隔离`() = runTest {
        val prep = repository.prepareImport(XmuKingosoftParser().parse(sampleHtml())) as ImportPreparation.Ready
        val r1 = repository.commitImport(prep.semester, prep.courses, overwrite = false) as ImportResult.Success

        // 手动创建第二学期并插入一门课程。
        val semester2 = Semester(code = "20252", name = "2025-2026学年 春季学期")
        val r2 = repository.commitImport(
            semester2,
            listOf(
                Course(
                    name = "手动课程",
                    dayOfWeek = 1,
                    startSection = 1,
                    duration = 2,
                    weeks = setOf(1, 2, 3),
                    source = CourseSource.MANUAL,
                ),
            ),
            overwrite = false,
        ) as ImportResult.Success

        assertEquals(38, repository.observeCourses(r1.semesterId).first().size)
        assertEquals(1, repository.observeCourses(r2.semesterId).first().size)
        assertEquals(2, repository.getSemesters().size)
    }

    @Test fun `删除学期级联删除课程`() = runTest {
        val prep = repository.prepareImport(XmuKingosoftParser().parse(sampleHtml())) as ImportPreparation.Ready
        val r = repository.commitImport(prep.semester, prep.courses, overwrite = false) as ImportResult.Success
        repository.deleteSemester(r.semesterId)
        assertEquals(0, repository.observeCourses(r.semesterId).first().size)
        assertTrue(repository.getSemesters().none { it.id == r.semesterId })
    }
}
