$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# Raster export of the companion original vector artwork. No borrowed game assets.
$bitmap = [Drawing.Bitmap]::new(400, 400)
$graphics = [Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit

function Fill-RoundedRect([float]$x, [float]$y, [float]$width, [float]$height, [float]$radius, [string]$color, [string]$outline = '') {
    $shape = [Drawing.Drawing2D.GraphicsPath]::new()
    $diameter = $radius * 2
    $shape.AddArc($x, $y, $diameter, $diameter, 180, 90)
    $shape.AddArc($x + $width - $diameter, $y, $diameter, $diameter, 270, 90)
    $shape.AddArc($x + $width - $diameter, $y + $height - $diameter, $diameter, $diameter, 0, 90)
    $shape.AddArc($x, $y + $height - $diameter, $diameter, $diameter, 90, 90)
    $shape.CloseFigure()
    $brush = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($color))
    $graphics.FillPath($brush, $shape)
    $brush.Dispose()
    if ($outline) {
        $outlinePen = [Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml($outline), 4)
        $graphics.DrawPath($outlinePen, $shape)
        $outlinePen.Dispose()
    }
    $shape.Dispose()
}

try {
    $graphics.Clear([Drawing.ColorTranslator]::FromHtml('#0e1726'))
    Fill-RoundedRect 32 38 336 250 24 '#1b3044' '#35526c'
    Fill-RoundedRect 52 58 296 210 12 '#10212f'
    $roadPen = [Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#35526c'), 9)
    $roadPen.StartCap = $roadPen.EndCap = [Drawing.Drawing2D.LineCap]::Round
    foreach ($row in @(106, 164, 222)) { $graphics.DrawLine($roadPen, 68, $row, 328, $row) }
    foreach ($column in @(100, 174, 274)) { $graphics.DrawLine($roadPen, $column, 78, $column, 248) }
    $roadPen.Dispose()
    $routePen = [Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#44e5c0'), 15)
    $routePen.StartCap = $routePen.EndCap = [Drawing.Drawing2D.LineCap]::Round
    $routePen.LineJoin = [Drawing.Drawing2D.LineJoin]::Round
    $routePoints = [Drawing.Point[]]@([Drawing.Point]::new(88,222),[Drawing.Point]::new(146,222),[Drawing.Point]::new(146,164),[Drawing.Point]::new(250,164),[Drawing.Point]::new(250,104))
    $graphics.DrawLines($routePen, $routePoints)
    $routePen.Dispose()
    $pin = [Drawing.Drawing2D.GraphicsPath]::new()
    $pin.AddBezier(250,114,240,100,224,85,224,71)
    $pin.AddArc(224,45,52,52,180,180)
    $pin.AddBezier(276,71,276,85,260,100,250,114)
    $pin.CloseFigure()
    $yellow = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#ffcd63'))
    $graphics.FillPath($yellow, $pin)
    $yellow.Dispose()
    $pin.Dispose()
    $dark = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#10212f'))
    $graphics.FillEllipse($dark,241,62,18,18)
    $dark.Dispose()
    $car = [Drawing.Point[]]@([Drawing.Point]::new(106,222),[Drawing.Point]::new(76,207),[Drawing.Point]::new(81,222),[Drawing.Point]::new(76,237))
    $graphics.FillPolygon([Drawing.Brushes]::White, $car)
    $font = [Drawing.Font]::new('Segoe UI', 34, [Drawing.FontStyle]::Bold, [Drawing.GraphicsUnit]::Point)
    $format = [Drawing.StringFormat]::new()
    $format.Alignment = [Drawing.StringAlignment]::Center
    $format.LineAlignment = [Drawing.StringAlignment]::Center
    $graphics.DrawString('MTS GPS',$font,[Drawing.Brushes]::White,[Drawing.RectangleF]::new(12,302,376,66),$format)
    $font.Dispose()
    $format.Dispose()
    $bitmap.Save((Join-Path $PSScriptRoot 'icon.png'),[Drawing.Imaging.ImageFormat]::Png)
} finally {
    $graphics.Dispose()
    $bitmap.Dispose()
}
