package com.xmu.course.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xmu.course.data.CourseRepository
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.domain.Semester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SettingsUiState(
    val semester: Semester? = null,
    val message: String? = null,
)

/** 设置页 ViewModel：学期信息 / 开学日期 / 删除课表。 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(AppDatabase.getInstance(application))
    private val timetableRepository = TimetableRepository(AppDatabase.getInstance(application))

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(TimetablePrefs.currentTimetableId, timetableRepository.observeTimetables(), repository.observeSemesters()) { currentId, timetables, semesters ->
                val selected = timetables.firstOrNull { it.timetable.id == currentId } ?: timetables.firstOrNull()
                semesters.firstOrNull { it.id == selected?.timetable?.semesterId }
            }.collect { semester -> _uiState.update { it.copy(semester = semester) } }
        }
    }

    /** 保存开学日期（yyyy-MM-dd），使课表页可自动定位当前周。 */
    fun saveStartDate(date: String) {
        val semester = _uiState.value.semester ?: return
        viewModelScope.launch {
            repository.updateSemesterStartDate(semester.id, date)
            _uiState.update { it.copy(message = "开学日期已保存：$date") }
        }
    }

    /**
     * 删除课表。
     * @param deleteSemester false=只删课程；true=连同学期记录一起删除。
     */
    fun deleteTimetable(deleteSemester: Boolean) {
        val semester = _uiState.value.semester ?: return
        viewModelScope.launch {
            if (deleteSemester) {
                repository.deleteSemester(semester.id)
                _uiState.update { it.copy(message = "已删除学期及全部课程") }
            } else {
                repository.deleteSemesterCourses(semester.id)
                _uiState.update { it.copy(message = "已删除课程（学期记录保留）") }
            }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }
}
