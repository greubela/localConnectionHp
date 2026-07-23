package busboard

import busboard.model.connections.{Connection, ConnectionLeg}
import busboard.model.info.{Route, Station}
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
  private val connections = Var(Map.empty[String, Seq[Connection]])
  private val errors = Var(Map.empty[String, String])
  private val active = Var(Set.empty[String])
  private var routes = Seq.empty[Route]
  private var refreshSeconds = 180
  private var refreshing = false

  def main(args: Array[String]): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), application)
    loadConfiguration().foreach { loaded =>
      routes = loaded; active.set(loaded.map(_.defaultTitle).toSet); refresh()
      dom.window.setInterval(() => refresh(), refreshSeconds * 1000)
    }

  private def loadConfiguration(): Future[Seq[Route]] = for
    cache <- loadJson("config/stationcache.json")
    _ = Station.setCache(cache.asInstanceOf[js.Array[js.Dynamic]].toSeq.map(readCachedStation))
    config <- loadJson("config/config.json")
    _ = refreshSeconds = number(config.refreshSeconds).map(_.toInt).getOrElse(180)
    loaded <- Future.sequence(config.routeStrings.asInstanceOf[js.Array[String]].toSeq.map(resolveRoute))
  yield loaded

  private def resolveRoute(value: String): Future[Route] =
    Future.sequence(Route.parts(value).map(resolveStation)).map(Route(_, Some(value)))

  /** A station request is only made when neither its EVA ID nor its name is cached. */
  private def resolveStation(value: String): Future[Station] =
    value.toIntOption.flatMap(Station.fromEvaId).orElse(Station.fromName(value)) match
      case Some(station) => Future.successful(station)
      case None => value.toIntOption match
        case Some(id) => HafasClient.station(id.toString).map(readApiStation)
        case None => HafasClient.locations(value).flatMap(_.headOption
          .fold(Future.failed(new RuntimeException(s"Station nicht gefunden: $value")))(raw => Future.successful(readApiStation(raw))))

  private def application: Element = mainTag(cls := "app",
    headerTag(cls := "topbar", div(p(cls := "eyebrow", "Live-Verbindungen"), h1("Meine Verbindungen")), div(cls := "status", child.text <-- status.signal)),
    sectionTag(cls := "controls", children <-- active.signal.map(selected => routes.map { route =>
      val id = route.defaultTitle
      button(cls := s"route-toggle ${if selected(id) then "active" else ""}", route.title.getOrElse(id),
        onClick --> { _ => active.update(ids => if ids(id) then ids - id else ids + id); refresh() })
    })),
    sectionTag(cls := "cards", children <-- connections.signal.combineWith(errors.signal).map { (loaded, failures) =>
      routes.filter(route => active.now()(route.defaultTitle)).map(route => routeCard(route, loaded.get(route.defaultTitle), failures.get(route.defaultTitle)))
    }))

  private def refresh(): Unit =
    if refreshing then return
    val selected = routes.filter(route => active.now()(route.defaultTitle))
    if selected.isEmpty then
      connections.set(Map.empty)
      status.set("Keine Route ausgewählt")
    else
      refreshing = true; status.set("Aktualisiere …"); errors.set(Map.empty)
      Future.sequence(selected.map(loadConnections)).foreach { results =>
        connections.set(results.collect { case Right((id, values)) => id -> values }.toMap)
        errors.set(results.collect { case Left((id, message)) => id -> message }.toMap)
        status.set(s"Aktualisiert ${currentLocalTime()}"); refreshing = false
      }

  private def loadConnections(route: Route): Future[Either[(String, String), (String, Seq[Connection])]] =
    HafasClient.journeys(route.legs.head.evaId.toString, route.legs(1).evaId.toString, 4, None, Map.empty).flatMap { result =>
      Future.sequence(journeys(result).map(first => extend(route, 1, Seq(first))))
        .map(found => route.defaultTitle -> found.flatten.flatMap(toConnection(route)).sortBy(_.legs.head.departure))
    }.map(Right(_)).recover { case error => Left(route.defaultTitle -> s"Verbindung konnte nicht geladen werden: ${error.getMessage}") }

  private def extend(route: Route, index: Int, accumulated: Seq[js.Dynamic]): Future[Option[Seq[js.Dynamic]]] =
    if index >= route.legs.size - 1 then Future.successful(Some(accumulated))
    else HafasClient.journeys(route.legs(index).evaId.toString, route.legs(index + 1).evaId.toString, 1,
      legs(accumulated.last).lastOption.flatMap(arrivalTime), Map.empty).flatMap(result => journeys(result).headOption
        .fold(Future.successful(Option.empty[Seq[js.Dynamic]]))(next => extend(route, index + 1, accumulated :+ next)))

  private def toConnection(route: Route)(parts: Seq[js.Dynamic]): Option[Connection] =
    val parsed = parts.zipWithIndex.map { (journey, index) =>
      val allLegs = legs(journey)
      for first <- allLegs.headOption; last <- allLegs.lastOption
          departure <- departureTime(first).flatMap(localDateTime); arrival <- arrivalTime(last).flatMap(localDateTime)
      yield ConnectionLeg(route.legs(index), route.legs(index + 1), departure, arrival,
        Duration.ofSeconds(number(first.departureDelay).getOrElse(0d).toLong),
        allLegs.flatMap(leg => dynamic(leg.line).flatMap(line => string(line.name))).distinct.mkString(" · "))
    }
    Option.when(parsed.forall(_.nonEmpty))(Connection(route, parsed.flatten))

  private def routeCard(route: Route, values: Option[Seq[Connection]], failure: Option[String]): Element = articleTag(cls := "card",
    div(cls := "card-head", div(h2(cls := "route-title", route.title.getOrElse(route.defaultTitle)),
      p(cls := "route-subtitle", route.legs.map(_.displayString).mkString(" → "))), div(cls := "badge", "DB")),
    failure.fold(values.fold[Element](div(cls := "empty", "Verbindungen werden geladen …")) { items =>
      if items.isEmpty then div(cls := "empty", "Aktuell wurde keine Verbindung gefunden.") else div(items.map(connectionView))
    })(message => div(cls := "error", message)))

  private def connectionView(connection: Connection): Element =
    val first = connection.legs.head
    div(cls := "connection",
      div(cls := "time", first.departure.format(timeFormat), span(cls := s"delay ${if first.delay.toMinutes > 0 then "late" else ""}", if first.delay.toMinutes > 0 then s"+${first.delay.toMinutes} min" else "pünktlich")),
      div(cls := "path", div(cls := "line", connection.legs.map(_.line).filter(_.nonEmpty).distinct.mkString(" · ")),
        div(cls := "meta", s"${(connection.legs.size - 1).max(0)} Umst. | ${connection.duration.toMinutes} min | an ${connection.legs.last.arrival.format(timeFormat)}"),
        div(cls := "legs", connection.legs.map(leg => div(cls := "leg", span(s"${leg.start.displayString} → ${leg.dest.displayString}"), strong(s"${leg.departure.format(timeFormat)}–${leg.arrival.format(timeFormat)}"), em(leg.line))))),
      div(cls := "duration", s"${connection.duration.toMinutes} min"))

  private def readCachedStation(raw: js.Dynamic): Station = Station(raw.evaId.toString.toInt, raw.stationName.toString, string(raw.displayName))
  private def readApiStation(raw: js.Dynamic): Station = Station.cache(Station(raw.id.toString.toInt, raw.name.toString, None))
  private def loadJson(url: String): Future[js.Dynamic] = dom.fetch(url).toFuture.flatMap(response =>
    if response.ok then response.json().toFuture else Future.failed(new RuntimeException(s"$url konnte nicht geladen werden (${response.status})"))).map(_.asInstanceOf[js.Dynamic])
  private def journeys(value: js.Dynamic): Seq[js.Dynamic] = value.journeys.asInstanceOf[js.UndefOr[js.Array[js.Dynamic]]].toOption.fold(Seq.empty)(_.toSeq)
  private def legs(value: js.Dynamic): Seq[js.Dynamic] = value.legs.asInstanceOf[js.Array[js.Dynamic]].toSeq
  private def string(value: js.Any): Option[String] = if value == null || js.isUndefined(value) then None else Some(value.toString)
  private def number(value: js.Any): Option[Double] = value.asInstanceOf[js.UndefOr[Double]].toOption
  private def dynamic(value: js.Any): Option[js.Dynamic] = value.asInstanceOf[js.UndefOr[js.Dynamic]].toOption
  private def departureTime(leg: js.Dynamic): Option[String] = string(leg.departure).orElse(string(leg.plannedDeparture))
  private def arrivalTime(leg: js.Dynamic): Option[String] = string(leg.arrival).orElse(string(leg.plannedArrival))
  private def localDateTime(value: String): Option[LocalDateTime] =
    val date = new js.Date(value)
    Option.when(!date.getTime().isNaN)(LocalDateTime.of(date.getFullYear().toInt, date.getMonth().toInt + 1, date.getDate().toInt, date.getHours().toInt, date.getMinutes().toInt, date.getSeconds().toInt))
  private def currentLocalTime(): String =
    val now = new js.Date(); f"${now.getHours().toInt}%02d:${now.getMinutes().toInt}%02d"
