package busboard.model.connections

import busboard.model.*
import busboard.model.info.*

import java.time.{Duration, LocalDateTime}

case class Connection(
                       route: Route,
                       legs: Seq[ConnectionLeg]
                     ) {
  def duration: Duration = ???

  def isPossible: Boolean = ??? // transfer possible because no one arrives after the other left because of delays

}

