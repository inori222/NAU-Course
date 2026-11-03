# NAU Course（南京审计大学本地课表）

项目仓库：[inori222/NAU-Course](https://github.com/inori222/NAU-Course)

基于 [Comzjh/XMU-Course](https://github.com/Comzjh/XMU-Course) 的 Android 本地课表项目，保留其 Compose、Room、课表布局与 Glance Widget 架构。源码基线为 `9a6f316c61ca8cd09a1ab7fe71d97e58c41f9ddd`。

应用打开[南京审计大学教务系统公开登录首页](https://jwc.nau.edu.cn/)，用户自行完成登录并进入“我的课表”，点击导入后本地读取课表 HTML、解析课程并保存。**所有课程数据默认保存在用户设备本地。** 项目没有后端、云同步或课程上传功能，应用不保存账号密码，不模拟登录或绕过认证。

### 统一认证的 HTTP 风险

学校公开首页的统一认证入口使用 `http://sso.nau.edu.cn`，其回跳地址使用 `http://jwc.nau.edu.cn`。匿名检查中，HTTPS 认证入口重定向回 HTTP；用户的真机诊断也观察到 HTTP 认证跳转及返回 HTTPS 教务站。为了兼容学校当前流程，应用只对这两个完整主机名开放 HTTP，不包括子域名；其他域名仍禁止明文。WebView 进入 HTTP 主页面时会显示风险提示。

HTTP 传输没有加密或完整性保护，经过的网络可能读取或篡改账号、密码、认证凭据及课程信息。即使使用可信网络也不能消除风险；建议向学校确认其 HTTPS 认证方案。该应用未关闭 TLS 证书校验，也未全局放宽 WebView 混合内容策略。

## 当前实现

- 根据用户提供的脱敏页面样本，读取顶层 `FrameContent` iframe 中的课表 DOM；等待动态内容加载，最多等待 12 秒。同源策略不允许访问或页面未就绪时会提示错误，不覆盖本地数据。
- `NauStudentPageAdapter` 读取学生课程列表中的学期、课程、教师、地点和每条上课时间；将“之单周／之双周”、离散周次及连堂转换为领域模型。
- 解析容忍单条记录损坏，保留警告并去除重复记录；学校页面结构仅在适配器中，不泄漏到 Room 或课表布局。
- 学期、课程、课表及配置在同一 Room 事务中提交。已有学期先询问是否覆盖；空结果和失败不会覆盖旧课程。成功后切换到导入的课表。
- 复用上游 `TimetableLayoutEngine`、多课表、课程管理和外观配置，按周一至周日显示；新导入课表默认显示周末。课程管理支持编辑课程信息及上课时间，新选背景图片会复制到应用私有存储。
- 采用学校[公开课表查询页面](https://jwc.nau.edu.cn/coursearrangeInfosearch.aspx)列出的 13 节作息，覆盖连堂和 Widget 倒计时；仍需确认实际校区是否适用该作息。
- 课程详情的翘课标记针对正在查看的周；课程管理页“所有周翘课设置”保留上游全周标记。Room v9→v10 迁移保留旧标记；对全周标记取消某一周时只为该周创建例外。
- 保留今日课程、下一节课两个 Glance Widget。今日课程显示尚未结束的课程，Widget 直接读取本地数据库；自动刷新时间可能受 Android 系统调度影响。
- 已针对真机发现的教务学生页面 `body` 高度为零问题加入局部布局兼容，仅在学校 HTTPS 域名、页面结构和异常条件均匹配时触发；不更改认证、Cookie 或网络安全策略。

## 使用方法

1. 安装应用后，在导入页选择“打开南审教务”，通过学校页面自行完成统一身份认证。
2. 在教务页面进入“我的课表”，待页面显示完成后点击应用顶部的“导入课表”。如果已有同学期课程，确认是否覆盖。
3. 返回课表查看和切换周数；在设置中填写学期开学日期，可离线查看已导入的课程。也可在系统桌面添加课程 Widget。

用户在 Android 16 设备上反馈：通过调试包手动修复布局后，可以正常显示教务页面并导入课表。自动触发版本已通过模拟和本地测试，仍需更多设备验证；学校调整页面结构后也可能需要更新适配。

## 支持项目

应用内“支持开发与赞助”分别标明 NAU 适配维护者的微信收款码及 XMU-Course 原作者的收款码。赞助完全自愿，请在支付前核对接收对象；项目本身免费开源。

## 样本和测试

`app/src/test/resources/sample_nau_timetable.html` 来自用户提供的脱敏 iframe 样本，课程、教师和地点使用示例值。原始页面文件已排除在版本控制之外。不要将包含身份信息、Cookie、认证字段或未脱敏课程数据的网页提交到仓库。

测试覆盖样本中的 18 条排课、单双周、多次上课、缺失字段、损坏记录、重复和冲突；以及 Room 导入、覆盖取消、事务回滚、数据库迁移、布局和 Widget。上游厦大解析器和其测试保留为兼容参考，不作为南审导入的回退。

## 当前限制

- 用户反馈一个 Android 16 设备通过调试包完成了认证、页面查看和导入；自动布局恢复、其他设备、重启持久化和桌面组件刷新仍需进一步真机验证。
- 课表显示节数可在课表设置中调整；具体每节起止时间目前使用学校公开的 13 节作息，尚不支持逐节修改。
- 同一学期的多个课表仍共享课程，沿用上游 Room 结构；详见 [上游架构分析](docs/UPSTREAM_ANALYSIS.md)。
- 旧版通过外部 URI 设置的背景会保持原样；如需确保图片独立保存在应用内部，可重新选择该图片。
- 旧版全周翘课标记在迁移后仍生效。课程详情的按周取消不会清除其他周；全周设置可在课程管理中修改。

## 构建

需要 JDK 17、Android SDK 35 和 Build Tools 36.0.0。保持上游 AGP 8.7.3、Kotlin 2.0.21、Room 2.6.1 等依赖版本；本机 SDK 路径放在 `local.properties`，不要提交个人路径和代理。

```sh
git clone https://github.com/inori222/NAU-Course.git
cd NAU-Course
./gradlew test
./gradlew assembleDebug
node app/src/test/js/check-page-inspection.cjs # 可选：验证布局脚本
```

Windows 使用 `gradlew.bat`。网络受限时，依赖已经缓存可加 `--offline`；受限 Windows 环境下 Robolectric 解压 SQLite DLL 可能需要非沙箱 `--no-daemon` 进程。上游 `testReleaseUnitTest` 任务保持禁用，完整测试运行在 Debug JVM/Robolectric variant。

正式版需要自己的 Android 签名密钥。复制 `keystore.properties.example` 为 `keystore.properties`，在本机填写密钥路径及密码，然后运行 `./gradlew assembleRelease`。密钥和配置不得提交到仓库；请离线备份两者，后续更新必须使用同一个签名。不同签名的 Debug 和 Release 包不能互相覆盖安装，卸载会删除本地课表数据。

测试结果、迁移说明和未完成事项见 [阶段记录](docs/PHASE_STATUS.md)。调试包位于 `app/build/outputs/apk/debug/app-debug.apk`；请在 Android 真机测试学校登录和课表导入，正式验收以实际设备结果为准。

测试和诊断的范围见 [阶段记录](docs/PHASE_STATUS.md) 与 [WebView 白屏排查](docs/WEBVIEW_DIAGNOSTICS.md)。本开发环境未连接 Android 设备，用户已另行反馈调试包可完成认证与导入。

## 许可与来源

原项目的 [MIT License](LICENSE) 及 `Copyright (c) 2026 XMU Course contributors` 保留；许可证全文也包含在 APK 的 `assets/licenses/XMU-Course-MIT.txt`。本项目与南京审计大学无官方隶属关系。
