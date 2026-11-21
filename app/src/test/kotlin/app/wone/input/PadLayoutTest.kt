package app.wone.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PadLayoutTest {
    @Test fun defaultRoundTrips() {
        val a = PadLayout.default()
        val back = PadLayout.listFromJson(PadLayout.listToJson(listOf(a)))
        assertEquals(listOf(a), back)
    }

    @Test fun keyBindingRoundTrips() {
        val c = PadControl("k1", CtlType.Button, "Run", 0.5f, 0.5f, 0.2f, 0.8f, false, Bind.Key(0x06, 0x01))
        val l = PadLayout("Dev", listOf(c))
        assertEquals(listOf(l), PadLayout.listFromJson(PadLayout.listToJson(listOf(l))))
    }

    @Test fun garbageGivesEmptyList() {
        assertTrue(PadLayout.listFromJson("not json").isEmpty())
        assertTrue(PadLayout.listFromJson("[{\"name\":1}]").isEmpty())
    }

    @Test fun outOfRangeValuesAreClamped() {
        val json = """[{"name":"x","controls":[{"id":"a","type":"Button","x":9,"y":-3,"size":5,"bind":{"k":"pad","v":99}}]}]"""
        val c = PadLayout.listFromJson(json).single().controls.single()
        assertEquals(1f, c.x); assertEquals(0f, c.y); assertEquals(0.6f, c.size)
        assertEquals(Bind.Pad(15), c.bind)
    }

    @Test fun controlCountIsCapped() {
        val ctl = """{"id":"a","type":"Button","x":0.5,"y":0.5,"size":0.1,"bind":{"k":"pad","v":0}}"""
        val json = "[{\"name\":\"big\",\"controls\":[" + List(100) { ctl }.joinToString(",") + "]}]"
        assertEquals(PadLayout.MAX_CONTROLS, PadLayout.listFromJson(json).single().controls.size)
    }
}
