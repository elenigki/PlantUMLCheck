// Simple, reliable upload UX with robust drag & drop.
// - Works for single files and whole folders (recursive) via dataTransfer.items.
// - One pretty button per box (+ hidden native input).
// - Lists show what's selected with tiny red "−" remove buttons.
// - Code-only hides Step 2 and keeps selections.

(() => {
  const $ = (sel, ctx) => (ctx || document).querySelector(sel);
  const $$ = (sel, ctx) => Array.from((ctx || document).querySelectorAll(sel));

  const form = $("#upload-form");
  const codeOnlyHidden = $("#codeOnly");
  const codeOnlyToggle = $("#codeonly-toggle");
  const dzContainer = $("#dz-container");

  const srcBox = $("#source-box");
  const srcInput = $("#source-input");
  const srcList = $("#source-list");
  const srcSummary = $("#source-summary");
  const srcClear = $("#source-clear");

  const pumlBox = $("#puml-box");
  const pumlInput = $("#plantuml-input");
  const pumlList = $("#plantuml-list");
  const pumlSummary = $("#puml-summary");
  const pumlClear = $("#puml-clear");

  // Keep selections in-memory until submit
  let sourceFiles = [];
  let plantumlFiles = [];

  const SRC_OK = (ext) => ext === ".java";
  const PUML_OK = (ext) =>
    [".puml", ".plantuml", ".iuml", ".txt"].includes(ext);
  const extOf = (name = "") =>
    name.lastIndexOf(".") >= 0
      ? name.slice(name.lastIndexOf(".")).toLowerCase()
      : "";
  const keyOf = (f) => `${f.name}|${f.size}|${f.lastModified}`;

  /* ---------- Render lists ---------- */
  function removeAt(arr, idx) {
    if (idx >= 0 && idx < arr.length) arr.splice(idx, 1);
  }

  function renderList(arr, ul, summaryEl, kind) {
    ul.innerHTML = "";
    if (arr.length === 0) {
      summaryEl.textContent =
        kind === "src"
          ? "No source selected yet."
          : "Optional. You can leave this empty if Code-only is ON.";
      return;
    }
    const folders = new Map();
    for (const f of arr) {
      const rel = f.webkitRelativePath || f.name;
      const top = rel.includes("/") ? rel.split("/")[0] : "(files)";
      folders.set(top, (folders.get(top) || 0) + 1);
    }
    const chips = Array.from(folders.entries())
      .map(([k, v]) => `${k} (${v})`)
      .join(" • ");
    summaryEl.textContent =
      kind === "src"
        ? `Selected ${arr.length} source file(s) — ${chips}`
        : `Selected ${arr.length} item(s) — ${chips}`;

    arr.forEach((f, i) => {
      const li = document.createElement("li");
      const rel = f.webkitRelativePath || f.name;
      li.innerHTML = `<span class="file-name">${rel}</span><button type="button" class="icon-btn remove-btn" data-index="${i}" title="Remove">−</button>`;
      ul.appendChild(li);
    });
    $$(".remove-btn", ul).forEach((btn) => {
      btn.addEventListener("click", () => {
        const idx = +btn.getAttribute("data-index");
        if (kind === "src") removeAt(sourceFiles, idx);
        else removeAt(plantumlFiles, idx);
        renderAll();
      });
    });
  }

  function renderAll() {
    renderList(sourceFiles, srcList, srcSummary, "src");
    renderList(plantumlFiles, pumlList, pumlSummary, "puml");
  }

  /* ---------- Add files (filter + de-dup) ---------- */
  function addFiles(kind, files) {
    const target = kind === "src" ? sourceFiles : plantumlFiles;
    const accept = kind === "src" ? SRC_OK : PUML_OK;
    Array.from(files || []).forEach((file) => {
      if (!accept(extOf(file.name))) return;
      const k = keyOf(file);
      if (!target.some((g) => keyOf(g) === k)) target.push(file);
    });
    renderAll();
  }

  /* ---------- Helpers: collect dropped files (files or folders) ---------- */
  async function filesFromDataTransfer(dt) {
    const out = [];

    // If items support directory traversal (Chrome/Edge), walk them
    if (
      dt.items &&
      dt.items.length &&
      typeof dt.items[0].webkitGetAsEntry === "function"
    ) {
      const entries = [];
      for (const item of dt.items) {
        const entry = item.webkitGetAsEntry && item.webkitGetAsEntry();
        if (entry) entries.push(entry);
      }
      const walkEntry = async (entry, path = "") => {
        if (entry.isFile) {
          await new Promise((res) =>
            entry.file((file) => {
              // Best effort to preserve a relative path feel (not standardized)
              try {
                Object.defineProperty(file, "webkitRelativePath", {
                  value: (path ? path + "/" : "") + file.name,
                });
              } catch {}
              out.push(file);
              res();
            })
          );
        } else if (entry.isDirectory) {
          const reader = entry.createReader();
          // readEntries can return partial results; loop until empty batch
          const readAll = async () => {
            const batch = await new Promise((resolve) =>
              reader.readEntries(resolve)
            );
            if (!batch.length) return;
            for (const e of batch)
              await walkEntry(e, path ? path + "/" + entry.name : entry.name);
            await readAll();
          };
          await readAll();
        }
      };
      for (const e of entries) await walkEntry(e, "");
      return out;
    }

    // Fallback: just use the files list
    return Array.from(dt.files || []);
  }

  /* ---------- Drag & drop, per box ---------- */
  // Prevent the browser from opening the file on drop anywhere on the page
  ["dragover", "drop"].forEach((ev) => {
    document.addEventListener(
      ev,
      (e) => {
        e.preventDefault();
      },
      false
    );
  });

  function enableDrop(cardEl, kind) {
    ["dragenter", "dragover"].forEach((ev) => {
      cardEl.addEventListener(ev, (e) => {
        e.preventDefault();
        e.stopPropagation();
        cardEl.classList.add("drag-over");
      });
    });
    cardEl.addEventListener("dragleave", (e) => {
      e.preventDefault();
      e.stopPropagation();
      cardEl.classList.remove("drag-over");
    });
    cardEl.addEventListener("drop", async (e) => {
      e.preventDefault();
      e.stopPropagation();
      cardEl.classList.remove("drag-over");

      const dt = e.dataTransfer;
      if (!dt) return;

      try {
        const files = await filesFromDataTransfer(dt);
        if (files && files.length) addFiles(kind, files);
      } catch (err) {
        console.error("Drop parse error:", err);
      }
    });
  }
  enableDrop(srcBox, "src");
  enableDrop(pumlBox, "puml");

  /* ---------- File inputs ---------- */
  srcInput.addEventListener("change", () => {
    addFiles("src", srcInput.files);
    // reset to allow picking same folder again
    srcInput.value = "";
  });
  pumlInput.addEventListener("change", () => {
    addFiles("puml", pumlInput.files);
    pumlInput.value = "";
  });

  /* ---------- Clear ---------- */
  srcClear.addEventListener("click", () => {
    sourceFiles = [];
    renderAll();
  });
  pumlClear.addEventListener("click", () => {
    plantumlFiles = [];
    renderAll();
  });

  /* ---------- Code-only toggle ---------- */
  codeOnlyToggle.addEventListener("change", () => {
    const on = codeOnlyToggle.checked;
    codeOnlyHidden.value = on ? "true" : "false";
    pumlBox.style.display = on ? "none" : "";
    dzContainer.classList.toggle("single", on);
  });
  dzContainer.classList.toggle("single", codeOnlyHidden.value === "true");

  /* ---------- Submit ---------- */
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const codeOnly = codeOnlyHidden.value === "true";
    if (sourceFiles.length === 0) {
      alert("Please select source code (folder or .java files).");
      return;
    }

    const fd = new FormData();
    sourceFiles.forEach((f) => fd.append("source", f, f.name));
    plantumlFiles.forEach((f) => fd.append("plantuml", f, f.name));
    fd.append("codeOnly", String(codeOnly));

    try {
      const resp = await fetch(form.getAttribute("action"), {
        method: "POST",
        body: fd,
      });
      if (resp.redirected) window.location.href = resp.url;
      else {
        const html = await resp.text();
        document.open();
        document.write(html);
        document.close();
      }
    } catch (err) {
      console.error("Upload failed", err);
      alert("Upload failed: " + (err?.message || err));
    }
  });

  renderAll();
})();
