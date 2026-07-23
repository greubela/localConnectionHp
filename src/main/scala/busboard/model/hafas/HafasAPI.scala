package busboard.model.hafas

import busboard.model.connections.Connection
import busboard.model.info.Station

/** Small domain-facing facade that keeps provider details out of callers. */
case class HafasAPI(provider: HafasProvider):
  def resolveEva(evaId: Int): Station = provider.resolveEva(evaId)
  def resolveLeg(from: Station, to: Station): Connection = provider.resolveLeg(from, to)
