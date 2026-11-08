package local.cleanhome.tv

import org.junit.Assert.*
import org.junit.Test

class AdbWireTest {
    @Test fun packetRoundTripPreservesBodyAndStreamIds() {
        val packet = AdbPacket("WRTE", 1, 7, "result\n".toByteArray())
        val read = AdbWire.read(AdbWire.encode(packet).inputStream())
        assertEquals(packet.command, read.command)
        assertEquals(1, read.arg0); assertEquals(7, read.arg1)
        assertArrayEquals(packet.data, read.data)
    }
    @Test fun corruptPacketCannotExecuteAsAnAdbMessage() {
        val bytes = AdbWire.encode(AdbPacket("WRTE", 1, 2, byteArrayOf(1)))
        bytes[24] = 2
        try { AdbWire.read(bytes.inputStream()); fail("Checksum mismatch accepted") } catch (_: java.io.IOException) { }
    }
    @Test fun truncatedHeaderIsRejected() {
        try { AdbWire.read(byteArrayOf(1, 2).inputStream()); fail("Truncated packet accepted") } catch (_: java.io.IOException) { }
    }
}
