package busboard.model.hafas

/** Provider-specific values used when constructing HAFAS requests. */
case class ConfigProvider(
  apiBaseUrl: String,
  transportationModes: Set[TransportationMode] = TransportationMode.values.toSet
)
