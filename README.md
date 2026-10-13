# DeepSeek 余额查询 App

一个 Android 应用：输入 DeepSeek API Key，查询账户余额。UI 使用 **Material 3（Material You 动态取色）**，用 **Views + XML** 实现。

## 功能

- 输入 / 保存 / 清除 API Key（密码遮罩 + 显隐切换）
- **历史 API Key**：用过的 Key 自动记录并显示在下方，**点击即填入并直接查询**；已脱敏显示（头 6 位 + 尾 4 位），最多保留 8 条，最近使用在前；可一键清空历史
- 查询余额：调用 `GET https://api.deepseek.com/user/balance`
- 展示总余额 / 赠送余额 / 充值余额、币种、可用状态
- 错误处理：401 无效 Key、402 余额不足、429 限流、网络异常
- **Material You 动态取色**：Android 12+ 跟随壁纸取色
- 支持深色模式

> **不再支持 Android 11（API 30）**：最低版本为 **Android 12（API 31）**。
> 小部件的动态尺寸/位置依赖 `RemoteViews.setViewLayoutWidth/Height/Margin`，
> 这三个方法 **API 31 才加入**；在 Android 11 上调用会抛 `NoSuchMethodError` 直接崩溃。
> 该类平台差异较多，故放弃 API 30 支持。

## 如何生成 API Key

1. 打开 <https://platform.deepseek.com/api_keys>
2. 登录后创建一个 API Key（形如 `sk-xxxxxxxx`）
3. 复制到本应用的 API Key 输入框，点「保存」（仅存本机）
4. 点「查询余额」

> 官方文档：<https://api-docs.deepseek.com/api/get-user-balance>

## 桌面小部件

在 App 里点「添加桌面小部件」即可固定到桌面（也可长按桌面手动添加）。

**小部件上没有按钮** —— **单击整块小部件 = 手动刷新余额**。设置统一放在 App 里（见下）。

### 图片轮播框

在小部件右侧空白处放一个可配置的图片轮播框。

**设置项（App 主活动 →「小部件设置」）**

| 项 | 说明 |
|---|---|
| 启用图片轮播 | 开关；关掉则隐藏图片框 |
| 图片文件夹 | 系统文件夹选择器（SAF），选中后显示目录名与图片数量；权限持久化 |
| 缩放方式 | **裁切**（等比放大居中裁剪）/ **拉伸**（不保比例铺满）/ **填充**（等比缩到框内居中留白） |
| 右边界距离 / 上边界距离 | 图片框右边缘距部件右边缘、上边缘距部件上边缘（dp） |
| 宽 / 高 | 图片框尺寸（dp） |
| **轮播间隔** | 用 `NumberPicker` 滚轮选「桌面基准的倍数」，旁边实时显示换算出的时间。手动模式见下 |
| **手动更新** | 开关。打开后不自动换图，**点图片区域就切下一张** |
| 边框粗细 / 圆角 | 边框描边宽度与圆角半径（dp） |
| 边框色 | 走**同一套配色系统**（系统 Material 语义色 / 576 色调色板 / 手输色号），颜色目标里多了「边框」一项 |

**关于轮播间隔（重要）**

桌面推进轮播的**基准间隔是宿主决定的，App 拿不到**。本机实测（Launcher3 / Android 16，屏幕保持点亮、10 秒采样）：

| 变化间隔 | 18s | 19s | 23s | 18s |
|---|---|---|---|---|

即**基准 ≈ 20 秒**（`WidgetPrefs.BASE_ADVANCE_SECONDS`）。因此：

- 能做到的是 **20 秒的整数倍**：20s、40s、60s、80s……
- **10 / 30 / 50 秒做不到**（不是 20 的整数倍），这也是为什么最终做成了「倍数」选择器而不是直接填秒数
- 实现方式：把每张图在适配器里**重复 k 遍**，视觉上就变成每 `k × 20` 秒换一张；超出范围的倍数（手动模式）用 3600 倍等效于「不自动换」

**实现要点**

