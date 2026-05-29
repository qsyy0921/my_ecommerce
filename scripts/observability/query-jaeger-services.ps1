param(
    [string]$JaegerUrl = "http://127.0.0.1:16686"
)

$ErrorActionPreference = "Stop"

$response = Invoke-RestMethod -Uri "$JaegerUrl/api/services" -TimeoutSec 5
$response.data
