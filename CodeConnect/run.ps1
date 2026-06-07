$ErrorActionPreference = "Stop"

# Ensure we are in the project directory regardless of where the script is called from
Set-Location $PSScriptRoot

$jdkUrl = "https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_windows-x64_bin.zip"
$mvnUrl = "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip"

$jdkZip = "jdk.zip"
$mvnZip = "mvn.zip"
$jdkDir = "jdk-21.0.2"
$mvnDir = "apache-maven-3.9.6"

if (-not (Test-Path $jdkDir) -or -not (Test-Path $mvnDir)) {
    Write-Host "--------------------------------------------------------"
    Write-Host "Warning: First run will download ~200 MB of dependencies"
    Write-Host "(OpenJDK 21 + Maven) to compile and run the application."
    Write-Host "This may take 1-2 minutes depending on your connection."
    Write-Host "--------------------------------------------------------"
}

if (-not (Test-Path $jdkDir)) {
    Write-Host "Downloading OpenJDK 21 (this may take a minute)..."
    Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkZip
    Write-Host "Extracting OpenJDK 21..."
    Expand-Archive -Path $jdkZip -DestinationPath . -Force
    Remove-Item $jdkZip
}

if (-not (Test-Path $mvnDir)) {
    Write-Host "Downloading Maven 3.9.6..."
    Invoke-WebRequest -Uri $mvnUrl -OutFile $mvnZip
    Write-Host "Extracting Maven..."
    Expand-Archive -Path $mvnZip -DestinationPath . -Force
    Remove-Item $mvnZip
}

Write-Host "Setting up Environment..."
$env:JAVA_HOME = "$PWD\$jdkDir"
$env:PATH = "$PWD\$jdkDir\bin;$PWD\$mvnDir\bin;" + $env:PATH

Write-Host "Compiling and Launching the App..."
mvn clean compile javafx:run
