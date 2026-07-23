package busboard.model.info

case class Route(legs: Seq[Station], title: Option[String]):
  def defaultTitle: String = legs.map(_.storageString).mkString(" -> ")

object Route:
  def parts(value: String): Seq[String] = value.split("\\s*->\\s*").map(_.trim).filter(_.nonEmpty).toSeq
