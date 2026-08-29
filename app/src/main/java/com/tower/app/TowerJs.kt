package com.tower.app

/**
 * JavaScript-движок Tower: работает с ЛЮБЫМ <video> на странице
 * (YouTube, VK, Rutube, Twitch, плееры на сайтах, локальный плеер Tower).
 */
object TowerJs {

    const val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    const val UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    /** Ядро: внедряется один раз на страницу. */
    val CORE = """
    (function () {
      if (window.__TOWER__) { return; }
      window.__TOWER__ = true;

      var st = { desired: 1, loop: false, ab: null, autoSkip: true,
                 focus: false, shorts: false, audioOnly: false, muted: false, vol: 1 };

      function vids() { return document.getElementsByTagName('video'); }
      function v() {
        var l = vids();
        if (!l.length) { return null; }
        var best = l[0];
        for (var i = 0; i < l.length; i++) {
          var e = l[i];
          if (e.currentTime > 0 && !e.paused) { best = e; break; }
        }
        return best;
      }
      function css(id, text) {
        var el = document.getElementById(id);
        if (!text) { if (el && el.parentNode) { el.parentNode.removeChild(el); } return; }
        if (!el) {
          el = document.createElement('style');
          el.id = id;
          (document.head || document.documentElement).appendChild(el);
        }
        el.textContent = text;
      }
      function isAd() {
        return !!document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay, .ytp-ad-module, .video-ads');
      }
      function applyRate() {
        var r = isAd() ? 16 : st.desired;
        var l = vids();
        for (var i = 0; i < l.length; i++) {
          try { if (Math.abs(l[i].playbackRate - r) > 0.01) { l[i].playbackRate = r; l[i].defaultPlaybackRate = r; } } catch (e) {}
        }
      }
      function clampTime(e, t) {
        if (!e) { return 0; }
        var d = (e.duration && isFinite(e.duration)) ? e.duration : 0;
        if (t < 0) { t = 0; }
        if (d && t > d - 0.2) { t = Math.max(0, d - 0.2); }
        return t;
      }

      var FOCUS_CSS = [
        '#related, #secondary, #secondary-inner, ytd-watch-next-secondary-results-renderer,',
        'ytd-comments, #comments, ytd-item-section-renderer, #chat, ytd-live-chat-frame,',
        '#donation-shelf, #masthead-container, .ytp-endscreen-content, .ytp-ce-element,',
        'ytd-merch-shelf-renderer, ytd-engagement-panel-section-list-renderer,',
        'ytd-popup-container, .html5-endscreen { display:none !important; }',
        'ytd-watch-flexy #primary, ytd-watch-flexy #player { max-width:100% !important; width:100% !important; }'
      ].join('\n');

      var SHORTS_CSS = [
        'a[href^="/shorts"], ytd-shorts, ytd-reel-shelf-renderer, ytd-rich-shelf-renderer[is-shorts],',
        'ytd-thumbnail-overlay-time-status-renderer[overlay-style="SHORTS"], .reel-shelf,',
        'ytd-shorts-lockup-view-model, ytd-rich-item-renderer[is-shorts] { display:none !important; }'
      ].join('\n');

      window.Tower = {
        ping: function () { return 'ok'; },

        setRate: function (r) { st.desired = r; applyRate(); },
        getRate: function () { var e = v(); return e ? e.playbackRate : st.desired; },

        playPause: function () {
          var e = v();
          if (!e) { return false; }
          if (e.paused) { e.play(); return true; }
          e.pause(); return false;
        },
        play: function () { var e = v(); if (e) { e.play(); } },
        pause: function () { var e = v(); if (e) { e.pause(); } },
        seek: function (d) {
          var e = v();
          if (e) { try { e.currentTime = clampTime(e, e.currentTime + d); } catch (err) {} }
        },

        setLoop: function (b) {
          st.loop = b;
          var l = vids();
          for (var i = 0; i < l.length; i++) { l[i].loop = b; }
        },

        setAB: function (a, b) { st.ab = { a: a, b: b }; var e = v(); if (e) { e.currentTime = a; } },
        clearAB: function () { st.ab = null; },
        hasAB: function () { return !!st.ab; },

        setAudioOnly: function (b) {
          st.audioOnly = b;
          css('tower-audio', b ? 'video { opacity:0.001 !important; }\n html,body { background:#000 !important; }' : '');
        },

        setMuted: function (b) {
          st.muted = b;
          var l = vids();
          for (var i = 0; i < l.length; i++) { l[i].muted = b; }
        },

        setVolume: function (x) {
          st.vol = x;
          var l = vids();
          for (var i = 0; i < l.length; i++) {
            try { l[i].volume = Math.max(0, Math.min(1, x)); } catch (e) {}
          }
        },

        skipAd: function () {
          var sels = ['.ytp-ad-skip-button', '.ytp-ad-skip-button-modern', '.videoAdUiSkipButton',
                      'button.ytp-skip-ad-button', '.ytp-ad-skip-button-container button',
                      '.ytp-ad-overlay-close-button', '.ytp-ad-text-overlay button'];
          for (var i = 0; i < sels.length; i++) {
            var b = document.querySelector(sels[i]);
            if (b) { b.click(); return true; }
          }
          var e = v();
          if (e && isAd()) {
            try { e.playbackRate = 16; } catch (err) {}
            try { if (e.duration && isFinite(e.duration)) { e.currentTime = e.duration; } } catch (err2) {}
            var sk = document.querySelector('.ytp-ad-skip-button');
            if (sk) { sk.click(); }
            return true;
          }
          return false;
        },

        setAutoSkip: function (b) { st.autoSkip = b; },
        setFocus: function (b) { st.focus = b; css('tower-focus', b ? FOCUS_CSS : ''); },
        setHideShorts: function (b) {
          st.shorts = b;
          css('tower-shorts', b ? SHORTS_CSS : '');
          if (b) { window.Tower.removeShorts(); }
        },
        removeShorts: function () {
          var as = document.querySelectorAll('a[href^="/shorts"], a[href*="/shorts/"]');
          for (var i = 0; i < as.length; i++) {
            var a = as[i];
            var host = null;
            if (a.closest) {
              host = a.closest('ytd-rich-item-renderer, ytd-video-renderer, ytd-grid-video-renderer, ytd-compact-video-renderer, ytd-guide-entry-renderer, ytd-reel-item-renderer, ytd-shorts-lockup-view-model');
            }
            var t = host || a;
            if (t && t.parentNode) { try { t.parentNode.removeChild(t); } catch (e) {} }
          }
        },

        fullscreen: function () {
          var e = v();
          if (!e) { return false; }
          if (e.requestFullscreen) { e.requestFullscreen(); return true; }
          if (e.webkitEnterFullscreen) { e.webkitEnterFullscreen(); return true; }
          var b = document.querySelector('.ytp-fullscreen-button');
          if (b) { b.click(); return true; }
          return false;
        },

        pip: function () {
          var e = v();
          if (!e || !e.requestPictureInPicture) { return false; }
          try {
            var p = e.requestPictureInPicture();
            if (p && p.catch) { p.catch(function () {}); }
            return true;
          } catch (err) { return false; }
        },

        inPip: function () { return !!document.pictureInPictureElement; },

        info: function () {
          var e = v();
          return JSON.stringify({
            hasVideo: !!e,
            title: (document.title || '').slice(0, 160),
            url: location.href,
            position: e ? e.currentTime : 0,
            duration: (e && e.duration && isFinite(e.duration)) ? e.duration : 0,
            rate: e ? e.playbackRate : st.desired,
            paused: e ? e.paused : true,
            muted: e ? e.muted : st.muted,
            ad: isAd(),
            audioOnly: st.audioOnly
          });
        }
      };

      setInterval(function () {
        try { if (st.autoSkip) { window.Tower.skipAd(); } } catch (e) {}
        try { applyRate(); } catch (e2) {}
        try { if (st.shorts) { window.Tower.removeShorts(); } } catch (e3) {}
      }, 700);

      setInterval(function () {
        if (!st.ab) { return; }
        var e = v();
        if (e && e.currentTime >= st.ab.b) {
          try { e.currentTime = st.ab.a; } catch (err) {}
        }
      }, 250);

      document.addEventListener('play', function (ev) {
        var e = ev.target;
        if (e && e.tagName === 'VIDEO') { applyRate(); }
      }, true);

      document.addEventListener('ratechange', function (ev) {
        var e = ev.target;
        if (e && e.tagName === 'VIDEO' && !isAd() && Math.abs(e.playbackRate - st.desired) > 0.01) {
          try { e.playbackRate = st.desired; } catch (err) {}
        }
      }, true);

      document.addEventListener('loadedmetadata', function (ev) {
        var e = ev.target;
        if (e && e.tagName === 'VIDEO') {
          applyRate();
          e.loop = st.loop;
          e.muted = st.muted;
          try { e.volume = st.vol; } catch (err) {}
        }
      }, true);
    })();
    """.trimIndent()

