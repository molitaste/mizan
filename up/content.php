<?php
// Public site content as JavaScript (edited in admin.php). No secrets are stored in content.json.
header('Content-Type: application/javascript; charset=utf-8');
header('Cache-Control: no-cache');
$f = __DIR__ . '/data/content.json';
$c = is_file($f) ? @file_get_contents($f) : false;
if ($c === false || json_decode($c) === null) { echo "window.CONTENT=null;"; exit; }
echo 'window.CONTENT=' . $c . ';';
