# Bus Homepage

Small Scala.js/SBT homepage for live VBB/HAFAS-based connection previews.

## Run locally

```bash
sbt "Compile / fastOptJS / webpack"
python3 -m http.server 8080
```

Open <http://localhost:8080>.

## Configure routes

Edit `routes.json`. Routes may use stop IDs or names. Names are resolved through `hafas-client` at runtime; IDs are faster and more stable.

```json
{
  "id": "rudow",
  "title": "Magnusstr. → U Rudow",
  "from": { "name": "Magnusstr. (Berlin)" },
  "to": { "id": "900174001", "name": "U Rudow (Berlin)" },
  "results": 4
}
```


Routes can force a precise multi-stop path by adding `waypoints` (or `via`). The app loads each leg in order, using the previous leg arrival as the next leg departure, and displays several complete arrival options with live delay information for every leg.

```json
{
  "id": "via-rudow",
  "title": "Magnusstr. → Rudow → Schwimmhalle",
  "from": { "name": "Magnusstr. (Berlin)", "id": "900194501" },
  "waypoints": [
    { "name": "U Rudow (Berlin)", "id": "900083201" }
  ],
  "to": { "name": "Schwimmhalle (Schönefeld)", "id": "900260519" },
  "results": 4
}
```

Stop IDs are optional. If omitted, the public VBB transport.rest API resolves the station name; adding an ID makes a configuration unambiguous.

## Notes

- Uses Laminar and the public VBB transport.rest API for the configured Berlin-area routes. Calling the REST API directly avoids shipping a provider profile and its private authentication data to the browser.
- Refreshes active routes every 60 seconds.
- VBB access is isolated in `HafasClient.scala`; the Laminar application maps its results into typed connection models before rendering.