    /** Локальный HTML5-плеер для прямых ссылок на видео/аудио (mp4, webm, m3u8, mp3…). */
    fun playerHtml(url: String, title: String): String = RAW_PLAYER
        .replace("__SRC__", url.replace("\"", "&quot;"))
        .replace("__TITLE__", title.replace("<", "").replace(">", ""))

    private val RAW_PLAYER = """
    <!doctype html>
    <html>
    <head>
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=5">
    <title>__TITLE__</title>
    <style>
      html,body { margin:0; padding:0; background:#000; height:100%; overflow:hidden; }
      #wrap { position:absolute; inset:0; display:flex; align-items:center; justify-content:center; }
      video { width:100%; height:100%; object-fit:contain; background:#000; }
      #msg { position:absolute; left:0; right:0; bottom:8px; text-align:center;
             color:#9AA2B8; font:12px system-ui, sans-serif; padding:0 12px; }
    </style>
    </head>
    <body>
      <div id="wrap"><video id="tv" playsinline webkit-playsinline controls autoplay src="__SRC__"></video></div>
      <div id="msg">Tower player · __TITLE__</div>
      <script src="https://cdn.jsdelivr.net/npm/hls.js@1.5.13/dist/hls.min.js"></script>
      <script>
      (function () {
        var v = document.getElementById('tv');
        var src = v.getAttribute('src');
        var msg = document.getElementById('msg');
        if (src && src.indexOf('.m3u8') !== -1 && window.Hls) {
          if (v.canPlayType('application/vnd.apple.mpegurl')) {
            v.src = src;
          } else if (window.Hls.isSupported()) {
            var hls = new window.Hls({ maxBufferLength: 30 });
            hls.loadSource(src);
            hls.attachMedia(v);
            hls.on(window.Hls.Events.ERROR, function (e, d) {
              if (d && d.fatal) { hls.destroy(); v.src = src; }
            });
          }
        }
        v.addEventListener('error', function () {
          msg.textContent = 'Не удалось открыть поток. Проверьте ссылку.';
        });
        v.addEventListener('dblclick', function () {
          if (document.fullscreenElement) { document.exitFullscreen(); }
          else if (v.requestFullscreen) { v.requestFullscreen(); }
        });
      })();
      </script>
    </body>
    </html>
    """.trimIndent()

    /** Похоже ли, что ссылка — прямой медиа-файл? */
    fun isDirectMedia(url: String): Boolean {
        val u = url.lowercase()
        val exts = arrayOf(
            ".mp4", ".m4v", ".webm", ".mkv", ".mov", ".avi", ".m3u8", ".mpd",
            ".mp3", ".m4a", ".aac", ".ogg", ".oga", ".flac", ".wav", ".ts"
        )
        if (exts.any { u.substringBefore('?').endsWith(it) }) return true
        return u.contains("mime=video") || u.contains("mime=audio")
    }
}
