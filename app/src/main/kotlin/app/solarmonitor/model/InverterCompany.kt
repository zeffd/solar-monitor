package app.solarmonitor.model

/** The remote service behind an account. Persisted with the session. */
enum class InverterCompany(val label: String) {
    KSOLARE("KSolare"),
    POLYCAB("Polycab"),
}
