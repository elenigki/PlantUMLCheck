// upload.js — highlights, lists, and code-only handling with refresh

(() => {
  const $ = (sel, ctx = document) => ctx.querySelector(sel);
  const $$ = (sel, ctx = document) => Array.from(ctx.querySelectorAll(sel));

  const params = new URLSearchParams(location.search);
  const codeOnlyParam = params.get("codeOnly") === "true";

  // elements (some may be absent if codeOnly=true)
  const plantumlInput = $("#plantuml-input");
  const sourceInput = $("#source-input");
  const plantumlList = $("#plantuml-list");
  const sourceList = $("#source-list");
  const form = $(".upload-form");
  const codeOnlyField = $("#codeOnly");

  // toggle switch (controls refresh)
  const codeOnlyToggle = $("#code-only-toggle");
  if (codeOnlyToggle) {
    codeOnlyToggle.addEventListener("change", () => {
      const newVal = codeOnlyToggle.checked ? "true" : "false";
      const p = new URLSearchParams(location.search);
      p.set("codeOnly", newVal);
      location.search = p.toString(); // refresh to re-render with/without Step 2
    });
  }

  // keep hidden field in sync with query
  if (codeOnlyField) codeOnlyField.value = String(codeOnlyParam);

  // render file names to a UL
  function renderList(input, list) {
    if (!input || !list) return;
    list.innerHTML = "";
    const files = Array.from(input.files || []);
    files.forEach((f) => {
      const li = document.createElement("li");
      li.textContent = f.webkitRelativePath || f.name;
      list.appendChild(li);
    });
  }

  // change listeners
  if (plantumlInput)
    plantumlInput.addEventListener("change", () =>
      renderList(plantumlInput, plantumlList)
    );
  if (sourceInput)
    sourceInput.addEventListener("change", () =>
      renderList(sourceInput, sourceList)
    );

  // make dropzones nicer
  $$(".dropzone").forEach((zone) => {
    zone.addEventListener("dragover", (e) => {
      e.preventDefault();
      zone.classList.add("drag");
    });
    zone.addEventListener("dragleave", () => zone.classList.remove("drag"));
    zone.addEventListener("drop", (e) => {
      e.preventDefault();
      zone.classList.remove("drag");

      const isPuml = zone.id === "puml-box";
      const targetInput = isPuml ? plantumlInput : sourceInput;
      const targetList = isPuml ? plantumlList : sourceList;
      if (!targetInput) return;

      const dt = new DataTransfer();
      Array.from(e.dataTransfer.files || []).forEach((f) => dt.items.add(f));
      targetInput.files = dt.files;

      renderList(targetInput, targetList);
    });
  });

  // intercept submit:
  // - Source required always
  // - If NOT code-only and PlantUML empty, ask via modal
  form.addEventListener("submit", (e) => {
    const sourceEmpty = !sourceInput || sourceInput.files.length === 0;
    if (sourceEmpty) {
      alert("Please add your source code (folder or .java files).");
      e.preventDefault();
      return;
    }

    if (!codeOnlyParam) {
      const plantumlEmpty = !plantumlInput || plantumlInput.files.length === 0;
      if (plantumlEmpty) {
        e.preventDefault();
        const modal = $("#code-only-modal");
        const yes = $("#code-only-yes");
        const no = $("#code-only-no");
        if (modal && yes && no) {
          modal.hidden = false;
          yes.onclick = () => {
            codeOnlyField.value = "true";
            modal.hidden = true;
            form.submit();
          };
          no.onclick = () => {
            codeOnlyField.value = "false";
            modal.hidden = true;
          };
        } else {
          if (
            confirm("No PlantUML scripts selected. Continue with code only?")
          ) {
            codeOnlyField.value = "true";
            form.submit();
          }
        }
      }
    }
  });
})();
