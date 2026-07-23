package busboard.model

case class Station
(
  evaId: Int,
  stationName: String,
  displayName: Option[String]
) {
  def displayString: String = displayName.getOrElse(stationName)
}

object Station {

  val stationCache: Map[Int, Station] = Map(

  )

  val displayNames: Map[Station, String] = Map(

  )
  
  

}
