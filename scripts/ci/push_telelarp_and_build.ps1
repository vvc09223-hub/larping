# Push to gamunkul671-stack/telelarp and trigger GitHub Actions IPA build.
#
# Token (one of):
#   $env:GITHUB_TOKEN = "ghp_..."
#   $env:GH_TOKEN = "ghp_..."
#   file:  .github-token.local  (repo root, never commit)
#   param: -GitHubToken "ghp_..."
#
# Usage:
#   cd C:\Users\twink\Desktop\Telegram-iOS-master
#   $env:GITHUB_TOKEN = "YOUR_PAT"
#   .\scripts\ci\push_telelarp_and_build.ps1

param(
    [string]$GitHubToken = "",
    [string]$Owner = "vvc09223-hub",
    [string]$RepoName = "larping",
    [switch]$SkipWorkflow
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $RepoRoot

if (-not $GitHubToken) {
    $GitHubToken = $env:GITHUB_TOKEN
}
if (-not $GitHubToken) {
    $GitHubToken = $env:GH_TOKEN
}
$tokenFile = Join-Path $RepoRoot ".github-token.local"
if (-not $GitHubToken -and (Test-Path $tokenFile)) {
    $GitHubToken = (Get-Content $tokenFile -Raw).Trim()
}

if (-not $GitHubToken) {
    Write-Error @"
GitHub token missing. Create a classic PAT with repo + workflow scopes, then:

  `$env:GITHUB_TOKEN = 'ghp_...'
  .\scripts\ci\push_telelarp_and_build.ps1

Or save the token (one line) to: $tokenFile
"@
}

$env:GH_TOKEN = $GitHubToken
$FullName = "$Owner/$RepoName"
$RemoteUrlClean = "https://github.com/$FullName.git"
$RemoteUrlAuth = "https://x-access-token:$GitHubToken@github.com/$FullName.git"
$RemoteName = "origin"

function Invoke-GitHubApi {
    param(
        [ValidateSet("GET", "POST", "PATCH")]
        [string]$Method,
        [string]$Uri,
        [object]$Body = $null
    )
    $headers = @{
        Authorization = "Bearer $GitHubToken"
        Accept        = "application/vnd.github+json"
        "User-Agent"  = "telelarp-push"
        "X-GitHub-Api-Version" = "2022-11-28"
    }
    $params = @{
        Method  = $Method
        Uri     = $Uri
        Headers = $headers
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Compress)
    }
    return Invoke-RestMethod @params
}

Write-Host "Checking repository $FullName ..."
$repoExists = $true
try {
    $null = Invoke-GitHubApi -Method GET -Uri "https://api.github.com/repos/$FullName"
} catch {
    $repoExists = $false
}

if (-not $repoExists) {
    Write-Host "Creating private repo $FullName ..."
    $createBody = @{
        name    = $RepoName
        private = $true
    }
    $created = $false
    try {
        $null = Invoke-GitHubApi -Method POST -Uri "https://api.github.com/orgs/$Owner/repos" -Body $createBody
        $created = $true
    } catch {
        Write-Host "Org create failed, trying user repo ..."
        try {
            $null = Invoke-GitHubApi -Method POST -Uri "https://api.github.com/user/repos" -Body $createBody
            $created = $true
        } catch {
            Write-Warning @"
Could not create the repo via API (fine-grained PAT often lacks "Administration").

1) Open: https://github.com/new?name=$RepoName&visibility=private
2) Create empty repo (no README / .gitignore).
3) Edit PAT: https://github.com/settings/personal-access-tokens
   - Repository access: include ""$RepoName""
   - Permissions: Contents (Read/Write), Actions (Read/Write), Workflows (Read/Write)

Then run this script again.
"@
        }
    }
    if ($created) {
        Start-Sleep -Seconds 2
    } else {
        try {
            $null = Invoke-GitHubApi -Method GET -Uri "https://api.github.com/repos/$FullName"
        } catch {
            Write-Error "Repository $FullName still missing. Create it on GitHub first."
        }
    }
}

git remote set-url $RemoteName $RemoteUrlAuth
Write-Host "Pushing main to $FullName (this can take several minutes) ..."
git push -u $RemoteName main --force-with-lease
git remote set-url $RemoteName $RemoteUrlClean

if (-not $SkipWorkflow) {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        Write-Warning "gh not installed; trigger CI manually on GitHub Actions -> CI -> Run workflow"
    } else {
        Write-Host "Starting workflow CI on main ..."
        gh workflow run "CI" --repo $FullName --ref main
        Start-Sleep -Seconds 4
        gh run list --repo $FullName --workflow=CI --limit 3
        Write-Host ""
        Write-Host "Download IPA when green:"
        Write-Host "  `$env:GH_TOKEN = `$env:GITHUB_TOKEN"
        Write-Host "  gh run download --repo $FullName --name Telegram-Larp-unsigned-ipa"
    }
}

Write-Host "Done."
