# Android 应用更新实现与复用指南

本文对应轻跑 v1.2.0（versionCode 5），供其他项目的开发 agent 复用。流程是：公开版本文件 → 用户选择下载 → 应用内进度 → 安装包校验 → Android 系统确认覆盖安装。

## 产品行为

- 客户端仅访问 Gitee；GitHub 可以镜像源码和产物，不参与应用更新。
- 默认自动检查，每 24 小时最多自动尝试一次，失败也计入间隔；手动检查不受间隔限制。
- 更新弹窗只显示版本、说明和“下载更新”，不显示平台名称，不打开浏览器。
- 应用内显示真实下载字节和进度，支持取消、关闭弹窗后继续下载、重新查看进度、失败重试。
- 下载完成并校验后显示“安装更新”。必要时先进入本应用的安装授权设置，再进入系统安装确认页。
- 不自动下载，不静默安装。关键业务进行中暂缓检查、下载和安装；轻跑下载期间也不允许开跑。
- 不上传业务数据或设备标识，不需要用户账号或访问令牌；托管服务仍会收到正常连接信息，例如 IP。

其他项目应把“跑步中”替换成自己的关键业务，例如录音、测量或文件写入。

## 源码结构

[轻跑源码](https://gitee.com/XLxiaoliao/lightrun) 中，相关文件位于 `app/src/main/java/cn/lightrun/app/`：

| 文件 | 职责 |
| --- | --- |
| UpdateInfo.java | 严格验证元数据 |
| UpdateChecker.java | 单源检查、间隔、并发保护和缓存 |
| UpdateJobService.java | Android JobScheduler 后台调度 |
| UpdateDownload.java | 查询附件、下载、取消、进度和 APK 校验 |
| UpdateApkProvider.java | 只读的私有 APK URI |
| MainActivity.java | 更新与进度弹窗、安装授权及安装器调用 |

使用 Java、原生 Android API、HttpURLConnection 和 org.json，没有更新 SDK。其他语言和 UI 框架可复用协议与校验顺序，不必复制整个 Activity。

## 公开元数据

仓库根目录放 `update.json`：

```json
{
  "schema": 1,
  "applicationId": "cn.example.app",
  "versionCode": 12,
  "versionName": "1.3.0",
  "notes": "优化下载体验，修复已知问题。",
  "sha256": "这里必须填写实际正式APK的64位小写SHA256",
  "giteeUrl": "https://gitee.com/OWNER/REPO/releases/tag/v1.3.0"
}
```

哈希文本是占位符，不能直接发布。判断更新用递增整数 versionCode，不能比较版本名的字符串大小。版本名须与 APK、Release 标签和附件名称一致。

轻跑继续提供旧 schema 1 客户端使用的 githubUrl 字段，便于旧版识别新版；v1.2.0 忽略该字段，不会请求 GitHub。新项目无需此兼容字段。旧二进制的联网行为只有覆盖升级后才会改变。

解析时验证 schema、applicationId、正整数代码、限定格式版本名、64 位小写哈希、说明长度及固定项目 HTTPS 发布路径。拒绝 HTTP、伪造域名、用户信息、额外端口、错版本路径。轻跑限制说明为 4000 字符。

## 检查更新

```text
GET https://gitee.com/api/v5/repos/OWNER/REPO/contents/update.json?ref=main
```

响应是 JSON 包装：验证 encoding 为 base64，解码 content 后再解析更新 JSON。轻跑限制响应为 64 KiB，连接/读取超时各 4 秒，拒绝重定向。请求没有令牌、定位、步数、设备型号或标识。

后台任务和进入应用时检查共用同一时间间隔与并发锁。请求前保存尝试时间，失败不误报“已是最新版本”；可以保留以前已经验证的版本缓存。自动检查不触发下载。系统后台调度可能被省电或网络策略延迟，因此不能承诺固定时刻执行；保留前台到期检查和手动入口。

## 查询真正的 APK 附件

发布页是网页，不能当下载文件交给浏览器。依次使用：

```text
GET https://gitee.com/api/v5/repos/OWNER/REPO/releases/tags/v{versionName}
GET https://gitee.com/api/v5/repos/OWNER/REPO/releases/{releaseId}/attach_files
GET https://gitee.com/api/v5/repos/OWNER/REPO/releases/{releaseId}/attach_files/{attachmentId}/download
```

验证标签恰好为 v{versionName}，不接受预发布；ID 为正数。附件必须唯一且名称符合约定，例如 `lightrun-{versionName}.apk`。其他项目统一替换自己的前缀。大小必须为正数，轻跑上限 100 MiB。

下载 GET 会重定向到 CDN。本实现只允许 HTTPS 的 gitee.com 和 foruda.gitee.com，最多四次重定向；元数据只允许 Gitee。严格比较完整域名，不能用宽松后缀匹配，不能 HTTP 回退。已验证接口的 HEAD 可能与 GET 行为不同，不能用 HEAD 代替实际下载验证。

下载连接超时 5 秒、读取超时 8 秒，Release 与附件列表最大 128 KiB。平台以后变更接口或 CDN 域名时，应查证和测试后更新明确的允许列表。

## 下载状态与文件

下载到应用私有 `cacheDir/updates/`，无需公共存储权限：

```text
update.part  下载中的临时文件
update.apk   完整且校验通过的文件
```

状态为 connecting → downloading → verifying → ready，另有 failed 和 cancelled。总量未知时显示不确定进度，取得附件大小后用实际接收字节计算百分比，不能用定时假增量。只有 ready 可以安装。

每块数据检查取消和大小上限；实际字节数必须与附件声明完全一致，有 Content-Length 时也要一致。截断、超量、取消均清理文件。取消同时断开连接。写入完成、同步并校验后再改名成最终文件。

关闭进度弹窗不等于取消，要保留查看入口。Activity 重建后恢复 UI 与当前状态，避免重复下载。

轻跑当前用应用级静态状态和独立线程，不持久化下载任务，不支持断点续传；进程结束后需重新下载，不能保证长期后台或重启续传。其他项目需要这些能力时，应增加持久化、恢复校验和任务管理，另行测试。

## 安装前必须验证

1. SHA-256 与元数据完全一致。
2. 可解析为 APK，applicationId 等于当前应用。
3. versionCode、versionName 与元数据完全一致。
4. minSdk 不高于当前系统。
5. 签名证书与当前安装应用一致。

Android 9 以上用 GET_SIGNING_CERTIFICATES，Android 8 用 GET_SIGNATURES。轻跑比较当前签名集合，不支持密钥轮换；有轮换需求的项目需设计证书历史验证，不能放宽为任何签名。

哈希能检测文件损坏或不一致，但元数据与 APK 同源时，哈希不是独立的真实性证明。签名一致性和系统安装器最终验证仍然必要。签名私钥及发布凭据必须保护且保存在仓库之外，永远不写入客户端。

## 安装授权与 Provider

Manifest：

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<provider android:name=".UpdateApkProvider"
    android:authorities="${applicationId}.updates"
    android:exported="false"
    android:grantUriPermissions="true" />
```

本项目最低 Android 8。先调用 canRequestPackageInstalls()，没有授权时打开针对本应用的 ACTION_MANAGE_UNKNOWN_APP_SOURCES；返回后重新检查，用户拒绝则保留稍后安装出口。这对应 Android 的 [安装授权接口](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())。

Provider 仅接受一个精确 URI：`content://{applicationId}.updates/update.apk`，拒绝写入和其他路径，不暴露整个目录。按 [ContentProvider 文件接口](https://developer.android.com/reference/android/content/ContentProvider#openFile(android.net.Uri,%20java.lang.String)) 提供只读描述符。

安装 Intent 用 ACTION_VIEW、MIME `application/vnd.android.package-archive`、FLAG_GRANT_READ_URI_PERMISSION，并将同一 URI 放入 ClipData。Manifest 同时声明 content APK VIEW query，适配包可见性。

不要用 file://、浏览器 URL 或公共可替换文件。系统安装器始终要求用户确认；不申请 UPDATE_PACKAGES_WITHOUT_USER_ACTION。覆盖升级需保持 applicationId 与正式签名，用户不要先卸载，否则数据会删除。

## 其他 agent 的迁移清单

- 统一替换包名、OWNER/REPO、主分支、标签规则与 APK 附件前缀。
- 使用自己项目原有正式签名；不能使用轻跑签名，也不能公开私钥。
- 每次递增 versionCode，同步 APK、元数据、标签和文件名。
- UI 只显示业务信息，托管平台名称留在维护文档。
- 把关键业务状态接入暂缓更新逻辑。
- 保留自动检查开关、手动入口、取消、重试、重开进度和授权拒绝后的出口。
- 配置 Provider、安装权限与 Intent；不申请不必要的存储权限。
- 后台和前台共用检查间隔；旧客户端需要的字段继续发布。

## 发布顺序

先准备真正可下载的文件，最后激活元数据：

1. 完成代码、版本配置、文档及测试，生成原正式签名 APK。
2. 审查源码、脚本、截图、Git 署名和 APK，排除凭据、本机路径、个人信息与真实用户数据。
3. 算最终 APK 哈希，准备校验文件及与提交一致的源码 ZIP。
4. 推版本标签，创建 Release，上传 APK、源码 ZIP 和校验文件。
5. 无账号、无令牌实际下载，核对长度、哈希、签名；检查公开 Contents API。
6. 最后推主分支的 update.json，避免先提示不可下载的新版。
7. 用旧正式版验证发现更新及覆盖升级，新正式版不重复提示。

GitHub 镜像可以同步同一提交、标签和相同产物，不需要客户端连接它。上传失败时保存 Release ID 便于恢复，避免重复创建。不要发布虚假的高版本测试元数据。

## 验证要求

| 场景 | 期望 |
| --- | --- |
| 正常检查、同版、新版、24 小时重复进入 | 状态正确、不重复自动请求，手动仍可检查 |
| 离线、超时、错 JSON/包名/发布路径 | 清晰失败，不误报最新，不打开浏览器 |
| 取消、关闭弹窗、旋转 | 清理临时文件、可重开进度、不重复下载 |
| 文件短缺、超量、错误哈希 | 拒绝安装，可重试 |
| 同包同版本但签名不同 | 即使哈希匹配仍拒绝 |
| Provider 任意路径、写入 | 拒绝 |
| 安装授权拒绝、允许后返回 | 拒绝可退出，允许进入系统确认 |
| 旧正式包覆盖升级 | 原记录保留，同一包名和签名 |
| 最低与当前新 Android 版本 | 网络、安装器、Provider 和 UI 可用 |

设备接口、真实网络、正式签名包分别测试。调试包通常不同签名，不能把它覆盖正式包失败误当更新故障。厂商安装限制仍需手机验证。

## 常见故障

点击下载打开浏览器：仍在用网页 ACTION_VIEW，改为附件 API 下载，只有校验成功才打开 APK content URI。

下载文件很小：可能是 HTML/错误响应。验证状态码、重定向、长度、哈希和 APK 解析，不能只看扩展名。

同版仍提示更新：检查整数 versionCode、虚假高代码或测试缓存，测试元数据不得进入公开主分支。

安装无法覆盖：检查包名、递增代码和原正式签名，不能让用户卸载来掩盖签名错误。

找不到附件：核对标签、Release ID、唯一附件名称及匿名 GET；不要回退浏览器或把令牌嵌入应用。
