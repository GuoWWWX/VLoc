# VLoc · Android 位置模拟

用地图或 WGS‑84 经纬度选点，在 Android 上模拟固定位置或圆内漂移。常用位置可以收藏、置顶和分享。

## 下载

从 [GitHub Releases](https://github.com/GuoWWWX/VLoc/releases/latest) 选择适合设备的 APK。最低支持 Android 8.0。

| 设备 | Android ABI | 文件名后缀 |
| --- | --- | --- |
| ARM v8，64 位 | `arm64-v8a` | `-arm64-v8a.apk` |
| ARM v7，32 位 | `armeabi-v7a` | `-armeabi-v7a.apk` |

在侧栏的「关于」里检查和安装更新。暂停下载或断网后可继续下载。检测到有匹配的增量包时优先下载增量包。开启通知权限后，VLoc 会在后台定期检查新版本。

已有同签名的预览版可以覆盖安装并保留历史。自行构建时如用了另一把签名密钥，请在卸载前把需要保留的位置分享保存，再复制分享文本导入新版。

## 构建和安装

- JDK 21，Android SDK Platform 36，Android SDK Build Tools 35.0.0。
- 在 Android Studio 将 Gradle JDK 设为 21。命令行构建时，将 `JAVA_HOME` 指向 JDK 21，并在 `local.properties` 中配置 `sdk.dir` 或设置 `ANDROID_HOME`。Gradle 和编译工具链使用 JDK 21，应用仍输出 Java 17 字节码。
- Windows 构建和检查：`.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`。
- 调试包位于 `app/build/outputs/apk/debug/`。Release 构建启用了代码和资源压缩。签名、差分生成和发布步骤见 [版本发布](docs/版本发布.md)。

## 首次使用

1. 首次打开 VLoc 后按提示进入系统开发者选项，在「选择模拟位置信息应用」里选择「VLoc」。也可以稍后授权，先使用地图和历史。
2. 在首页点「添加位置」新建，或点地图预览修改已有位置。点击地图放置定位针，拖动、双指缩放和「＋／−」只调整视野。也可以搜索已保存地点和系统地名，输入 WGS‑84 坐标，或用定位按钮选中当前位置。
3. 确认后回到首页，再点「开始模拟」。可从应用内或运行通知停止。
4. 使用其他定位应用验证位置。Android 会标记模拟位置，目标应用可能不采纳。

新选点开启漂移时，默认每次开始模拟在范围内随机选一次位置，之后保持该坐标。也可以设置每隔若干秒、分钟、小时或天漂移，或在模拟运行期间按手机当地时间每天漂移一次。

运行中的漂移预览用蓝针标出本次位置，用灰针标出上次位置。点「刷新」可在圆内手动换点。长按坐标可以复制。长按历史记录可编辑、置顶或分享。复制 VLoc 分享文本后回到应用，可确认导入位置。

安装 APK 后可直接在手机上使用。只有通过电脑安装、读取日志或真机调试时需要打开 USB 调试。

## 地图和权限

在首页侧栏的「地图来源」或选点页右上角，可切换 OpenFreeMap、VersaTiles 和 OpenStreetMap。三者都可在应用内显示地图、点击选点和预览漂移范围，无需申请或配置地图 Key。新安装默认使用 OpenFreeMap。底图加载取决于手机网络，遇到问题可切换来源或手动输入经纬度。

「高德地图」和「百度地图」也在应用内提供选点。两者使用随包的 Leaflet 和 ChineseCRS，通过 HTTPS 请求在线瓦片。百度使用独立的瓦片投影，选点坐标按近似 WGS‑84 返回。服务商可能调整直连瓦片接口或限制使用。对外发布前需确认服务条款和授权范围。[高德 Android SDK 密钥](https://lbs.amap.com/api/android-sdk/guide/create-project/get-key) · [百度 Android SDK 密钥](https://lbsyun.baidu.com/docs/guide?title=android-guide%2Fcreate-project%2Fak)

地图列表的第三项是 Google 地图。官方应用内接入需要 Google Cloud 项目和密钥，目前该项显示配置提示。[Google Maps SDK 配置说明](https://developers.google.com/maps/documentation/android-sdk/get-api-key)

各免费服务的使用说明见 [OpenStreetMap](https://operations.osmfoundation.org/policies/tiles/)、[OpenFreeMap](https://openfreemap.org/quick_start/) 和 [VersaTiles](https://docs.versatiles.org/guides/use_tiles_versatiles_org)。

应用使用 Android 模拟位置授权、联网和前台服务。Android 13 及以上会在首次开始时询问是否展示运行通知。仅点击「定位到当前位置」时申请前台定位权限，允许选择大致位置。它只请求一次当前定位，离开页面或切到后台会取消请求。定位成功后移动视野和选点，确认后才写入历史。历史、收藏和设置保存在本机，漂移轨迹不落盘。

项目结构和验收规则见 [功能需求](docs/功能需求文档.md)、[架构设计](docs/架构设计文档.md) 与 [项目规范](docs/项目规范文档.md)。

VLoc 源码按 [MIT 许可证](LICENSE) 发布。随包第三方组件保留各自许可证。版本变更见 [更新记录](CHANGELOG.md)。
