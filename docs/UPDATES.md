# 更新发布

轻跑从 Gitee 的公开 Contents API 和 GitHub 的公开原始文件读取仓库根目录的 `update.json`。GitHub 原始文件不消耗 REST API 的匿名额度。不使用账号令牌，不下载或安装 APK。后台检查采用 Android JobScheduler，应用启动时调度；每日最多一次自动尝试，系统可能延后。手动检查不受每日间隔限制。

两个更新源分别请求，一个失败时仍可使用另一个。版本以递增的 `versionCode` 比较，相同版本优先使用 Gitee；同一版本代码的 APK 校验值冲突时拒绝采纳本次结果。元数据只允许本项目的 HTTPS 发布页地址。

元数据格式：

```json
{
  "schema": 1,
  "applicationId": "cn.lightrun.app",
  "versionCode": 3,
  "versionName": "1.1.1",
  "notes": "版本更新说明",
  "sha256": "正式签名 APK 的 64 位小写 SHA-256",
  "giteeUrl": "https://gitee.com/XLxiaoliao/lightrun/releases/tag/v1.1.1",
  "githubUrl": "https://github.com/XLxiaoliaoGmail/lightrun/releases/tag/v1.1.1"
}
```

发布顺序：提高 App 版本代码与版本名，构建并测试同一签名 APK，审查源码和产物，将标签推送到两个仓库，在两个发布页上传同一 APK 和校验文件，最后更新两个仓库的主分支元数据。不要发布用于测试的虚构高版本号。

元数据中的校验值用于校验镜像一致性和用户手动核验。轻跑不自行下载 APK，因此不会宣称已校验用户浏览器下载的文件；Android 安装器会检查覆盖安装时的签名匹配。维护者的发布凭据及签名私钥必须保存在仓库之外。
