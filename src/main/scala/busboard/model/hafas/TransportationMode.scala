package busboard.model.hafas

/** Product categories understood by a HAFAS provider. */
enum TransportationMode(val parameterName: String):
  case Bus extends TransportationMode("bus")
  case Tram extends TransportationMode("tram")
  case Subway extends TransportationMode("subway")
  case SuburbanTrain extends TransportationMode("suburban")
  case RegionalTrain extends TransportationMode("regional")
  case LongDistanceTrain extends TransportationMode("national")
  case Ferry extends TransportationMode("ferry")
  case Taxi extends TransportationMode("taxi")
