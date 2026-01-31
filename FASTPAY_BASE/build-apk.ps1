# ========================================
# Smart APK Builder Script
# Easy, Fast, and Smart way to build Android APKs
# ========================================

param(
    [string]$Project = "",
    [string]$BuildType = "debug",
    [switch]$Clean = $false,
    [switch]$Release = $false,
    [switch]$Quick = $false,
    [switch]$List = $false,
    [switch]$AutoFixLock = $true,
    [bool]$NoPrompt = $true
)

# Colors for output
function Write-ColorOutput {
    param([string]$Message, [string]$Color = "White")
    Write-Host $Message -ForegroundColor $Color
}

function Write-Step {
    param([string]$Message)
    Write-ColorOutput "`n> $Message" "Cyan"
}

function Write-Success {
    param([string]$Message)
    Write-ColorOutput "[OK] $Message" "Green"
}

function Write-Error {
    param([string]$Message)
    Write-ColorOutput "[ERROR] $Message" "Red"
}

function Write-Warning {
    param([string]$Message)
    Write-ColorOutput "[WARN] $Message" "Yellow"
}

function Write-Info {
    param([string]$Message)
    Write-ColorOutput "[INFO] $Message" "Cyan"
}

# Detect Android projects
function Find-AndroidProjects {
    $projects = @()
    $rootPath = $PSScriptRoot

    # Check root directory
    if (Test-Path "$rootPath\gradlew.bat") {
        $projectName = Split-Path $rootPath -Leaf
        $projects += @{
            Name = $projectName
            Path = $rootPath
        }
    }

    # Check subdirectories
    Get-ChildItem -Path $rootPath -Directory | ForEach-Object {
        $gradlePath = "$($_.FullName)\gradlew.bat"
        if (Test-Path $gradlePath) {
            $projects += @{
                Name = $_.Name
                Path = $_.FullName
            }
        }
    }

    return $projects
}

# Setup Java environment
function Setup-JavaEnvironment {
    # Check if JAVA_HOME is already set
    if ($env:JAVA_HOME -and (Test-Path $env:JAVA_HOME)) {
        Write-Info "Java found at: $env:JAVA_HOME"
        return $true
    }

    # Common Java installation paths
    $javaPaths = @(
        "$env:ProgramFiles\Android\Android Studio\jbr",
        "$env:ProgramFiles\Android\Android Studio\jre",
        "${env:ProgramFiles(x86)}\Android\Android Studio\jbr",
        "${env:ProgramFiles(x86)}\Android\Android Studio\jre",
        "$env:ProgramFiles\Java\jdk-11",
        "$env:ProgramFiles\Java\jdk-17",
        "$env:ProgramFiles\Java\jdk-21",
        "$env:ProgramFiles\Java\jdk",
        "${env:ProgramFiles(x86)}\Java\jdk-11",
        "${env:ProgramFiles(x86)}\Java\jdk-17",
        "${env:ProgramFiles(x86)}\Java\jdk"
    )

    foreach ($path in $javaPaths) {
        if (Test-Path $path) {
            $env:JAVA_HOME = $path
            Write-Success "Auto-detected Java at: $path"
            return $true
        }
    }

    # Try to find Java in PATH
    try {
        $javaVersion = java -version 2>&1 | Select-Object -First 1
        if ($javaVersion -match "version") {
            Write-Info "Java found in PATH: $javaVersion"
            return $true
        }
    } catch {
        # Java not in PATH
    }

    Write-Warning "Java not automatically detected. Please set JAVA_HOME manually."
    Write-Info "You can set it with: `$env:JAVA_HOME = 'C:\path\to\java'"
    return $false
}

# Get APK path
function Get-ApkPath {
    param(
        [string]$ProjectPath,
        [string]$BuildType
    )

    $apkPath = "$ProjectPath\app\build\outputs\apk\$BuildType\app-$BuildType.apk"

    # Alternative path (some projects use different structure)
    if (-not (Test-Path $apkPath)) {
        $apkPath = "$ProjectPath\app\build\outputs\apk\$BuildType\*.apk"
        $apkFiles = Get-ChildItem -Path $apkPath -ErrorAction SilentlyContinue
        if ($apkFiles) {
            return $apkFiles[0].FullName
        }
    }

    return $apkPath
}

