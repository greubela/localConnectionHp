package busboard.model.info


case class Route(
                  legs: Seq[Station],
                  title: Option[String]
                ) {

  def defaultTitle: String = legs.map(_.storageString).mkString(" -> ")

}

object Route {


  def parseFromString(): Unit = {


  }

}
