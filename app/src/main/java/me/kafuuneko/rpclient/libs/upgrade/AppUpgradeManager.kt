package me.kafuuneko.rpclient.libs.upgrade

/**
 * 按目标 versionCode 调度业务数据升级。
 *
 * 每个迁移和清理步骤成功后分别写入检查点，因此中途失败时，下次启动只需从失败处继续。
 * 所有迁移完成后才开始清理旧存储，防止后续步骤依赖的数据被过早删除。
 */
class AppUpgradeManager(
    versionCodeProvider: AppVersionCodeProvider,
    versionStore: AppUpgradeVersionStore,
    upgrades: List<AppUpgrade>
) {
    private val mVersionCodeProvider = versionCodeProvider
    private val mVersionStore = versionStore
    private val mUpgrades = upgrades.sortedBy(AppUpgrade::targetVersionCode)

    init {
        require(mUpgrades.all { it.targetVersionCode > 0 }) {
            "App upgrade target versions must be positive"
        }
        require(mUpgrades.map { it.targetVersionCode }.distinct().size == mUpgrades.size) {
            "App upgrade target versions must be unique"
        }
    }

    /** 执行当前安装包范围内尚未完成的迁移和清理。 */
    suspend fun upgrade(
        currentVersionCode: Int = mVersionCodeProvider.currentVersionCode(),
    ) {
        require(currentVersionCode > 0) {
            "Current version code must be positive"
        }

        var completedVersionCode = mVersionStore.lastCompletedVersionCode
        mUpgrades.forEach { upgrade ->
            val targetVersionCode = upgrade.targetVersionCode
            if (targetVersionCode !in (completedVersionCode + 1)..currentVersionCode) {
                return@forEach
            }
            upgrade.migrate()
            mVersionStore.lastCompletedVersionCode = targetVersionCode
            completedVersionCode = targetVersionCode
        }

        var cleanedVersionCode = mVersionStore.lastCleanedVersionCode
        mUpgrades
            .asSequence()
            .filter { it.targetVersionCode > cleanedVersionCode }
            .filter { it.targetVersionCode <= completedVersionCode }
            .filter { it.targetVersionCode <= currentVersionCode }
            .forEach { upgrade ->
                upgrade.cleanup()
                mVersionStore.lastCleanedVersionCode = upgrade.targetVersionCode
                cleanedVersionCode = upgrade.targetVersionCode
            }
    }
}
