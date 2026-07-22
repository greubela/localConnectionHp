package busboard

case class ConnectionConfig(
    id: String,
    title: String,
    stations: Seq[Station],
    results: Int = 4,
    products: Map[String, Boolean] = Map.empty
)
