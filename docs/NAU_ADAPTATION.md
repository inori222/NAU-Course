# 南京审计大学页面适配说明

## 样本来源和边界

用户提供了两个脱敏 HTML：顶层学生页面包含 `iframe#FrameContent`，iframe 内含课程列表 `table#content` 与课表网格 `table#kb`。脱敏后的 iframe 样本保存在 `app/src/test/resources/sample_nau_timetable.html`，共有 11 行课程、18 条排课；用户原始文件仍保留在项目根目录并被 `.gitignore` 忽略。

学生页面的 URL 查询参数可能属于登录会话，不保存、不提交、不硬编码。应用从[学校公开登录首页](https://jwc.nau.edu.cn/)进入，用户自行认证。

学校公开首页中的统一认证入口指向 `http://sso.nau.edu.cn`，回跳参数指向 `http://jwc.nau.edu.cn/login_single.aspx`。匿名访问 HTTPS 认证入口时观察到它重定向回 HTTP；匿名访问 HTTPS 回跳时也重定向至 HTTP 认证入口。用户真机的脱敏诊断观察到了 HTTP 认证跳转和 HTTPS 教务站回跳；不同网络或学校后续更新可能改变该结果。

`network_security_config.xml` 默认禁止明文，只允许上述两个完整主机名，不包含子域名；未启用全局 HTTP 或放宽 TLS / 混合内容策略。HTTP 页面会向用户提示账号密码和认证信息可能被窃取或篡改；仅允许域名不意味着传输安全。

## 已依据样本实现

- `NauDomCapture` 根据顶层样本读取同源 `FrameContent.contentDocument.documentElement.outerHTML`；无 iframe 时读取当前文档。跨源 iframe 无法通过此 API 读取，超时后提示用户。
- `NauStudentPageAdapter` 用课程列表标题和结构判断页面，将每个时间 span 解析为一条排课；从同一行读取课程和教师，并从当前时间前的地点文本读取地点。
- 学期标题形如 `2026-2027学年第一学期`，转换为本地稳定 code `2026-2027-1`；该 code 是应用内部标识，不假称学校接口编码。
- 样本的 `1-16之双周`、`1-18之单周`、`第10周` 由周次解析器处理。星期、起止节次和地点按真实样本字段提取。
- 导入保存为 Room 事务，只有用户确认后才覆盖已有学期；局部损坏记录会产生 warnings，空结果不写库。

## 样本覆盖和未确认事项

自动测试使用静态脱敏样本验证 18 条排课、单双周、连堂、不同地点、教师、缺失字段、重复、冲突、错误网页和空结果，并验证本地数据库保存及回滚。

静态样本没有 iframe 的原始 src 和页面 JavaScript。用户反馈 Android 16 上手动执行零高度页面布局修复后可以查看并导入课表，同源 iframe 在该次诊断中可读取。自动触发版本、其他设备、学期切换、重新导入确认、重启及断网查看仍需进一步验证；若 iframe 跨域，需在不绕过浏览器同源策略的前提下进一步设计。

[学校公开课表查询](https://jwc.nau.edu.cn/coursearrangeInfosearch.aspx)列出 13 节上课时间，应用当前以该表为默认；是否适用于特定校区需用户核对。课表显示节数可在设置中调整，但具体的每节起止时间尚不支持由用户修改。