# Build APK
function Build-Apk {
    param(
        [string]$ProjectPath,
        [string]$BuildType,
        [bool]$CleanFirst,
        [bool]$QuickCheck
    )

    Push-Location $ProjectPath

    try {
        # Setup Java
        if (-not (Setup-JavaEnvironment)) {
            Write-Error "Java setup failed. Cannot proceed with build."
            return $false
        }

        # Proactive file lock cleanup (runs by default to prevent build failures)
        if ($AutoFixLock) {
            Resolve-LockedBuildArtifacts -Proactive $true
            Start-Sleep -Seconds 1
        }

        # Clean if requested
        if ($CleanFirst) {
            Write-Step "Cleaning project..."
            & .\gradlew.bat clean --no-daemon 2>&1 | Out-Null
            if ($LASTEXITCODE -ne 0) {
                Write-Warning "Clean command had issues, but continuing..."
            }
        }

        # Quick check mode
        if ($QuickCheck) {
            Write-Step "Running quick compilation check..."
            & .\gradlew.bat compileDebugKotlin --no-daemon 2>&1 | Tee-Object -Variable output

            if ($LASTEXITCODE -eq 0) {
                Write-Success "Quick check passed! No compilation errors."
                return $true
            } else {
                Write-Error "Compilation errors found!"
                return $false
            }
        }

        function Invoke-Build {
            Write-Step "Building $BuildType APK (this may take a few minutes)..."
            Write-Info "Please wait..."

            $startTime = Get-Date
            $buildOutput = @()

            # Use Start-Process with timeout to prevent hanging
            $processInfo = New-Object System.Diagnostics.ProcessStartInfo
            $processInfo.FileName = ".\gradlew.bat"
            $processInfo.Arguments = "assemble$($BuildType.Substring(0,1).ToUpper() + $BuildType.Substring(1)) --no-daemon"
            $processInfo.UseShellExecute = $false
            $processInfo.RedirectStandardOutput = $true
            $processInfo.RedirectStandardError = $true
            $processInfo.CreateNoWindow = $true
            $processInfo.WorkingDirectory = $ProjectPath

            $process = New-Object System.Diagnostics.Process
            $process.StartInfo = $processInfo

            # Capture output
            $outputBuilder = New-Object System.Text.StringBuilder
            $errorBuilder = New-Object System.Text.StringBuilder

            $outputEvent = Register-ObjectEvent -InputObject $process -EventName OutputDataReceived -Action {
                if ($EventArgs.Data) {
                    [void]$Event.MessageData.AppendLine($EventArgs.Data)
                    Write-Host $EventArgs.Data
                }
            } -MessageData $outputBuilder

            $errorEvent = Register-ObjectEvent -InputObject $process -EventName ErrorDataReceived -Action {
                if ($EventArgs.Data) {
                    [void]$Event.MessageData.AppendLine($EventArgs.Data)
                    Write-Host $EventArgs.Data -ForegroundColor Red
                }
            } -MessageData $errorBuilder

            try {
                $process.Start() | Out-Null
                $process.BeginOutputReadLine()
                $process.BeginErrorReadLine()

                # Wait with timeout (15 minutes max)
                $timeout = 900
                $process.WaitForExit($timeout * 1000)

                if (-not $process.HasExited) {
                    Write-Warning "Build process timed out after $timeout seconds. Terminating..."
                    $process.Kill()
                    $process.WaitForExit(5000)
                    return @{
                        ExitCode = -1
                        Output = "Build timed out"
                        Duration = $timeout
                    }
                }

                # Get remaining output
                Start-Sleep -Milliseconds 500
                $buildOutput = ($outputBuilder.ToString() + $errorBuilder.ToString()) -split "`n"

            } finally {
                Unregister-Event -SourceIdentifier $outputEvent.Name
                Unregister-Event -SourceIdentifier $errorEvent.Name
                if (-not $process.HasExited) {
                    $process.Kill()
                }
                $process.Dispose()
            }

            $endTime = Get-Date
            $duration = ($endTime - $startTime).TotalSeconds

            return @{
                ExitCode = $process.ExitCode
                Output = $buildOutput
                Duration = $duration
            }
        }

        function Resolve-LockedBuildArtifacts {
            param([bool]$Proactive = $false)

            if ($Proactive) {
                Write-Step "Proactively checking and cleaning locked build artifacts..."
            } else {
                Write-Warning "Detected locked build artifacts. Attempting to unlock..."
            }

            # Stop Gradle daemons first
            try {
                Write-Info "Stopping Gradle daemons..."
                & .\gradlew.bat --stop 2>&1 | Out-Null
                Start-Sleep -Seconds 1
            } catch {}

            # Kill Java processes that may lock build files
            try {
                Write-Info "Checking for Java processes that might lock files..."
                $javaProcs = Get-Process -Name "java","javaw" -ErrorAction SilentlyContinue
                if ($javaProcs) {
                    Write-Info "Found $($javaProcs.Count) Java process(es) - terminating..."
                    taskkill /F /IM java.exe /IM javaw.exe /T 2>$null | Out-Null
                    Start-Sleep -Seconds 1
                }
            } catch {}

            # Clean .gradle lock files
            $gradleDir = Join-Path $ProjectPath ".gradle"
            if (Test-Path $gradleDir) {
                try {
                    Write-Info "Cleaning Gradle lock files..."
                    Get-ChildItem -Path $gradleDir -Filter "*.lock" -Recurse -ErrorAction SilentlyContinue |
                        Remove-Item -Force -ErrorAction SilentlyContinue
                } catch {}
            }

            # Try to delete build directory using multiple methods
            $buildDir = Join-Path $ProjectPath "app\build"
            if (Test-Path $buildDir) {
                Write-Info "Cleaning build directory..."

                # Method 1: Standard deletion
                try {
                    Remove-Item -Recurse -Force $buildDir -ErrorAction Stop
                    Write-Success "Build directory cleaned successfully"
                    return
                } catch {
                    Write-Info "Standard deletion failed, trying advanced methods..."
                }

                # Method 2: Try to delete specific locked files first (common R.jar issue)
                $commonLockedFiles = @(
                    "$buildDir\intermediates\compile_and_runtime_not_namespaced_r_class_jar\debug\processDebugResources\R.jar",
                    "$buildDir\intermediates\compile_and_runtime_not_namespaced_r_class_jar\release\processReleaseResources\R.jar"
                )

                foreach ($lockedFile in $commonLockedFiles) {
                    if (Test-Path $lockedFile) {
                        try {
                            # Try renaming first (sometimes works when deletion doesn't)
                            $tempName = "$lockedFile.tmp_$(Get-Random)"
                            Rename-Item -Path $lockedFile -NewName (Split-Path $tempName -Leaf) -Force -ErrorAction Stop
                            Write-Info "Renamed locked file: $(Split-Path $lockedFile -Leaf)"
                            Start-Sleep -Milliseconds 500
                        } catch {
                            # Continue to next method
                        }
                    }
                }

                # Method 3: Robocopy trick (mirror empty directory to delete locked files)
                try {
                    Write-Info "Using robocopy method to force delete locked files..."
                    $emptyDir = Join-Path $env:TEMP "empty_build_dir_$(Get-Random)"
                    New-Item -ItemType Directory -Path $emptyDir -Force | Out-Null

                    try {
                        & robocopy $emptyDir $buildDir /MIR /NFL /NDL /NJH /NJS 2>&1 | Out-Null
                        Start-Sleep -Seconds 1

                        # Check if it worked
                        if (-not (Test-Path $buildDir)) {
                            Write-Success "Build directory cleaned using robocopy method"
                        } else {
                            Write-Warning "Some files may still be locked"
                        }
                    } finally {
                        Remove-Item -Path $emptyDir -Recurse -Force -ErrorAction SilentlyContinue
                    }
                } catch {
                    Write-Warning "Robocopy method failed: $_"
                }

                # Method 4: Final attempt with standard deletion
                Start-Sleep -Seconds 1
                try {
                    if (Test-Path $buildDir) {
                        Remove-Item -Recurse -Force $buildDir -ErrorAction SilentlyContinue
                    }
                } catch {
                    Write-Warning "Could not fully clean build directory. Some files may still be locked."
                    Write-Info "If build fails, try closing Android Studio/VS Code and File Explorer windows."
                }
            } else {
                if ($Proactive) {
                    Write-Info "No existing build directory to clean"
                }
            }
        }

        # Full build (first attempt)
        $buildResult = Invoke-Build
        $duration = $buildResult.Duration
        $buildOutput = $buildResult.Output
        $exitCode = $buildResult.ExitCode

        if ($exitCode -eq 0) {
            Write-Success "Build completed successfully in $([math]::Round($duration, 1)) seconds!"

            # Find APK
            $apkPath = Get-ApkPath -ProjectPath $ProjectPath -BuildType $BuildType

            if (Test-Path $apkPath) {
                $apkInfo = Get-Item $apkPath
                $apkSize = [math]::Round($apkInfo.Length / 1MB, 2)

                Write-ColorOutput "`nAPK Information:" "Cyan"
                Write-ColorOutput "   Location: $apkPath" "White"
                Write-ColorOutput "   Size: $apkSize MB" "White"
                Write-ColorOutput "   Build Type: $BuildType" "White"
                Write-ColorOutput "   Modified: $($apkInfo.LastWriteTime)" "White"

                # Ask to open folder
                Write-Host ""
                $openFolder = Read-Host "Open APK folder? (Y/N)"
                if ($openFolder -eq "Y" -or $openFolder -eq "y") {
                    $apkDir = Split-Path $apkPath -Parent
                    Invoke-Item $apkDir
                }

                return $true
            } else {
                Write-Warning "Build succeeded but APK not found at expected location: $apkPath"
                Write-Info "Searching for APK files..."
                $allApks = Get-ChildItem -Path "$ProjectPath\app\build\outputs\apk" -Filter "*.apk" -Recurse -ErrorAction SilentlyContinue
                if ($allApks) {
                    Write-ColorOutput "`nFound APK files:" "Cyan"
                    $allApks | ForEach-Object {
                        Write-ColorOutput "   $($_.FullName) ($([math]::Round($_.Length / 1MB, 2)) MB)" "White"
                    }
                }
                return $true
            }
        } else {
            # Auto-fix for locked files if enabled or detected
            $lockErrorDetected = $buildOutput -match "Couldn't delete|process cannot access|Unable to delete directory|R\.jar|The process cannot access the file"
            if ($lockErrorDetected -and $AutoFixLock) {
                Write-Warning "Build failed due to locked files. Auto-fix is enabled - retrying once..."
                Resolve-LockedBuildArtifacts -Proactive $false
                Start-Sleep -Seconds 2

                # Retry build once
                $buildResult = Invoke-Build
                $duration = $buildResult.Duration
                $buildOutput = $buildResult.Output
                $exitCode = $buildResult.ExitCode

                if ($exitCode -eq 0) {
                    Write-Success "Build completed successfully after auto-fix in $([math]::Round($duration, 1)) seconds!"

                    # Find APK
                    $apkPath = Get-ApkPath -ProjectPath $ProjectPath -BuildType $BuildType
                    if (Test-Path $apkPath) {
                        $apkInfo = Get-Item $apkPath
                        $apkSize = [math]::Round($apkInfo.Length / 1MB, 2)

                        Write-ColorOutput "`nAPK Information:" "Cyan"
                        Write-ColorOutput "   Location: $apkPath" "White"
                        Write-ColorOutput "   Size: $apkSize MB" "White"
                        Write-ColorOutput "   Build Type: $BuildType" "White"
                        Write-ColorOutput "   Modified: $($apkInfo.LastWriteTime)" "White"
                    }

                    return $true
                }
            }

            Write-Error "Build failed!"
            Write-Info "Check the output above for error details."

            # Show common errors and solutions
            if ($buildOutput -match "FAILURE|error|Error") {
                Write-ColorOutput "`nCommon solutions:" "Yellow"
                Write-Info "1. Run with -Clean flag: .\build-apk.ps1 -Clean"
                Write-Info "2. Check if all dependencies are synced"
                Write-Info "3. Verify JAVA_HOME is set correctly"
                Write-Info "4. Try: .\gradlew.bat clean build"
                if ($lockErrorDetected) {
                    Write-Info "5. Locked files detected - try: .\build-apk.ps1 -AutoFixLock"
                    Write-Info "6. Close Android Studio/VS Code and rerun"
                }
            }

            return $false
        }
    } finally {
        Pop-Location
    }
}

