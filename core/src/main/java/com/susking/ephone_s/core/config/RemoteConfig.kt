package com.susking.ephone_s.core.config

/**
 * 远程服务配置中心。
 *
 * 集中存放 Cloudflare Worker、Gitee 等外部服务的地址常量，供激活、更新检查、
 * 意见反馈三大功能复用。所有占位符在正式发布前必须替换为真实地址。
 *
 * 架构分工：
 *  - Worker：处理“小数据”逻辑——激活验证、版本信息、反馈转发（免费 workers.dev）。
 *  - Gitee：托管“大文件”——APK 安装包放在 Releases，国内下载稳定。
 */
object RemoteConfig {

    /**
     * Cloudflare Worker 根地址。
     * 部署后替换为你的实际地址，形如 https://your-worker-name.your-subdomain.workers.dev
     * 末尾不要带斜杠。
     */
    const val WORKER_BASE_URL: String = "https://your-worker-name.your-subdomain.workers.dev"

    /** 激活验证接口路径，POST 提交「激活码 + 设备指纹」。 */
    const val PATH_ACTIVATE: String = "/activate"

    /** 版本信息接口路径，GET 返回最新版本号、更新日志、APK 下载地址。 */
    const val PATH_VERSION: String = "/version"

    /** 意见反馈接口路径，POST 提交纯文字反馈内容。 */
    const val PATH_FEEDBACK: String = "/feedback"

    /** 激活接口完整地址。 */
    val activateUrl: String get() = WORKER_BASE_URL + PATH_ACTIVATE

    /** 版本信息接口完整地址。 */
    val versionUrl: String get() = WORKER_BASE_URL + PATH_VERSION

    /** 反馈接口完整地址。 */
    val feedbackUrl: String get() = WORKER_BASE_URL + PATH_FEEDBACK

    /**
     * 网络请求超时（毫秒）。
     * 免费 workers.dev 在国内访问不稳，超时设短一些以便快速失败并提示用户重试。
     */
    const val NETWORK_TIMEOUT_MILLIS: Long = 15_000L
}
