package busboard.model

import busboard.model.{Connection, ConnectionConfig}

case class ConnectionOverview(
    config: ConnectionConfig,
    connections: Seq[Connection]
)
