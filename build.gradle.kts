// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.versions) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// JaCoCo：统一排除生成代码和纯数据类
val jacocoExcludes = listOf(
    "**/http/entity/**",      // HTTP 响应实体类（纯数据持有，无业务逻辑）
    "**/http/Bili*HttpApi*.class", // HTTP API 客户端单例（需集成测试）
    "**/http/BiliHttpApiKt*.class", // HTTP API 顶层函数（需集成测试）
    "**/ApiSignKt\$encApiSign*.class", // Ktor 插件 lambda（需集成测试）
    "**/ApiSignKt\$injectCookies*.class", // Ktor 插件 lambda（需集成测试）
    "**/db/entity/**",         // Room 实体类
    "**/di/**",                // Hilt DI 模块
    "**/theme/**",             // 主题定义
    "**/navigation/**",        // 路由定义
    "**/impl/exo/**",         // ExoPlayer 实现（需 Android 环境）
    "**/BvVideoPlayer*.class", // Composable 播放器组件（需插桩测试）
    "**/OkHttpUtil*.class",   // SSL 工具（需 Android 环境）
    "**/ui/screen/**",        // Composable 屏幕（需插桩测试）
    "**/ui/component/**",      // Composable 组件（需插桩测试）
    "**/BVApplication*.class", // Application 类（需 Android 环境）
    "**/MainActivity*.class", // Activity 类（需插桩测试）
    "**/ComposableSingletons*.class", // Compose 编译器生成
    "**/BuildConfig*.class",  // Gradle 生成
    "**/CodecUtil*.class",    // Android MediaCodec 工具（需 Android 环境）
    "**/CodecInfoData*.class", // CodecUtil 内部数据类
    "**/CodecType*.class",    // CodecUtil 内部枚举
    "**/CodecMedia*.class",   // CodecUtil 内部枚举
    "**/CodecMode*.class",    // CodecUtil 内部枚举
    "**/SupportedFrameRate*.class", // CodecUtil 内部数据类
    "**/VideoShotExtends*.class", // Android Bitmap 工具（需 Android 环境）
    "**/VideoShotImageCache*.class", // Android LruCache（需 Android 环境）
    "**/SpriteFrame*.class",  // VideoShotExtends 内部数据类
    "**/network/GithubApi*.class", // GitHub API 客户端单例（需集成测试）
    "**/websocket/**",        // WebSocket 客户端（需集成测试）
    "**/grpc/utils/**",       // gRPC 工具（需集成测试）
    "**/http/plugins/**",     // Ktor 插件（需集成测试）
    "**/*_Hilt*.class",
    "**/Hilt_*.class",
    "**/Dagger*.class",
    "**/*_Factory.class",
    "**/*_MembersInjector.class",
    "**/*_Generated*.class",
    "**/dagger/**",
    "**/hilt_aggregated_deps/**",
    "**/bilibili/**",          // protobuf 生成代码
)

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "dev.detekt")
    apply(plugin = "jacoco")

    // detekt 配置：使用根目录的 config/detekt.yml
    tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
        config.setFrom(rootProject.files("config/detekt.yml"))
        buildUponDefaultConfig.set(true)
    }

    // JaCoCo：所有模块统一生成覆盖率报告
    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    // JVM 模块（bili-api, bili-subtitle）：配置 jacocoTestReport 排除项
    plugins.withId("org.jetbrains.kotlin.jvm") {
        tasks.named<JacocoReport>("jacocoTestReport") {
            dependsOn("test")
            classDirectories.setFrom(
                fileTree("${layout.buildDirectory.get().asFile}/classes/kotlin/main") {
                    include("**/*.class")
                    exclude(jacocoExcludes)
                }
            )
        }
    }

    // Android 应用与库使用相同的 debug 单元测试覆盖率配置。
    listOf("com.android.library", "com.android.application").forEach { androidPlugin ->
        plugins.withId(androidPlugin) {
            val createDebugJacocoReport by tasks.registering(JacocoReport::class) {
                group = "verification"
                description = "Generates JaCoCo coverage report for debug unit tests."
                dependsOn("testDebugUnitTest")
                sourceDirectories.setFrom(
                    files("src/main/kotlin", "src/main/java"),
                )
                classDirectories.setFrom(
                    fileTree("${layout.buildDirectory.get().asFile}/intermediates/built_in_kotlinc/debug") {
                        include("**/*.class")
                        exclude(jacocoExcludes)
                    },
                    fileTree("${layout.buildDirectory.get().asFile}/intermediates/javac/debug") {
                        include("**/*.class")
                        exclude(jacocoExcludes)
                    },
                )
                executionData.setFrom(
                    fileTree(layout.buildDirectory) {
                        include("outputs/unit_test_code_coverage/**/*.exec")
                        include("jacoco/*.exec")
                    }
                )
            }
        }
    }
}

apply(plugin = "jacoco")

// 聚合模块报告的输入和依赖，避免扫描仓库目录混入其他构建产物。
val jacocoAggregatedReport = tasks.register<JacocoReport>("jacocoAggregatedReport") {
    group = "verification"
    description = "Generates aggregated JaCoCo coverage report for all modules."
}

subprojects {
    fun aggregateReport(reportName: String) {
        val moduleReport = tasks.named<JacocoReport>(reportName)
        jacocoAggregatedReport.configure {
            dependsOn(moduleReport)
            sourceDirectories.from(moduleReport.map { it.sourceDirectories })
            classDirectories.from(moduleReport.map { it.classDirectories })
            executionData.from(moduleReport.map { it.executionData })
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        if (name != "bili-api-grpc") aggregateReport("jacocoTestReport")
    }
    listOf("com.android.library", "com.android.application").forEach { androidPlugin ->
        plugins.withId(androidPlugin) {
            aggregateReport("createDebugJacocoReport")
        }
    }
}
