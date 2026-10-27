# 理光模式全焦段

面向 realme GT 8 Pro（RMX5200）`com.oplus.camera` **6.106.782**（versionCode 60000）的 LSPosed 模块：让「理光 GR」模式可以选择相机的全部焦段，而不只是 28 mm / 40 mm。

> 模块只在 `Build.MODEL` 以 `RMX5200` 开头，且相机版本为 `6.106.782`（versionCode 60000）时运行。其他机型/版本会因门禁直接跳过，不做任何修改。

## 功能

- 理光 GR 模式下，左格 `16 mm`（0.6x 超广角），右格点按循环 `24 → 48 → 72 → 144 → 24 …`（1x / 2x / 3x / 6x）。
- 焦段真实下发到拍摄管线：6x 实拍 EXIF 为 `FocalLengthIn35mmFilm = 144`、`DigitalZoomRatio = 6.8571`。
- 附带一个模块界面：桌面图标，或 LSPosed 模块页右下角的启动按钮，内含「重启相机（需要 root）」按钮。

## 原理

GR 模式的变焦控件固定显示两个焦段：**左格 = 配置列表第一项**（也是进入 GR 时的默认焦段），**右格 = 点一下切到列表下一项**，循环。

原厂只配了 `28 mm / 40 mm` 两项。模块 hook 相机的配置读取函数，把两个 GR 键替换成五项全焦段列表：

```text
0.6(16),1(24),2(48),3(72),6(144)
```

涉及的键：

| 键 | 原值 | 替换为 |
| --- | --- | --- |
| `com.oplus.gr.mode.marked.zoomvalues` | `1.26(28),2.0(40)` | 上述五项 |
| `com.oplus.available.gr.mode.zoomvalues` | `1.26(28),1.575(35),2.0(40),2.50(50)` | 上述五项 |

要点：

- 列表必须**升序**。试过非升序（`1(24),0.6(16),…`）：两格都显示 `24 mm`，且那一次相机进程随后退出。不要再试非升序。
- 两个键要一起换：`marked` 决定两格显示和循环序列，`available` 决定 GR 的可选变焦范围。
- 只 hook 主进程 `com.oplus.camera`；`Build.MODEL` 不以 `RMX5200` 开头或相机版本不符时均 fail closed，不做任何替换。
- 不写相机设置、不改持久化数据。

## 项目结构

```
app/src/main/java/dev/codex/ricohfullzoom/
  RicohFullZoomHook.java   # LSPosed 入口：替换 GR 焦段配置
  SettingsActivity.java    # 模块界面（root 可用时重启相机）
app/src/main/AndroidManifest.xml
app/src/main/assets/xposed_init
app/src/main/res/mipmap-*/ic_launcher.png
dist/app-debug.apk         # 已构建好的 debug 签名 APK（可删）
```

## 构建

环境：JDK 21、Android SDK Platform 36、Android Gradle Plugin 8.13.0。

```bash
./gradlew :app:assembleDebug
```

Windows：

```bat
gradlew.bat :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

`de.robv.android.xposed:api:82` 仅 `compileOnly`，不会打进 APK。

## 安装与启用

1. 安装 APK；
2. LSPosed 中启用模块，作用域勾选 `com.oplus.camera`（模块声明了 `xposedscope`，会自动推荐勾选）；
3. 强制停止相机后重新打开（不需要重启手机）。

之后打开相机 → 切到「理光 GR」：左下 `16 mm`，右侧 `24 mm`；点右侧格循环，点左侧格回到 16。

## 模块界面（一键生效）

- 已授予 root 时，大按钮会 `am force-stop com.oplus.camera` 再重新打开相机；这只表示相机已重启。
- 模块是否生效仍需确认 LSPosed 已启用、作用域正确，并在理光 GR 焦段中确认。
- 未获得 root 时，模块不会尝试后台终止或自动打开相机；请在系统设置中手动强制停止相机后重新打开。

## 已知限制

- **GR 模式只有两格 + 循环**，没有原生的连续变焦转盘。照片模式里长按变焦条会展开一个半圆转盘（连续数码变焦），但 GR 模式会在切换时强制把控件收回两格状态；直接改 `getZoomBarType()` / `getZoomUnitSuffix()` 或调用 `ZoomUIManager` 的 `q/o/l0/I/m/n` 都只改控件内部状态，不会真正驱动相机变焦。
- 要做连续无级变焦，需要找到 `ZoomPresenter.setZoomValue` 的可用入口，再自绘滑杆/转盘；这部分**尚未实现**。
- 6x 是主摄数码变焦（EXIF 镜头仍标注 wide camera），不是切到独立长焦镜头。
- 只影响 GR 模式的变焦列表；照片、视频、风光、人像等模式的变焦栏不受影响。

## 回滚

LSPosed 中取消启用（或取消 `com.oplus.camera` 作用域），强制停止相机；也可以直接卸载 `dev.codex.ricohfullzoom`。模块没有写入任何相机或系统数据。
