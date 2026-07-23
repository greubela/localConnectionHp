package busboard.model

import busboard.model.info.{Route, Station}

case class Config
(
  routes: Seq[Route],
  updateEverySeconds: Int = 180,
  requestTimeoutSeconds: Int = 10
)
