package busboard.model

import busboard.model.connections.*
import busboard.model.info.Route

case class ConnectionOverview
(
  route: Route,
  connections: Seq[Connection]
) {

}
