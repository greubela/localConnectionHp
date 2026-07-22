package busboard

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("hafas-client", "createClient")
private object CreateClient extends js.Object:
  def apply(profile: js.Any, userAgent: String): js.Dynamic = js.native

@js.native
@JSImport("hafas-client/p/bvg/index.js", "profile")
private object BvgProfile extends js.Object

/** Thin Scala.js facade around hafas-client, using the VBB/BVG profile for Berlin routes. */
object HafasClient:
  // The profile's legacy URL redirects without a CORS header. Point directly at
  // the redirect target so browsers can complete the request.
  private val profile = js.Object.assign(
    js.Dynamic.literal(),
    BvgProfile,
    js.Dynamic.literal(endpoint = "https://bvg.hafas.cloud/apps/gate")
  )
  private val client = CreateClient(profile, "local-connection-homepage")

  def locations(query: String): Future[js.Array[js.Dynamic]] =
    client.locations(query, js.Dynamic.literal(results = 1, stops = true, poi = false, addresses = false))
      .asInstanceOf[js.Promise[js.Array[js.Dynamic]]].toFuture

  def journeys(from: String, to: String, results: Int, departure: Option[String], products: Map[String, Boolean]): Future[js.Dynamic] =
    val options = js.Dynamic.literal(results = results, stopovers = true, remarks = true, language = "de")
    departure.foreach(value => options.updateDynamic("departure")(value))
    if products.nonEmpty then
      options.updateDynamic("products")(js.Dictionary(products.toSeq*))
    client.journeys(from, to, options).asInstanceOf[js.Promise[js.Dynamic]].toFuture
