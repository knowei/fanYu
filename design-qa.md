# Design QA

- Visual references: `android-app/design/broadcast-reference.png`, `my-reference.png`, `detail-reference.png`, `player-reference.png`
- Latest implementation captures: `broadcast-latest.png`, `my-latest.png`, `detail-final.png`, `player-implementation.png`
- Device: Android emulator, 1080 x 2280 physical pixels at 440 dpi
- Data: live Bangumi proxy data plus local watch/favorite state

## Verified

- 详情页顶部操作：已使用 Material Components 和官方 Material 图标替换旧系统 drawable；返回为标准 24dp 左箭头，收藏为空心/实心心形，48dp 触控区、透明背景和轻量水波纹均通过截图对照。证据：`android-app/design/material-detail-final.png`。
- 播放器控制层：自定义进度条置于控制区首行，播放/暂停置于下方左侧，全屏置于下方右侧；详情页直接进入播放器，解析窗口透明且不拦截触摸。证据：`android-app/design/player-check.png`。
- 播放器第二版：按选定的 `player-option2.png` 实现独立深色控制坞；进度、播放/暂停、时间、视频源和全屏集中在坞内。点击视频可完全隐藏控制坞，再次点击显示；播放时三秒自动隐藏。对照证据：`android-app/design/player-option2-comparison.png`，隐藏态：`player-hidden.png`。
- 放送日期：星期栏改为横向滑动的固定宽度日期项，支持选择未来日期并刷新对应 weekday 的 Bangumi calendar 数据；未来日期列表使用正确星期标签。证据：`home-scroll.png`、`home-future.png`。
- 已播集数：使用 `/v0/episodes` 的正篇章节 `airdate` 计算，实测条目只展示已播出的 1–3 集。
- 精确更新时间：Bangumi calendar 条目以 AniList `AiringSchedule` 补充北京时间与更新集数；实机成功显示 `19:30 更新 · 第3集`、`22:00 更新 · 第3集`。标题未严格匹配时不显示时间，避免错误归属。证据：`android-app/design/home-times.png`。
- 放送页：日期栏、实时 Bangumi 列表、封面代理、长标题换行、两项底部导航均正常。
- 我的页：继续观看、进度、收藏空态、设置入口和底部导航均正常。
- 详情页：真实条目信息、简介、选集、收藏和底部播放按钮均正常。
- 导航流程：放送 -> 详情 -> 线路解析 -> 播放器，以及放送/我的切换均已实现。
- 继续观看：曾发现只进入解析页而不自动解析；已补传 `auto_resolve` 并重新构建通过。
- 构建：preview APK 使用 Gradle 9.2.1 构建成功。

## Findings

- [P2] 播放器还没有“真实视频画面播放成功”的截图证据。
  - `player-implementation.png` 只证明播放器布局、选集和线路控件能够显示；测试流当时停留在黑色加载态。
  - 必须用最终解析出的可用视频地址，验证首帧、暂停/继续、拖动和切集后，才能关闭此项。

- [P2] 我的页旧历史记录可能显示灰色封面占位。
  - 最新列表和详情封面代理已经正常；模拟器现存的“水星的魔女”记录是在修复前写入，保存的 cover 字段为空。
  - 新产生的播放记录会保存封面。若要兼容旧数据，需要在缺失封面时按条目名补查 Bangumi。

- [P3] 系统图标与视觉稿的线性图标粗细不完全一致。
  - 返回、收藏、分区和底栏目前使用 Android 系统资源。

- [P3] Bangumi calendar 不提供可信的单集播出时刻，因此放送列表使用“今日”，没有伪造视觉稿中的具体时间。

## Fidelity assessment

- Typography and hierarchy: passed
- Layout, spacing and safe areas: passed
- Colors and visual tokens: passed
- Live list/detail image quality: passed
- Primary navigation and controls: passed
- Legacy history image fallback: blocked by P2
- Real video playback proof: blocked by P2

final result: blocked

## My page redesign (pending device capture)

- Selected visual target: the second generated “quiet personal library” direction from 2026-07-21.
- Implemented: compact header, single-row continue-watching module, horizontal favorites rail, and lightweight settings rows.
- Build: `:app:assemblePreview --offline` passed.
- Capture status: blocked — `adb` reported no connected device or emulator, so the implementation could not yet be compared against the selected visual target at the same viewport.

final result: blocked
