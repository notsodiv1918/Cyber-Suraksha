/* Shareable warning image — turns a scam verdict into a simple poster-style
 * image ("⚠️ यह एक स्कैम मैसेज है") the user can forward to a family WhatsApp
 * group in one tap. Drawn on a <canvas>, matching the site's own tricolour
 * palette (saffron / india-green / navy tokens from style.css), then offered
 * via the Web Share API (mobile) or a plain download link (desktop). */
(function (global) {
  "use strict";

  var W = 900, H = 1100;

  function wrapText(ctx, text, x, y, maxWidth, lineHeight, align) {
    ctx.textAlign = align || "center";
    var words = text.split(" ");
    var line = "";
    for (var i = 0; i < words.length; i++) {
      var test = line ? line + " " + words[i] : words[i];
      if (ctx.measureText(test).width > maxWidth && line) {
        ctx.fillText(line, x, y);
        line = words[i];
        y += lineHeight;
      } else {
        line = test;
      }
    }
    if (line) ctx.fillText(line, x, y);
    return y;
  }

  function draw(result, lang) {
    var isEn = lang === "en";
    var canvas = document.createElement("canvas");
    canvas.width = W; canvas.height = H;
    var ctx = canvas.getContext("2d");

    var bannerColor = result.verdict === "scam" ? "#C62828" : result.verdict === "suspicious" ? "#9A6700" : "#0C6B05";
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, W, H);

    var bannerH = 280;
    ctx.fillStyle = bannerColor;
    ctx.fillRect(0, 0, W, bannerH);

    ctx.fillStyle = "#ffffff";
    ctx.font = "bold 46px 'Noto Sans Devanagari', 'Noto Sans', sans-serif";
    var headline = result.verdict === "scam"
      ? (isEn ? "⚠️ WARNING: This is a SCAM message" : "⚠️ चेतावनी: यह एक स्कैम मैसेज है")
      : (isEn ? "⚠️ CAUTION: This message is suspicious" : "⚠️ सावधान: यह मैसेज संदिग्ध है");
    wrapText(ctx, headline, W / 2, 110, W - 100, 56, "center");

    ctx.font = "26px 'Noto Sans Devanagari', 'Noto Sans', sans-serif";
    ctx.fillText(isEn ? "Checked with Cyber Suraksha" : "साइबर सुरक्षा से जाँचा गया", W / 2, bannerH - 30);

    ctx.fillStyle = "#F4F6F9";
    ctx.beginPath();
    ctx.roundRect ? ctx.roundRect(50, bannerH + 50, W - 100, H - bannerH - 220, 16) : ctx.rect(50, bannerH + 50, W - 100, H - bannerH - 220);
    ctx.fill();

    ctx.fillStyle = "#1B2430";
    ctx.font = "32px 'Noto Sans Devanagari', 'Noto Sans', sans-serif";
    var shown = (result.text || "").slice(0, 220);
    if ((result.text || "").length > 220) shown += "…";
    wrapText(ctx, "“" + shown + "”", 90, bannerH + 130, W - 180, 44, "left");

    ctx.fillStyle = "#5A6472";
    ctx.font = "24px 'Noto Sans Devanagari', 'Noto Sans', sans-serif";
    ctx.textAlign = "center";
    ctx.fillText(
      isEn ? "Forward this to warn your family and friends" : "अपने परिवार व मित्रों को सचेत करने हेतु आगे भेजें",
      W / 2, H - 50
    );

    return canvas;
  }

  function shareOrDownload(canvas, lang) {
    canvas.toBlob(function (blob) {
      var isEn = lang === "en";
      var file = new File([blob], "cyber-suraksha-warning.png", { type: "image/png" });
      if (global.navigator && navigator.canShare && navigator.canShare({ files: [file] })) {
        navigator.share({
          files: [file],
          title: isEn ? "Scam warning" : "स्कैम चेतावनी",
          text: isEn ? "This message looks like a scam — be careful." : "यह मैसेज स्कैम जैसा लगता है — सावधान रहें।",
        }).catch(function () {});
      } else {
        var url = URL.createObjectURL(blob);
        var a = document.createElement("a");
        a.href = url;
        a.download = "cyber-suraksha-warning.png";
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(function () { URL.revokeObjectURL(url); }, 2000);
      }
    }, "image/png");
  }

  function generateAndShare(result, lang) {
    var canvas = draw(result, lang);
    shareOrDownload(canvas, lang);
  }

  global.CGPoster = { draw, generateAndShare };
})(window);
