# Paper/Leaf 插件构建脚本（不依赖 Gradle/Maven，纯 javac + jar）
# 用法：
#   build.bat            -> 只编译打包，产物在 build\dist
#   build.bat -Deploy    -> 编译打包并复制到服务器 plugins 目录（默认 D:\leef\servers\leaf-server\plugins）
#   build.bat -Clean     -> 先清空 build 目录
param([switch]$Deploy, [switch]$Clean)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

# ---- 读 plugin.yml 里的 name / version ----
$ymlPath = Join-Path $root 'src\main\resources\plugin.yml'
$yml = Get-Content $ymlPath -Raw
$name = [regex]::Match($yml, '(?m)^name:\s*(\S+)').Groups[1].Value
$version = [regex]::Match($yml, '(?m)^version:\s*(\S+)').Groups[1].Value
if (-not $name -or -not $version) { throw '无法从 plugin.yml 读取 name/version' }

# ---- 找 JDK ----
$javaHome = $env:JAVA_HOME
if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome 'bin\javac.exe'))) { $javaHome = 'D:\pcl\java23' }
$javac = Join-Path $javaHome 'bin\javac.exe'
$jarTool = Join-Path $javaHome 'bin\jar.exe'
if (-not (Test-Path $javac)) { throw "找不到 javac：$javac" }

$buildDir = Join-Path $root 'build'
$classes = Join-Path $buildDir 'classes'
$dist = Join-Path $buildDir 'dist'
if ($Clean -and (Test-Path $buildDir)) { Remove-Item $buildDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classes, $dist | Out-Null
Get-ChildItem $classes -Recurse -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force

# ---- 服务器目录（可用环境变量 PAPER_SERVER_DIR 覆盖）----
$serverDir = $env:PAPER_SERVER_DIR
if (-not $serverDir) { $serverDir = 'D:\leef\servers\leaf-server' }
if (-not (Test-Path $serverDir)) {
    # 兼容旧位置
    $legacy = Join-Path (Split-Path -Parent $root) 'leaf-server'
    if (Test-Path $legacy) { $serverDir = $legacy }
}

# ---- 组装 classpath：lib\*.jar + 服务器 libraries（adventure / annotations 等）----
$cp = @()
$libDir = Join-Path $root 'lib'
if (Test-Path $libDir) { $cp += (Get-ChildItem $libDir -Filter *.jar).FullName }
$serverLibs = Join-Path $serverDir 'libraries'
if (Test-Path $serverLibs) { $cp += (Get-ChildItem $serverLibs -Recurse -Filter *.jar).FullName }
if ($cp.Count -eq 0) { throw 'classpath 为空：lib\ 下需要 leaf-api jar' }

$sources = @(Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object FullName)
if ($sources.Count -eq 0) { throw '没有找到 Java 源码' }

# ---- javac（用 argfile，路径全部转正斜杠，避免 Windows 反斜杠转义问题）----
$argfile = Join-Path $buildDir 'javac.args'
$lines = @('-encoding', 'UTF-8', '--release', '21', '-nowarn', '-g')
$lines += '-cp'
$lines += ('"' + (($cp | ForEach-Object { $_ -replace '\\', '/' }) -join ';') + '"')
$lines += '-d'
$lines += ('"' + ($classes -replace '\\', '/') + '"')
$lines += ($sources | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' })
Set-Content -Path $argfile -Value $lines -Encoding ascii

& $javac "@$argfile"
if ($LASTEXITCODE -ne 0) { throw "javac 编译失败，退出码 $LASTEXITCODE" }

# ---- 资源（plugin.yml 等）打进根目录 ----
Copy-Item (Join-Path $root 'src\main\resources\*') $classes -Recurse -Force

# ---- 打包 ----
$out = Join-Path $dist "$name-$version.jar"
if (Test-Path $out) { Remove-Item $out -Force }
& $jarTool cf $out -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar 打包失败，退出码 $LASTEXITCODE" }
Write-Host "OK -> $out"

if ($Deploy) {
    $plugins = Join-Path $serverDir 'plugins'
    New-Item -ItemType Directory -Force -Path $plugins | Out-Null
    Copy-Item $out $plugins -Force
    Write-Host "已部署 -> $plugins"
}
