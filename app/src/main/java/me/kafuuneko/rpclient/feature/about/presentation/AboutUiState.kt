package me.kafuuneko.rpclient.feature.about.presentation

/** 关于页展示的外部联系与项目信息。 */
data class AboutUiState(
    /** 关于页面展示的应用版本名称。 */
    val appVersionName: String,
    /** 关于页面打开项目主页所使用的仓库地址。 */
    val githubRepoUrl: String,
    /** 关于页面展示的代码仓库名称。 */
    val githubRepoName: String,
    /** 关于页面提供的开发者联系邮箱。 */
    val developerEmail: String,
    /** 关于页面打开问题反馈所使用的仓库地址。 */
    val githubIssuesUrl: String = "$githubRepoUrl/issues"
)
