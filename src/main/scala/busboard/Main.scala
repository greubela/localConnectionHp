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

  def main(args: Array[String]): Unit =
    loadJson("routes.json").foreach { cfg =>
      config = cfg
      routes = cfg.routes.asInstanceOf[js.Array[js.Dynamic]].toSeq
      activeIds = routes.map(_.id.asInstanceOf[String]).toSet
      drawControls()
      refresh()
      timer = dom.window.setInterval(() => refresh(), refreshMs())
    }

  private def refreshMs(): Int =
    val seconds = optNum(config.refreshSeconds).getOrElse(60.0)
    (seconds * 1000).toInt.max(15_000)

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
    val selected = routes.filter(r => activeIds(r.id.asInstanceOf[String]))
    status.textContent = "Updating…"
    cards.innerHTML = ""
    if selected.isEmpty then
      cards.appendChild(message("empty", "Select at least one route."))
      status.textContent = "No route selected"
    else
      selected.foreach { r => cards.appendChild(routeShell(r)) }
      selected.foreach { r => loadRoute(r) }
      status.textContent = s"Updated ${clock()}"

  private def loadRoute(route: js.Dynamic): Unit =
    val id = route.id.asInstanceOf[String]
    val result = for
      from <- stopId(route.from)
      to <- stopId(route.to)
      journeys <- loadJson(journeyUrl(route, from, to))
    yield journeys

    result.foreach { data =>
      val container = doc.getElementById(s"body-$id")
      if container != null then
        container.innerHTML = ""
        val jsJourneys = data.journeys.asInstanceOf[js.UndefOr[js.Array[js.Dynamic]]].toOption.getOrElse(js.Array())
        if jsJourneys.isEmpty then container.appendChild(message("empty", "No connection found right now."))
        else jsJourneys.foreach(j => container.appendChild(connectionRow(j)))
    }
    result.recover { case e =>
      val container = doc.getElementById(s"body-$id")
      if container != null then
        container.innerHTML = ""
        container.appendChild(message("error", s"Could not load this route: ${e.getMessage}"))
    }

  private def journeyUrl(route: js.Dynamic, from: String, to: String): String =
    val base = config.apiBase.asInstanceOf[String]
    val results = optNum(route.results).getOrElse(4.0).toInt
    val params = js.Dictionary[Any](
      "from" -> from,
      "to" -> to,
      "results" -> results,
      "language" -> "de",
      "stopovers" -> false,
      "remarks" -> false,
      "pretty" -> false
    )
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
          <p class="route-subtitle">${esc(route.from.name.asInstanceOf[String])} → ${esc(route.to.name.asInstanceOf[String])}</p>
        </div>
        <div class="badge">VBB</div>
      </div>
      <div id="body-$id"><div class="empty">Loading…</div></div>
      """
    div

  private def connectionRow(j: js.Dynamic): Element =
    val legs = j.legs.asInstanceOf[js.Array[js.Dynamic]].toSeq
    val first = legs.head
    val dep = date(first.departure)
    val planned = optStr(first.plannedDeparture).map(s => date(s))
    val delayMin = optNum(first.departureDelay).map(_ / 60).orElse(optNum(first.delay).map(_ / 60)).getOrElse(0.0).round.toInt
    val lines = legs.flatMap(l => optObj(l.line).flatMap(line => optStr(line.name))).distinct.mkString(" · ")
    val changes = (legs.size - 1).max(0)
    val duration = minutesBetween(j.departure, j.arrival)
    val div = doc.createElement("div")
    div.setAttribute("class", "connection")
    div.innerHTML =
      s"""
      <div class="time">${hhmm(dep)}<span class="delay ${if delayMin > 0 then "late" else ""}">${delayText(delayMin, planned)}</span></div>
      <div class="path"><div class="line">${esc(if lines.nonEmpty then lines else "Connection")}</div><div class="meta">${changesText(changes)} · arrives ${hhmm(date(j.arrival))}</div></div>
      <div class="duration">${duration} min</div>
      """
    div

  private def loadJson(url: String): Future[js.Dynamic] =
    dom.fetch(url).toFuture.flatMap { r =>
      if r.ok then r.text().toFuture.map(t => js.JSON.parse(t))
      else scala.concurrent.Future.failed(RuntimeException(s"HTTP ${r.status.toInt}"))
    }

  private def query(values: js.Dictionary[Any]): String =
    values.map { case (k, v) => s"${enc(k)}=${enc(v.toString)}" }.mkString("&")

  private def date(x: js.Any): js.Date = new js.Date(x.asInstanceOf[String])
  private def hhmm(d: js.Date): String = f"${d.getHours().toInt}%02d:${d.getMinutes().toInt}%02d"
  private def clock(): String = hhmm(new js.Date())
  private def minutesBetween(a: js.Any, b: js.Any): Int = ((date(b).getTime() - date(a).getTime()) / 60000).round.toInt
  private def changesText(n: Int) = if n == 0 then "direct" else s"$n change${if n == 1 then "" else "s"}"
  private def delayText(min: Int, planned: Option[js.Date]) = if min == 0 then "on time" else s"+$min min"
  private def enc(s: String): String = js.URIUtils.encodeURIComponent(s)
  private def esc(s: String): String =
    val e = doc.createElement("span"); e.textContent = s; e.innerHTML
  private def message(cls: String, text: String): Element =
    val div = doc.createElement("div"); div.setAttribute("class", cls); div.textContent = text; div
  private def optStr(x: js.Any): Option[String] = if js.isUndefined(x) || x == null then None else Some(x.asInstanceOf[String])
  private def optNum(x: js.Any): Option[Double] = if js.isUndefined(x) || x == null then None else Some(x.asInstanceOf[Double])
  private def optObj(x: js.Any): Option[js.Dynamic] = if js.isUndefined(x) || x == null then None else Some(x.asInstanceOf[js.Dynamic])
