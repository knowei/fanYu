# 番悠（FanYu）

一个面向番剧放送、索引与多视频源解析的 Android 应用。

## 功能

- 放送页支持周一至周日切换，并会显示已更新集数与放送时间。
- 可在“我的 → 设置 → 索引数据源”选择：自动、Bangumi / AniList、AniFun。
  - 自动：优先 AniFun；放送或搜索不可用时自动回退 Bangumi。
  - Bangumi / AniList：使用 Bangumi 条目与 AniList 放送时间。
  - AniFun：放送、搜索、详情均使用 AniFun；详情保留季度与已发布集数。
- 视频播放使用统一的本地/订阅视频源解析流程，索引源不影响播放源选择。
- 播放历史、收藏、倍速、手势控制和本地讨论区。
- AniFun 封面会携带必要的图片请求头，以兼容其图片防盗链。

## 视频源管理

- `css1.json` 是只读订阅源，应用不会修改它。
- 在“我的 → 视频源管理”可添加、编辑、测试本地源，并完成站点验证。
- 支持导入/导出 `fanyu-source-rules.json`；导入时按规则 ID 或搜索地址合并，不会覆盖订阅源。

## 构建

调试包：

```powershell
$env:JAVA_HOME='D:\environ\Java\jdk-21'
.\package-app.ps1 -BuildType debug
```

签名正式包（默认）：

```powershell
$env:JAVA_HOME='D:\environ\Java\jdk-21'
.\package-app.ps1
```

脚本会读取 `app/build.gradle.kts` 中的版本号，将 APK 复制到 `releases`，并输出 SHA256。
