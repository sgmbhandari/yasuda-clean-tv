package local.cleanhome.tv

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AdbPacket(val command: String, val arg0: Int, val arg1: Int, val data: ByteArray = byteArrayOf())

object AdbWire {
    private fun code(command: String) = ByteBuffer.wrap(command.toByteArray(Charsets.US_ASCII)).order(ByteOrder.LITTLE_ENDIAN).int
    fun encode(packet: AdbPacket): ByteArray {
        val code = code(packet.command)
        return ByteBuffer.allocate(24 + packet.data.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(code).putInt(packet.arg0).putInt(packet.arg1).putInt(packet.data.size)
            .putInt(packet.data.sumOf { it.toInt() and 255 }).putInt(code.inv()).put(packet.data).array()
    }
    fun read(input: InputStream): AdbPacket {
        val stream = DataInputStream(input)
        val header = ByteArray(24); stream.readFully(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val code = buffer.int; val first = buffer.int; val second = buffer.int
        val length = buffer.int; val checksum = buffer.int; val magic = buffer.int
        if (magic != code.inv() || length !in 0..(256 * 1024)) throw IOException("Invalid ADB packet")
        val data = ByteArray(length); stream.readFully(data)
        if (data.sumOf { it.toInt() and 255 } != checksum) throw IOException("ADB packet checksum mismatch")
        val name = String(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(code).array(), Charsets.US_ASCII)
        return AdbPacket(name, first, second, data)
    }
}

object LocalAdb {
    private fun service(name: String): String {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", 5555), 3000)
            socket.soTimeout = 90_000
            val input = socket.getInputStream(); val output = socket.getOutputStream()
            fun send(packet: AdbPacket) { output.write(AdbWire.encode(packet)); output.flush() }
            send(AdbPacket("CNXN", 0x01000000, 4096, "host::\u0000".toByteArray()))
            val connected = AdbWire.read(input)
            if (connected.command == "AUTH") throw IOException("ADB authorization is required; use the Mac recovery tool")
            if (connected.command != "CNXN" || !String(connected.data).contains("ro.product.model=CloudTV708")) throw IOException("Unexpected local ADB device")
            send(AdbPacket("OPEN", 1, 0, (name + '\u0000').toByteArray()))
            val result = java.io.ByteArrayOutputStream()
            while (true) {
                val packet = AdbWire.read(input)
                when (packet.command) {
                    "OKAY" -> if (packet.arg1 != 1) throw IOException("Unexpected ADB stream")
                    "WRTE" -> {
                        if (packet.arg1 != 1 || result.size() + packet.data.size > 128 * 1024) throw IOException("Invalid ADB response")
                        result.write(packet.data); send(AdbPacket("OKAY", 1, packet.arg0))
                    }
                    "CLSE" -> { send(AdbPacket("CLSE", 1, packet.arg0)); return result.toString("UTF-8") }
                    else -> throw IOException("Unexpected ADB message")
                }
            }
        }
    }

    fun requireRoot() {
        if (service("shell:id -u").trim() == "0") return
        try { service("root:") } catch (_: IOException) { }
        repeat(20) {
            Thread.sleep(250)
            try { if (service("shell:id -u").trim() == "0") return } catch (_: IOException) { }
        }
        throw IOException("Local ADB could not obtain restore access")
    }

    fun recovery(digest: String, apply: Boolean): String {
        require(digest.matches(Regex("[a-f0-9]{64}")))
        val script = "/data/local/tmp/cleanhome-recovery/restore.sh"
        val check = service("shell:sha256sum $script").trim().substringBefore(' ')
        if (check != digest) throw IOException("Saved recovery script is missing or changed")
        val result = service("shell:/system/bin/sh $script ${if (apply) "--apply" else "--check"}")
        val expected = if (apply) "RESTORE_APPLY_OK" else "RESTORE_CHECK_OK"
        if (expected !in result.lineSequence().map { it.trim() }.toList()) throw IOException("Restore stopped before completion: " + result.takeLast(500))
        return result
    }

    fun launchRecovery(digest: String) {
        recovery(digest, false)
        val script = "/data/local/tmp/cleanhome-recovery/restore.sh"
        // Keep the parent alive until its child has entered a separate session.
        // The component restore can terminate the app and its ADB stream.
        service("shell:setsid /system/bin/sh $script --apply >/data/local/tmp/cleanhome-recovery/restore.log 2>&1 </dev/null & wait")
    }
}
