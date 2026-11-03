# 阶段与验证记录

| 阶段 | 实现状态 | 验证边界 |
| --- | --- | --- |
| 1 上游分析 | 完成；保留 MIT 与上游实现 | 见 UPSTREAM_ANALYSIS.md |
| 2 基础工程 | 完成；applicationId 为 `com.nau.course` | 保留原依赖版本和现有测试 |
| 3 Parser 解耦 | 完成 | UI/Room 不依赖学校 HTML，厦大解析器兼容测试保留 |
| 4 南审 Parser | 根据脱敏 iframe 样本实现 | 静态样本 11 门课程、18 条排课；用户反馈真机导入成功 |
| 5 WebView | 公开登录入口、进度、返回、刷新、iframe DOM 轮询、错误提示及零高度布局兼容 | 用户真机手动修复后成功导入；自动恢复仍需真机验证 |
| 6 Repository / Room | 导入、课表创建及覆盖为事务；失败回滚 | 样本导入、冲突取消、覆盖和失败回滚测试 |
| 7 课表 UI | 周一到周日、13 节默认作息、显示节数可配置、课程时间编辑、按周翘课 | 逐节时间仍为固定默认；Android 真机交互待验证 |
| 8 Widget | 保留今日与下一节；今日筛选剩余课程；按实际周读取翘课 | Robolectric 数据测试；桌面调度、系统省电行为待真机验证 |
| 9 说明与测试 | 文档、样本、测试和 Debug APK 已准备 | 以最后一次构建日志和测试报告为准；无正式上线验收 |

用户反馈自动恢复版本使用中暂未发现问题；NAU Course 1.0.0 已构建签名 Release APK，仍需在目标设备验证正式包的安装和使用。

## 迁移与隐私

Room 升至 v10，`MIGRATION_9_10` 添加翘课周次并保留原有全周标记；特定周可对旧标记创建例外。不使用破坏性迁移。用户课程、背景、设置保存在本机；页面 HTML 不写入日志或上传。用户提供的原始脱敏文件未删除，已加入 `.gitignore`。

## 本机验证

`gradlew test assembleDebug --offline --no-daemon` 运行完整 Debug JVM/Robolectric 测试并生成 `app/build/outputs/apk/debug/app-debug.apk`。上游禁用 Release unit test 任务的配置未变。

本机测试包括网络安全配置、WebView 诊断脱敏、课程解析、数据库和课表组件；布局修复脚本另用模拟 DOM 验证条件检查、窗口尺寸变化和异常回滚。本开发环境没有连接 Android 设备，用户另行反馈真机调试包完成认证和导入。登录后白屏的诊断步骤见 [WebView 白屏排查](WEBVIEW_DIAGNOSTICS.md)。

本环境 JDK 默认值与 Gradle 不兼容，测试使用 JDK 17 和项目内忽略的 SDK/缓存。Windows 沙箱内 Robolectric 无法解压 SQLite 原生库时，使用获准的非沙箱独立进程；未禁用数据库测试。

曾观察到上游设置页 Compose 测试在 Room Flow 完成前执行滚动的时序问题，现等待实际课表标题出现再进行原断言。
