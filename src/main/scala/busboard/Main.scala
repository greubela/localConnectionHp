package busboard

import busboard.model.{Connection, ConnectionConfig, ConnectionLeg, ConnectionOverview}
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import java.time.{Duration, LocalDateTime}
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.Thenable.Implicits.*

object Main:
  given ExecutionContext = ExecutionContext.global
  private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
  private val status = Var("Konfiguration wird geladen …")
  private val overviews = Var(Seq.empty[ConnectionOverview])
  private val errors = Var(Map.empty[String, String])
  private val active = Var(Set.empty[String])
  private var configs = Seq.empty[ConnectionConfig]
  private var refreshing = false

  def main(args: Array[String]): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), application)
    loadJson("routes.json").map(readConfigs).foreach { loaded =>
      configs = loaded
      active.set(loaded.map(_.id).toSet)
      refresh()
      dom.window.setInterval(() => refresh(), 60000)
    }

  private def application: Element =
    mainTag(
      cls := "app",
      headerTag(cls := "topbar",
        div(p(cls := "eyebrow", "Live-Verbindungen"), h1("Meine Verbindungen")),
        div(cls := "status", child.text <-- status.signal)
      ),
      sectionTag(cls := "controls", children <-- active.signal.map { selected =>
        configs.map { config =>
          button(
            cls := s"route-toggle ${if selected(config.id) then "active" else ""}",
            config.title,
            onClick --> { _ =>
              active.update(ids => if ids(config.id) then ids - config.id else ids + config.id)
              refresh()
            }
          )
        }
      }),
      sectionTag(cls := "cards", children <-- overviews.signal.combineWith(errors.signal).map { (items, failures) =>
        val selected = configs.filter(c => active.now()(c.id))
        selected.map { config =>
          items.find(_.config.id == config.id) match
            case Some(overview) => overviewView(overview)
            case None => routeCard(config, div(cls := (if failures.contains(config.id) then "error" else "empty"), failures.getOrElse(config.id, "Verbindungen werden geladen …")))
        }
      })
    )

  private def refresh(): Unit =
    if refreshing then return
    val selected = configs.filter(c => active.now()(c.id))
    if selected.isEmpty then
      overviews.set(Seq.empty); status.set("Keine Route ausgewählt")
    else
      refreshing = true
      status.set("Aktualisiere …"); errors.set(Map.empty); overviews.set(Seq.empty)
      Future.sequence(selected.map(loadOverview)).foreach { results =>
        overviews.set(results.collect { case Right(value) => value })
        errors.set(results.collect { case Left((id, message)) => id -> message }.toMap)
        status.set(s"Aktualisiert ${currentLocalTime()}")
        refreshing = false
      }

  private def loadOverview(config: ConnectionConfig): Future[Either[(String, String), ConnectionOverview]] =
    resolveStations(config.stations).flatMap { stations =>
      HafasClient.journeys(stations.head._2, stations(1)._2, config.results, None, config.products).flatMap { result =>
        Future.sequence(journeys(result).map(first => extend(config, stations, 1, Seq(first))))
          .map(found => ConnectionOverview(config, found.flatten.flatMap(toConnection(config, stations)).sortBy(_.departures.head)))
      }
    }.map(Right(_)).recover { case error => Left(config.id -> s"Verbindung konnte nicht geladen werden: ${error.getMessage}") }

  private def extend(config: ConnectionConfig, stations: Seq[(Station, String)], index: Int, accumulated: Seq[js.Dynamic]): Future[Option[Seq[js.Dynamic]]] =
    if index >= stations.size - 1 then Future.successful(Some(accumulated))
    else
      val departure = legs(accumulated.last).lastOption.flatMap(arrivalTime)
      HafasClient.journeys(stations(index)._2, stations(index + 1)._2, 1, departure, config.products)
        .flatMap(result => journeys(result).headOption match
          case Some(next) => extend(config, stations, index + 1, accumulated :+ next)
          case None => Future.successful(None))

  private def toConnection(config: ConnectionConfig, stations: Seq[(Station, String)])(parts: Seq[js.Dynamic]): Option[Connection] =
    val parsedLegs = parts.zipWithIndex.map { (journey, index) =>
      val partLegs = legs(journey)
      for
        firstLeg <- partLegs.headOption
        lastLeg <- partLegs.lastOption
        departure <- departureTime(firstLeg).flatMap(localDateTime)
        arrival <- arrivalTime(lastLeg).flatMap(localDateTime)
      yield
        val delay = Duration.ofSeconds(number(firstLeg.departureDelay).getOrElse(0d).toLong)
        val lines = partLegs.flatMap(leg => obj(leg.line).flatMap(line => string(line.name))).distinct
        ConnectionLeg(stations(index)._1.name, stations(index + 1)._1.name, departure, arrival, delay, lines)
    }
    if parsedLegs.exists(_.isEmpty) then None
    else
      val connectionLegs = parsedLegs.flatten
      for
        first <- connectionLegs.headOption
        last <- connectionLegs.lastOption
      yield Connection(config, connectionLegs.map(_.departure), connectionLegs.map(_.delay), last.arrival,
        Duration.between(first.departure, last.arrival), (parts.flatMap(legs).count(leg => obj(leg.line).nonEmpty) - 1).max(0), connectionLegs)

  private def overviewView(overview: ConnectionOverview): Element =
    routeCard(overview.config,
      if overview.connections.isEmpty then div(cls := "empty", "Aktuell wurde keine Verbindung gefunden.")
      else div(overview.connections.map(connectionView)))

  private def routeCard(config: ConnectionConfig, body: Element): Element = articleTag(cls := "card",
    div(cls := "card-head", div(h2(cls := "route-title", config.title), p(cls := "route-subtitle", config.stations.map(_.name).mkString(" → "))), div(cls := "badge", "DB")), body)

  private def connectionView(connection: Connection): Element =
    val departure = connection.departures.head
    val delayMinutes = connection.delays.head.toMinutes
    div(cls := "connection",
      div(cls := "time", departure.format(timeFormat), span(cls := s"delay ${if delayMinutes > 0 then "late" else ""}", if delayMinutes > 0 then s"+$delayMinutes min" else "pünktlich")),
      div(cls := "path",
        div(cls := "line", connection.legs.flatMap(_.lines).distinct.mkString("  ·  ")),
        div(cls := "meta", s"${connection.changes} Umst.  |  ${connection.duration.toMinutes} min  |  an ${connection.arrival.format(timeFormat)}"),
        div(cls := "legs", connection.legs.map { leg =>
          val delayed = !leg.delay.isZero && !leg.delay.isNegative
          div(cls := "leg", span(s"${leg.from} → ${leg.to}"), strong(s"${leg.departure.format(timeFormat)}–${leg.arrival.format(timeFormat)}"), em(leg.lines.mkString(" · ")), small(cls := s"delay ${if delayed then "late" else ""}", if delayed then s"+${leg.delay.toMinutes} min" else "pünktlich"))
        })
      ),
      div(cls := "duration", s"${connection.duration.toMinutes} min")
    )

  private def readConfigs(data: js.Dynamic): Seq[ConnectionConfig] =
    data.routes.asInstanceOf[js.Array[js.Dynamic]].toSeq.map { route =>
      val via = route.via.asInstanceOf[js.UndefOr[js.Array[js.Dynamic]]].toOption.orElse(route.waypoints.asInstanceOf[js.UndefOr[js.Array[js.Dynamic]]].toOption).fold(Seq.empty)(_.toSeq)
      val stops = Seq(route.from) ++ via ++ Seq(route.to)
      val products = obj(route.products).fold(Map.empty[String, Boolean])(p => js.Object.keys(p.asInstanceOf[js.Object]).map(key => key -> p.selectDynamic(key).asInstanceOf[Boolean]).toMap)
      ConnectionConfig(route.id.toString, route.title.toString, stops.map(s => Station(s.name.toString, string(s.id))), number(route.results).fold(4)(_.toInt), products)
    }

  private def resolveStations(stations: Seq[Station]): Future[Seq[(Station, String)]] = Future.sequence(stations.map(station => station.id.fold(HafasClient.locations(station.name).map(values => values.head.id.toString))(Future.successful).map(station -> _)))
  private def loadJson(url: String): Future[js.Dynamic] =
    dom.fetch(url).toFuture.flatMap(_.json().toFuture).map(_.asInstanceOf[js.Dynamic])
  private def journeys(value: js.Dynamic): Seq[js.Dynamic] = value.journeys.asInstanceOf[js.UndefOr[js.Array[js.Dynamic]]].toOption.fold(Seq.empty)(_.toSeq)
  private def legs(value: js.Dynamic): Seq[js.Dynamic] = value.legs.asInstanceOf[js.Array[js.Dynamic]].toSeq
  private def string(value: js.Any): Option[String] =
    if value == null || js.isUndefined(value) then None else Some(value.toString)
  private def number(value: js.Any): Option[Double] = value.asInstanceOf[js.UndefOr[Double]].toOption
  private def obj(value: js.Any): Option[js.Dynamic] = value.asInstanceOf[js.UndefOr[js.Dynamic]].toOption
  private def departureTime(leg: js.Dynamic): Option[String] =
    string(leg.departure).orElse(string(leg.plannedDeparture))
  private def arrivalTime(leg: js.Dynamic): Option[String] =
    string(leg.arrival).orElse(string(leg.plannedArrival))
  private def localDateTime(value: String): Option[LocalDateTime] =
    val date = new js.Date(value)
    if date.getTime().isNaN then None
    else Some(LocalDateTime.of(date.getFullYear().toInt, date.getMonth().toInt + 1, date.getDate().toInt,
      date.getHours().toInt, date.getMinutes().toInt, date.getSeconds().toInt))
  private def currentLocalTime(): String =
    val now = new js.Date()
    f"${now.getHours().toInt}%02d:${now.getMinutes().toInt}%02d"
