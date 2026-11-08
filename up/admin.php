<?php
/* Moli Taste — Control Panel (PHP 7.2+)
   Edits data/content.json (banners, highlight boxes, menu, photo blocks, texts, site info) and uploads images. */
define('ROOT', __DIR__);
define('DATA_DIR', ROOT . '/data');
define('CONTENT_FILE', DATA_DIR . '/content.json');
define('AUTH_FILE', DATA_DIR . '/auth.json');
define('ATT_FILE', DATA_DIR . '/attempts.json');
define('UPLOAD_DIR', ROOT . '/uploads');

// Recommended image sizes (also used for automatic crop + resize)
$PRESETS = array(
  'banner'  => array('w' => 1920, 'h' => 720,  'label' => 'Banner',            'mobile' => 900),
  'ad'      => array('w' => 900,  'h' => 1200, 'label' => 'Highlight box'),
  'dish'    => array('w' => 800,  'h' => 800,  'label' => 'Dish photo'),
  'feature' => array('w' => 1200, 'h' => 1000, 'label' => 'Photo block'),
  'intro'   => array('w' => 1920, 'h' => 1000, 'label' => 'Intro background'),
  'qr'      => array('w' => 600,  'h' => 600,  'label' => 'QR code', 'contain' => true),
);

function ensure_dirs() {
  foreach (array(DATA_DIR, UPLOAD_DIR) as $d) if (!is_dir($d)) @mkdir($d, 0755, true);
  if (!file_exists(DATA_DIR . '/.htaccess')) @file_put_contents(DATA_DIR . '/.htaccess', "Require all denied\nDeny from all\n");
  if (!file_exists(UPLOAD_DIR . '/.htaccess')) @file_put_contents(UPLOAD_DIR . '/.htaccess',
    "php_flag engine off\nRemoveHandler .php .phtml .php5 .php7 .php8\n<FilesMatch \"\\.(php|phtml|php\\d)$\">\nRequire all denied\n</FilesMatch>\n");
}
ensure_dirs();

