package busboard.model.connections

import busboard.model.info.Route
import java.time.Duration

case class Connection(route: Route, legs: Seq[ConnectionLeg]):
  def duration: Duration = legs.headOption.zip(legs.lastOption)
    .map((first, last) => Duration.between(first.departure, last.arrival)).getOrElse(Duration.ZERO)
  def isPossible: Boolean = legs.zip(legs.drop(1)).forall((first, next) =>
    !first.arrival.plus(first.delay).isAfter(next.departure.plus(next.delay)))

  /** Time available at each change, including the delays known for both legs. */
  def ChangingTimes(): Seq[Duration] = legs.zip(legs.drop(1)).map((first, next) =>
    Duration.between(first.arrival.plus(first.delay), next.departure.plus(next.delay)))
