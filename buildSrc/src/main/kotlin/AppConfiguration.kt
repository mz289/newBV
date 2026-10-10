import org.gradle.api.Project

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

    private val versionBase =
        "$major.$minor.$patch${".$hotFix".takeIf { hotFix != 0 } ?: ""}"

    /**
     * versionCode：当前 HEAD 的提交总数。
     *
     * 必须传 [project] 走 `providers.exec` 取值——configuration cache 下
     * 配置期裸跑 git 会构建失败或把旧值冻结进缓存；providers.exec 会在
     * 缓存复用时重跑进程、值变化则使缓存失效。
     */
    fun versionCode(project: Project): Int =
        git(project, "rev-list", "--count", "HEAD").toIntOrNull() ?: 1

    /** versionName：`<base>.r<提交数>.<短哈希>`，versionCode 同源。 */
    fun versionName(project: Project): String {
        val base =
            project.providers
                .environmentVariable("NEWBV_VERSION_BASE")
                .orElse(versionBase)
                .get()
                .ifBlank { versionBase }
        val shortHash =
            git(project, "rev-list", "HEAD", "--abbrev-commit", "--max-count=1")
        return "$base.r${versionCode(project)}.$shortHash"
    }

    /** 执行 git 并返回标准输出；命令缺失或失败时返回空串。 */
    private fun git(
        project: Project,
        vararg args: String,
    ): String =
        runCatching {
            project.providers.exec {
                commandLine("git", *args)
            }.standardOutput.asText.get().trim()
        }.getOrDefault("")
}
