package net.rwhps.server.net.rwpp

import net.rwhps.server.io.packet.Packet

/**
 * Headless 协议连接对 Mod 传输子系统的最小接口。
 *
 * 必须定义在 app 类加载器可见的包内，避免 [ModTransferHandler] 直接引用
 * Headless 模块加载器中的 `GameVersionServer`（否则会触发 LinkageError）。
 */
interface ModTransferEndpoint {
    val connectionId: String
    val isDis: Boolean
    fun playerLabel(): String
    fun sendPacket(packet: Packet)
    fun disconnect()
}
