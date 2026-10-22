# 密集弹幕与 60 帧验证

性能验收使用 Release 构建。`DenseDanmaku2000HardwareTest` 通过默认 `ModeAwareRenderer`、
真实 DataSystem、后台测量与缓存、LayoutSystem、动作系统和 RenderSystem 完成播放。
每帧重新录制硬件画布，并等待 GPU 呈现、消费 ImageReader 输出；耗时包含状态更新、
绘制命令和 GPU 输出等待。

## 场景与验收条件

- 1920×1080，2000 条同时可见、文字内容各不相同，持续运动。
- 普通场景：2000 条滚动弹幕，18 号载荷字号、50% 不透明度，显式开启 `allowOverlap`。
- 高级场景：2000 条两行高级弹幕，18 号载荷字号、50% 不透明度，一半旋转 15°。
- 混合场景：1000 条普通弹幕与 1000 条高级弹幕，高级弹幕中一半旋转 15°。
- 预热 30 帧，采样 600 帧；每帧检查 2000 条缓存命中、每个输出帧均存在。
  采样起止还检查所有条目的变换后边界完整落在画布内，防止通过裁剪减少负载。
- 首次准备全部缓存低于入场前的 3 秒窗口；完整帧耗时 P95 < 16.667 ms，
  超过 16.667 ms 的采样帧数不多于 1%。同时报告 P99、最长帧和超预算帧数。

关闭重叠时仍遵守原有轨道排布规则。普通弹幕仅为排布接受的条目生成缓存，避免为大量
被拒绝的条目提前分配纹理；高级弹幕和允许重叠的普通弹幕利用已有数据窗口提前准备缓存。

## 2026-10-22 实测

环境：Android 15 x86_64 模拟器，WHPX，host GPU（AMD Radeon 780M），Release 构建，
1920×1080 离屏硬件画布。以下为同一次六项硬件测试全部通过的结果，每种场景输出 630 帧。

| 同屏场景 | 中位数 | P95 | P99 | 最长帧 | 超过 16.667 ms | 缓存准备 | 纹理估算 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 普通 2000 条 | 7.72 ms | 9.08 ms | 10.26 ms | 12.34 ms | 0/600 | 770 ms | 89.29 MiB |
| 高级 2000 条 | 9.02 ms | 10.54 ms | 11.47 ms | 14.25 ms | 0/600 | 1006 ms | 110.62 MiB |
| 普通/高级各 1000 条 | 9.35 ms | 11.00 ms | 13.05 ms | 18.08 ms | 1/600 | 1236 ms | 146.58 MiB |

三种场景均达到上述 60 帧预算门槛。混合场景有一帧超预算，数据保留在表内。
每种场景共用 32 张图集纹理；纹理估算不含缓存准备过程的临时位图、驱动、视频和其他 UI 内存。
引擎 79 项及弹幕模块 98 项单元测试全部通过，7046 条真实高级弹幕尺寸与像素差异均为零。
Android lint 无错误，本次修改文件的 ktlint 检查通过。

## 优化内容

- 后台将最多 64 个精灵打包为一张纹理，减少集中入场时的 GPU 上传次数。
  位图与图集裁剪区域原子发布；上传失败时保留 CPU 缓存。
- 平移条目直接通过目标矩形绘制，省去逐条矩阵构造、画布保存和恢复；复杂变换仍走完整矩阵。
- 硬件缓存绘制复用 HWUI 自身的裁剪；软件和未命中缓存的路径提前剔除透明或屏幕外条目，
  半透明文字使用有界图层，保持描边与填充组合后的透明度。
- 按线程复用字体度量和缩放因子，减少文字拆行、临时集合、装箱及画笔状态的重复设置。
- 渲染对象池容纳完整的 2000 条退休帧；释放播放器时清理所有系统、实体和缓存线程。

Android 8 起可以使用硬件图集；更低版本和软件画布保留兼容绘制路径。性能门槛需要
Android 10 以上的硬件画布测试设备。软件绘制压力用例用于验证回退行为与对象复用，
不作为 60 帧的性能验收。结果针对准备好缓存后的弹幕渲染，其他设备和整屏视频/UI
的耗时需要在目标设备上复测。

## 复现

连接 Android 10 以上的设备或模拟器，确保硬件加速已启用，运行：

```powershell
$cases = @(
  'com.kuaishou.akdanmaku.render.DenseDanmaku2000HardwareTest#ordinaryPlayback2000SpritesMeets60FpsBudget',
  'com.kuaishou.akdanmaku.render.DenseDanmaku2000HardwareTest#fullPlayback2000SpritesMeets60FpsBudget',
  'com.kuaishou.akdanmaku.render.DenseDanmaku2000HardwareTest#mixedPlayback2000SpritesMeets60FpsBudget',
  'com.kuaishou.akdanmaku.render.HardwareDrawingCacheTest'
) -join ','
.\gradlew.bat :danmaku-engine:connectedReleaseAndroidTest -PdanmakuTestBuildType=release "-Pandroid.testInstrumentationRunnerArguments.class=$cases"
```

结果包含在 Gradle 的 connected Android 测试 XML 和 `Danmaku2000Stress` logcat 中。
请使用 Release 构建测量；Debug 的调试和跟踪开销会显著影响密集场景。

单元回归与 Android API 兼容检查：

```powershell
.\gradlew.bat :danmaku-engine:testDebugUnitTest :danmaku:testDebugUnitTest :danmaku-engine:lintDebug
```

像素回归包含 7046 条真实高级弹幕，以及普通文字在字号、粗体、显示尺寸和自发送样式
变化下的独立像素参考。另有图集隔离、密度保留、缓存复用、软件回退、复杂变换、
透明度、裁剪、未来条目预缓存和播放器释放测试。
