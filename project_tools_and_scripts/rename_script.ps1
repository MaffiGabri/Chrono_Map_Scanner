$ErrorActionPreference = "Stop"

$oldPkg = "com.example.traccianei"
$newPkg = "com.example.chronomapscanner"
$oldPath = "com\example\traccianei"
$newPath = "com\example\chronomapscanner"

Write-Host "Moving directories..."
foreach ($dir in @("main", "test", "androidTest")) {
    $srcDir = "app\src\$dir\java\$oldPath"
    $dstParent = "app\src\$dir\java\com\example\chronomapscanner"
    
    if (Test-Path $srcDir) {
        Write-Host "Processing $srcDir"
        New-Item -ItemType Directory -Force -Path $dstParent | Out-Null
        
        # Move all contents from traccianei to chronomapscanner
        Get-ChildItem -Path $srcDir | Move-Item -Destination $dstParent -Force
        
        # Remove the old empty directory
        Remove-Item -Path $srcDir -Force
    }
}

Write-Host "Renaming Application class file..."
$oldAppFile = "app\src\main\java\com\example\chronomapscanner\TracciaNeiApplication.kt"
$newAppFile = "app\src\main\java\com\example\chronomapscanner\ChronoMapScannerApplication.kt"
if (Test-Path $oldAppFile) {
    Rename-Item -Path $oldAppFile -NewName "ChronoMapScannerApplication.kt"
}

Write-Host "Replacing text in files..."
$filesToProcess = Get-ChildItem -Path . -Recurse -Include *.kt, *.xml, *.kts, *.md, *.properties | Where-Object { $_.FullName -notmatch "\\\.git\\" -and $_.FullName -notmatch "\\\.gradle\\" -and $_.FullName -notmatch "\\build\\" }

foreach ($file in $filesToProcess) {
    $content = [System.IO.File]::ReadAllText($file.FullName)
    $originalContent = $content
    
    if ($content -match "(?i)traccianei") {
        # 1. Package names and imports
        $content = $content -replace "com\.example\.traccianei", "com.example.chronomapscanner"
        
        # 2. Application class names
        $content = $content -replace "TracciaNeiApplication", "ChronoMapScannerApplication"
        
        # 3. String app name (e.g. in strings.xml, markdown docs)
        # Note: we need to be careful with TracciaNei -> Chrono Map Scanner
        # because some might be in comments or docs. We'll replace exact match "TracciaNei" with "Chrono Map Scanner"
        $content = $content -replace "TracciaNei", "Chrono Map Scanner"
        
        # 4. Any remaining lowercase traccianei that might have been missed
        $content = $content -replace "traccianei", "chronomapscanner"
        
        if ($content -cne $originalContent) {
            Write-Host "Updating file: $($file.Name)"
            [System.IO.File]::WriteAllText($file.FullName, $content, [System.Text.Encoding]::UTF8)
        }
    }
}

Write-Host "Done!"
