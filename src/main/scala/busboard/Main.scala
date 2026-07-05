package busboard

import org.scalajs.dom
import org.scalajs.dom.{Document, Element}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

object Main:
  private val doc: Document = dom.document
  private val cards = doc.getElementById("cards")
  private val controls = doc.getElementById("routeControls")
  private val status = doc.getElementById("status")

  private var config: js.Dynamic = _
  private var routes: Seq[js.Dynamic] = Seq.empty
  private var activeIds: Set[String] = Set.empty
  private var timer: Int = 0
  private var isRefreshing: Boolean = false

  def main(args: Array[String]): Unit =
    val startup = for
      cfg <- loadJson("config.json")
      routeCfg <- loadJson("routes.json")
    yield (cfg, routeCfg)

    startup.foreach { case (cfg, routeCfg) =>
      config = cfg
      routes = routeCfg.routes.asInstanceOf[js.Array[js.Dynamic]].toSeq
      activeIds = routes.map(_.id.asInstanceOf[String]).toSet
      drawControls()
      refresh()
      timer = dom.window.setInterval(() => refresh(), refreshMs())
    }
    startup.recover { case e =>
      status.textContent = s"Could not load configuration: ${e.getMessage}"
      cards.innerHTML = ""
      cards.appendChild(message("error", "Check that config.json and routes.json are available."))
    }

  private def refreshMs(): Int =
    val seconds = optNum(config.refreshSeconds).getOrElse(60.0)
    val minimumSeconds = optNum(config.minimumRefreshSeconds).getOrElse(15.0)
    (seconds.max(minimumSeconds) * 1000).toInt

  private def requestTimeoutMs(): Int =
    val seconds = if config == null then 25.0 else optNum(config.requestTimeoutSeconds).getOrElse(25.0)
    (seconds * 1000).toInt.max(1_000)

  private def drawControls(): Unit =
    controls.innerHTML = ""
    routes.foreach { route =>
      val id = route.id.asInstanceOf[String]
      val button = doc.createElement("button")
      button.setAttribute("class", s"route-toggle ${if activeIds(id) then "active" else ""}")
      button.textContent = route.title.asInstanceOf[String]
      button.addEventListener("click", _ =>
        activeIds = if activeIds(id) then activeIds - id else activeIds + id
        drawControls(); refresh()
      )
      controls.appendChild(button)
    }

  private def refresh(): Unit =
    if isRefreshing then
      status.textContent = s"Still updating… ${clock()}"
      return

    val selected = routes.filter(r => activeIds(r.id.asInstanceOf[String]))
    status.textContent = "Updating…"
    cards.innerHTML = ""
    if selected.isEmpty then
      cards.appendChild(message("empty", "Select at least one route."))
      status.textContent = "No route selected"
    else
      isRefreshing = true
      selected.foreach { r => cards.appendChild(routeShell(r)) }
      Future.sequence(selected.map(loadRoute)).foreach { _ =>
        isRefreshing = false
        status.textContent = s"Updated ${clock()}"
      }

  private case class RoutePoint(id: String, name: String)
  private case class RouteOption(segments: Seq[js.Dynamic])

  private def loadRoute(route: js.Dynamic): Future[Unit] =
    val id = route.id.asInstanceOf[String]
    val result = for
      points <- routePoints(route)
      options <- loadOptions(route, points)
    yield (points, options)

    result.map { case (points, options) =>
      val container = doc.getElementById(s"body-$id")
      if container != null then
        container.innerHTML = ""
        if options.isEmpty then container.appendChild(message("empty", "No connection found right now."))
        else options.foreach(o => container.appendChild(connectionRow(o, points)))
    }.recover { case e =>
      val container = doc.getElementById(s"body-$id")
      if container != null then
        container.innerHTML = ""
        container.appendChild(message("error", s"Could not load this route: ${e.getMessage}"))
    }

  private def routePoints(route: js.Dynamic): Future[Seq[RoutePoint]] =
    val rawStops = Seq(route.from) ++ waypoints(route) ++ Seq(route.to)
    Future.sequence(rawStops.map(s => stopId(s).map(id => RoutePoint(id, s.name.asInstanceOf[String]))))

  private def waypoints(route: js.Dynamic): Seq[js.Dynamic] =
    optArray(route.waypoints).orElse(optArray(route.via)).map(_.toSeq).getOrElse(Seq.empty)

  private def loadOptions(route: js.Dynamic, points: Seq[RoutePoint]): Future[Seq[RouteOption]] =
    if points.size < 2 then Future.successful(Seq.empty)
    else
      loadJson(journeyUrl(route, points.head.id, points(1).id, None)).flatMap { data =>
        val firstChoices = journeysFrom(data).toSeq
        Future.sequence(firstChoices.map(first => extendOption(route, points, 1, Seq(first))))
          .map(_.flatten.sortBy(o => date(o.segments.head.departure).getTime()))
      }

  private def extendOption(route: js.Dynamic, points: Seq[RoutePoint], nextPointIdx: Int, acc: Seq[js.Dynamic]): Future[Option[RouteOption]] =
    if nextPointIdx >= points.size - 1 then Future.successful(Some(RouteOption(acc)))
    else
      val after = optStr(acc.last.arrival).orElse(lastLegTime(acc.last, "arrival"))
      loadJson(journeyUrl(route, points(nextPointIdx).id, points(nextPointIdx + 1).id, after)).flatMap { data =>
        journeysFrom(data).toSeq.headOption match
          case Some(next) => extendOption(route, points, nextPointIdx + 1, acc :+ next)
          case None => Future.successful(None)
      }

  private def journeyUrl(route: js.Dynamic, from: String, to: String, departure: Option[String]): String =
    val base = config.apiBase.asInstanceOf[String]
    val results = optNum(route.results).getOrElse(4.0).toInt
    val params = js.Dictionary[Any](
      "from" -> from,
      "to" -> to,
      "results" -> results,
      "language" -> "de",
      "stopovers" -> true,
      "remarks" -> true,
      "pretty" -> false
    )
    departure.foreach(d => params("departure") = d)
    optObj(route.products).foreach { products =>
      js.Object.keys(products.asInstanceOf[js.Object]).foreach(k => params(k) = products.selectDynamic(k).asInstanceOf[Any])
    }
    s"$base/journeys?${query(params)}"

  private def stopId(stop: js.Dynamic): Future[String] =
    optStr(stop.id).map(id => scala.concurrent.Future.successful(id)).getOrElse {
      val base = config.apiBase.asInstanceOf[String]
      val name = stop.name.asInstanceOf[String]
      loadJson(s"$base/locations?${query(js.Dictionary("query" -> name, "results" -> 1, "stops" -> true, "addresses" -> false, "poi" -> false, "language" -> "de", "pretty" -> false))}")
        .map(_.asInstanceOf[js.Array[js.Dynamic]].head.id.asInstanceOf[String])
    }

  private def routeShell(route: js.Dynamic): Element =
    val id = route.id.asInstanceOf[String]
    val div = doc.createElement("article")
    div.setAttribute("class", "card")
    div.innerHTML =
      s"""
      <div class="card-head">
        <div>
          <h2 class="route-title">${esc(route.title.asInstanceOf[String])}</h2>
          <p class="route-subtitle">${esc(routePointNames(route).mkString(" → "))}</p>
        </div>
        <div class="badge">VBB</div>
      </div>
      <div id="body-$id"><div class="empty">Loading…</div></div>
      """
    div

  private def routePointNames(route: js.Dynamic): Seq[String] =
    (Seq(route.from) ++ waypoints(route) ++ Seq(route.to)).map(_.name.asInstanceOf[String])

  private def connectionRow(option: RouteOption, points: Seq[RoutePoint]): Element =
    val journeys = option.segments
    val allLegs = journeys.flatMap(j => j.legs.asInstanceOf[js.Array[js.Dynamic]].toSeq)
    val first = allLegs.head
    val last = allLegs.last
    val dep = date(first.departure)
    val arr = date(last.arrival)
    val delayMin = delayMinutes(first, "departure")
    val lines = allLegs.flatMap(l => optObj(l.line).flatMap(line => optStr(line.name))).distinct.mkString(" · ")
    val changes = (allLegs.count(l => optObj(l.line).isDefined) - 1).max(0)
    val duration = ((arr.getTime() - dep.getTime()) / 60000).round.toInt
    val waypointInfo = waypointText(points.size - 2)
    val div = doc.createElement("div")
    div.setAttribute("class", "connection")
    div.innerHTML =
      s"""
      <div class="time">${hhmm(dep)}<span class="delay ${if delayMin > 0 then "late" else ""}">${delayText(delayMin)}</span></div>
      <div class="path">
        <div class="line">${esc(if lines.nonEmpty then lines else "Connection")}</div>
        <div class="meta">${esc(changesText(changes))} · arrives ${hhmm(arr)}${waypointInfo}</div>
        <div class="legs">${segmentSummary(journeys, points)}</div>
      </div>
      <div class="duration">${duration} min</div>
      """
    div

  private def loadJson(url: String): Future[js.Dynamic] =
    val controller = new dom.AbortController()
    val timeout = dom.window.setTimeout(() => controller.abort(), requestTimeoutMs())
    val init = js.Dynamic.literal("signal" -> controller.signal).asInstanceOf[dom.RequestInit]
    dom.fetch(url, init).toFuture.flatMap { r =>
      dom.window.clearTimeout(timeout)
      if r.ok then r.text().toFuture.map(t => js.JSON.parse(t))
      else scala.concurrent.Future.failed(RuntimeException(s"HTTP ${r.status.toInt}"))
    }.recoverWith { case e =>
      dom.window.clearTimeout(timeout)
      scala.concurrent.Future.failed(e)
    }

  private def query(values: js.Dictionary[Any]): String =
    values.map { case (k, v) => s"${enc(k)}=${enc(v.toString)}" }.mkString("&")

  private def date(x: js.Any): js.Date = new js.Date(x.asInstanceOf[String])
  private def hhmm(d: js.Date): String = f"${d.getHours().toInt}%02d:${d.getMinutes().toInt}%02d"
  private def clock(): String = hhmm(new js.Date())
  private def minutesBetween(a: js.Any, b: js.Any): Int = ((date(b).getTime() - date(a).getTime()) / 60000).round.toInt
  private def changesText(n: Int) = if n == 0 then "direct" else s"$n change${if n == 1 then "" else "s"}"
  private def delayText(min: Int) = if min == 0 then "on time" else if min > 0 then s"+$min min" else s"$min min"
  private def waypointText(count: Int): String = if count <= 0 then "" else s" · $count waypoint${if count == 1 then "" else "s"}"
  private def segmentSummary(journeys: Seq[js.Dynamic], points: Seq[RoutePoint]): String =
    journeys.zipWithIndex.map { case (j, idx) =>
      val legs = j.legs.asInstanceOf[js.Array[js.Dynamic]].toSeq
      val dep = date(legs.head.departure)
      val arr = date(legs.last.arrival)
      val legLines = legs.flatMap(l => optObj(l.line).flatMap(line => optStr(line.name))).distinct.mkString(" · ")
      val delay = delayMinutes(legs.head, "departure")
      val delayClass = if delay > 0 then " late" else ""
      s"<div class=\"leg\"><span>${esc(points(idx).name)} → ${esc(points(idx + 1).name)}</span><strong>${hhmm(dep)}–${hhmm(arr)}</strong><em>${esc(if legLines.nonEmpty then legLines else "walk/transfer")}</em><small class=\"delay$delayClass\">${delayText(delay)}</small></div>"
    }.mkString

  private def journeysFrom(data: js.Dynamic): js.Array[js.Dynamic] =
    data.journeys.asInstanceOf[js.UndefOr[js.Array[js.Dynamic]]].toOption.getOrElse(js.Array())

  private def lastLegTime(j: js.Dynamic, field: String): Option[String] =
    val legs = j.legs.asInstanceOf[js.Array[js.Dynamic]].toSeq
    if legs.isEmpty then None else optStr(legs.last.selectDynamic(field))

  private def delayMinutes(leg: js.Dynamic, prefix: String): Int =
    val field = if prefix == "arrival" then leg.arrivalDelay else leg.departureDelay
    optNum(field).map(_ / 60).orElse(optNum(leg.delay).map(_ / 60)).getOrElse(0.0).round.toInt
  private def enc(s: String): String = js.URIUtils.encodeURIComponent(s)
  private def esc(s: String): String =
    val e = doc.createElement("span"); e.textContent = s; e.innerHTML
  private def message(cls: String, text: String): Element =
    val div = doc.createElement("div"); div.setAttribute("class", cls); div.textContent = text; div
  private def optStr(x: js.Any): Option[String] = if js.isUndefined(x) || x == null then None else Some(x.asInstanceOf[String])
  private def optNum(x: js.Any): Option[Double] = if js.isUndefined(x) || x == null then None else Some(x.asInstanceOf[Double])
  private def optObj(x: js.Any): Option[js.Dynamic] = if js.isUndefined(x) || x == null then None else Some(x.asInstanceOf[js.Dynamic])
  private def optArray(x: js.Any): Option[js.Array[js.Dynamic]] = if js.isUndefined(x) || x == null then None else Some(x.asInstanceOf[js.Array[js.Dynamic]])
