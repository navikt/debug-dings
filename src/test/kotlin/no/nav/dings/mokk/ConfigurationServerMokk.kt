package no.nav.dings.mokk

data class ConfigurationServerMokk(
    var issuer: String,
    var token_endpoint: String,
    var jwks_uri: String,
    var token_endpoint_auth_methods_supported: List<String>,
    var grant_types_supported: List<String>
)
