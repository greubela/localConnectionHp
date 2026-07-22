package busboard

case class ConnectionOverview(
    config: ConnectionConfig,
    connections: Seq[Connection]
)
