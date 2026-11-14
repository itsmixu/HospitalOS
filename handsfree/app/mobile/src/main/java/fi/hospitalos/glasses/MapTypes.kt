package fi.hospitalos.glasses

/** Staff map (demo extra, P1): positions are simulated and labelled as such (SPEC F1). */
enum class StaffStatus { FREE, BUSY }

/** Position in the map's 0..260 x 0..150 space. */
data class MapPoint(val x: Float, val y: Float)

data class Staff(
    val name: String,
    val status: StaffStatus,
    val position: MapPoint,
    val isMe: Boolean = false,
)

object SimulatedPositions {
  val me = MapPoint(28f, 76f)

  fun staff(myName: String, colleagues: List<String>): List<Staff> {
    val spots = listOf(MapPoint(180f, 76f), MapPoint(132f, 34f), MapPoint(234f, 116f))
    return listOf(Staff("You", StaffStatus.FREE, me, isMe = true)) +
        colleagues.take(spots.size).mapIndexed { i, n ->
          Staff(n, if (i == 1) StaffStatus.BUSY else StaffStatus.FREE, spots[i])
        }
  }
}