ini_set('session.cookie_httponly', 1);
ini_set('session.use_strict_mode', 1);
if (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ini_set('session.cookie_secure', 1);
session_name('molitaste_cp');
session_start();
header('X-Frame-Options: SAMEORIGIN');
header('X-Content-Type-Options: nosniff');
header('Cache-Control: no-store');

function json_out($a) { header('Content-Type: application/json; charset=utf-8'); echo json_encode($a, JSON_UNESCAPED_UNICODE); exit; }
function csrf() { if (empty($_SESSION['csrf'])) $_SESSION['csrf'] = bin2hex(random_bytes(16)); return $_SESSION['csrf']; }
function check_csrf() { if (!hash_equals(isset($_SESSION['csrf']) ? $_SESSION['csrf'] : '', isset($_POST['csrf']) ? $_POST['csrf'] : '')) json_out(array('ok' => false, 'error' => 'Session expired — reload the page.')); }
function auth() { return file_exists(AUTH_FILE) ? json_decode(file_get_contents(AUTH_FILE), true) : null; }
function save_auth($a) { return file_put_contents(AUTH_FILE, json_encode($a), LOCK_EX) !== false; }
function e($s) { return htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8'); }

function load_content() {
  $c = file_exists(CONTENT_FILE) ? json_decode(file_get_contents(CONTENT_FILE), true) : null;
  return is_array($c) ? $c : array();
}
function save_content($c) {
  $tmp = CONTENT_FILE . '.tmp';
  $json = json_encode($c, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
  if ($json === false || file_put_contents($tmp, $json, LOCK_EX) === false) return false;
  if (file_exists(CONTENT_FILE)) {
    @copy(CONTENT_FILE, DATA_DIR . '/content.backup.json');
    // keep a dated history (last 30 saves)
    @mkdir(DATA_DIR . '/history', 0755);
    @copy(CONTENT_FILE, DATA_DIR . '/history/content-' . date('Ymd-His') . '.json');
    $h = glob(DATA_DIR . '/history/content-*.json'); sort($h);
    while (count($h) > 30) @unlink(array_shift($h));
  }
  return rename($tmp, CONTENT_FILE);
}

/* ---------- sanitising ---------- */
function clean_text($s) {
  $s = (string)$s;
  $s = strip_tags($s, '<b><strong><i><em><br>');
  $s = preg_replace('/<(b|strong|i|em|br)\b[^>]*>/i', '<$1>', $s);   // no attributes on allowed tags
  return mb_substr($s, 0, 2000);
}
function clean_img($p) { $p = (string)$p; return preg_match('#^(img|uploads)/[A-Za-z0-9._/-]+$#', $p) && strpos($p, '..') === false ? $p : ''; }
function clean_link($p) {
  $p = trim((string)$p);
  if ($p === '') return '';
  if (preg_match('#^(https?://|\#|[A-Za-z0-9._-]+\.html(\#[A-Za-z0-9_-]*)?$|/)#', $p) && !preg_match('#^\s*javascript:#i', $p)) return mb_substr($p, 0, 300);
  return '';
}
function clean_color($c) { return preg_match('/^#[0-9a-fA-F]{3,8}$/', (string)$c) ? $c : '#d7263d'; }
function clean4($o) {
  $r = array();
  foreach (array('zh', 'en', 'ar', 'tr') as $l) $r[$l] = clean_text(is_array($o) && isset($o[$l]) ? $o[$l] : '');
  return $r;
}
function b($v) { return !empty($v); }
function clean_content($c) {
  $o = array('version' => 1);
  $s = isset($c['site']) && is_array($c['site']) ? $c['site'] : array();
  $o['site'] = array(
    'phone' => clean_text(isset($s['phone']) ? $s['phone'] : ''),
    'wechat' => clean_text(isset($s['wechat']) ? $s['wechat'] : ''),
    'address' => clean4(isset($s['address']) ? $s['address'] : array()),
    'hours' => array(),
  );
  foreach ((isset($s['hours']) && is_array($s['hours']) ? $s['hours'] : array()) as $h)
    $o['site']['hours'][] = array('day' => clean4(isset($h['day']) ? $h['day'] : array()), 'time' => clean_text(isset($h['time']) ? $h['time'] : ''), 'closed' => b(isset($h['closed']) ? $h['closed'] : false));
  $o['banners'] = array();
  foreach ((isset($c['banners']) && is_array($c['banners']) ? $c['banners'] : array()) as $x)
    $o['banners'][] = array('img' => clean_img(@$x['img']), 'img_m' => clean_img(@$x['img_m']), 'link' => clean_link(@$x['link']), 'alt' => clean_text(@$x['alt']), 'show' => b(@$x['show']));
  $o['ads'] = array();
  foreach ((isset($c['ads']) && is_array($c['ads']) ? $c['ads'] : array()) as $x)
    $o['ads'][] = array('img' => clean_img(@$x['img']), 'link' => clean_link(@$x['link']), 'color' => clean_color(@$x['color']),
      'badge' => clean4(@$x['badge']), 'title' => clean4(@$x['title']), 'sub' => clean4(@$x['sub']), 'price' => clean4(@$x['price']), 'cta' => clean4(@$x['cta']), 'show' => b(@$x['show']));
  $o['features'] = array();
  foreach ((isset($c['features']) && is_array($c['features']) ? $c['features'] : array()) as $x)
    $o['features'][] = array('img' => clean_img(@$x['img']), 'icon' => mb_substr(clean_text(@$x['icon']), 0, 8), 'title' => clean4(@$x['title']), 'text' => clean4(@$x['text']), 'show' => b(@$x['show']));
  $im = isset($c['images']) && is_array($c['images']) ? $c['images'] : array();
  $o['images'] = array('intro_bg' => clean_img(@$im['intro_bg']), 'menu_bg' => clean_img(@$im['menu_bg']), 'qr' => clean_img(@$im['qr']));
  $o['texts'] = array();
  foreach ((isset($c['texts']) && is_array($c['texts']) ? $c['texts'] : array()) as $k => $v)
    if (preg_match('/^[a-z0-9_]{1,40}$/', $k)) $o['texts'][$k] = clean4($v);
  $o['menu'] = array();
  foreach ((isset($c['menu']) && is_array($c['menu']) ? $c['menu'] : array()) as $cat) {
    $id = preg_replace('/[^a-z0-9_-]/', '', strtolower((string)@$cat['id']));
    if ($id === '') $id = 'cat' . count($o['menu']);
    $items = array();
    foreach ((isset($cat['items']) && is_array($cat['items']) ? $cat['items'] : array()) as $it) {
      $home = in_array(@$it['home'], array('special', 'more'), true) ? $it['home'] : '';
      $items[] = array('name' => clean4(@$it['name']), 'price' => is_numeric(@$it['price']) ? 0 + $it['price'] : 0, 'from' => b(@$it['from']),
        'img' => clean_img(@$it['img']), 'show' => b(@$it['show']), 'home' => $home);
    }
    $o['menu'][] = array('id' => $id, 'name' => clean4(@$cat['name']), 'note' => b(@$cat['note']), 'show' => b(@$cat['show']), 'items' => $items);
  }
  return $o;
}

/* ---------- images ---------- */
function load_image($path, $type) {
  if ($type === IMAGETYPE_JPEG && function_exists('imagecreatefromjpeg')) $im = @imagecreatefromjpeg($path);
  elseif ($type === IMAGETYPE_PNG && function_exists('imagecreatefrompng')) $im = @imagecreatefrompng($path);
  elseif (defined('IMAGETYPE_WEBP') && $type === IMAGETYPE_WEBP && function_exists('imagecreatefromwebp')) $im = @imagecreatefromwebp($path);
  elseif ($type === IMAGETYPE_GIF && function_exists('imagecreatefromgif')) $im = @imagecreatefromgif($path);
  else return null;
  if (!$im) return null;
  // phone photos: respect EXIF rotation
  if ($type === IMAGETYPE_JPEG && function_exists('exif_read_data')) {
    $ex = @exif_read_data($path);
    $o = isset($ex['Orientation']) ? (int)$ex['Orientation'] : 1;
    if ($o === 3) $im = imagerotate($im, 180, 0); elseif ($o === 6) $im = imagerotate($im, -90, 0); elseif ($o === 8) $im = imagerotate($im, 90, 0);
  }
  return $im;
}
function fit_image($src, $w, $h, $contain) {
  $sw = imagesx($src); $sh = imagesy($src);
  $dst = imagecreatetruecolor($w, $h);
  $white = imagecolorallocate($dst, 255, 255, 255);
  imagefill($dst, 0, 0, $white);
  if ($contain) {                       // whole image inside (QR codes)
    $sc = min($w / $sw, $h / $sh); $nw = (int)round($sw * $sc); $nh = (int)round($sh * $sc);
    imagecopyresampled($dst, $src, (int)(($w - $nw) / 2), (int)(($h - $nh) / 2), 0, 0, $nw, $nh, $sw, $sh);
  } else {                              // fill and crop the centre
    $sc = max($w / $sw, $h / $sh); $cw = (int)round($w / $sc); $ch = (int)round($h / $sc);
    imagecopyresampled($dst, $src, 0, 0, (int)(($sw - $cw) / 2), (int)(($sh - $ch) / 2), $w, $h, $cw, $ch);
  }
  return $dst;
}
function save_image($im, $base) {
  if (function_exists('imagewebp')) { $f = $base . '.webp'; if (@imagewebp($im, UPLOAD_DIR . '/' . $f, 80)) return 'uploads/' . $f; }
  $f = $base . '.jpg'; imagejpeg($im, UPLOAD_DIR . '/' . $f, 86); return 'uploads/' . $f;
}

/* ---------- actions ---------- */
$A = auth();
$act = isset($_POST['action']) ? $_POST['action'] : '';
$ip = preg_replace('/[^0-9a-fA-F:.]/', '', isset($_SERVER['REMOTE_ADDR']) ? $_SERVER['REMOTE_ADDR'] : '');
function attempts() { $a = file_exists(ATT_FILE) ? json_decode(file_get_contents(ATT_FILE), true) : array(); return is_array($a) ? $a : array(); }

if (!$A && $act === 'setup') {
  check_csrf();
  $u = trim(isset($_POST['user']) ? $_POST['user'] : ''); $p = isset($_POST['pass']) ? $_POST['pass'] : '';
  if (strlen($u) < 3 || strlen($p) < 8) json_out(array('ok' => false, 'error' => 'Username min 3 characters, password min 8.'));
  save_auth(array('user' => $u, 'hash' => password_hash($p, PASSWORD_DEFAULT)));
  session_regenerate_id(true); $_SESSION['ok'] = 1; json_out(array('ok' => true));
}
if ($A && $act === 'login') {
  check_csrf();
  $att = attempts(); $now = time();
  $mine = array_values(array_filter(isset($att[$ip]) ? $att[$ip] : array(), function ($t) use ($now) { return $t > $now - 900; }));
  if (count($mine) >= 6) json_out(array('ok' => false, 'error' => 'Too many attempts. Try again in 15 minutes.'));
  $u = isset($_POST['user']) ? $_POST['user'] : ''; $p = isset($_POST['pass']) ? $_POST['pass'] : '';
  if (hash_equals((string)$A['user'], (string)$u) && password_verify($p, $A['hash'])) {
    unset($att[$ip]); file_put_contents(ATT_FILE, json_encode($att), LOCK_EX);
    session_regenerate_id(true); $_SESSION['ok'] = 1; json_out(array('ok' => true));
  }
  $mine[] = $now; $att[$ip] = $mine; file_put_contents(ATT_FILE, json_encode($att), LOCK_EX);
  sleep(1);
  json_out(array('ok' => false, 'error' => 'Wrong username or password.'));
}
$logged = $A && !empty($_SESSION['ok']);
if ($act === 'logout') { check_csrf(); $_SESSION = array(); session_destroy(); json_out(array('ok' => true)); }

if ($logged && $act === 'save') {
  check_csrf();
  $raw = isset($_POST['content']) ? $_POST['content'] : '';
  if (strlen($raw) > 2000000) json_out(array('ok' => false, 'error' => 'Content too large.'));
  $c = json_decode($raw, true);
  if (!is_array($c) || !isset($c['menu'])) json_out(array('ok' => false, 'error' => 'Invalid data — nothing was saved.'));
  $clean = clean_content($c);
  if (!save_content($clean)) json_out(array('ok' => false, 'error' => 'Could not write the file on the server.'));
  json_out(array('ok' => true, 'content' => $clean, 'saved' => date('H:i:s')));
}
if ($logged && $act === 'upload') {
  check_csrf();
  $preset = isset($_POST['preset']) ? $_POST['preset'] : '';
  if (!isset($PRESETS[$preset])) json_out(array('ok' => false, 'error' => 'Unknown image type.'));
  $P = $PRESETS[$preset];
  if (empty($_FILES['file']) || $_FILES['file']['error'] !== UPLOAD_ERR_OK) json_out(array('ok' => false, 'error' => 'Upload failed (file too big? max ' . ini_get('upload_max_filesize') . ').'));
  if ($_FILES['file']['size'] > 15 * 1024 * 1024) json_out(array('ok' => false, 'error' => 'File is larger than 15 MB.'));
  $tmp = $_FILES['file']['tmp_name'];
  $info = @getimagesize($tmp);
  if (!$info) json_out(array('ok' => false, 'error' => 'This is not an image (use JPG, PNG or WebP).'));
  $src = load_image($tmp, $info[2]);
  if (!$src) json_out(array('ok' => false, 'error' => 'This image type cannot be read on the server — use JPG or PNG.'));
  $ow = imagesx($src); $oh = imagesy($src);
  // never enlarge: if the photo is smaller, keep the ratio but at the photo's own size
  $scale = min(1, max($ow / $P['w'], $oh / $P['h']));
  if (!empty($P['contain'])) $scale = min(1, max($ow, $oh) / max($P['w'], $P['h']));
  $tw = max(1, (int)round($P['w'] * $scale)); $th = max(1, (int)round($P['h'] * $scale));
  $base = $preset . '-' . date('Ymd-His') . '-' . bin2hex(random_bytes(3));
  $out = fit_image($src, $tw, $th, !empty($P['contain']));
  $path = save_image($out, $base);
  $res = array('ok' => true, 'path' => $path, 'w' => $tw, 'h' => $th, 'orig_w' => $ow, 'orig_h' => $oh);
  if (!empty($P['mobile'])) {
    $mh = (int)round($P['mobile'] * $P['h'] / $P['w']);
    $m = fit_image($out, $P['mobile'], $mh, false);
    $res['path_m'] = save_image($m, $base . '-m');
    imagedestroy($m);
  }
  imagedestroy($out); imagedestroy($src);
  $warn = array();
  if ($ow < $P['w'] || $oh < $P['h']) $warn[] = 'Your photo is ' . $ow . '×' . $oh . ' px — smaller than the recommended ' . $P['w'] . '×' . $P['h'] . ' px, so it may look soft.';
  $r1 = $ow / $oh; $r2 = $P['w'] / $P['h'];
  if (empty($P['contain']) && abs($r1 - $r2) / $r2 > 0.08) $warn[] = 'Different shape than recommended — the edges were cropped to fit.';
  $res['warn'] = $warn;
  json_out($res);
}
if ($logged && $act === 'password') {
  check_csrf();
  $old = isset($_POST['old']) ? $_POST['old'] : ''; $new = isset($_POST['new']) ? $_POST['new'] : ''; $user = trim(isset($_POST['user']) ? $_POST['user'] : '');
  if (!password_verify($old, $A['hash'])) json_out(array('ok' => false, 'error' => 'Current password is wrong.'));
  if (strlen($new) < 8) json_out(array('ok' => false, 'error' => 'New password: at least 8 characters.'));
  if (strlen($user) < 3) $user = $A['user'];
  save_auth(array('user' => $user, 'hash' => password_hash($new, PASSWORD_DEFAULT)));
  json_out(array('ok' => true));
}
if ($act !== '') json_out(array('ok' => false, 'error' => 'Please log in again.'));

$token = csrf();
?><!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex,nofollow">
<title>Moli Taste · Control Panel</title>
<style>
:root{--tan:#dfa25f;--tan-d:#c8873f;--red:#d7263d;--navy:#232d41;--line:#e6e0d6;--bg:#f6f4f0;--ink:#1d1d1f;--muted:#6b6b70;--ok:#2f8f4e}
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"PingFang SC","Microsoft YaHei","Geeza Pro",Tahoma,sans-serif;background:var(--bg);color:var(--ink);font-size:14px;line-height:1.5}
button,input,select,textarea{font:inherit;color:inherit}
.login{max-width:380px;margin:9vh auto;background:#fff;border-radius:16px;padding:32px;box-shadow:0 10px 40px rgba(0,0,0,.08)}
.login h1{font-size:22px;margin-bottom:4px}.login p{color:var(--muted);margin-bottom:18px}
.login input{width:100%;padding:12px;border:1px solid var(--line);border-radius:10px;margin-bottom:10px}
.btn{border:none;border-radius:10px;padding:10px 16px;font-weight:600;cursor:pointer;background:#fff;border:1px solid var(--line)}
.btn.pri{background:var(--red);border-color:var(--red);color:#fff}.btn.tan{background:var(--tan);border-color:var(--tan);color:#fff}
.btn.sm{padding:5px 10px;font-size:13px}.btn:disabled{opacity:.5}
.err{color:var(--red);margin-top:8px;min-height:20px}
.app{display:grid;grid-template-columns:230px 1fr;min-height:100vh}
.side{background:var(--navy);color:#e9e6df;padding:18px 12px;position:sticky;top:0;height:100vh;overflow:auto}
.side h2{font-size:17px;padding:4px 10px 16px}.side h2 small{display:block;color:var(--tan);font-size:12px;font-weight:500}
.side a{display:flex;gap:10px;align-items:center;padding:10px 12px;border-radius:10px;color:#e9e6df;text-decoration:none;cursor:pointer;margin-bottom:2px}
.side a.on,.side a:hover{background:rgba(255,255,255,.1)}.side a.on{color:#fff;font-weight:700;box-shadow:inset 3px 0 0 var(--tan)}
.side .sp{height:14px}
main{padding:0 26px 120px;min-width:0}
.top{position:sticky;top:0;z-index:20;background:var(--bg);display:flex;align-items:center;gap:12px;padding:16px 0 12px;border-bottom:1px solid var(--line);margin-bottom:18px}
.top h1{font-size:20px;flex:1}
.status{color:var(--muted);font-size:13px}.status.dirty{color:var(--red);font-weight:700}.status.saved{color:var(--ok);font-weight:700}
.card{background:#fff;border:1px solid var(--line);border-radius:14px;padding:16px;margin-bottom:14px}
.card h3{font-size:15px;margin-bottom:10px;display:flex;gap:8px;align-items:center}
.row{display:flex;gap:12px;flex-wrap:wrap;align-items:flex-start}
.grow{flex:1;min-width:240px}
label.f{display:block;font-size:12px;color:var(--muted);font-weight:600;margin:8px 0 3px}
input[type=text],input[type=number],textarea,select{width:100%;padding:8px 10px;border:1px solid var(--line);border-radius:8px;background:#fff}
textarea{min-height:58px;resize:vertical}
.l4{display:grid;grid-template-columns:repeat(4,1fr);gap:8px}
.l4 .lg{font-size:11px;font-weight:700;color:var(--tan-d);margin-bottom:2px}
.l4 [dir=rtl]{text-align:right}
.imgbox{width:240px;flex:none}
.imgbox .pv{border:1px dashed #cfc5b5;border-radius:10px;background:#faf8f4 center/contain no-repeat;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:12px;overflow:hidden}
.imgbox .pv img{width:100%;height:100%;object-fit:cover}
.rec{display:inline-block;margin-top:6px;background:#fff4e6;color:#8a5a22;border:1px solid #f0d6b4;border-radius:6px;padding:3px 8px;font-size:12px;font-weight:600}
.dim{font-size:12px;color:var(--muted);margin-top:4px}.warn{font-size:12px;color:#b45309;margin-top:4px}
.tools{display:flex;gap:6px;flex-wrap:wrap;margin-top:10px;align-items:center}
.chk{display:inline-flex;gap:6px;align-items:center;font-weight:600;cursor:pointer}
.item{display:grid;grid-template-columns:110px 1fr;gap:12px;padding:12px 0;border-top:1px solid var(--line)}
.item:first-child{border-top:none}
.item .thumb{width:110px;height:110px;border-radius:10px;overflow:hidden;background:#f2efe9;cursor:pointer;position:relative}
.item .thumb img{width:100%;height:100%;object-fit:cover}
.item .thumb span{position:absolute;inset:auto 0 0;background:rgba(0,0,0,.55);color:#fff;font-size:11px;text-align:center;padding:2px}
.cat-h{display:flex;align-items:center;gap:10px;cursor:pointer}
.cat-h b{font-size:16px;flex:1}
.pill{font-size:11px;background:#eee;border-radius:99px;padding:2px 8px;color:#555}
.hidden-badge{background:#fde2e2;color:#a11}
.sizes td,.sizes th{padding:7px 10px;border-bottom:1px solid var(--line);text-align:left}
.toast{position:fixed;bottom:20px;left:50%;transform:translateX(-50%);background:#222;color:#fff;padding:10px 18px;border-radius:10px;opacity:0;transition:.3s;z-index:99}
.toast.on{opacity:1}
.mobile-nav{display:none}
@media (max-width:900px){.app{grid-template-columns:1fr}.side{display:none}.mobile-nav{display:block;margin-bottom:10px}.l4{grid-template-columns:1fr 1fr}.imgbox{width:100%}main{padding:0 12px 120px}}
</style>
</head>
<body>
<?php if (!$A || !$logged): ?>
<div class="login">
  <h1>🍕 Moli Taste</h1>
  <p><?= $A ? 'Control panel — please sign in.' : 'First time: create your control-panel account.' ?></p>
  <input id="u" type="text" placeholder="Username" autocomplete="username">
  <input id="p" type="password" placeholder="Password<?= $A ? '' : ' (min 8 characters)' ?>" autocomplete="<?= $A ? 'current-password' : 'new-password' ?>">
  <button class="btn pri" style="width:100%" id="go"><?= $A ? 'Sign in' : 'Create account' ?></button>
  <div class="err" id="err"></div>
</div>
<script>
document.getElementById("go").onclick = function () {
  var fd = new FormData(); fd.append("action", "<?= $A ? 'login' : 'setup' ?>"); fd.append("csrf", "<?= e($token) ?>");
  fd.append("user", document.getElementById("u").value); fd.append("pass", document.getElementById("p").value);
  fetch("admin.php", { method: "POST", body: fd, credentials: "same-origin" }).then(function (r) { return r.json(); }).then(function (j) {
    if (j.ok) location.reload(); else document.getElementById("err").textContent = j.error;
  }).catch(function () { document.getElementById("err").textContent = "Network error."; });
};
document.getElementById("p").onkeydown = function (e) { if (e.key === "Enter") document.getElementById("go").click(); };
</script>
<?php else: ?>
<div class="app">
  <nav class="side" id="side">
    <h2>🍕 Moli Taste<small>Control Panel</small></h2>
    <a data-s="banners">🖼️ Banners</a>
    <a data-s="ads">⭐ Highlight boxes</a>
    <a data-s="menu">🍽️ Menu &amp; prices</a>
    <a data-s="features">📸 Photo blocks</a>
    <a data-s="texts">✏️ Homepage texts</a>
    <a data-s="site">📍 Address &amp; hours</a>
    <a data-s="images">🧩 Other images</a>
    <a data-s="sizes">📐 Image sizes</a>
    <div class="sp"></div>
    <a data-s="account">🔒 Password</a>
    <a href="index.html" target="_blank">↗ View website</a>
    <a id="logout">⎋ Sign out</a>
  </nav>
  <main>
    <div class="top"><h1 id="title"></h1><span class="status" id="status">All changes saved</span>
      <button class="btn pri" id="save">💾 Save all changes</button></div>
    <select class="mobile-nav" id="mnav"></select>
    <div id="view"></div>
  </main>
</div>
<div class="toast" id="toast"></div>
<input type="file" id="file" accept="image/jpeg,image/png,image/webp" style="display:none">
<script>
var CSRF = "<?= e($token) ?>";
var PRESETS = <?= json_encode($PRESETS) ?>;
var C = <?= json_encode(load_content(), JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_HEX_TAG | JSON_HEX_AMP) ?>;
var LANGS = [["zh", "中文"], ["en", "English"], ["ar", "العربية"], ["tr", "Türkçe"]];
var dirty = false, section = "banners";
function $(s, r) { return (r || document).querySelector(s); }
function esc(s) { return String(s == null ? "" : s).replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; }); }
function toast(m) { var t = $("#toast"); t.textContent = m; t.classList.add("on"); clearTimeout(t._h); t._h = setTimeout(function () { t.classList.remove("on"); }, 2600); }
function markDirty() { dirty = true; var s = $("#status"); s.textContent = "Unsaved changes"; s.className = "status dirty"; }
function post(data) {
  var fd = new FormData(); fd.append("csrf", CSRF);
  for (var k in data) fd.append(k, data[k]);
  return fetch("admin.php", { method: "POST", body: fd, credentials: "same-origin" }).then(function (r) { return r.json(); });
}
window.onbeforeunload = function () { if (dirty) return "You have unsaved changes."; };

/* ---------- small builders ---------- */
function getPath(obj, path) { return path.split(".").reduce(function (o, k) { return o == null ? o : o[k]; }, obj); }
function setPath(obj, path, val) { var ks = path.split("."), o = obj; for (var i = 0; i < ks.length - 1; i++) { if (o[ks[i]] == null) o[ks[i]] = {}; o = o[ks[i]]; } o[ks[ks.length - 1]] = val; }
var BIND = []; // [element, objectRef, path, type]
function field(obj, path, label, opts) {
  opts = opts || {};
  var id = "f" + BIND.length; BIND.push([id, obj, path, opts.type || "text"]);
  var v = getPath(obj, path); if (v == null) v = "";
  var inp = opts.type === "textarea" ? '<textarea id="' + id + '">' + esc(v) + "</textarea>"
    : opts.type === "number" ? '<input type="number" step="0.5" min="0" id="' + id + '" value="' + esc(v) + '">'
    : opts.type === "color" ? '<input type="color" id="' + id + '" value="' + esc(v || "#d7263d") + '" style="height:36px;padding:2px">'
    : opts.type === "check" ? '<label class="chk"><input type="checkbox" id="' + id + '"' + (v ? " checked" : "") + "> " + label + "</label>"
    : opts.type === "select" ? '<select id="' + id + '">' + opts.options.map(function (o) { return '<option value="' + esc(o[0]) + '"' + (o[0] === v ? " selected" : "") + ">" + esc(o[1]) + "</option>"; }).join("") + "</select>"
    : '<input type="text" id="' + id + '" value="' + esc(v) + '"' + (opts.placeholder ? ' placeholder="' + esc(opts.placeholder) + '"' : "") + ">";
  return opts.type === "check" ? inp : (label ? '<label class="f">' + label + "</label>" : "") + inp;
}
function l4(obj, path, label, multi) {
  var o = getPath(obj, path); if (!o || typeof o !== "object") { o = { zh: "", en: "", ar: "", tr: "" }; setPath(obj, path, o); }
  return (label ? '<label class="f">' + label + "</label>" : "") + '<div class="l4">' + LANGS.map(function (L) {
    var id = "f" + BIND.length; BIND.push([id, o, L[0], multi ? "textarea" : "text"]);
    var dir = L[0] === "ar" ? ' dir="rtl"' : "";
    return '<div><div class="lg">' + L[1] + "</div>" + (multi ? '<textarea id="' + id + '"' + dir + ">" + esc(o[L[0]] || "") + "</textarea>"
      : '<input type="text" id="' + id + '"' + dir + ' value="' + esc(o[L[0]] || "") + '">') + "</div>";
  }).join("") + "</div>";
}
function wire() {
  BIND.forEach(function (b) {
    var el = document.getElementById(b[0]); if (!el) return;
    var ev = b[3] === "check" || b[3] === "select" || b[3] === "color" ? "change" : "input";
    el.addEventListener(ev, function () {
      var v = b[3] === "check" ? el.checked : b[3] === "number" ? (el.value === "" ? 0 : parseFloat(el.value)) : el.value;
      setPath(b[1], b[2], v); markDirty();
      if (b[3] === "check" || b[3] === "select") render();
    });
  });
}
/* image picker with recommended size */
var IMGS = [];
function imgBox(obj, key, preset, opts) {
  opts = opts || {};
  var P = PRESETS[preset], id = IMGS.length; IMGS.push([obj, key, preset, opts]);
  var src = obj[key] || "";
  var ratio = P.h / P.w, w = opts.width || 240, h = Math.round(w * ratio);
  if (h > 260) { h = 260; }
  return '<div class="imgbox"' + (opts.width ? ' style="width:' + w + 'px"' : "") + '>' +
    '<div class="pv" style="height:' + h + 'px" data-img="' + id + '">' + (src ? '<img src="' + esc(src) + '" data-dim="' + id + '">' : "No image") + "</div>" +
    '<div class="rec">📐 Recommended: ' + P.w + " × " + P.h + " px</div>" +
    '<div class="dim" id="dim' + id + '"></div><div class="warn" id="warn' + id + '"></div>' +
    '<div class="tools"><button class="btn sm tan" data-up="' + id + '">⬆ Upload photo</button>' +
    (opts.clear && src ? '<button class="btn sm" data-clr="' + id + '">Remove</button>' : "") + "</div></div>";
}
function wireImgs() {
  document.querySelectorAll("[data-up]").forEach(function (b) { b.onclick = function () { pickImage(+b.getAttribute("data-up")); }; });
  document.querySelectorAll("[data-clr]").forEach(function (b) { b.onclick = function () { var x = IMGS[+b.getAttribute("data-clr")]; x[0][x[1]] = ""; if (x[1] === "img") x[0].img_m = ""; markDirty(); render(); }; });
  document.querySelectorAll("[data-dim]").forEach(function (im) {
    im.addEventListener("load", function () {
      var x = IMGS[+im.getAttribute("data-dim")], P = PRESETS[x[2]], d = $("#dim" + im.getAttribute("data-dim"));
      if (d) d.textContent = "Current: " + im.naturalWidth + " × " + im.naturalHeight + " px";
    });
  });
}
function pickImage(id) {
  var x = IMGS[id], P = PRESETS[x[2]], f = $("#file");
  f.value = "";
  f.onchange = function () {
    var file = f.files[0]; if (!file) return;
    var w = $("#warn" + id); w.textContent = "Uploading…";
    // check size before uploading
    var url = URL.createObjectURL(file), test = new Image();
    test.onload = function () {
      var msgs = [];
      if (test.naturalWidth < P.w || test.naturalHeight < P.h) msgs.push("⚠ Your photo is " + test.naturalWidth + "×" + test.naturalHeight + " — smaller than " + P.w + "×" + P.h + ".");
      var r1 = test.naturalWidth / test.naturalHeight, r2 = P.w / P.h;
      if (!P.contain && Math.abs(r1 - r2) / r2 > 0.08) msgs.push("⚠ Different shape — edges will be cropped.");
      URL.revokeObjectURL(url);
      upload(file, x, id, msgs);
    };
    test.onerror = function () { upload(file, x, id, []); };
    test.src = url;
  };
  f.click();
}
function upload(file, x, id, pre) {
  var fd = new FormData(); fd.append("csrf", CSRF); fd.append("action", "upload"); fd.append("preset", x[2]); fd.append("file", file);
  fetch("admin.php", { method: "POST", body: fd, credentials: "same-origin" }).then(function (r) { return r.json(); }).then(function (j) {
    if (!j.ok) { $("#warn" + id).textContent = "❌ " + j.error; return; }
    x[0][x[1]] = j.path; if (j.path_m) x[0].img_m = j.path_m;
    markDirty(); render();
    var msgs = (j.warn || []).map(function (m) { return "⚠ " + m; });
    toast("Photo uploaded — saved as " + j.w + "×" + j.h + " px. Press Save to publish.");
    setTimeout(function () { var w = $("#warn" + id); if (w && msgs.length) w.textContent = msgs.join(" "); }, 50);
  }).catch(function () { $("#warn" + id).textContent = "❌ Upload failed (network)."; });
}
function listTools(arr, i, extra) {
  return '<div class="tools">' + (extra || "") +
    '<button class="btn sm" data-mv="' + i + ',-1"' + (i === 0 ? " disabled" : "") + ">↑ Up</button>" +
    '<button class="btn sm" data-mv="' + i + ',1"' + (i === arr.length - 1 ? " disabled" : "") + ">↓ Down</button>" +
    '<button class="btn sm" data-del="' + i + '">🗑 Delete</button></div>';
}
function wireList(arr, root) {
  (root || document).querySelectorAll("[data-mv]").forEach(function (b) { b.onclick = function () {
    var p = b.getAttribute("data-mv").split(","), i = +p[0], j = i + +p[1]; if (j < 0 || j >= arr.length) return;
    var t = arr[i]; arr[i] = arr[j]; arr[j] = t; markDirty(); render(); }; });
  (root || document).querySelectorAll("[data-del]").forEach(function (b) { b.onclick = function () {
    if (!confirm("Delete this? (You can still undo by not saving and reloading.)")) return;
    arr.splice(+b.getAttribute("data-del"), 1); markDirty(); render(); }; });
}

/* ---------- sections ---------- */
var S = {};
S.banners = { title: "Banners (top slider)", html: function () {
  C.banners = C.banners || [];
  return '<p style="margin-bottom:12px;color:#6b6b70">Banners rotate at the top of the homepage. Text you want on a banner must be part of the image. A phone-size copy is made automatically.</p>' +
    C.banners.map(function (b, i) {
      return '<div class="card"><h3>Banner ' + (i + 1) + (b.show === false ? ' <span class="pill hidden-badge">hidden</span>' : "") + '</h3><div class="row">' +
        imgBox(b, "img", "banner", { width: 360 }) + '<div class="grow">' +
        field(b, "link", "When tapped, open (e.g. menu.html#pizza, #order, or a full https:// link)", { placeholder: "menu.html#pizza" }) +
        field(b, "alt", "Short description (for search engines)") +
        '<div style="margin-top:10px">' + field(b, "show", "Show this banner", { type: "check" }) + "</div>" +
        listTools(C.banners, i) + "</div></div></div>";
    }).join("") + '<button class="btn tan" id="addB">＋ Add banner</button>';
}, after: function () { wireList(C.banners); $("#addB").onclick = function () { C.banners.push({ img: "", img_m: "", link: "menu.html", alt: "", show: true }); markDirty(); render(); }; } };

var EMPTY4 = function () { return { zh: "", en: "", ar: "", tr: "" }; };
S.ads = { title: "Highlight boxes (under the banners)", html: function () {
  C.ads = C.ads || [];
  return '<p style="margin-bottom:12px;color:#6b6b70">Ad boxes with a photo, badge, title, short text and price — 4 across on computers, 2 on phones. Text is real text, so it changes with the language.</p>' +
    C.ads.map(function (a, i) {
      return '<div class="card"><h3>Box ' + (i + 1) + (a.show === false ? ' <span class="pill hidden-badge">hidden</span>' : "") + '</h3><div class="row">' +
        imgBox(a, "img", "ad", { width: 180 }) + '<div class="grow">' +
        l4(a, "badge", "Badge (e.g. NEW)") + l4(a, "title", "Title") + l4(a, "sub", "Short text") + l4(a, "price", "Price line") + l4(a, "cta", "Link text") +
        '<div class="row"><div class="grow">' + field(a, "link", "Link", { placeholder: "menu.html#pizza" }) + '</div><div style="width:120px">' + field(a, "color", "Badge colour", { type: "color" }) + "</div></div>" +
        '<div style="margin-top:10px">' + field(a, "show", "Show this box", { type: "check" }) + "</div>" + listTools(C.ads, i) + "</div></div></div>";
    }).join("") + '<button class="btn tan" id="addA">＋ Add box</button>';
}, after: function () { wireList(C.ads); $("#addA").onclick = function () { C.ads.push({ img: "", link: "menu.html", color: "#d7263d", badge: EMPTY4(), title: EMPTY4(), sub: EMPTY4(), price: EMPTY4(), cta: { zh: "查看 →", en: "See more →", ar: "اكتشف ←", tr: "Keşfet →" }, show: true }); markDirty(); render(); }; } };

var openCat = 0;
S.menu = { title: "Menu & prices", html: function () {
  C.menu = C.menu || [];
  return '<p style="margin-bottom:12px;color:#6b6b70">Click a category to open it. "Homepage" puts a dish in <b>Special Dishes</b> (top, 3 recommended) or <b>More Dishes</b> (6 recommended). Untick "Show" to hide a dish without deleting it.</p>' +
    C.menu.map(function (c, ci) {
      var open = ci === openCat;
      var h = '<div class="card"><div class="cat-h" data-open="' + ci + '"><span>' + (open ? "▾" : "▸") + "</span><b>" + esc(c.name.zh) + " · " + esc(c.name.en) + "</b>" +
        '<span class="pill">' + c.items.length + " dishes</span>" + (c.show === false ? '<span class="pill hidden-badge">hidden</span>' : "") + "</div>";
      if (!open) return h + "</div>";
      h += '<div style="margin-top:12px">' + l4(c, "name", "Category name") +
        '<div class="row" style="margin-top:8px;gap:18px">' + field(c, "show", "Show category", { type: "check" }) + field(c, "note", "Show “fresh-baked bread” note", { type: "check" }) + "</div>" +
        '<div class="tools"><button class="btn sm" data-cmv="' + ci + ',-1">↑ Move category up</button><button class="btn sm" data-cmv="' + ci + ',1">↓ Move down</button><button class="btn sm" data-cdel="' + ci + '">🗑 Delete category</button></div></div>';
      h += '<div style="margin-top:8px">' + c.items.map(function (it, ii) {
        return '<div class="item"><div>' + imgBox(it, "img", "dish", { width: 110 }) + '</div><div>' +
          l4(it, "name", "Dish name") +
          '<div class="row" style="align-items:flex-end"><div style="width:110px">' + field(it, "price", "Price ¥", { type: "number" }) + "</div>" +
          '<div style="width:170px">' + field(it, "home", "Homepage", { type: "select", options: [["", "—"], ["special", "Special Dishes"], ["more", "More Dishes"]] }) + "</div>" +
          '<div style="padding-bottom:8px;display:flex;gap:16px">' + field(it, "from", "“起 / from” price", { type: "check" }) + field(it, "show", "Show", { type: "check" }) + "</div></div>" +
          '<div class="tools"><button class="btn sm" data-imv="' + ci + "," + ii + ',-1">↑</button><button class="btn sm" data-imv="' + ci + "," + ii + ',1">↓</button>' +
          '<button class="btn sm" data-idel="' + ci + "," + ii + '">🗑 Delete dish</button></div></div></div>';
      }).join("") + '</div><button class="btn tan sm" data-iadd="' + ci + '" style="margin-top:10px">＋ Add dish to this category</button>';
      return h + "</div>";
    }).join("") + '<button class="btn tan" id="addC">＋ Add category</button>';
}, after: function () {
  document.querySelectorAll("[data-open]").forEach(function (el) { el.onclick = function () { var i = +el.getAttribute("data-open"); openCat = openCat === i ? -1 : i; render(); }; });
  document.querySelectorAll("[data-cmv]").forEach(function (b) { b.onclick = function () { var p = b.getAttribute("data-cmv").split(","), i = +p[0], j = i + +p[1]; if (j < 0 || j >= C.menu.length) return; var t = C.menu[i]; C.menu[i] = C.menu[j]; C.menu[j] = t; openCat = j; markDirty(); render(); }; });
  document.querySelectorAll("[data-cdel]").forEach(function (b) { b.onclick = function () { if (!confirm("Delete the whole category and its dishes?")) return; C.menu.splice(+b.getAttribute("data-cdel"), 1); openCat = -1; markDirty(); render(); }; });
  document.querySelectorAll("[data-imv]").forEach(function (b) { b.onclick = function () { var p = b.getAttribute("data-imv").split(","), c = C.menu[+p[0]].items, i = +p[1], j = i + +p[2]; if (j < 0 || j >= c.length) return; var t = c[i]; c[i] = c[j]; c[j] = t; markDirty(); render(); }; });
  document.querySelectorAll("[data-idel]").forEach(function (b) { b.onclick = function () { if (!confirm("Delete this dish?")) return; var p = b.getAttribute("data-idel").split(","); C.menu[+p[0]].items.splice(+p[1], 1); markDirty(); render(); }; });
  document.querySelectorAll("[data-iadd]").forEach(function (b) { b.onclick = function () { C.menu[+b.getAttribute("data-iadd")].items.push({ name: EMPTY4(), price: 0, from: false, img: "", show: true, home: "" }); markDirty(); render(); }; });
  $("#addC").onclick = function () { C.menu.push({ id: "cat" + Date.now().toString(36), name: EMPTY4(), note: false, show: true, items: [] }); openCat = C.menu.length - 1; markDirty(); render(); };
} };

S.features = { title: "Photo blocks (homepage)", html: function () {
  C.features = C.features || [];
  return '<p style="margin-bottom:12px;color:#6b6b70">Large photo + text boxes on the homepage (e.g. Napoletana style, daily gelato, fresh bread). They alternate left/right automatically.</p>' +
    C.features.map(function (f, i) {
      return '<div class="card"><h3>Block ' + (i + 1) + (f.show === false ? ' <span class="pill hidden-badge">hidden</span>' : "") + '</h3><div class="row">' + imgBox(f, "img", "feature", { width: 260 }) +
        '<div class="grow"><div style="width:120px">' + field(f, "icon", "Icon (emoji)") + "</div>" + l4(f, "title", "Title") + l4(f, "text", "Text", true) +
        '<div style="margin-top:10px">' + field(f, "show", "Show this block", { type: "check" }) + "</div>" + listTools(C.features, i) + "</div></div></div>";
    }).join("") + '<button class="btn tan" id="addF">＋ Add photo block</button>';
}, after: function () { wireList(C.features); $("#addF").onclick = function () { C.features.push({ img: "", icon: "⭐", title: EMPTY4(), text: EMPTY4(), show: true }); markDirty(); render(); }; } };

S.texts = { title: "Homepage & menu texts", html: function () {
  C.texts = C.texts || {};
  var keys = Object.keys(C.texts);
  return '<p style="margin-bottom:12px;color:#6b6b70">Every text on the website in all four languages. You may use &lt;b&gt;bold&lt;/b&gt; in longer texts.</p>' +
    keys.map(function (k) {
      var v = C.texts[k], long = (v.en || "").length > 60;
      return '<div class="card"><h3 style="font-size:13px;color:#6b6b70">' + esc((v.en || k).replace(/<[^>]+>/g, "").slice(0, 70)) + "</h3>" + l4(C.texts, k, "", long) + "</div>";
    }).join("");
}, after: function () {} };

S.site = { title: "Address, phone & opening hours", html: function () {
  C.site = C.site || { hours: [] };
  return '<div class="card"><h3>Contact</h3><div class="row"><div class="grow">' + field(C.site, "phone", "Phone (shown in footer and hours box)", { placeholder: "+86 …" }) + "</div>" +
    '<div class="grow">' + field(C.site, "wechat", "WeChat mini-program name") + "</div></div>" + l4(C.site, "address", "Address") + "</div>" +
    '<div class="card"><h3>Opening hours</h3><p style="color:#6b6b70;margin-bottom:8px">Leave the time empty to show “Open”. Tick “Closed” for days off.</p>' +
    (C.site.hours || []).map(function (d, i) {
      return '<div class="row" style="align-items:center;margin:6px 0"><b style="width:150px">' + esc(d.day.zh) + " · " + esc(d.day.en) + "</b>" +
        '<div style="width:220px">' + field(d, "time", "", { placeholder: "11:00 – 22:00" }) + "</div>" + field(d, "closed", "Closed", { type: "check" }) + "</div>";
    }).join("") + "</div>";
}, after: function () {} };

S.images = { title: "Other images", html: function () {
  C.images = C.images || {};
  return '<div class="card"><h3>Dark photo behind the logo text (homepage + menu page top)</h3>' + imgBox(C.images, "intro_bg", "intro", { width: 380 }) + "</div>" +
    '<div class="card"><h3>WeChat mini-program QR code (homepage “Opening hours” section)</h3>' + imgBox(C.images, "qr", "qr", { width: 200, clear: true }) + "</div>";
}, after: function () {} };

S.sizes = { title: "Recommended image sizes", html: function () {
  var where = { banner: "Top slider (phone copy made automatically)", ad: "Highlight boxes under the banners", dish: "Every dish on the menu", feature: "Photo blocks on the homepage", intro: "Dark background behind the logo", qr: "WeChat QR code" };
  return '<div class="card"><table class="sizes"><tr><th>Image</th><th>Recommended size</th><th>Shape</th><th>Used for</th></tr>' +
    Object.keys(PRESETS).map(function (k) { var P = PRESETS[k]; var g = function (a, b) { return b ? g(b, a % b) : a; }, d = g(P.w, P.h);
      return "<tr><td><b>" + P.label + "</b></td><td>" + P.w + " × " + P.h + " px</td><td>" + (P.w / d) + ":" + (P.h / d) + "</td><td>" + where[k] + "</td></tr>"; }).join("") +
    '</table><p style="margin-top:12px;color:#6b6b70">JPG, PNG or WebP, up to 15 MB. Photos are automatically cropped to the right shape, resized and converted to fast-loading WebP. Bigger photos are fine; smaller ones work but may look soft. Keep important things in the centre — edges may be cropped.</p></div>';
}, after: function () {} };

S.account = { title: "Password", html: function () {
  return '<div class="card" style="max-width:460px"><label class="f">Username (leave empty to keep)</label><input type="text" id="pu">' +
    '<label class="f">Current password</label><input type="password" id="po" style="width:100%;padding:8px 10px;border:1px solid #e6e0d6;border-radius:8px">' +
    '<label class="f">New password (min 8)</label><input type="password" id="pn" style="width:100%;padding:8px 10px;border:1px solid #e6e0d6;border-radius:8px">' +
    '<button class="btn pri" id="pw" style="margin-top:12px">Change password</button><div class="err" id="pwe"></div></div>';
}, after: function () { $("#pw").onclick = function () { post({ action: "password", user: $("#pu").value, old: $("#po").value, "new": $("#pn").value }).then(function (j) { $("#pwe").textContent = j.ok ? "" : j.error; if (j.ok) toast("Password changed."); }); }; } };

function render() {
  var y = window.scrollY;
  BIND = []; IMGS = [];
  var s = S[section];
  $("#title").textContent = s.title;
  $("#view").innerHTML = s.html();
  wire(); wireImgs(); s.after();
  document.querySelectorAll("#side a[data-s]").forEach(function (a) { a.className = a.getAttribute("data-s") === section ? "on" : ""; });
  window.scrollTo(0, y);
}
function go(sec) { section = sec; try { localStorage.setItem("cp-sec", sec); } catch (e) {} render(); window.scrollTo(0, 0); $("#mnav").value = sec; }
document.querySelectorAll("#side a[data-s]").forEach(function (a) {
  a.onclick = function () { go(a.getAttribute("data-s")); };
  var o = document.createElement("option"); o.value = a.getAttribute("data-s"); o.textContent = a.textContent; $("#mnav").appendChild(o);
});
$("#mnav").onchange = function () { go(this.value); };
$("#save").onclick = function () {
  var b = this; b.disabled = true; b.textContent = "Saving…";
  post({ action: "save", content: JSON.stringify(C) }).then(function (j) {
    b.disabled = false; b.textContent = "💾 Save all changes";
    if (!j.ok) { toast("❌ " + j.error); return; }
    C = j.content; dirty = false; var s = $("#status"); s.textContent = "✓ Saved at " + j.saved + " — live on the website"; s.className = "status saved"; render();
  }).catch(function () { b.disabled = false; b.textContent = "💾 Save all changes"; toast("❌ Network error — not saved."); });
};
document.addEventListener("keydown", function (e) { if ((e.ctrlKey || e.metaKey) && e.key === "s") { e.preventDefault(); $("#save").click(); } });
$("#logout").onclick = function () { post({ action: "logout" }).then(function () { location.reload(); }); };
try { var last = localStorage.getItem("cp-sec"); if (last && S[last]) section = last; } catch (e) {}
go(section);
</script>
<?php endif; ?>
</body>
</html>
