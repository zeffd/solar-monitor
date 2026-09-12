# ShineMonitor API notes

What the app relies on, verified against the live service in September 2026
with a single-plant account. Nothing here is documented by the vendor; it was
read off the web portal's JavaScript and confirmed by calling the endpoints.

The portal at `shinemonitor.com` is a thin front end over this API.

## Base URL and nodes

- Default node: `https://web.shinemonitor.com/public/`
- Overseas node: `https://web1.shinemonitor.com/public/`
- The node list is public: `?sign=<sha1(salt+action)>&salt=<salt>&action=queryDomainListNotLogin`.
- The app tries the default node
  first; on login error 261 (user not found) it retries on the overseas node
  and remembers whichever succeeded.

## Requests

Every call is an HTTPS GET with no cookies and no custom headers. The response
is `application/json` with the envelope `{ "err": <int>, "desc": <string>, "dat": <any> }`.
`err == 0` means success. The server sets `Access-Control-Allow-Origin: *`.

`salt` is the current time in milliseconds. The "action string" is the exact
text appended to the URL, starting with `&action=`, with parameter values
already URL-encoded. The signature is computed over that exact text, so the
string that is signed and the string that is sent must be byte-identical.

**Login (no token yet)**

```
action = "&action=auth&usr=" + urlencode(username) + "&company-key=bnrl_frRFjEz8Mkn"
sign   = sha1hex(salt + sha1hex(password) + action)
GET    base + "?sign=" + sign + "&salt=" + salt + action
```

Success `dat`: `{ "token": string, "secret": string, "expire": 432000, "uid": int, "usr": string, "role": int }`.
`expire` is in seconds (5 days). The app stores `expiresAt = now + expire*1000`.

**Every other call**

```
sign = sha1hex(salt + secret + token + action)
GET  base + "?sign=" + sign + "&salt=" + salt + "&token=" + token + action
```

The `company-key` is a constant belonging to the ShineMonitor web portal and
is public in its JavaScript. It is not a user secret.

## Endpoints used

All actions take `&i18n=en_US` so field titles come back in English.

| Purpose | Action string (`&i18n=en_US` is appended to each) | `dat` shape |
|---|---|---|
| Plant list | `&action=webQueryPlants&page=0&pagesize=10` | `{ total, page, pagesize, plant: [Plant] }` |
| Device list | `&action=webQueryDeviceEs&page=0&pagesize=20` | `{ total, page, pagesize, device: [Device] }` |
| Inverter last report | `&action=queryDeviceLastData&pn=<pn>&devcode=<devcode>&devaddr=<devaddr>&sn=<sn>` | `[ { title, unit?, val } ]` (about 40 entries) |
| Power curve for a day | `&action=queryPlantActiveOuputPowerOneDay&plantid=<pid>&date=YYYY-MM-DD` | `{ outputPower: [ { ts, val } ] }` 288 points at 5-minute steps, `val` in kW |
| Energy per day of a month | `&action=queryPlantEnergyMonthPerDay&plantid=<pid>&date=YYYY-MM` | `{ perday: [ { ts, val } ] }` kWh, one entry per calendar day |
| Energy per month of a year | `&action=queryPlantEnergyYearPerMonth&plantid=<pid>&date=YYYY` | `{ permonth: [ { ts, val } ] }` kWh, 12 entries |
| Energy per year, lifetime | `&action=queryPlantEnergyTotalPerYear&plantid=<pid>` | `{ peryear: [ { ts, val } ] }` kWh |

Note the vendor's spelling `Ouput` in `queryPlantActiveOuputPowerOneDay`. It is
intentional and must not be corrected.

**Plant fields the app uses** (all numeric fields arrive as strings):
`pid`, `name`, `status`, `address.timezone` (offset in seconds, e.g. 19800),
`nominalPower` (kW), `outputPower` (kW, live), `energy` (kWh today),
`energyMonth`, `energyYear`, `energyTotal` (kWh).

**Device fields the app uses**: `pn`, `sn`, `devcode`, `devaddr`, `devalias`, `status`, `pid`.

**Inverter last report**: an ordered list of `{title, unit?, val}`. The
first entries include `Timestamp` (local time of the report, e.g.
`2026-09-08 15:17:11`), and later ones include `Output Power` (W),
`Energy today` (kWh), `energy_total` (kWh), `Inverter status`, PV string
voltages and currents, grid voltages, frequency and temperatures. The app
shows the list as returned and only special-cases `Timestamp` for the header.

## Error codes seen

| `err` | `desc` | Meaning for the app |
|---|---|---|
| 0 | ERR_NONE | success |
| 6 | ERR_FORMAT_ERROR | bad or missing parameter (programming error) |
| 8 | ERR_FORBIDDEN(can not found action: …) | unknown action (programming error) |
| 10 | ERR_NO_AUTH | token missing, invalid or expired: re-login once and retry |
| 16 | ERR_PASSWORD_VERIF_FAIL | wrong password |
| 261 | ERR_NOT_FOUND_USR | user not on this node: try the other node, else "user not found" |

Any other non-zero code is shown to the user as `desc` with the code in
parentheses.

## Quirks

- The server sometimes never answers a request. In testing, a small fraction of
  otherwise-working calls hung until the client timeout. The client uses hard
  timeouts, one retry, and issues calls sequentially.
- `queryDeviceDataOneDayPaging` (raw per-row device data for a day) never
  returned within 60 s. The app does not use it.
- `queryPlantCurrentData` returns ERR_FORMAT_ERROR for this plant. Not used.
- Device-level totals in `webQueryDeviceEs` (`outpower`, `energyToday`, …) are
  zero for this plant even while producing. Plant-level values are
  authoritative; device data is used only to obtain `pn/sn/devcode/devaddr`.

