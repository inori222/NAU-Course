package com.xmu.course.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import android.util.Log
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xmu.course.domain.Course
import com.xmu.course.domain.Exam
import com.xmu.course.domain.examZone
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.takeOrElse
import com.xmu.course.domain.TimetableConfig
import com.xmu.course.data.DisplaySettings
import com.xmu.course.ui.background.TimetableBackground

private val CELL_HEIGHT = 56.dp

/**
 * 课表页：周视图 + HorizontalPager 周切换 + 当前周定位 + 课程详情 + 手动添加。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    modifier: Modifier = Modifier,
    initialCourseId: Long? = null,
    onInitialCourseConsumed: (Long?) -> Unit = {},
    viewModel: TimetableViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(initialPage = state.viewWeek - 1, pageCount = { state.totalWeeks })
    var showAddDialog by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var detailCourse by remember { mutableStateOf<Course?>(null) }

    val fontColor = state.config.fontColor?.let { Color(it) } ?: Color.Unspecified
    var showExams by remember(state.timetable?.id) { mutableStateOf(false) }
    var selectedExam by remember(state.timetable?.id) { mutableStateOf<Exam?>(null) }

    // 查看周变化（首次进入默认定位实际周；周选择器选择/回到本周也会触发）。
    LaunchedEffect(state.viewWeek) {
        if (state.hasData) pagerState.scrollToPage(state.viewWeek - 1)
    }
    LaunchedEffect(state.courses) {
        Log.d("XmuImport", "Screen: 渲染 courses=${state.courses.size}")
    }

    // Widget 点击后打开对应课程详情；详情展示一次后清除请求。
    LaunchedEffect(initialCourseId, state.courses) {
        val requested = initialCourseId ?: return@LaunchedEffect
        state.courses.firstOrNull { it.id == requested }?.let {
            detailCourse = it
            onInitialCourseConsumed(requested)
        }
    }
    // Pager 滑动同步回状态。
    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectWeek(pagerState.currentPage + 1)
    }

    Box(modifier.fillMaxSize()) {
        // 背景在最底层；不参与课程位置、高度、宽度或冲突分栏计算。
        if (state.hasData) TimetableBackground(state.config)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                if (state.config.headerCompactMode) {
                    TimetableCompactHeader(
                        fontColor = fontColor,
                        title = if (state.hasData) {
                            val suffix = if (pagerState.currentPage + 1 == state.actualWeek) " · 本周" else ""
                            "第 ${pagerState.currentPage + 1} 周$suffix ▼"
                        } else {
                            state.timetable?.name ?: "NAU Course"
                        },
                        subtitle = if (state.hasData) {
                            state.timetable?.name ?: "NAU Course"
                        } else {
                            "尚无数据，请先导入"
                        },
                        onTitleClick = { if (state.hasData) showWeekPicker = true },
                        onAddClick = { if (state.hasData) showAddDialog = true },
                    )
                } else {
                    TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = fontColor.takeOrElse { MaterialTheme.colorScheme.onSurface },
                    ),
                    title = {
                        Column {
                            Text(
                                text = if (state.hasData) {
                                    val suffix = if (pagerState.currentPage + 1 == state.actualWeek) " · 本周" else ""
                                    "第 ${pagerState.currentPage + 1} 周$suffix ▼"
                                } else {
                                    state.timetable?.name ?: "NAU Course"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.clickable(enabled = state.hasData) { showWeekPicker = true },
                            )
                            Text(
                                text = if (state.hasData) state.timetable?.name ?: "NAU Course" else "尚无数据，请先导入",
                                style = MaterialTheme.typography.bodyMedium,
                                color = fontColor.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }, enabled = state.hasData) {
                            Icon(Icons.Filled.Add, contentDescription = "添加课程")
                        }
                    },
                    )
                }
            },
        ) { innerPadding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            !state.hasData || state.timetable == null -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text("还没有课表\n去“导入”页保存课表，或在“设置 → 课表管理”新建", textAlign = TextAlign.Center) }

            state.courses.isEmpty() && state.exams.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无课程\n请先导入南审课表，或点右上角 + 手动添加",
                    textAlign = TextAlign.Center,
                )
            }

            else -> HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) { page ->
                val startDate = state.timetable?.startDate
                val weekOne = startDate ?: LocalDate.now(examZone)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
                val exams = remember(state.exams, weekOne, page) { examsInWeek(state.exams, weekOne, page + 1) }
                TimetableWeekGrid(
                    week = page + 1, courses = state.courses, exams = exams,
                    skippedCourseIds = state.skippedCourseIds, showGrid = state.showGrid,
                    config = state.config, onCourseClick = { detailCourse = it },
                    onExamClick = { selectedExam = it },
                )
            }
        }
    }

    // ---- 周选择器 ----
    if (showWeekPicker && state.hasData) {
        WeekPickerSheet(
            totalWeeks = state.totalWeeks,
            viewWeek = state.viewWeek,
            actualWeek = state.actualWeek,
            onSelect = {
                viewModel.selectWeek(it)
                showWeekPicker = false
            },
            onBackToCurrent = {
                viewModel.backToCurrentWeek()
                showWeekPicker = false
            },
            onExams = if (state.exams.isEmpty()) null else ({
                showWeekPicker = false
                showExams = true
            }),
            onDismiss = { showWeekPicker = false },
        )
    }

    // 对话框挂在 Scaffold 之外，保证任何分支下都能弹出（Bug 4 修复）。
    if (showAddDialog) {
        AddCourseDialog(
            onDismiss = { showAddDialog = false },
            onSave = {
                viewModel.addCourse(it)
                showAddDialog = false
            },
        )
    }

    if (showExams) SemesterExams(state.exams, state.timetable?.startDate, onSelect = {
        selectedExam = it
        showExams = false
    }, onClose = { showExams = false })
    selectedExam?.let { exam ->
        ExamDetails(exam, onRemove = {
            state.timetable?.let { viewModel.removeExam(it.semesterId, exam) }
            selectedExam = null
        }, onClose = { selectedExam = null })
    }

    detailCourse?.let { course ->
        CourseDetailSheet(
            course = course,
            isSkipped = course.id in state.skippedCourseIds,
            onDismiss = { detailCourse = null },
            onToggleSkipped = { skipped ->
                viewModel.toggleCourseSkipped(course.id, skipped)
                detailCourse = null
            },
            onColorChange = { hex ->
                viewModel.updateCourseColor(course.id, hex)
                detailCourse = null
            },
            onNoteChange = { note ->
                viewModel.updateCourseNote(course.id, note)
            },
        )
    }
    }
}

@Composable
private fun TimetableCompactHeader(
    title: String,
    subtitle: String,
    fontColor: Color,
    onTitleClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = 12.dp, end = 2.dp, top = 0.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = fontColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.clickable(onClick = onTitleClick),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = fontColor.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
                maxLines = 1,
            )
        }
        IconButton(
            onClick = onAddClick,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加课程", modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * 单周网格：左侧节次（含时间）+ 周日~周六 7 列，课程卡片按布局引擎放置。
 */
