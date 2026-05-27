$dir = 'd:\Workspace\OffPayApp\docs\screenshots'

$map = @{
    'screenshot-01.jpeg' = 'payment-running.jpeg'
    'screenshot-02.jpeg' = 'pay-form.jpeg'
    'screenshot-03.jpeg' = 'history-detail.jpeg'
    'screenshot-04.jpeg' = 'faq.jpeg'
    'screenshot-05.jpeg' = 'settings.jpeg'
    'screenshot-06.jpeg' = 'history-list.jpeg'
    'screenshot-07.jpeg' = 'payment-success.jpeg'
    'screenshot-08.jpeg' = 'balance-form.jpeg'
    'screenshot-09.jpeg' = 'pay-form-alt.jpeg'
    'screenshot-10.jpeg' = 'balance-result.jpeg'
    'screenshot-11.jpeg' = 'payment-failed.jpeg'
    'screenshot-12.jpeg' = 'payment-success-alt.jpeg'
    'screenshot-13.jpeg' = 'qr-scanner.jpeg'
}

foreach ($key in $map.Keys) {
    $src = Join-Path $dir $key
    $dst = Join-Path $dir $map[$key]
    if (Test-Path $src) {
        Move-Item -Path $src -Destination $dst -Force
        Write-Host ($map[$key] + '  <-  ' + $key)
    }
}
