/**
 * 应用全局配置。
 *
 * 集中管理包名、SDK 版本、版本号等构建配置，
 * 供各模块 build.gradle.kts 引用。
 */
object AppConfiguration {
    const val appId = "dev.frost819.newbv"
    const val applicationId = "dev.frost819.newbv"
    const val compileSdk = 36
    const val minSdk = 21
    const val targetSdk = 36

    private const val major = 0
    private const val minor = 1
    private const val patch = 0
    private const val hotFix = 0

    // git 信息在一次构建内只取一次：避免配置期多次求值导致数值漂移与进程泄漏
    private val gitCommitCount: Int by lazy {
        runGit("git", "rev-list", "--count", "HEAD").toIntOrNull() ?: 1
    }
    private val gitShortHash: String by lazy {
        runGit("git", "rev-list", "HEAD", "--abbrev-commit", "--max-count=1")
    }

    @Suppress("KotlinConstantConditions")
    val versionName: String by lazy {
        val base =
            System.getenv("NEWBV_VERSION_BASE")?.takeIf { it.isNotBlank() }
                ?: "$major.$minor.$patch${".$hotFix".takeIf { hotFix != 0 } ?: ""}"
        "$base.r$gitCommitCount.$gitShortHash"
    }
    val versionCode: Int get() = gitCommitCount

    /** 执行外部命令并返回标准输出；命令缺失或失败时返回空串。 */
    private fun runGit(vararg command: String): String =
        runCatching {
            ProcessBuilder(*command)
                .start()
                .let { process ->
                    process.inputStream.bufferedReader().readText().trim().also {
                        process.waitFor()
                    }
                }
        }.getOrDefault("")
}
