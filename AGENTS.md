# 工作约束

禁止批量删除文件或目录。不要使用 `del /s`、`rd /s`、`rmdir /s`、`Remove-Item -Recurse`、`rm -rf`。
需要删除文件时，只能一次删除一个明确路径的文件。

优先复用 XMU-Course 的成熟架构；MIT LICENSE 必须保留。
没有真实南京审计大学页面时，不猜测 URL、DOM selector 或接口，不编造南审 HTML 样本。
每阶段执行 `./gradlew test`，通过后再推进；可用时执行 `./gradlew assembleDebug`。
