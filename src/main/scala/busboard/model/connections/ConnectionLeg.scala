package busboard.model.connections


import busboard.model.info.Station

import java.time.{Duration, LocalDateTime}

case class ConnectionLeg
(
  start: Station,
  dest: Station,
  departure: LocalDateTime,
  arrival: LocalDateTime,
  delay: Duration,
  line: String
)
