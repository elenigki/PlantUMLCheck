// upload.js — small and simple

(() => {
  const $ = (sel, ctx = document) => ctx.querySelector(sel);
  const $$ = (sel, ctx = document) => Array.from(ctx.querySelectorAll(sel));

  // elements
  const plantumlInput = $("#plantuml-input");
  const sourceInput = $("#source-input");
  const plantumlList = $("#plantuml-list");
  const sourceList = $("#source-list");
  const form = $(".upload-form");
  const codeOnlyField = $("#codeOnly");

  const modal = $("#code-only-modal");
  const yesBtn = $("#code-only-yes");
  const noBtn = $("#code-only-no");

  // render file names to a UL
  function renderList(input, list) {
    list.innerHTML = "";
    const files = Array.from(input.files || []);
    files.forEach((f) => {
      const li = document.createElement("li");
      li.textContent = f.webkitRelativePath || f.name;
      list.appendChild(li);
    });
  }

  // initial change listeners
  plantumlInput.addEventListener("change", () =>
    renderList(plantumlInput, plantumlList)
  );
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

      const targetInput = zone.id === "puml-box" ? plantumlInput : sourceInput;
      const targetList = zone.id === "puml-box" ? plantumlList : sourceList;

      // collect dropped files (directories may not come through here; folder picker works better)
      const dt = new DataTransfer();
      Array.from(e.dataTransfer.files || []).forEach((f) => dt.items.add(f));
      targetInput.files = dt.files;

      renderList(targetInput, targetList);
    });
  });

  // modal helpers
  const openModal = () => {
    modal.hidden = false;
  };
  const closeModal = () => {
    modal.hidden = true;
  };

  yesBtn.addEventListener("click", () => {
    codeOnlyField.value = "true";
    closeModal();
    form.submit();
  });
  noBtn.addEventListener("click", () => {
    codeOnlyField.value = "false";
    closeModal();
  });

  // intercept submit: require source; ask about code-only if PlantUML is empty
  form.addEventListener("submit", (e) => {
    const plantumlEmpty = plantumlInput.files.length === 0;
    const sourceEmpty = sourceInput.files.length === 0;

    if (sourceEmpty) {
      // let backend also validate; but we give a quick, friendly notice
      alert("Please add your source code (folder or .java files).");
      e.preventDefault();
      return;
    }

    if (plantumlEmpty) {
      e.preventDefault();
      openModal(); // ask the user about Code-only
    }
  });
})();
