# Перерисовать иконки предметов мода в стиле Conquest Reforged (32x32).
# Запуск из корня репозитория: powershell -File tools/icon_painter/render.ps1
# Иконки пишутся прямо в src/main/resources/assets/halgventmod/textures/item/<папка>/ (и mob_effect, block).
#   SculptIcons.cs (+ Sculpt.cs) — все вещи: 3D-сцены, просвеченные лучами, с контуром;
#   Vessels.cs — посуда и сырец; Icons.cs (+ Painter.cs) — стадии грядок лука и чеснока.
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = ''
foreach ($f in 'Painter.cs', 'Icons.cs', 'Vessels.cs', 'Sculpt.cs', 'SculptIcons.cs') { $src += (Get-Content "$dir\$f" -Raw) + "`n" }
$usings = "using System;`nusing System.IO;`nusing System.Collections.Generic;`nusing System.Drawing;`nusing System.Drawing.Imaging;`nusing F = System.Func<double, double, double>;`n"
$src = $usings + ($src -replace '(?m)^using [^\r\n]*;\r?\n', '')
Add-Type -TypeDefinition $src -ReferencedAssemblies System.Drawing
$root = Join-Path (Resolve-Path "$dir\..\..") 'src\main\resources\assets\halgventmod\textures\item'
[Icons]::Run($root)
[Vessels]::Run($root)
[SculptIcons]::Run($root)
