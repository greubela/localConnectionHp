package busboard.model.info

case class Station
(
  evaId: Int,
  stationName: String,
  displayName: Option[String]
) {
  def displayString: String = displayName.getOrElse(stationName)

  def storageString: String = evaId + ""
  
}

object Station {

  def fromEvaId(int: Int) = // look at local cache first, then resolve with hafas
  
  def fromName(name: String) = ??? // look first at the loaded cache, only after that resolve with hafas
  
  def loadCache(): Map[Int, Station] = ??? // load from file /config/stationcache.json
  
  def storeCache(cache: Map[Int, Station]) = ??? // store to file /config/stationcache.json
  
  val stationCache: Map[Int, Station] = Map(

  )

  val displayNames: Map[Station, String] = Map(

  )


}
