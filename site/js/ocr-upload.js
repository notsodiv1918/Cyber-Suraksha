/* On-device OCR for the message detector: lets the user upload a screenshot or a short
   video of a message instead of typing it. Images are OCR'd directly; videos have a few
   frames grabbed via <canvas> and each frame is OCR'd, then the results are merged.
   Needs Tesseract.js (loaded before this file) — the engine + language data download once
   from a CDN the first time it runs, after which the browser caches them. */
(function(){
  var uploadBtn = document.getElementById('upload-btn');
  var fileInput = document.getElementById('media-upload');
  var statusEl = document.getElementById('ocr-status');
  var textarea = document.getElementById('msg-text');
  if(!uploadBtn || !fileInput || !textarea) return;

  function isEn(){ return document.documentElement.lang === 'en'; }
  function setStatus(hi, en){
    statusEl.style.display = 'block';
    statusEl.textContent = isEn() ? en : hi;
  }
  function clearStatus(){ statusEl.style.display = 'none'; statusEl.textContent = ''; }

  uploadBtn.addEventListener('click', function(){ fileInput.click(); });

  fileInput.addEventListener('change', function(){
    var file = fileInput.files && fileInput.files[0];
    if(!file) return;
    if(file.type.indexOf('image/') === 0){
      handleImage(file);
    } else if(file.type.indexOf('video/') === 0){
      handleVideo(file);
    } else {
      setStatus('केवल फ़ोटो या वीडियो फ़ाइल समर्थित है।', 'Only image or video files are supported.');
    }
    fileInput.value = '';
  });

  function ocrImageSource(source){
    return Tesseract.recognize(source, 'eng+hin', {
      logger: function(m){
        if(m.status === 'recognizing text'){
          var pct = Math.round((m.progress || 0) * 100);
          setStatus('टेक्स्ट पहचाना जा रहा है… ' + pct + '%', 'Reading text… ' + pct + '%');
        }
      }
    }).then(function(res){ return (res.data.text || '').trim(); });
  }

  function finishWithText(text){
    clearStatus();
    if(!text){
      setStatus('इस फ़ाइल में कोई पठनीय टेक्स्ट नहीं मिला।', 'No readable text found in that file.');
      return;
    }
    textarea.value = text;
    if(typeof runCheck === 'function') runCheck();
  }

  function handleImage(file){
    setStatus('छवि लोड हो रही है…', 'Loading image…');
    var url = URL.createObjectURL(file);
    var img = new Image();
    img.onload = function(){
      ocrImageSource(img).then(function(text){
        URL.revokeObjectURL(url);
        finishWithText(text);
      }).catch(function(){
        URL.revokeObjectURL(url);
        setStatus('टेक्स्ट पढ़ने में समस्या हुई।', 'Could not read text from that image.');
      });
    };
    img.onerror = function(){
      setStatus('यह फ़ाइल नहीं खुल पाई।', 'Could not open that file.');
    };
    img.src = url;
  }

  function grabFrame(video, canvas){
    var ctx = canvas.getContext('2d');
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL('image/png');
  }

  function mergeLines(frames){
    var seen = {};
    return frames.join('\n').split(/\n+/).filter(function(line){
      line = line.trim();
      if(!line || seen[line]) return false;
      seen[line] = true;
      return true;
    }).join('\n').trim();
  }

  /* Captures frames while the video actually plays, instead of seeking to fractions of
     video.duration — some recordings (e.g. MediaRecorder-captured webm) report an
     unreliable/Infinity duration, which would break a seek-based approach. */
  function handleVideo(file){
    setStatus('वीडियो लोड हो रहा है…', 'Loading video…');
    var url = URL.createObjectURL(file);
    var video = document.createElement('video');
    video.muted = true;
    video.playsInline = true;
    video.preload = 'auto';
    video.src = url;
    var canvas = document.createElement('canvas');
    var maxFrames = 3;
    var frameGapMs = 600;
    var frames = [];
    var done = false;

    function finish(){
      if(done) return;
      done = true;
      video.pause();
      URL.revokeObjectURL(url);
      finishWithText(mergeLines(frames));
    }

    video.addEventListener('error', function(){
      done = true;
      setStatus('यह वीडियो फ़ाइल नहीं खुल पाई।', 'Could not open that video file.');
    });

    video.addEventListener('canplay', function(){
      if(done) return;
      video.play().catch(function(){});
      setTimeout(function captureLoop(){
        if(done || frames.length >= maxFrames) { finish(); return; }
        setStatus('वीडियो से फ्रेम पढ़े जा रहे हैं… (' + (frames.length+1) + '/' + maxFrames + ')',
                   'Reading video frames… (' + (frames.length+1) + '/' + maxFrames + ')');
        var dataUrl = grabFrame(video, canvas);
        ocrImageSource(dataUrl).then(function(text){
          frames.push(text);
          if(done) return;
          if(frames.length >= maxFrames || video.ended) finish();
          else setTimeout(captureLoop, frameGapMs);
        }).catch(function(){
          if(done) return;
          if(frames.length >= maxFrames || video.ended) finish();
          else setTimeout(captureLoop, frameGapMs);
        });
      }, 150);
    });

    /* safety net in case playback/canplay never fires (corrupt file, unsupported codec) */
    setTimeout(function(){ if(!done && frames.length === 0) finish(); }, 8000);
  }
})();
