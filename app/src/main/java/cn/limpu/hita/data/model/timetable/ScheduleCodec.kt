package cn.limpu.hita.data.model.timetable

/** A weekly occupied slot: Monday is 1, Sunday is 7; section bounds are inclusive. */
data class BusySlot(
    val weekday: Int,
    val startSection: Int,
    val endSection: Int
)

/** Encodes slots in order as `weekday,startSection,endSection` rows separated by semicolons. */
fun encodeSchedule(schedule: List<BusySlot>): String = schedule.joinToString(";") { slot ->
    "${slot.weekday},${slot.startSection},${slot.endSection}"
}

/** Decodes every row, rejecting malformed or non-integer fields rather than losing slots. */
fun decodeSchedule(text: String): List<BusySlot> {
    if (text.isBlank()) return emptyList()

    return text.split(';').map { row ->
        val fields = row.split(',')
        require(fields.size == 3) { "Each schedule row must contain exactly three integer fields" }
        BusySlot(
            weekday = fields[0].toInt(),
            startSection = fields[1].toInt(),
            endSection = fields[2].toInt()
        )
    }
}
