# Polycab PV Solar Monitoring API notes

The Polycab mobile client is a React Native application. Its configuration
selects this API base URL:

```
https://pv.polycabmonitoring.com/dist/server/api/CodeIgniter/index.php/version3/v2/Inverterapi
```

The application uses `POST` requests with form-encoded fields. Login is
`/UserLogin` with `username` and `password`; a successful response includes a
token. Follow-up requests send that token in `Authorization` and include the
app-generated `sign` field. `PolycabHttp` owns that protocol detail; callers
only provide ordinary request fields.

The adapter in `PolycabClient` maps these Polycab routes to Solar Monitor's
existing data model:

| Solar Monitor view | Polycab route |
| --- | --- |
| Sign in | `/UserLogin` |
| Plant overview | `/monitoringOverView` |
| Inverter list | `/InverterList` |
| Inverter parameters | `/getInverterInfo_v1` |
| Daily power curve | `/getDailyEnergyChart` |
| Monthly energy | `/getMonthlyAndAnnualEnergyChart` |
| Annual/lifetime energy | `/getYearAndAnnualEnergyChart` |

The vendor does not publish a stable public schema. The adapter therefore
accepts common response container names (`data`, `result`, `rows`, and `list`)
and leaves the existing UI unchanged. Capture responses from an account you
own before changing the fallback field mappings.
