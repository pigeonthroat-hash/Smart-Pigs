function $(id) { return document.getElementById(id); }

var statusText = $("statusText");
var modifierGrid = $("modifierGrid");
var imageUrlInput = $("imageUrl");
var pigCountDisplay = $("pigCountDisplay");
var pigCountBadge = $("pigCountBadge");
var pigsList = $("pigsList");
var pigSize = $("pigSize");

var DEFAULT_IMAGE = "https://static.wikia.nocookie.net/neutral-characters/images/d/d0/PeppaPig.webp/revision/latest?cb=20250701022738";

var MODIFIER_DEFS = [
  { key: "noGravity", label: "No gravity" },
  { key: "bouncy", label: "Bouncy" },
  { key: "floaty", label: "Floaty" },
  { key: "moon", label: "Moon jump" },
  { key: "magnet", label: "Finger magnet" },
  { key: "superSpeed", label: "Super speed" },
  { key: "giant", label: "Giant" },
  { key: "tiny", label: "Tiny" },
  { key: "frozen", label: "Frozen" },
  { key: "chaos", label: "Chaos" },
  { key: "ghost", label: "Ghost" },
  { key: "nameTags", label: "Name tags" },
  { key: "party", label: "Party" },
  { key: "ragdoll", label: "Ragdoll" },
  { key: "rope", label: "Rope" },
  { key: "blank", label: "Blank face" },
  { key: "wetPile", label: "Wet pile" }
];

var currentModifiers = {};
for (var i = 0; i < MODIFIER_DEFS.length; i++) {
  var k = MODIFIER_DEFS[i].key;
  currentModifiers[k] = (k === "nameTags" || k === "ragdoll");
}

function hasBridge() {
  return !!(window.AndroidPigs && typeof window.AndroidPigs.send === "function");
}

function setStatus(text, isError) {
  if (!statusText) return;
  statusText.textContent = text;
  statusText.style.color = isError ? "#c92a2a" : "#555";
}

function bridgeSend(action, extra) {
  if (!hasBridge()) return { success: false, message: "No overlay bridge" };
  extra = extra || {};
  extra.action = action;
  try {
    return JSON.parse(window.AndroidPigs.send(JSON.stringify(extra)));
  } catch (e) {
    return { success: false, message: String(e) };
  }
}

function storageGet(defaults) {
  if (!hasBridge()) return defaults;
  try {
    var raw = window.AndroidPigs.storageGet(JSON.stringify(Object.keys(defaults)));
    var parsed = JSON.parse(raw);
    var out = {};
    for (var k in defaults) out[k] = defaults[k];
    for (var k2 in parsed) out[k2] = parsed[k2];
    return out;
  } catch (e) {
    return defaults;
  }
}

function storageSet(obj) {
  if (!hasBridge()) return;
  window.AndroidPigs.storageSet(JSON.stringify(obj));
}

