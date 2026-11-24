/* Draws the standalone "top 5 scam signs" poster onto a canvas for download/
 * share — a village-to-village forwardable image, big Hindi text, icons only
 * (drawn as simple numbered badges, no emoji, matching the site's icon rules). */
(function (global) {
  "use strict";

  var SIGNS = [
    { hi: "कभी भी OTP, पिन या पासवर्ड किसी को न बताएँ", en: "Never share your OTP, PIN or password with anyone" },
    { hi: "बहुत जल्दी, धमकी या डर दिखाने वाला कॉल/मैसेज", en: "A call/message that rushes you, threatens or scares you" },
    { hi: "बिना भाग लिए इनाम या लॉटरी जीतने का दावा", en: "A prize or lottery win you never entered" },
    { hi: "अनजान लिंक पर क्लिक करने को कहा जाना", en: "Being told to click an unknown link" },
    { hi: "फोन पर पुलिस/अधिकारी बनकर गिरफ़्तारी की धमकी", en: "Someone on the phone posing as police/official, threatening arrest" },
  ];

  function wrapText(ctx, text, x, y, maxWidth, lineHeight, align) {
    ctx.textAlign = align || "left";
    var words = text.split(" ");
    var line = "";
    var lines = 0;
    for (var i = 0; i < words.length; i++) {
      var test = line ? line + " " + words[i] : words[i];
      if (ctx.measureText(test).width > maxWidth && line) {
        ctx.fillText(line, x, y + lines * lineHeight);
        line = words[i];
        lines++;
      } else {
        line = test;
      }
    }
    if (line) ctx.fillText(line, x, y + lines * lineHeight);
    return lines + 1;
  }

  function draw(lang) {
    var isEn = lang === "en";
    var W = 900, H = 1300;
    var canvas = document.createElement("canvas");
    canvas.width = W; canvas.height = H;
    var ctx = canvas.getContext("2d");

    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, W, H);

    ctx.fillStyle = "#0b2447";
    ctx.fillRect(0, 0, W, 190);
    ctx.fillStyle = "#FF9933";
    ctx.fillRect(0, 0, W, 10);
    ctx.fillStyle = "#138808";
    ctx.fillRect(0, 180, W, 10);

    ctx.fillStyle = "#ffffff";
    ctx.font = "bold 44px 'Noto Sans Devanagari', 'Noto Sans', sans-serif";
    ctx.textAlign = "center";
    ctx.fillText(isEn ? "5 SIGNS OF A SCAM" : "स्कैम के 5 संकेत", W / 2, 90);
    ctx.font = "22px 'Noto Sans Devanagari', 'Noto Sans', sans-serif";
    ctx.fillText(isEn ? "Cyber Suraksha — Chhattisgarh" : "साइबर सुरक्षा — छत्तीसगढ़", W / 2, 135);

    var y = 260;
    var rowH = 200;
    SIGNS.forEach(function (s, i) {
      ctx.fillStyle = "#FF9933";
      ctx.beginPath();
      ctx.arc(95, y + 35, 44, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = "#211400";
      ctx.font = "bold 44px sans-serif";
      ctx.textAlign = "center";
      ctx.fillText(String(i + 1), 95, y + 50);

      ctx.fillStyle = "#1B2430";
      ctx.font = "bold 30px 'Noto Sans Devanagari', 'Noto Sans', sans-serif";
      ctx.textAlign = "left";
      wrapText(ctx, isEn ? s.en : s.hi, 165, y + 25, W - 220, 42, "left");

      if (i < SIGNS.length - 1) {
        ctx.strokeStyle = "#E1E6EC";
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(60, y + rowH - 30);
        ctx.lineTo(W - 60, y + rowH - 30);
        ctx.stroke();
      }
      y += rowH;
    });

    ctx.fillStyle = "#138808";
    ctx.fillRect(0, H - 110, W, 110);
    ctx.fillStyle = "#ffffff";
    ctx.font = "bold 26px 'Noto Sans Devanagari', 'Noto Sans', sans-serif";
    ctx.textAlign = "center";
    ctx.fillText(isEn ? "Suspect a scam? Call 1930" : "स्कैम लगे तो 1930 पर कॉल करें", W / 2, H - 60);
    ctx.font = "20px 'Noto Sans Devanagari', 'Noto Sans', sans-serif";
    ctx.fillText(isEn ? "Forward this poster to your family & village group" : "यह पोस्टर अपने परिवार व गाँव के समूह में आगे भेजें", W / 2, H - 28);

    return canvas;
  }

  function download(canvas) {
    canvas.toBlob(function (blob) {
      var url = URL.createObjectURL(blob);
      var a = document.createElement("a");
      a.href = url;
      a.download = "cyber-suraksha-5-scam-signs.png";
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(function () { URL.revokeObjectURL(url); }, 2000);
    }, "image/png");
  }

  function shareOrDownload(canvas, lang) {
    canvas.toBlob(function (blob) {
      var isEn = lang === "en";
      var file = new File([blob], "cyber-suraksha-5-scam-signs.png", { type: "image/png" });
      if (global.navigator && navigator.canShare && navigator.canShare({ files: [file] })) {
        navigator.share({
          files: [file],
          title: isEn ? "5 signs of a scam" : "स्कैम के 5 संकेत",
        }).catch(function () {});
      } else {
        download(canvas);
      }
    }, "image/png");
  }

  global.CGScamSignsPoster = { draw, download, shareOrDownload, SIGNS };
})(window);
