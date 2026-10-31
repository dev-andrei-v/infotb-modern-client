# STB API

InfoTB reads everything from the same public API that STB's web app (info.stb.ro) uses. It is undocumented; this page records what the app relies on. The client lives in `shared/src/commonMain/kotlin/ro/infotb/modern/data/api`.

```
Base URL: https://info.stb.ro/api/web/v2-6
```

## Authentication

1. On first launch the app generates a random `App-Id` (UUID) and keeps it.
2. `GET /proxy/user/auth` with the headers below plus `App-key`, which returns JSON with `data.userInfo`.
3. Every later request sends that token as `User-Info`.
4. On HTTP **401** or **412** the token is dropped, the app re-authenticates once and retries. Concurrent requests share one re-authentication.

Headers sent with every request (`StbApi.getHeaders`):

| Header | Value |
|---|---|
| `App-Id` | the stored UUID |
| `User-Info` | token from the auth call (omitted on the auth call itself) |
| `Source` | `ro.radcom.smartcity.web` |
| `OS-Type` | `Web` |
| `Lang` | `ro` |
| `App-Version` | `0.0.0` |
| `Accept` | `application/json, text/plain, */*` |

The `App-key` value is in `Constants.APP_KEY`.

## Endpoints

| Path | Format | Decoded into | Used for |
|---|---|---|---|
| `/lines?lang=ro` | protobuf | `List<Line>` | Lines tab |
| `/lines/{lineId}?lang=ro` | protobuf | `LineDetail` | line with its stops |
| `/lines/{lineId}/direction/{0\|1}?lang=ro` | protobuf | `LineDetail` | one direction: stops, route polyline, direction names |
| `/lines/stop?stop_id={id}[&selected_line_id={lineId}&direction={0\|1}]` | protobuf | `StopInfo` | arrivals at a stop; with a line selected it also carries that line's timetable, route segment and vehicles |
| `/lines/stops/{stopId}` | protobuf | `StopDetails` | lines at a stop grouped by transport type |
| `/lines/v2/{lineId}/vehicles/{0\|1}?lang=ro` | protobuf | `List<VehiclePosition>` | live vehicles on a line |
| `/lines/v2/home/stops/{stopId}` | protobuf | `List<Stop>` | stop coordinates (maps, nearest-first sorting) |
| `/notifications?limit=…&offset=…` | JSON | `List<Announcement>` | STB service notices |

Directions are `0` (tur, outbound) and `1` (retur, return).

## Protobuf

Most responses are binary Protocol Buffers without a published schema. Instead of generated code, a small `ProtobufReader` walks the wire format and `ProtoDecoder` maps field numbers to model properties:

| Wire type | Meaning |
|---|---|
| 0 | varint (ints, booleans) |
| 1 | 64-bit (doubles, e.g. coordinates) |
| 2 | length-delimited (strings, nested messages) |
| 5 | 32-bit |

Some useful field numbers, for poking at responses by hand:

- **Line** (in `/lines`): `1` id, `2` name, `3` type (`BUS`, `TRAM`, `CABLE_CAR` = trolleybus, `SUBWAY`), `6` colour.
- **Stop** (in `/lines/{id}`, field `12`): `1` id, `4` name.

Arrival times are seconds from now; a negative value means the line has no upcoming arrival. Route shapes are Google encoded polylines (`decodePolyline`).

## Quirks

- **Metro colours.** Every metro line comes back with the same near-black colour (`#1D1D1B`). The app replaces it with the Metrorex colours in `LineColors.kt`.
- **Metro stops.** Metro stations appear in line data, but `/lines/stop` doesn't serve their ids, so their stop page has no arrivals.
- **Stop coordinates.** `/lines/v2/home/stops/{id}` has been seen returning HTTP 400, in which case the stop map and distance sorting have nothing to show.
- **Notice timestamps** (`created_at`) are in milliseconds.

## Refresh rates

| Data | How often |
|---|---|
| Stop arrivals (dashboard, stop page) | every 10 s while on screen |
| Vehicle positions | every 10 s on line and map screens |
| Tracked arrival (Dynamic Island / notification) | every 30 s |
| Notices | at most every 15 min, plus a background check |
| Stop info cache | answers younger than 5 s are reused |
