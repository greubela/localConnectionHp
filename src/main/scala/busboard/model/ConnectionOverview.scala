package busboard.model

import busboard.model.connections.*

case class ConnectionOverview
(
  config: Config,
  connections: Seq[Connection]
) {

}
