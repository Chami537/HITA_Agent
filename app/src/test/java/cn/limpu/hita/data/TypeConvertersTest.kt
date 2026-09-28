package cn.limpu.hita.data

import cn.limpu.hita.data.model.timetable.EventItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeConvertersTest {
    @Test
    fun stringToEventType_readsSchema6OrdinalsAndCurrentNames() {
        assertEquals(EventItem.TYPE.CLASS, TypeConverters.stringToEventType("0"))
        assertEquals(EventItem.TYPE.EXAM, TypeConverters.stringToEventType("1"))
        assertEquals(EventItem.TYPE.OTHER, TypeConverters.stringToEventType("2"))
        assertEquals(EventItem.TYPE.TAG, TypeConverters.stringToEventType("3"))
        assertEquals(EventItem.TYPE.CLASS, TypeConverters.stringToEventType("CLASS"))
        assertEquals(EventItem.TYPE.OTHER, TypeConverters.stringToEventType("not-a-type"))
    }
}
