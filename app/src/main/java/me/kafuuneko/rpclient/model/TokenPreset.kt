package me.kafuuneko.rpclient.model

/** 应用内共用的 Token 数值快捷预设，按十进制计数（1K = 1000，1M = 1_000_000）。 */
enum class TokenPreset(
    val displayName: String,
    val value: Int
) {
    K8("8K", 8_000),

    K16("16K", 16_000),

    K32("32K", 32_000),

    K64("64K", 64_000),

    K128("128K", 128_000),

    K256("256K", 256_000),

    K512("512K", 512_000),

    M1("1M", 1_000_000)
}
