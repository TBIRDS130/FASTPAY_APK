# FastPay APK Zip Creator - Small Size, All Important Files
# Creates a compressed zip of APK directory with only essential files
# Usage: .\zip-apk.ps1 [buildType]

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("debug", "release", "all")]
    [string]$BuildType = "all",
    
    [Parameter(Mandatory=$false)]
    [string]$OutputPath = "",
    
    [Parameter(Mandatory=$false)]
    [switch]$IncludeMapping,
    
    [Parameter(Mandatory=$false)]
    [switch]$ShowDetails
)

# Colors
function Write-ColorOutput {
    param([string]$Message, [string]$Color = "White")
    Write-Host $Message -ForegroundColor $Color
}

Write-ColorOutput "=========================================" "Cyan"
Write-ColorOutput "FastPay APK Zip Creator" "Cyan"
Write-ColorOutput "=========================================" "Cyan"
Write-ColorOutput ""

# Get project root
$projectRoot = $PSScriptRoot
$apkBasePath = Join-Path $projectRoot "app\build\outputs\apk"

# Check if APK directory exists
if (-not (Test-Path $apkBasePath)) {
    Write-ColorOutput "Error: APK directory not found: $apkBasePath" "Red"
    Write-ColorOutput "Please build the APK first using: .\build-apk.ps1" "Yellow"
    exit 1
}

# Find all APK files
$apkFiles = Get-ChildItem -Path $apkBasePath -Filter "*.apk" -Recurse -ErrorAction SilentlyContinue

if (-not $apkFiles) {
    Write-ColorOutput "Error: No APK files found in $apkBasePath" "Red"
    Write-ColorOutput "Please build the APK first." "Yellow"
    exit 1
}

Write-ColorOutput "Found APK files:" "Green"
$apkFiles | ForEach-Object {
    $sizeMB = [math]::Round($_.Length / 1MB, 2)
    Write-ColorOutput "  - $($_.Name) ($sizeMB MB) - $($_.DirectoryName)" "White"
}
Write-ColorOutput ""

# Determine which build types to include
$buildTypesToInclude = @()
if ($BuildType -eq "all") {
    $buildTypesToInclude = @("debug", "release")
} else {
    $buildTypesToInclude = @($BuildType)
}

# Create output filename
if (-not $OutputPath) {
    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $outputName = "FastPay_APK_$timestamp.zip"
    $OutputPath = Join-Path $projectRoot $outputName
} else {
    if (-not $OutputPath.EndsWith(".zip")) {
        $OutputPath += ".zip"
    }
}

# Create temporary directory for files to zip
$tempDir = Join-Path $env:TEMP "fastpay_apk_zip_$(Get-Date -Format 'yyyyMMddHHmmss')"
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

Write-ColorOutput "Collecting files to zip..." "Yellow"

$totalSize = 0
$fileCount = 0

# Collect APK files and metadata
foreach ($buildType in $buildTypesToInclude) {
    $buildTypePath = Join-Path $apkBasePath $buildType
    
    if (Test-Path $buildTypePath) {
        # Copy APK files
        $apks = Get-ChildItem -Path $buildTypePath -Filter "*.apk" -ErrorAction SilentlyContinue
        foreach ($apk in $apks) {
            $destPath = Join-Path $tempDir "$buildType\$($apk.Name)"
            $destDir = Split-Path $destPath -Parent
            New-Item -ItemType Directory -Path $destDir -Force | Out-Null
            Copy-Item $apk.FullName $destPath -Force
            $totalSize += $apk.Length
            $fileCount++
            
            if ($ShowDetails) {
                Write-ColorOutput "  ✓ $($apk.Name)" "Gray"
            }
        }
        
        # Copy metadata files
        $metadataFiles = @("output-metadata.json", "*.json")
        foreach ($pattern in $metadataFiles) {
            $files = Get-ChildItem -Path $buildTypePath -Filter $pattern -ErrorAction SilentlyContinue
            foreach ($file in $files) {
                $destPath = Join-Path $tempDir "$buildType\$($file.Name)"
                Copy-Item $file.FullName $destPath -Force
                $totalSize += $file.Length
                $fileCount++
                
                if ($ShowDetails) {
                    Write-ColorOutput "  ✓ $($file.Name)" "Gray"
                }
            }
        }
        
        # Copy mapping files if requested and available (for release builds)
        if ($IncludeMapping) {
            $mappingFiles = Get-ChildItem -Path $buildTypePath -Filter "mapping.txt" -ErrorAction SilentlyContinue
            foreach ($mapping in $mappingFiles) {
                $destPath = Join-Path $tempDir "$buildType\$($mapping.Name)"
                Copy-Item $mapping.FullName $destPath -Force
                $totalSize += $mapping.Length
                $fileCount++
                
                if ($ShowDetails) {
                    Write-ColorOutput "  ✓ $($mapping.Name)" "Gray"
                }
            }
        }
    }
}

if ($fileCount -eq 0) {
    Write-ColorOutput "Error: No files found to zip" "Red"
    Remove-Item $tempDir -Recurse -Force -ErrorAction SilentlyContinue
    exit 1
}

$totalSizeMB = [math]::Round($totalSize / 1MB, 2)
Write-ColorOutput ""
Write-ColorOutput "Files to zip: $fileCount files ($totalSizeMB MB)" "Green"
Write-ColorOutput ""

# Create zip file
Write-ColorOutput "Creating zip file with compression..." "Yellow"

try {
    # Remove existing zip if present
    if (Test-Path $OutputPath) {
        Remove-Item $OutputPath -Force
    }
    
    # Use .NET compression for better control
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $compressionLevel = [System.IO.Compression.CompressionLevel]::Optimal
    
    [System.IO.Compression.ZipFile]::CreateFromDirectory($tempDir, $OutputPath, $compressionLevel, $false)
    
    # Get zip file size
    $zipInfo = Get-Item $OutputPath
    $zipSizeMB = [math]::Round($zipInfo.Length / 1MB, 2)
    $compressionRatio = [math]::Round((1 - ($zipInfo.Length / $totalSize)) * 100, 1)
    
    Write-ColorOutput ""
    Write-ColorOutput "=========================================" "Green"
    Write-ColorOutput "✓ Zip file created successfully!" "Green"
    Write-ColorOutput "=========================================" "Green"
    Write-ColorOutput ""
    Write-ColorOutput "Location: $OutputPath" "Cyan"
    Write-ColorOutput "Original Size: $totalSizeMB MB" "White"
    Write-ColorOutput "Compressed Size: $zipSizeMB MB" "White"
    Write-ColorOutput "Compression: $compressionRatio% smaller" "Green"
    Write-ColorOutput "Files: $fileCount" "White"
    Write-ColorOutput ""
    
    # Ask to open folder
    $openFolder = Read-Host "Open folder containing zip file? (Y/N)"
    if ($openFolder -eq "Y" -or $openFolder -eq "y") {
        $zipDir = Split-Path $OutputPath -Parent
        Invoke-Item $zipDir
    }
    
} catch {
    Write-ColorOutput "Error creating zip file: $($_.Exception.Message)" "Red"
    exit 1
} finally {
    # Cleanup temp directory
    Remove-Item $tempDir -Recurse -Force -ErrorAction SilentlyContinue
}

Write-ColorOutput ""
Write-ColorOutput "Done! ✓" "Green"