function escapeHtml(str) {
  return String(str == null ? "" : str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function renderModifiers() {
  if (!modifierGrid) return;
  var html = "";
  for (var i = 0; i < MODIFIER_DEFS.length; i++) {
    var mod = MODIFIER_DEFS[i];
    var on = currentModifiers[mod.key] ? " on" : "";
    html += '<button type="button" class="mod-chip' + on + '" data-key="' + mod.key + '">' + mod.label + "</button>";
  }
  modifierGrid.innerHTML = html;
  var chips = modifierGrid.querySelectorAll(".mod-chip");
  for (var c = 0; c < chips.length; c++) {
    chips[c].onclick = function () {
      var key = this.getAttribute("data-key");
      currentModifiers[key] = !currentModifiers[key];
      if (key === "giant" && currentModifiers.giant) currentModifiers.tiny = false;
      if (key === "tiny" && currentModifiers.tiny) currentModifiers.giant = false;
      renderModifiers();
      var res = bridgeSend("setModifiers", { modifiers: currentModifiers });
      setStatus(res.success ? "Modifiers updated" : (res.message || "Saved locally"));
    };
  }
}

function setRunning(running, count) {
  var ids = ["btnStart", "btnStop", "btnAddPig", "btnAddFive", "btnRemovePig", "btnTreats", "btnBall", "btnTeddy", "btnCall"];
  for (var i = 0; i < ids.length; i++) {
    var el = $(ids[i]);
    if (!el) continue;
    if (ids[i] === "btnStart") el.disabled = running;
    else el.disabled = !running;
  }
  if (pigCountDisplay) pigCountDisplay.value = String(count || 0);
  if (pigCountBadge) {
    if (count > 0) {
      pigCountBadge.textContent = count + " pigs";
      pigCountBadge.classList.remove("hidden");
    } else {
      pigCountBadge.classList.add("hidden");
    }
  }
}

function statBar(label, value, cls) {
  var v = Math.round(value || 0);
  return '<div class="stat"><span class="stat-label">' + label + " " + v +
    '</span><div class="stat-bar-bg"><div class="stat-bar ' + cls + '" style="width:' + v + '%"></div></div></div>';
}

function renderPigs(pigs) {
  if (!pigsList) return;
  if (!pigs || !pigs.length) {
    pigsList.innerHTML = '<p class="empty-state">No pigs running.<br>Tap Start on the Play tab.</p>';
    return;
  }
  var html = "";
  for (var i = 0; i < pigs.length; i++) {
    var pig = pigs[i];
    var p = pig.personality || {};
    html += '<div class="pig-card">' +
      '<div class="pig-card-header"><span class="pig-name">' + escapeHtml(pig.name) + "</span>" +
      '<span class="pig-mood">' + escapeHtml(pig.mood || "") + "</span></div>" +
      '<div class="pig-state">' + escapeHtml(pig.state || "") + "</div>" +
      '<div class="pig-feeling">' + escapeHtml(pig.reason || pig.feeling || "") + "</div>" +
      '<div class="stats-grid">' +
      statBar("En", pig.energy, "energy") +
      statBar("Happy", pig.happiness, "happiness") +
      statBar("Bored", pig.boredom, "boredom") +
      statBar("Curious", pig.curiosity, "curiosity") +
      "</div>" +
      '<div class="personality-row">Play ' + Math.round((p.playfulness || 0) * 100) +
      " · Social " + Math.round((p.sociability || 0) * 100) +
      " · Lazy " + Math.round((p.laziness || 0) * 100) +
      " · Brave " + Math.round((p.bravery || 0) * 100) + "</div></div>";
  }
  pigsList.innerHTML = html;
}

function refreshInfo() {
  var res = bridgeSend("getInfo");
  if (res && res.success) {
    setRunning(!!res.running, (res.pigs && res.pigs.length) || 0);
    renderPigs(res.pigs || []);
    if (res.modifiers) {
      for (var key in res.modifiers) currentModifiers[key] = !!res.modifiers[key];
      renderModifiers();
    }
    if (res.config && res.config.imageUrl && imageUrlInput) {
      imageUrlInput.value = res.config.imageUrl;
    }
    setStatus(res.running ? "Pigs are bouncing on your screen" : "Ready – tap Start");
  } else {
    renderPigs([]);
    setStatus((res && res.message) || "Cannot talk to overlay", true);
  }
}

var pigPresets = [];

function renderPresets() {
  var box = $("presetList");
  if (!box) return;
  var stored = storageGet({ pigPresets: [] });
  pigPresets = stored.pigPresets || [];
  if (!pigPresets.length) {
    box.innerHTML = '<p class="empty-state">No custom types yet.</p>';
    return;
  }
  var html = "";
  for (var i = 0; i < pigPresets.length; i++) {
    var pr = pigPresets[i];
    html += '<div class="pig-card"><div class="pig-card-header"><span class="pig-name">' +
      escapeHtml(pr.name) + "</span></div>" +
      '<div class="pig-feeling">' + escapeHtml((pr.words || []).slice(0, 6).join(", ") || "") + "</div>" +
      '<div class="row wrap"><button type="button" class="btn btn-small btn-add-preset" data-i="' + i +
      '">Add to screen</button><button type="button" class="btn btn-small btn-del-preset" data-i="' + i +
      '">Delete</button></div></div>';
  }
  box.innerHTML = html;
  var adds = box.querySelectorAll(".btn-add-preset");
  for (var a = 0; a < adds.length; a++) {
    adds[a].onclick = function () {
      var preset = pigPresets[parseInt(this.getAttribute("data-i"), 10)];
      var res = bridgeSend("addPig", { preset: preset });
      setStatus(res.message || ("Added " + preset.name), !res.success);
      refreshInfo();
    };
  }
  var dels = box.querySelectorAll(".btn-del-preset");
  for (var d = 0; d < dels.length; d++) {
    dels[d].onclick = function () {
      pigPresets.splice(parseInt(this.getAttribute("data-i"), 10), 1);
      storageSet({ pigPresets: pigPresets });
      renderPresets();
    };
  }
}

function bind(id, fn) {
  var el = $(id);
  if (el) el.onclick = fn;
}

bind("btnStart", function () {
  var options = {
    pigCount: parseInt((pigCountDisplay && pigCountDisplay.value) || "2", 10) || 2,
    imageUrl: (imageUrlInput && imageUrlInput.value.trim()) || DEFAULT_IMAGE,
    modifiers: currentModifiers,
    pigWidth: parseInt((pigSize && pigSize.value) || "168", 10) || 168
  };
  var res = bridgeSend("start", { options: options });
  setStatus(res.message || (res.success ? "Started" : "Start failed"), !res.success);
  refreshInfo();
});
bind("btnStop", function () { bridgeSend("remove"); refreshInfo(); });
bind("btnRefresh", refreshInfo);
bind("btnAddPig", function () { bridgeSend("addPig"); refreshInfo(); });
bind("btnAddFive", function () { for (var i = 0; i < 5; i++) bridgeSend("addPig"); refreshInfo(); });
bind("btnRemovePig", function () { bridgeSend("removePig"); refreshInfo(); });
bind("btnTreats", function () { setStatus((bridgeSend("dropTreats", { count: 5 }).message) || "Snacks"); });
bind("btnBall", function () { setStatus((bridgeSend("addBall").message) || "Ball"); });
bind("btnTeddy", function () { setStatus((bridgeSend("addTeddy").message) || "Toy"); });
bind("btnCall", function () { setStatus((bridgeSend("callPigs").message) || "Called"); });
bind("btnApplyImage", function () {
  var url = imageUrlInput ? imageUrlInput.value.trim() : "";
  if (!url) { setStatus("Enter an image URL", true); return; }
  bridgeSend("setImage", { url: url });
  setStatus("Image saved");
});
bind("btnSavePreset", function () {
  function n(id) { return (parseInt(($(id) && $(id).value) || "50", 10) || 50) / 100; }
  var wordsRaw = ($("presetWords") && $("presetWords").value) || "";
  var parts = wordsRaw.split(",");
  var clean = [];
  for (var i = 0; i < parts.length; i++) {
    var w = parts[i].replace(/^\s+|\s+$/g, "");
    if (w) clean.push(w);
  }
  var nameEl = $("presetName");
  var imgEl = $("presetImage");
  var preset = {
    id: "p_" + Date.now(),
    name: (((nameEl && nameEl.value) || "Custom Pig").replace(/^\s+|\s+$/g, "")).slice(0, 18),
    imageUrl: (((imgEl && imgEl.value) || (imageUrlInput && imageUrlInput.value) || DEFAULT_IMAGE).replace(/^\s+|\s+$/g, "")),
    words: clean.slice(0, 24),
    personality: {
      playfulness: n("pPlay"),
      curiosity: n("pCur"),
      sociability: n("pSoc"),
      laziness: n("pLazy"),
      bravery: n("pBrave")
    }
  };
  var stored = storageGet({ pigPresets: [] });
  pigPresets = stored.pigPresets || [];
  pigPresets.push(preset);
  storageSet({ pigPresets: pigPresets });
  setStatus("Saved type " + preset.name);
  renderPresets();
});

if (pigSize) {
  pigSize.oninput = function () {
    var lab = $("pigSizeLabel");
    if (lab) lab.textContent = pigSize.value + "px";
  };
  pigSize.onchange = function () {
    bridgeSend("setPigSize", { size: parseInt(pigSize.value, 10) });
  };
}

var tabs = document.querySelectorAll(".tab");
for (var t = 0; t < tabs.length; t++) {
  tabs[t].onclick = function () {
    var name = this.getAttribute("data-tab");
    for (var i = 0; i < tabs.length; i++) tabs[i].classList.remove("on");
    this.classList.add("on");
    var ids = ["play", "pigs", "presets"];
    for (var j = 0; j < ids.length; j++) {
      var sec = $("tab-" + ids[j]);
      if (sec) {
        if (ids[j] === name) sec.classList.remove("hidden");
        else sec.classList.add("hidden");
      }
    }
    if (name === "pigs") refreshInfo();
    if (name === "presets") renderPresets();
  };
}

setStatus("JS loaded");
if (imageUrlInput && !imageUrlInput.value) imageUrlInput.value = DEFAULT_IMAGE;
renderModifiers();
renderPresets();
refreshInfo();