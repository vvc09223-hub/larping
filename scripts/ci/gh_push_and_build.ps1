# GitHub CLI: login as repo owner, push main, trigger CI (nomigren07-blip/larping).
#
#   cd C:\Users\twink\Desktop\Telegram-iOS-master
#   .\scripts\ci\gh_push_and_build.ps1
#
# Only login (browser), no push:
#   .\scripts\ci\gh_push_and_build.ps1 -LoginOnly

param(
    [switch]$LoginOnly,
    [switch]$SkipPush
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $RepoRoot

$RepoOwner = "nomigren07-blip"
$RepoName = "larping"
$FullRepo = "$RepoOwner/$RepoName"

function Invoke-Gh {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & gh @Args
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prev
    if ($code -ne 0) {
        throw "gh exited with $code (args: gh $($Args -join ' '))"
    }
}

function Test-GhAuth {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & gh auth status *> $null
    $ok = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $prev
    return $ok
}

function Get-GhActiveLogin {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $login = gh api user -q .login 2>$null
    $ErrorActionPreference = $prev
    if ($LASTEXITCODE -ne 0) { return $null }
    return $login
}

function Invoke-GhBrowserLogin {
    Write-Host ""
    Write-Host "=== GitHub login (browser) ===" -ForegroundColor Cyan
    Write-Host "Use account: $RepoOwner (owner of $FullRepo)" -ForegroundColor Cyan
    Write-Host "Scopes: repo, workflow" -ForegroundColor Cyan
    Write-Host ""
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    gh auth login -h github.com -p https -w -s repo,workflow,read:org
    $ok = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $prev
    if (-not $ok) {
        Write-Error "gh auth login failed. Run: gh auth login -h github.com -p https -w -s repo,workflow"
    }
}

function Ensure-GhLoggedInAsOwner {
    Remove-Item Env:GH_TOKEN -ErrorAction SilentlyContinue
    Remove-Item Env:GITHUB_TOKEN -ErrorAction SilentlyContinue

    if (-not (Test-GhAuth)) {
        Invoke-GhBrowserLogin
    }

    $active = Get-GhActiveLogin
    if ($active -eq $RepoOwner) {
        Write-Host "GitHub: logged in as $active" -ForegroundColor Green
        return
    }

    if ($active) {
        Write-Host "Current gh account: $active (need: $RepoOwner)" -ForegroundColor Yellow
        $prev = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        gh auth switch -u $RepoOwner 2>$null | Out-Null
        $ErrorActionPreference = $prev
        if ($LASTEXITCODE -eq 0) {
            $active = Get-GhActiveLogin
            if ($active -eq $RepoOwner) {
                Write-Host "Switched to $RepoOwner" -ForegroundColor Green
                return
            }
        }
    }

    Write-Host ""
    Write-Host "Log in with $RepoOwner in the browser (old session will be replaced)." -ForegroundColor Yellow
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    gh auth logout -h github.com 2>$null | Out-Null
    $ErrorActionPreference = $prev
    Invoke-GhBrowserLogin

    $active = Get-GhActiveLogin
    if ($active -ne $RepoOwner) {
        Write-Error @"
Still logged in as '$active', not '$RepoOwner'.
1) gh auth logout
2) gh auth login -h github.com -p https -w -s repo,workflow
3) In browser choose $RepoOwner
4) Run this script again
"@
    }
    Write-Host "GitHub: logged in as $active" -ForegroundColor Green
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Error "Install GitHub CLI: winget install GitHub.cli"
}

Ensure-GhLoggedInAsOwner
Invoke-Gh auth setup-git

if ($LoginOnly) {
    Write-Host "Login OK. Run without -LoginOnly to push and start CI." -ForegroundColor Green
    exit 0
}

Write-Host "Remote origin -> https://github.com/$FullRepo.git"
git remote set-url origin "https://github.com/$FullRepo.git"

Write-Host "Checking repo..."
$prev = $ErrorActionPreference
$ErrorActionPreference = "Continue"
gh repo view $FullRepo
$repoOk = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = $prev
if (-not $repoOk) {
    Write-Error "Repo $FullRepo not found. Create https://github.com/new?name=larping while logged in as $RepoOwner"
}

if (-not $SkipPush) {
    Write-Host "Pushing main (may take a few minutes)..."
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    git push -u origin main
    $pushOk = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $prev
    if (-not $pushOk) {
        Write-Error "git push failed. Check: gh auth status (must be $RepoOwner)"
    }
}

Write-Host "Starting CI (IPA build)..."
Invoke-Gh workflow run "CI" --repo $FullRepo --ref main
Start-Sleep -Seconds 4
Invoke-Gh run list --repo $FullRepo --workflow=CI --limit 3
Write-Host ""
Write-Host "When build is green, download IPA:"
Write-Host "  gh run download --repo $FullRepo --name Telegram-Larp-unsigned-ipa"
Write-Host "Actions: https://github.com/$FullRepo/actions"
