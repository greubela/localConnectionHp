package busboard.model.hafas

import busboard.model.connections.Connection
import busboard.model.info.Station

/** Boundary implemented by a concrete synchronous HAFAS integration. */
trait HafasProvider:
  def configProvider: ConfigProvider
  def resolveEva(evaId: Int): Station
  def resolveLeg(from: Station, to: Station): Connection
