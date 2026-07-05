# Bus Homepage

Small Scala.js/SBT homepage for live VBB/HAFAS-based connection previews.

## Run locally

```bash
sbt fastLinkJS
python3 -m http.server 8080
```

Open <http://localhost:8080>.

## Configure routes

Edit `routes.json`. Routes may use stop IDs or names. Names are resolved via `/locations` at runtime; IDs are faster and more stable.

```json
{
  "id": "rudow",
  "title": "Magnusstr. → U Rudow",
  "from": { "name": "Magnusstr. (Berlin)" },
  "to": { "id": "900174001", "name": "U Rudow (Berlin)" },
  "results": 4
}
```

If a route resolves to the wrong stop, query `https://v6.vbb.transport.rest/locations?query=...` once, copy the stop `id`, and add it to `routes.json`.

## Notes

- Uses `https://v6.vbb.transport.rest`, a REST wrapper around VBB/HAFAS data.
- Refreshes every 60 seconds by default; adjust `refreshSeconds`.
- The code is intentionally compact and script-like. API access is concentrated in `loadJson`, `stopId`, and `journeyUrl`.
