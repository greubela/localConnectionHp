# Bus Homepage

Small Scala.js/SBT homepage for live VBB/HAFAS-based connection previews.

## Run locally

```bash
sbt "Compile / fastOptJS / webpack"
python3 -m http.server 8080
```

Open <http://localhost:8080>.

## Configure routes

Routes are read from `config/config.json`. Each route is a sequence of station
names, display names, or EVA IDs separated by `->`:

```json
{
  "routeStrings": [
    "Berlin Hbf -> Würzburg Hbf",
    "Arbeit -> U Rudow -> 900260519"
  ],
  "refreshSeconds": 180,
  "requestTimeoutSeconds": 25
}
```

Known stations belong in `config/stationcache.json`. A cache entry contains an
`evaId`, the official `stationName`, and optionally a convenient `displayName`.
Both names can be used in a route. The app uses the EVA ID from the cache and
only asks the VBB API to resolve stations that are not cached.

## Notes

- Uses Laminar and the public VBB transport.rest API.
- Loads every leg of a multi-stop route in order, using the preceding arrival
  as the departure constraint for the next leg.
- The refresh interval is controlled by `refreshSeconds` in the configuration.
- HAFAS domain integrations implement `HafasProvider`; provider request settings
  and supported transportation modes are represented by typed model values.
