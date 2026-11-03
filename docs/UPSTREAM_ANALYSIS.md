# Phase 1：XMU-Course 架构分析

基线：<https://github.com/Comzjh/XMU-Course>，commit `9a6f316c61ca8cd09a1ab7fe71d97e58c41f9ddd`。
保留 Git 历史及原始 LICENSE（MIT，Copyright (c) 2026 XMU Course contributors）。

## 数据流与复用边界

| 模块 | 已有实现 | 处理方式 |
| --- | --- | --- |
| domain | Course（Set<Int> 周次、来源、颜色、备注）、Semester、Timetable、TimetableConfig | 保留 Set，无需改成重复的 List 模型 |
| Room | v9，5 张表，DAO Flow，外键级联，1→9 连续 Migration | 保留结构和迁移；启用 schema 导出以便后续演进 |
| CourseRepository | 解析、学期冲突准备、课程提交、编辑、翘课 | 解耦具体 Parser，解析移到 Default dispatcher；修正导入事务范围 |
| TimetableRepository | 创建/删除/重命名、学期绑定、独立外观配置 | 保留；加强事务和日期同步 |
| TimetablePrefs / DisplaySettings | SharedPreferences + StateFlow | 保留 |
| TimetableViewModel | 当前课表 Flow → 课程/配置/翘课 combine → StateFlow | 保留；保留最新的开学日期更新修复 |
| TimetableLayoutEngine | 先按周过滤、同课合并、按天计算重叠连通组、贪心分栏 | 原样复用；节次高度与横向分栏解耦 |
| TimetableScreen / CourseCard | 自定义网格坐标、连堂高度、非本周视觉、详情与手动加课 | 保留；调整学校文案与作息配置 |
| manager / course / settings | 多课表、课程字段编辑、日期和显示设置 | 保留交互；审查当前学期选择与完整课程编辑 |
| background / theme | 本地资源/图片 URI、裁剪、模糊、遮罩、Material 3、系统深浅色 | 保留渲染；厦大图片必须标为上游素材，不冒充南审 |
| widget | Glance 今日课程、下一节课；Room/Prefs 快照、Flow 更新、非精确 Alarm | 保留主体；审查剩余课程筛选、作息越界与 receiver 生命周期 |
| XmuKingosoftParser | Jsoup，#myXnxqSelect、#jsTbl_01、div.arrage、jc/xq/jcxq | 厦大专用，保留为兼容实现，禁止复制这些 selector 到南审解析器 |
| WeekPatternParser | 区间、离散、混合单/双周，返回 Set | 通用，扩展括号单双周及“第”前缀测试 |
| WebViewScreen | 硬编码厦大入口、10×1.2s arrage 轮询 | 抽出学校适配和 DOM 等待器，增加总超时及取消保护 |
| WebSessionManager | CookieManager 持久化、DOM storage | 保留；移除整个缓存目录递归删除 |
| ImportViewModel / ImportScreen | 导入准备与覆盖，文件导入 | 移除 HTML/认证页面日志，文件读取转 IO；WebView 共用覆盖确认 |

## 数据库真实关系

`Semester → Course`，`Semester → Timetable → TimetableConfig`，`Course → SkippedCourse`。
课程没有 timetableId；DAO 经 semesterId JOIN 课表。同一学期的多个课表共享课程。
自定义课表创建 `custom-*` 隐藏学期，删除时级联；导入课表删除只移除课表，学期课程保留。
不能声称当前库已是独立的 `Semester → Timetable → Course`。优先保留上游语义，独立副本需另行迁移。

周次在库中用逗号分隔字符串保存，Domain 转 Set；单双周在解析时展开，UI/Widget 用 occursInWeek 过滤。
SkippedCourse v9 只存 courseId、createdAt，为全课程标记，不是“只隐藏本周”。
历史迁移没有调用破坏性兜底，但 AppDatabase 注释过期且 exportSchema=false。

## 关键缺口

1. CourseRepository 直接构造 XmuKingosoftParser；学校 HTML 耦合到了数据入口。
2. ImportViewModel 把 HTML 前 500 字符写入 Logcat，可能包含个人信息，必须去除。
3. 只有 ImportScreen 展示覆盖对话框，WebViewScreen 的独立 ViewModel 冲突无 UI。
4. 抓取按钮可重复点击；evaluateJavascript 回调没有取消检查；只有轮询次数限制，没有回调总超时。
5. 文件在 ActivityResult 回调主线程读取；解析也没有显式切换 dispatcher。
6. commitImport 的学期创建在事务外，课表绑定又在后续步骤，异常可能留下部分数据。
7. TimeTableConfig 硬编码厦大 11 节作息，影响课表和 Widget；不能将其标为南审真实时间。
8. 今日 Widget 当前展示全天，下一节倒计时使用非精确 Alarm，受系统省电策略影响。
9. SettingsViewModel 当前取最新 code 学期，而非当前选择的课表，需避免改错学期。
10. 原有 support 页面含捐款码和占位 GitHub 地址，衍生应用不能误导为南审维护者。

## 测试基线

仓库已有 Parser、周次、Room、Migration、TimetableRepository、布局、Compose、背景和 Widget 测试。
MigrationTest 从 v1 种子库走完整迁移链并验证课程保留。
上游 commit 已将 semester.startDate 同步到关联课表，避免 actualWeek 不更新；保留对应回归测试。
本机验证结果单独记录在 PHASE_STATUS.md，不将静态阅读或编译通过等同于测试通过。

## 南京审计大学资料清单

当前仓库无南审真实 HTML、课表入口或认证跳转资料。以下均待确认：入口 URL、登录后 URL、SPA/AJAX、iframe、课程节点、星期、节次、周次、教师、教室、学期与官方作息表。
需要提供**南京审计大学我的课表页面完整 HTML**（登录并等课程出现后运行 `document.documentElement.outerHTML`，脱敏后保存）。
HTML 应保留结构、属性、周次和节次；移除姓名、学号、认证字段/Token 等个人信息。
未取得样本前不创建假 `sample_nau_timetable.html`，不实现具体南审 selector，不以厦大样本冒充南审样本。
