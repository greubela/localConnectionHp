package busboard

import java.time.{Duration, LocalDateTime}

case class ConnectionLeg(
    from: String,
    to: String,
    departure: LocalDateTime,
    arrival: LocalDateTime,
    delay: Duration,
    lines: Seq[String]
)
