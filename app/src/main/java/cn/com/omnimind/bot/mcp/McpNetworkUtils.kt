package cn.com.omnimind.bot.mcp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * MCP 网络工具类
 */
object McpNetworkUtils {

    /**
     * 检查设备当前是否处于可访问局域网的网络环境。
     */
    fun isLanConnected(context: Context): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val active = connectivity?.activeNetwork
        val capabilities = active?.let { connectivity.getNetworkCapabilities(it) }
        if (capabilities != null) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return true
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && currentLanIp() != null) {
                return true
            }
        }
        return currentLanIp() != null
    }

    /**
     * 获取当前局域网 IP 地址
     */
    fun currentLanIp(): String? {
        val interfaces = runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.let { Collections.list(it) }
                .orEmpty()
        }.getOrDefault(emptyList())

        for (netIf in interfaces) {
            val interfaceUsable = runCatching {
                netIf.isUp && !netIf.isLoopback && !netIf.isVirtual
            }.getOrDefault(true)
            if (!interfaceUsable) continue

            val addresses = runCatching { Collections.list(netIf.inetAddresses) }
                .getOrDefault(emptyList())
            val lanAddress = addresses.firstOrNull { address ->
                !address.isLoopbackAddress &&
                    address is Inet4Address &&
                    isLanAddress(address.hostAddress)
            } as? Inet4Address
            if (lanAddress != null) {
                return lanAddress.hostAddress
            }
        }
        return null
    }

    /**
     * 检查是否为局域网地址（包括 RFC1918 和 Tailscale CGNAT）
     */
    fun isLanAddress(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val normalizedHost = host.trim().lowercase()

        if (normalizedHost == "localhost") return true
        if (normalizedHost == "127.0.0.1") return true
        if (normalizedHost == "::1" || normalizedHost == "[::1]") return true

        // RFC1918 私网网段
        if (normalizedHost.startsWith("192.168.")) return true
        if (normalizedHost.startsWith("10.")) return true
        if (normalizedHost.startsWith("172.")) {
            val parts = normalizedHost.split(".")
            if (parts.size >= 2) {
                val second = parts[1].toIntOrNull()
                if (second != null && second in 16..31) return true
            }
        }

        // Tailscale / CGNAT 网段（100.64.0.0/10）
        if (normalizedHost.startsWith("100.")) {
            val parts = normalizedHost.split(".")
            if (parts.size >= 2) {
                val second = parts[1].toIntOrNull()
                if (second != null && second in 64..127) return true
            }
        }

        return false
    }
}
