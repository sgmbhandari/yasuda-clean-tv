package local.cleanhome.tv

import org.junit.Assert.*
import org.junit.Test

class InputRoutingTest {
    @Test fun usesHardwareSuffixRatherThanDisplayOrder() {
        assertEquals(5, InputRouting.sourceId("com.droidlogic.tvinput/.services.Hdmi1InputService/HW5"))
        assertEquals(6, InputRouting.sourceId("com.droidlogic.tvinput/.services.Hdmi2InputService/HW6"))
        assertEquals(7, InputRouting.sourceId("com.droidlogic.tvinput/.services.Hdmi3InputService/HW7"))
        assertEquals(1, InputRouting.sourceId("com.droidlogic.tvinput/.services.AV1InputService/HW1"))
        assertEquals(2, InputRouting.sourceId("com.droidlogic.tvinput/.services.AV2InputService/HW2"))
        assertNull(InputRouting.sourceId("org.videolan.vlc/.PreviewVideoInputService"))
        assertNull(InputRouting.sourceId("x/HWbroken"))
    }
}
