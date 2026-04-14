[CmdletBinding()]
param(
    [string]$ContainerName = "gachi-test-postgres",
    [int]$HostPort = 55432,
    [string]$DbName = "gachi_test",
    [string]$DbUser = "gachi_test",
    [string]$DbPassword = "gachi_test"
)

$ErrorActionPreference = "Stop"
function Remove-TestContainer {
    param([string]$Name)
    $containerId = docker ps -aq -f "name=^${Name}$"
    if ($containerId) {
        docker rm -f $Name | Out-Null
    }
}

Write-Host "Starting PostgreSQL test container: $ContainerName"
Remove-TestContainer -Name $ContainerName

docker run -d `
    --name $ContainerName `
    -e "POSTGRES_DB=$DbName" `
    -e "POSTGRES_USER=$DbUser" `
    -e "POSTGRES_PASSWORD=$DbPassword" `
    -p "${HostPort}:5432" `
    postgres:16-alpine | Out-Null

$maxWaitSeconds = 60
$isReady = $false

for ($i = 0; $i -lt $maxWaitSeconds; $i++) {
    docker exec $ContainerName pg_isready -U $DbUser -d $DbName | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $isReady = $true
        break
    }
    Start-Sleep -Seconds 1
}

if (-not $isReady) {
    Remove-TestContainer -Name $ContainerName
    throw "PostgreSQL test container is not ready within $maxWaitSeconds seconds."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location $repoRoot

$previousDbUrl = $env:DB_URL
$previousDbUsername = $env:DB_USERNAME
$previousDbPassword = $env:DB_PASSWORD
$previousGradleUserHome = $env:GRADLE_USER_HOME

try {
    $env:DB_URL = "jdbc:postgresql://localhost:$HostPort/$DbName"
    $env:DB_USERNAME = $DbUser
    $env:DB_PASSWORD = $DbPassword
    $env:GRADLE_USER_HOME = Join-Path $repoRoot ".gradle-user-home"

    Write-Host "Running tests against $($env:DB_URL)"
    .\gradlew.bat --no-daemon test
}
finally {
    if ($null -ne $previousDbUrl) {
        $env:DB_URL = $previousDbUrl
    }
    else {
        Remove-Item Env:DB_URL -ErrorAction SilentlyContinue
    }

    if ($null -ne $previousDbUsername) {
        $env:DB_USERNAME = $previousDbUsername
    }
    else {
        Remove-Item Env:DB_USERNAME -ErrorAction SilentlyContinue
    }

    if ($null -ne $previousDbPassword) {
        $env:DB_PASSWORD = $previousDbPassword
    }
    else {
        Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    }

    if ($null -ne $previousGradleUserHome) {
        $env:GRADLE_USER_HOME = $previousGradleUserHome
    }
    else {
        Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue
    }

    Pop-Location
    Remove-TestContainer -Name $ContainerName
}
