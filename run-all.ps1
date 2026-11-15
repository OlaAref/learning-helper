# Starts the two microservices and the gateway, each in its own window.
# Run from a terminal that has NOTION_TOKEN set; the new windows inherit it.
# Build first:  cd ui; npm install; npm run build; cd ..; mvn -q package
$root = $PSScriptRoot
$version = '0.1.0-SNAPSHOT'

function Start-Service-Window($title, $dir, $jar) {
    $path = Join-Path $root $dir
    if (-not (Test-Path (Join-Path $path "target\$jar"))) {
        Write-Host "Missing $dir\target\$jar - run 'mvn -q package' first." -ForegroundColor Red
        exit 1
    }
    # each service runs from its own folder: db-lab-service finds ..\..\content relative to it
    Start-Process powershell -WorkingDirectory $path -ArgumentList '-NoExit', '-Command',
        "`$host.UI.RawUI.WindowTitle = '$title'; java -jar target\$jar"
}

if (-not $env:NOTION_TOKEN) {
    Write-Host 'NOTION_TOKEN is not set: Notion Sync will start but show "Not connected".' -ForegroundColor Yellow
}

Start-Service-Window 'notion-service :8081' 'services\notion-service' "notion-service-$version.jar"
Start-Service-Window 'db-lab-service :8082' 'services\db-lab-service' "db-lab-service-$version.jar"
Start-Service-Window 'gateway :8080'        'gateway'                 "gateway-$version.jar"

Write-Host 'Open http://localhost:8080 once the three windows say "Started".'