@Composable
internal fun TimetableWeekGrid(
    week: Int,
    courses: List<Course>,
    exams: List<Exam>,
    onExamClick: (Exam) -> Unit,
    skippedCourseIds: Set<Long>,
    showGrid: Boolean,
    config: TimetableConfig,
    onCourseClick: (Course) -> Unit,
) {
    val cellHeight = config.courseHeight.dp
    val configuredCount by DisplaySettings.sectionCount.collectAsState()
    val sectionCount = effectiveSectionCount(configuredCount, courses)
    // TimeColumn 和 Grid 使用同一纵向布局配置，禁止各自独立推算高度。
    val grid = remember(sectionCount, exams) { MinuteGrid(sectionCount, exams) }
    val examsByDay = remember(exams) { exams.groupBy { it.date().dayOfWeek.value } }
    val fontColor = config.fontColor?.let { Color(it) } ?: Color.Unspecified
    // showNonCurrentWeek=false 时隐藏非本周课程
    val layout = remember(courses, week, config.showNonCurrentWeek) {
        TimetableLayoutEngine.layoutForWeek(courses, week, onlyCurrentWeek = !config.showNonCurrentWeek)
    }
    val scrollState = rememberScrollState()
    val axisTypography = timeAxisTypography(cellHeight.value)
    // 列顺序：周一到周日。
    val dayColumns: List<Int> = buildList {
        addAll(listOf(1, 2, 3, 4, 5))
        if (config.showSaturday || 6 in examsByDay) add(6)
        if (config.showSunday || 7 in examsByDay) add(7)
    }
    val dayNames = mapOf(7 to "周日", 1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四", 5 to "周五", 6 to "周六")

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            Text("", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall)
            dayColumns.forEach { day ->
                Text(
                    text = dayNames.getValue(day),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = fontColor.takeOrElse { MaterialTheme.colorScheme.onSurface },
                )
            }
        }
        HorizontalDivider()

        Row(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            // 左侧节次标号 + 起止时间（两行，避免截断）。
            Column(Modifier.width(60.dp)) {
                // 分隔线画在固定高度的 Box 内部，不能使用额外 HorizontalDivider。
                // 否则每节累计 +1dp，时间轴会逐渐偏离右侧网格线。
                val axisLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                if (grid.leadingRows > 0) OutsidePeriods("课前", cellHeight * grid.leadingRows, fontColor)
                repeat(sectionCount) { index ->
                    Box(
                        Modifier
                            .height(cellHeight)
                            .fillMaxWidth()
                            .drawBehind {
                                if (showGrid) {
                                    drawLine(
                                        color = axisLineColor,
                                        start = Offset(0f, size.height),
                                        end = Offset(size.width, size.height),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(axisTypography.spacingDp.dp),
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = axisTypography.numberSizeSp.sp,
                                lineHeight = axisTypography.numberLineHeightSp.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = fontColor.takeOrElse { MaterialTheme.colorScheme.onSurface },
                            )
                            if (config.showFullTimeAxis) {
                                val parts = TimeTableConfig.timeOf(index + 1).split("-")
                                Text(
                                    text = parts.getOrNull(0) ?: "",
                                    fontSize = axisTypography.timeSizeSp.sp,
                                    lineHeight = axisTypography.timeLineHeightSp.sp,
                                    color = fontColor.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
                                )
                                Text(
                                    text = parts.getOrNull(1) ?: "",
                                    fontSize = axisTypography.timeSizeSp.sp,
                                    lineHeight = axisTypography.timeLineHeightSp.sp,
                                    color = fontColor.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
                                )
                            }
                        }
                    }
                    if (index + 1 == grid.knownCount && grid.trailingRows > 0) {
                        OutsidePeriods("课后", cellHeight * grid.trailingRows, fontColor)
                    }
                }
            }
            // 天列。
            dayColumns.forEach { day ->
                DayColumn(
                    exams = examsByDay[day].orEmpty(),
                    onExamClick = onExamClick,
                    items = layout[day].orEmpty(),
                    skippedCourseIds = skippedCourseIds,
                    showGrid = showGrid,
                    grid = grid,
                    config = config,
                    onCourseClick = onCourseClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 单天列：背景节次分隔线 + 课程卡片（冲突分栏）。
 */
@Composable
private fun DayColumn(
    exams: List<Exam>,
    onExamClick: (Exam) -> Unit,
    items: List<TimetableLayoutItem>,
    skippedCourseIds: Set<Long>,
    showGrid: Boolean,
    grid: MinuteGrid,
    config: TimetableConfig,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cellHeight = config.courseHeight.dp
    BoxWithConstraints(
        modifier = modifier.height(cellHeight * grid.height),
    ) {
        // 渲染顺序：先画 ghost（非本周淡化，独立布局），再画本周课程覆盖其上。
        val placed = remember(grid, items, exams) { grid.layout(items, exams) }
        val cardText = config.fontColor?.let { Color(it) } ?: Color.White

        // 网格只作为 Canvas 视觉层：不新增 padding/size，也不改变课程 Box 的约束。
        if (showGrid) {
            val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            Canvas(Modifier.matchParentSize()) {
                val strokeWidth = 1.dp.toPx()
                for (index in 0 until grid.sectionCount) {
                    val y = (grid.sectionTop(index) + 1f) * cellHeight.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth,
                    )
                }
                // 天列右边界；列与列之间连续形成竖向分隔线。
                drawLine(
                    color = lineColor,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidth,
                )
            }
        }

        placed.forEach { lane ->
            val event = lane.item
            val slotWidth = maxWidth / lane.count
            val (top, bottom) = grid.bounds(event)
            Box(Modifier.offset(x = slotWidth * lane.lane, y = cellHeight * top)
                .width(slotWidth).height(cellHeight * (bottom - top)).clip(androidx.compose.ui.graphics.RectangleShape)
                .padding(1.dp)) {
                when (event) {
                    is GridEvent.ExamSlot -> ExamCard(event.exam, config, cardText) { onExamClick(event.exam) }
                    is GridEvent.Lesson -> key(event.item.course.id) {
                        val item = event.item
                        val course = item.course
                        CourseCard(
                            course = course,
                            fontColor = cardText,
                            isCurrentWeek = item.isCurrentWeek,
                            isSkipped = course.id in skippedCourseIds,
                            showTeacher = config.showTeacher,
                            showLocation = config.showLocation,
                            showNote = config.showNote,
                            textSize = config.textSize,
                            cornerRadius = config.cornerRadius,
                            cardAlpha = config.courseAlpha,
                            textHorizontalAlignment = config.textHorizontalAlignment,
                            textVerticalAlignment = config.textVerticalAlignment,
                            onClick = { onCourseClick(course) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 周选择器 BottomSheet：动态 1..totalWeeks 四列网格。
 * 实际周描边提示，查看周高亮填充；"回到本周"一键复位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekPickerSheet(
    totalWeeks: Int,
    viewWeek: Int,
    actualWeek: Int,
    onSelect: (Int) -> Unit,
    onBackToCurrent: () -> Unit,
    onExams: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("选择周", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                onExams?.let { TextButton(onClick = it) { Text("学期考试") } }
                TextButton(onClick = {
                    onBackToCurrent()
                    onDismiss()
                }) { Text("回到本周") }
            }
            val rows = (totalWeeks + 3) / 4
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height((rows * 56).dp),
            ) {
                items((1..totalWeeks).toList()) { week ->
                    val selected = week == viewWeek
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(shape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .then(
                                if (week == actualWeek && !selected) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable {
                                onSelect(week)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$week",
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else null,
                        )
                    }
                }
            }
        }
    }
}

