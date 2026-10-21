# Перерисовать иконки кожевенного дела, готовки и пчеловодства в стиле Conquest Reforged (32x32).
# Запуск из корня репозитория: powershell -File tools/icon_painter/render.ps1
# Иконки пишутся прямо в src/main/resources/assets/halgventmod/textures/item/<папка>/.
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = (Get-Content "$dir\Painter.cs" -Raw) + "`n" + (Get-Content "$dir\Icons.cs" -Raw)
$usings = "using System;`nusing System.IO;`nusing System.Collections.Generic;`nusing System.Drawing;`nusing System.Drawing.Imaging;`nusing F = System.Func<double, double, double>;`n"
$src = $usings + ($src -replace '(?m)^using [^\r\n]*;\r?\n', '')
Add-Type -TypeDefinition $src -ReferencedAssemblies System.Drawing
$root = Join-Path (Resolve-Path "$dir\..\..") 'src\main\resources\assets\halgventmod\textures\item'
[Icons]::Run($root)