- 位置与宽高通过 `RemoteViews.setViewLayoutWidth/Height/Margin`（API 31+）动态设定；文字区的 end margin 会按框宽自动让位，避免重叠。
- 每帧位图在工厂里**先变换成正好等于框的像素尺寸**并裁成圆角，`ImageView` 用 `fitXY` 就是 1:1 渲染，不会被拉伸；同时把尺寸限制在 512px 以内，避免 RemoteViews 的 binder 事务上限（约 1MB）。
- 翻页靠 `balance_widget_info.xml` 里的 **`android:autoAdvanceViewId="@id/carousel"`**，由桌面宿主定时推进。

**⚠️ 两个踩过的平台坑（别再犯）**

1. **`AdapterViewFlipper.setAutoStart` 和 `setFlipInterval` 都不是 `@RemotableViewMethod`**，在 RemoteViews 里调用会让宿主抛 `ActionException: can't use method with RemoteViews`，**整个小部件变成「无法加载微件」**。所以翻页只能靠 `autoAdvanceViewId`。
2. **因此轮播间隔无法由 App 设定**，由平台决定（本机实测约 **10 秒**一张）。原计划的「轮播间隔」输入框已移除 —— 留个不生效的控件比没有更糟。
3. 还有一次真实崩溃：给配色目标加了 `border` 却没同步补 `targetLabels` 数组，导致 `ArrayIndexOutOfBoundsException`、设置页直接打不开。加枚举项时记得同步所有平行数组。

### 三种外观风格

| 风格 | 效果 |
|---|---|
| **磨砂** | 半透明磨砂背景 + 圆角（约 70% 不透明），可透出壁纸 |
| **透明** | 完全无背景，文字/图标直接浮在壁纸上 |
| **Material 纯色** | 不透明纯色圆角背景 |

> 说明：Android 小部件由桌面渲染，拿不到其背后的像素，因此**无法做到真正的背景模糊**。「磨砂」是半透明磨砂效果。

### 颜色系统

**背景 / 文字 / 图标 三处颜色各自独立设置**，每处都可从三种来源里选：

| 来源 | 说明 |
|---|---|
| **系统 Material** | Material 3 语义色，14 个角色（主色 / 主容器 / 第三色 / 错误色 / 表面 / 轮廓…），Android 12+ 为**动态取色**，跟随壁纸 |
| **调色板** | 自绘 **32 色相 × 18 明度 = 576 格**全光谱色板，触摸选色，配透明度滑杆（0–255） |
| **色号** | 手动输入 `#RRGGBB` 或 `#AARRGGBB`（**支持透明度**） |

- 纯色风格下，若「文字 / 图标」用默认语义角色，会按背景亮度**自动取深/浅色**保证可读
- 磨砂风格下，背景色由所选颜色 + 透明度决定，因此可以做出任意色调的半透明面板

### 圆角大小

滑杆 0–48dp，完全生效（0dp = 直角，48dp = 接近胶囊形）。

实现上踩过三个坑，都已修掉：

1. **背景位图必须铺满部件**。最初把背景 ImageView 放在带 `padding` 的父容器里，实际尺寸比位图小，`fitXY` 拉伸后圆角被拉成**椭圆**。修法：去掉父容器 padding，把边距移到内容层。

2. **Android 12+ 的桌面会把 Widget 强制裁剪成系统圆角（本机约 14dp）**。背景贴边时，用户设的圆角**小于系统圆角的部分会被系统裁掉**（表现为「设 0dp 却仍是圆角」）。修法：背景面板相对部件边缘**内缩 8dp**，使面板圆角落在系统裁剪区域之内。

3. **`getAppWidgetOptions()` 返回的是「可调整范围」，不是当前尺寸**。本机实测：`MIN=271x72`、`MAX=505x150`，而实际渲染是 **272x151dp** —— 即**当前宽 ≈ MIN_WIDTH、当前高 ≈ MAX_HEIGHT**。最初用 `MIN_WIDTH × MIN_HEIGHT`（宽高比 3.76）估位图尺寸，与实际（1.80）差太多，`fitXY` 把圆角拉伸成**纵椭圆**。修法：**宽取 `MIN_WIDTH`、高取 `MAX_HEIGHT`**，位图宽高比与实际一致，圆角恢复为正圆。

> 因此小部件背景不会完全贴边，留 8dp 透明边距 —— 这是绕开系统裁剪的必要代价。

### 使用金额统计

设置里填「充值总量」，应用即用 `充值总量 − 当前总额` 算出**已用金额**，同时显示在：

