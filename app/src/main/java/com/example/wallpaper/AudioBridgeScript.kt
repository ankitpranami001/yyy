package com.example.wallpaper

/**
 * Safe, minimal JavaScript bridge injected into local HTML wallpapers.
 * Controls HTML5 <audio>/<video> volume/mute/playback and tracks Web Audio API AudioContext
 * instances for clean suspend/resume on visibility and sound toggles without mutating
 * native WebIDL properties on AudioContext.
 */
object AudioBridgeScript {

    val HEAD_INJECTION: String = """
<script>
(function() {
  if (window.__liveHtmlBridgeReady) return;
  window.__liveHtmlBridgeReady = true;

  var state = { soundEnabled: true, volume: 0.75, visible: true };
  var audioContexts = [];

  function applyMediaEl(el) {
    try {
      var active = state.soundEnabled && state.visible;
      el.volume = Math.max(0, Math.min(1, state.volume));
      el.muted = !active;
      if (!state.visible) {
        if (!el.paused) {
          el.dataset.__liveHtmlWasPlaying = '1';
          el.pause();
        }
      } else if (active && (el.dataset.__liveHtmlWasPlaying === '1' || el.autoplay)) {
        el.dataset.__liveHtmlWasPlaying = '0';
        var p = el.play();
        if (p && p.catch) p.catch(function(){});
      }
    } catch (_) {}
  }

  function scanMedia() {
    try {
      var els = document.querySelectorAll('audio, video');
      for (var i = 0; i < els.length; i++) {
        applyMediaEl(els[i]);
      }
    } catch (_) {}
  }

  function applyAudioContext(ctx) {
    try {
      if (!ctx) return;
      var active = state.soundEnabled && state.visible;
      if (active) {
        if (ctx.state === 'suspended' && typeof ctx.resume === 'function') {
          ctx.resume().catch(function(){});
        }
      } else {
        if (ctx.state === 'running' && typeof ctx.suspend === 'function') {
          ctx.suspend().catch(function(){});
        }
      }
    } catch (_) {}
  }

  function wrapAudioContext(OriginalAC) {
    if (!OriginalAC) return OriginalAC;
    function TrackedAudioContext(options) {
      var ctx = options !== undefined ? new OriginalAC(options) : new OriginalAC();
      audioContexts.push(ctx);
      if (!state.soundEnabled || !state.visible) {
        try {
          if (typeof ctx.suspend === 'function') ctx.suspend().catch(function(){});
        } catch (_) {}
      }
      return ctx;
    }
    TrackedAudioContext.prototype = OriginalAC.prototype;
    return TrackedAudioContext;
  }

  try {
    if (window.AudioContext) window.AudioContext = wrapAudioContext(window.AudioContext);
    if (window.webkitAudioContext) window.webkitAudioContext = wrapAudioContext(window.webkitAudioContext);
  } catch (_) {}

  window.__liveHtmlState = state;
  window.__liveHtmlApplyState = function(soundEnabled, volume, visible) {
    state.soundEnabled = !!soundEnabled;
    state.volume = typeof volume === 'number' ? volume : 0.75;
    state.visible = !!visible;
    for (var i = 0; i < audioContexts.length; i++) {
      applyAudioContext(audioContexts[i]);
    }
    scanMedia();
    try {
      window.dispatchEvent(new CustomEvent('livehtml:state', { detail: state }));
    } catch (_) {}
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', scanMedia);
  } else {
    scanMedia();
  }
})();
</script>
""".trimIndent()

    fun buildApplyStateJs(soundEnabled: Boolean, volume: Float, visible: Boolean): String {
        val safeVol = volume.coerceIn(0f, 1f)
        return "if(window.__liveHtmlApplyState){window.__liveHtmlApplyState($soundEnabled,$safeVol,$visible);}"
    }

    fun injectIntoHtml(html: String): String {
        val headIdx = html.indexOf("<head>", ignoreCase = true)
        if (headIdx >= 0) {
            val insertPos = headIdx + "<head>".length
            return html.substring(0, insertPos) + "\n" + HEAD_INJECTION + "\n" + html.substring(insertPos)
        }
        val htmlTagIdx = html.indexOf("<html", ignoreCase = true)
        if (htmlTagIdx >= 0) {
            val closeBracket = html.indexOf('>', htmlTagIdx)
            if (closeBracket >= 0) {
                val insertPos = closeBracket + 1
                return html.substring(0, insertPos) + "\n" + HEAD_INJECTION + "\n" + html.substring(insertPos)
            }
        }
        return HEAD_INJECTION + "\n" + html
    }
}
