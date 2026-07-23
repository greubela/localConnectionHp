package busboard.model

import busboard.model.info.{Route, Station}

case class Config
(
  routes: List[Route],
  updateEverySeconds: Integer = 180,
  requestTimeoutSeconds: Integer = 10
) {

}


object Config {

  def loadConfig(): Config = ??? // load from file /config/config.json

  def storeConfig(config: Config) = ??? // store to file /config/config.json

}