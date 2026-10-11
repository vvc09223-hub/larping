$ErrorActionPreference = 'Stop'

Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class NativeCred {
    [DllImport("advapi32", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern bool CredDelete(string target, int type, int reserved);
}
"@

$targets = @(
    'LegacyGeneric:target=git:https://github.com',
    'LegacyGeneric:target=GitHub - https://api.github.com/quantaum2-star'
)

foreach ($target in $targets) {
    if ([NativeCred]::CredDelete($target, 1, 0)) {
        Write-Host "Deleted: $target"
    } else {
        Write-Host "Not found or already deleted: $target"
    }
}

$eraseBlock = @"
protocol=https
host=github.com

protocol=https
host=api.github.com

"@

$eraseBlock | git credential reject 2>$null
$eraseBlock | git credential-manager erase 2>$null

Write-Host "Remaining GitHub credentials:"
cmdkey /list | Select-String -Pattern 'git|GitHub' -CaseSensitive:$false