# Main script logic
function Main {
    Write-ColorOutput "`n========================================" "Cyan"
    Write-ColorOutput "     Smart APK Builder Script" "Cyan"
    Write-ColorOutput "========================================" "Cyan"

    # Override BuildType if Release flag is set
    if ($Release) {
        $BuildType = "release"
    }

    # Normalize BuildType
    $BuildType = $BuildType.ToLower()
    if ($BuildType -notin @("debug", "release")) {
        Write-Error "Invalid build type: $BuildType (must be 'debug' or 'release')"
        exit 1
    }

    # Find projects
    $projects = Find-AndroidProjects

    if ($projects.Count -eq 0) {
        Write-Error "No Android projects found in current directory or subdirectories!"
        Write-Info "Make sure you're running this script from the workspace root."
        exit 1
    }

    # List projects mode
    if ($List -or $projects.Count -gt 1 -and $Project -eq "") {
        Write-ColorOutput "`nAvailable Android Projects:" "Cyan"
        for ($i = 0; $i -lt $projects.Count; $i++) {
            Write-ColorOutput "   [$($i + 1)] $($projects[$i].Name)" "White"
            Write-ColorOutput "       Path: $($projects[$i].Path)" "DarkGray"
        }
        Write-Host ""

        if ($Project -eq "") {
            if ($projects.Count -eq 1) {
                $selectedProject = $projects[0]
                Write-Info "Auto-selecting only project: $($selectedProject.Name)"
            } else {
                $choice = Read-Host "Select project number (1-$($projects.Count))"
                try {
                    $index = [int]$choice - 1
                    if ($index -ge 0 -and $index -lt $projects.Count) {
                        $selectedProject = $projects[$index]
                    } else {
                        Write-Error "Invalid selection!"
                        exit 1
                    }
                } catch {
                    Write-Error "Invalid input!"
                    exit 1
                }
            }
        }
    } else {
        # Single project or specified project
        if ($Project -ne "") {
            $selectedProject = $projects | Where-Object { $_.Name -eq $Project }
            if (-not $selectedProject) {
                Write-Error "Project '$Project' not found!"
                Write-Info "Available projects: $($projects.Name -join ', ')"
                exit 1
            }
        } else {
            $selectedProject = $projects[0]
        }
    }

    if (-not $selectedProject) {
        Write-Error "No project selected!"
        exit 1
    }

    # Display build information
    Write-ColorOutput "`nBuild Configuration:" "Cyan"
    Write-ColorOutput "   Project: $($selectedProject.Name)" "White"
    Write-ColorOutput "   Path: $($selectedProject.Path)" "White"
    Write-ColorOutput "   Build Type: $BuildType" "White"
    Write-ColorOutput "   Clean First: $Clean" "White"
    Write-ColorOutput "   Quick Check: $Quick" "White"
    Write-ColorOutput "   Auto-Fix Locks: $AutoFixLock (proactive)" "White"

    # Confirm build unless prompts are disabled
    if (-not $Quick -and -not $NoPrompt) {
        Write-Host ""
        $confirm = Read-Host "Start build? (Y/N)"
        if ($confirm -ne "Y" -and $confirm -ne "y") {
            Write-Info "Build cancelled."
            exit 0
        }
    }

    # Execute build
    $success = Build-Apk -ProjectPath $selectedProject.Path -BuildType $BuildType -CleanFirst $Clean -QuickCheck $Quick

    if ($success) {
        Write-ColorOutput "`nAll done!" "Green"
        exit 0
    } else {
        exit 1
    }
}

# Run main function
Main