- 主界面的余额卡片（「已用金额：¥xx.xx」）
- 桌面小部件的状态行（「已用 ¥xx.xx · 19:55」）

> 注意：这是按「总余额」口径计算的。如果账户里有**赠送额度**，赠送部分也会被算作「已用」，数值会偏大。

### 图标

使用 Material 风格的刷新（refresh）与设置（settings）图标，颜色纳入同一套配色系统，可独立设定；尺寸随文字大小档位同步缩放。

### 文字大小

四档：小 / 默认 / 大 / 特大。文字与图标同步缩放。

### 自动更新

在设置里选择间隔：关闭 / 15 分钟 / 30 分钟 / 1 小时 / 3 / 6 / 12 / 24 小时。

实现方式：`AlarmManager.setAndAllowWhileIdle` 单次闹钟 + 每次触发后重新排程（不需要 `SCHEDULE_EXACT_ALARM` 权限）。**重启后会由 `BootReceiver` 重新排程**。

### 设置入口

- **App 主活动里的「小部件设置」按钮** → 设置 API Key、更新间隔、充值总量、外观风格、主题色、圆角、文字大小
- 添加小部件时桌面也会拉起同一个设置页（`android:configure`）

> 因为小部件上不再有设置按钮，**外观设置改为全局**（风格 / 圆角 / 文字大小 / 三处颜色由所有小部件共用），
> 新建的小部件直接沿用当前设置。

## 技术栈（全部使用本机 Gradle 缓存，可完全离线构建）

| 项 | 版本 |
|---|---|
| Gradle | 9.6.0 |
| AGP | 9.4.0 |
| JDK | 25（Android Studio 自带 JBR） |
| compileSdk / targetSdk / minSdk | 37 / 37 / 31 |
| UI | Material Components 1.10.0（Views + XML） |
| 网络 | `HttpURLConnection` + `org.json`（Android 内置，零第三方依赖） |

## 构建

```bat
set JAVA_HOME=E:\GoogleAndroidStudio\jbr
E:\...\gradle-9.6.0\bin\gradle.bat assembleDebug --offline --console=plain
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 安装

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 源码结构

```
app/src/main/java/com/zcode/deepseekbalance/
    DeepSeekApp.kt      — Application，应用 Material You 动态取色
    MainActivity.kt     — 界面逻辑与状态渲染
    BalanceViewModel.kt — UiState（Idle/Loading/Success/Error）+ StateFlow
    DeepSeekApi.kt      — 余额接口调用与错误映射
    KeyStore.kt         — SharedPreferences 存 API Key
```

## 两个做不到的需求（已核实官方文档）

最初需求是「登录 DeepSeek 账号 + 查询余额 + 模型柱状图 + 使用时间分布图」。核实后有两项在官方 API 层面**无法实现**，已在实现中移除：

1. **登录 DeepSeek 账号 —— 不存在的接口。**
   DeepSeek 官方 API 只支持一种是凭证：**账号级 API Key**（Bearer）。没有 OAuth 授权流程、没有账号密码登录接口、没有 OIDC。第三方 App 无法以「用户账号身份」登录。因此本应用改为**输入 API Key**。

2. **模型用量柱状图 / 使用时间分布图 —— 没有数据源。**
   官方 API 全部端点只有九个（`/chat/completions`、`/responses`、`/completions`、`/models`、`/user/balance`、`/files` 系列），**没有任何用量统计接口**：既没有按模型的 token 消耗统计，也没有按时间的历史用量。`/user/balance` 只返回剩余金额（total / granted / topped_up），**不含任何消耗明细**。

   如需用量图表，可行途径只有：
   - 应用自己拦截并记录每次 `/chat/completions` 响应的 `usage` 字段（需应用自身发起请求）；
   - 或从 DeepSeek 网页控制台导出数据（非公开 API，不稳定）。

## 安全说明

- API Key 存在 **SharedPreferences（MODE_PRIVATE）**，即**明文**、仅本应用可读。
- 如需加密存储，应改用 `androidx.security-crypto` 的 `EncryptedSharedPreferences`（需联网下载依赖）。
- 应用仅与本机保存的 Key 一起调用 DeepSeek 官方接口，不经过任何第三方服务器。
