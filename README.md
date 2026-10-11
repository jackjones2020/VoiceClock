# VoiceClock · 语音报时

原生 Android 语音报时应用，使用系统中文文字转语音（TTS）引擎播报当前时间。支持试听、按星期和时段运行，以及整点／半点报时。应用本身无需网络权限。

## 下载和使用

前往 [Releases](https://github.com/jackjones2020/VoiceClock/releases/latest) 下载 APK，支持 Android 8.0 及以上。

1. 安装后打开“语音报时”，允许通知权限。
2. 点“试听当前时间”检查中文语音；试听不受星期和时段限制。
3. 选择报时模式、星期和每日时段。
4. 点“保存并开启语音报时”。在有效时段内会立即播报，随后按计划运行。
5. 点“停止报时”结束运行，已保存的设置会保留。

## 报时规则

| 模式 | 规则 |
| --- | --- |
| 每隔 N 分钟 | 可选 1、5、10、15、30、60 分钟，从开启时间计算 |
| 仅整点 | 每小时的 `:00` |
| 仅半点 | 每小时的 `:30` |
| 整点和半点 | 每小时的 `:00` 和 `:30` |

- 勾选一周中的运行日期，所选日期共用一组每日时段，可以添加多个时段。
- 时段包含开始时间，不包含结束时间；起止时间相同表示全天。
- 结束时间早于开始时间表示跨午夜。例如选择周一 `22:00–02:00`，会包含周二凌晨至 `02:00` 前。
- 时段外不播报。开启时若在时段外，将等待下一次有效计划时间。
- “保存设置”保存星期、时段、模式和间隔；运行中保存会立即应用，并重新计算计划。
- 设置保存在手机本地。设备重启后需要打开应用恢复运行。

## 构建

使用 Android Studio 打开项目，设置 Gradle JDK 为 Java 17 或 Java 21，并安装 Android SDK Platform 35、Build Tools 35.0.0 和 Platform Tools。真机开发无需模拟器镜像。

版本：Gradle 8.11.1、Android Gradle Plugin 8.9.2、Kotlin 2.1.10；最低 Android API 26，目标 API 35。

命令行构建前设置 `JAVA_HOME`，并设置 `ANDROID_HOME` 或在项目根目录的 `local.properties` 中配置 `sdk.dir`：

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```

APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。连接已开启 USB 调试的手机后：

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.voiceclock/.MainActivity
```

仓库不包含本机 SDK／Java 路径、构建缓存或签名私钥。当前公开下载为调试签名 APK；商店发布需要自行配置正式签名。

## 项目结构

- `MainActivity.kt`：设置界面、试听。
- `Settings.kt`：本地设置保存和读取。
- `Schedule.kt`：星期、时段和下一次报时计算。
- `ClockService.kt`：前台报时服务及通知。
- `ScheduleTest.kt`：时段边界、跨午夜、模式和跨周调度测试。

欢迎通过 Issue 反馈问题或提交 Pull Request。提交修改前请运行上述测试和代码检查，并说明复现方式和验证结果。

## 使用限制

- 手机需要可用的中文 TTS 引擎和语音数据；没有声音时，请检查系统“文字转语音输出”设置。
- 后台服务持有唤醒锁以支持熄屏计时，停止时释放；持续运行会增加耗电。
- 系统休眠和厂商省电策略可能导致播报延迟或服务中断。需要长期运行时，可将应用电池用量设为“不受限制”。

## 开源许可

采用 [MIT License](LICENSE)，允许使用、修改和分发。
