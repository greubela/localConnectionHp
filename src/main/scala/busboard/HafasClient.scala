package busboard

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*
import org.scalajs.dom

/** Thin client for the public VBB transport.rest API. */
object HafasClient:
  private val apiBase = "https://v6.vbb.transport.rest"

  def locations(query: String): Future[js.Array[js.Dynamic]] =
    get("locations", Seq(
      "query" -> query,
      "results" -> "1",
      "stops" -> "true",
      "poi" -> "false",
      "addresses" -> "false"
    )).map(_.asInstanceOf[js.Array[js.Dynamic]])

  def journeys(from: String, to: String, results: Int, departure: Option[String], products: Map[String, Boolean]): Future[js.Dynamic] =
    val parameters = Seq(
      "from" -> from,
      "to" -> to,
      "results" -> results.toString,
      "stopovers" -> "true",
      "remarks" -> "true",
      "language" -> "de"
    ) ++ departure.map("departure" -> _) ++ products.map((name, enabled) => name -> enabled.toString)
    get("journeys", parameters).map(_.asInstanceOf[js.Dynamic])

  def station(id: String): Future[js.Dynamic] =
    get(s"stops/${encode(id)}", Seq.empty).map(_.asInstanceOf[js.Dynamic])

  private def get(path: String, parameters: Iterable[(String, String)]): Future[js.Any] =
    val query = parameters.map((name, value) => s"${encode(name)}=${encode(value)}").mkString("&")
    dom.fetch(s"$apiBase/$path?$query").toFuture.flatMap { response =>
      if response.ok then response.json().toFuture
      else Future.failed(new RuntimeException(s"VBB API request failed (${response.status} ${response.statusText})"))
    }

  private def encode(value: String): String = js.URIUtils.encodeURIComponent(value)
