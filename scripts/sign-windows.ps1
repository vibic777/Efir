# ============================================================================
#  Sign Efir Windows installers (MSI/EXE).
# ----------------------------------------------------------------------------
#  By default creates a self-signed code-signing certificate in
#  CurrentUser\My (if absent) and signs the artifacts.
#
#  For production pass your own certificate:
#    -PfxPath path\to\cert.pfx -PfxPassword <pass>
#  or use signtool with a certificate from the store.
#
#  Params:
#    -CertificateName  subject (CN) of the self-signed certificate.
#    -PfxPath/-PfxPassword  existing PFX certificate (optional).
#    -ArtifactsDir     folder with MSI/EXE (default Compose build output).
# ============================================================================
param(
    [string]$CertificateName = "Efir Local Code Signing",
    [string]$PfxPath = "",
    [string]$PfxPassword = "",
    [string]$ArtifactsDir = "$PSScriptRoot\..\desktopApp\build\installer"
)

$ErrorActionPreference = "Stop"

function Get-SigningCertificate {
    if ($PfxPath -ne "") {
        Write-Host "Loading certificate from $PfxPath"
        return New-Object System.Security.Cryptography.X509Certificates.X509Certificate2(
            $PfxPath, $PfxPassword,
            [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::Exportable)
    }

    $existing = Get-ChildItem Cert:\CurrentUser\My |
    Where-Object { $_.Subject -eq "CN=$CertificateName" } |
    Select-Object -First 1
    if ($existing) {
        Write-Host "Using existing certificate: $($existing.Thumbprint)"
        return $existing
    }

    Write-Host "Creating self-signed certificate: $CertificateName"
    return New-SelfSignedCertificate `
        -Type CodeSigningCert `
        -Subject "CN=$CertificateName" `
        -CertStoreLocation Cert:\CurrentUser\My `
        -KeyUsage DigitalSignature `
        -KeyAlgorithm RSA `
        -KeyLength 2048 `
        -NotAfter (Get-Date).AddYears(5)
}

function Find-SignTool {
    $cmd = Get-Command signtool.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $kits = "C:\Program Files (x86)\Windows Kits\10\bin"
    if (Test-Path $kits) {
        return Get-ChildItem -Path $kits -Recurse -Filter signtool.exe -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match "x64" } |
        Select-Object -First 1 -ExpandProperty FullName
    }
    return $null
}

$cert = Get-SigningCertificate
$signtool = Find-SignTool
if ($signtool) {
    Write-Host "signtool: $signtool"
}
else {
    Write-Host "signtool: not found - using PowerShell (EXE only; MSI needs signtool)"
}

# Sign only the installers (msi/ and exe/ subfolders), not the unpacked
# app-image binary (app\...\Efir.exe), which is locked/in-use.
$targets = @()
$msiDir = Join-Path $ArtifactsDir "msi"
$exeDir = Join-Path $ArtifactsDir "exe"
if (Test-Path $msiDir) {
    $targets += Get-ChildItem -Path $msiDir -Filter *.msi -File -ErrorAction SilentlyContinue
}
if (Test-Path $exeDir) {
    $targets += Get-ChildItem -Path $exeDir -Filter *.exe -File -ErrorAction SilentlyContinue
}
if ($targets.Count -eq 0) {
    Write-Host "No artifacts found in $ArtifactsDir. Run build-release.cmd first."
    exit 1
}

$timestamp = "http://timestamp.digicert.com"
$failed = 0
foreach ($file in $targets) {
    Write-Host "Signing: $($file.FullName)"
    if ($signtool) {
        & $signtool sign /fd SHA256 /sha1 $cert.Thumbprint /tr $timestamp /td SHA256 $file.FullName
        if ($LASTEXITCODE -ne 0) { $failed++ }
    }
    else {
        # PowerShell signing supports EXE/DLL (MSI requires signtool).
        if ($file.Extension -ieq ".exe") {
            $result = Set-AuthenticodeSignature -FilePath $file.FullName -Certificate $cert -TimestampServer $timestamp
            Write-Host "  Status: $($result.Status)"
            if ($result.Status -ne "Valid") { $failed++ }
        }
        else {
            Write-Host "  Skipped: MSI signing requires signtool (Windows SDK)."
        }
    }
}

if ($failed -gt 0) {
    Write-Host "Signing finished with errors: $failed"
    exit 1
}
Write-Host "Signing completed successfully."
