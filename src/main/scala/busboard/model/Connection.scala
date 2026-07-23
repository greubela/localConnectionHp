package busboard.model

import java.time.{Duration, LocalDateTime}

case class Connection(
    config: ConnectionConfig,
    departures: Seq[LocalDateTime],
    delays: Seq[Duration],
    arrival: LocalDateTime,
    duration: Duration,
    changes: Int,
    legs: Seq[ConnectionLeg]
)
