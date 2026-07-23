package busboard.model.info

case class Station(evaId: Int, stationName: String, displayName: Option[String]):
  def displayString: String = displayName.getOrElse(stationName)
  def storageString: String = evaId.toString

object Station:
  private var stations = Map.empty[Int, Station]
  def setCache(values: Iterable[Station]): Unit = stations = values.map(s => s.evaId -> s).toMap
  def cache(station: Station): Station = { stations += station.evaId -> station; station }
  def fromEvaId(evaId: Int): Option[Station] = stations.get(evaId)
  def fromName(name: String): Option[Station] = stations.values.find(s =>
    s.stationName.equalsIgnoreCase(name) || s.displayName.exists(_.equalsIgnoreCase(name)))
