# 轻跑 · LightRun

一个打开就能开跑的极简 Android 跑步轨迹记录器。

无需账号、没有广告、不联网、不依赖 Google 服务。运动数据保存在手机本地。

## 界面预览

以下截图来自 v1.0.0 发布版在 Android 模拟器中的实际界面，仅展示应用内容区域。运动记录和轨迹均为模拟数据，不包含用户的真实位置或运动历史。

| 首页 · 一键开跑 | 跑步中 · 实时轨迹 |
| :---: | :---: |
| <img src="docs/screenshots/home.png" width="270" alt="轻跑首页：运动时长、距离、配速和开跑按钮"> | <img src="docs/screenshots/running.png" width="270" alt="跑步中：定位状态、运动数据、暂停和保存按钮及离线轨迹"> |
| **历史记录** | **轨迹详情 · GPX 导出** |
| <img src="docs/screenshots/history.png" width="270" alt="历史记录列表：每次跑步的日期、距离、时长和配速"> | <img src="docs/screenshots/detail.png" width="270" alt="轨迹详情：运动数据、离线轨迹、GPX 导出和删除按钮"> |

## 功能

- 开跑、暂停、继续、结束保存
- GPS 轨迹、运动时长、距离和平均配速
- 锁屏后通过定位前台服务继续记录
- 北向上的离线轨迹图、历史记录和删除
- 导出 GPX 1.1 文件
- 当前记录定期保存，异常退出后恢复为暂停状态

轨迹图没有街道底图。距离是 GPS 估算值；室内或弱信号可能少记。暂停和较长的定位中断会分段，避免把缺失路段计入距离。

## 安装

最低 Android 8.0（API 26）。从仓库的 **Releases** 下载 `lightrun-1.0.0.apk`，或使用 [仓库中的发布包](releases/v1.0.0/lightrun-1.0.0.apk)。

开启手机定位，授予精确位置权限后点“开跑”。建议允许通知，并在手机的电池设置中允许应用后台活动。首次使用请在室外锁屏试跑，确认记录持续进行。

## 构建

需要 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。Gradle 8.11.1 由仓库内 Wrapper 下载，Android Gradle Plugin 版本为 8.9.2。

设置 `JAVA_HOME`，并通过 `ANDROID_HOME` 或未提交的 `local.properties` 指定 Android SDK。缓存目录可用 `GRADLE_USER_HOME` 自行指定。

```sh
./gradlew assembleDebug
./gradlew assembleRelease lintRelease
```

Windows 可以使用 `gradlew.bat` 或 `./build.ps1`。构建结果位于 `app/build/outputs/apk/`。

默认的 release 构建不带签名。要生成可安装的发布包，将 `signing.properties.example` 复制为 `signing.properties` 并填写自己的密钥信息。官方发布包的私钥不会公开；自行签名的 APK 无法覆盖安装官方签名的 APK。

## 验证

```powershell
./tests/core.ps1
./gradlew.bat assembleDebug assembleDebugAndroidTest lintRelease
```

`RunInstrumentation` 使用合成 GPS 点验证 Android 定位、服务、通知、锁屏、暂停、恢复、保存、删除和 GPX。设备测试需要测试模拟器、定位权限以及 mock-location AppOp。

`tests/release-smoke.ps1` 和 `tests/export-file.ps1` 使用 `adb` 验证发布包界面与系统文件导出。只在测试模拟器上运行：测试会清除轻跑数据，并修改模拟器定位设置。

已完成的 v1.0.0 验证见 [验证记录](docs/VALIDATION.md)。

## 数据与隐私

应用不申请联网权限，不上传位置或运动历史。记录正常情况下每约 5 秒保存一次，异常退出可能丢失最近一个保存周期的数据。卸载会删除记录；重要轨迹请先导出。

公开源码和产物的隐私审查范围见 [发布审查说明](docs/RELEASE-AUDIT.md)。

## 许可证

项目代码采用 [MIT](LICENSE) 许可证。Gradle Wrapper 保留其 Apache-2.0 许可，详见 [第三方许可说明](docs/THIRD-PARTY-NOTICES.md)。
