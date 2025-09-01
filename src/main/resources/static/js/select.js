(() => {
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

  // Existing structure
  const selectAll = $("#selectAll");
  const pkgBlocks = $$(".pkg");

  // New bits
  const continueBtn =
    $("#continue-btn") || $("#continueBtn") || $("button[type='submit']");
  const needOne = $("#needOne");
  const form = $("#select-form") || $("form");

  function setChecked(cb, on) {
    cb.checked = !!on;
    cb.indeterminate = false;
  }

  function updatePkgState(pkgEl) {
    const classes = $$(".cls-checkbox", pkgEl);
    const head = $(".pkg-checkbox", pkgEl);
    const checked = classes.filter((c) => c.checked).length;
    if (checked === 0) {
      head.checked = false;
      head.indeterminate = false;
    } else if (checked === classes.length) {
      head.checked = true;
      head.indeterminate = false;
    } else {
      head.checked = false;
      head.indeterminate = true;
    }
  }

  function updateSelectAllState() {
    const allClassCbs = $$(".cls-checkbox");
    const checked = allClassCbs.filter((c) => c.checked).length;
    if (checked === 0) {
      setChecked(selectAll, false);
    } else if (checked === allClassCbs.length) {
      setChecked(selectAll, true);
    } else {
      if (selectAll) {
        selectAll.checked = false;
        selectAll.indeterminate = true;
      }
    }
  }

  // NEW: disable/enable Continue based on at least one class selected
  function updateContinueState() {
    const hasAny = $$(".cls-checkbox").some((c) => c.checked);
    if (continueBtn) continueBtn.disabled = !hasAny;
    if (needOne) needOne.classList.toggle("show", !hasAny);
  }

  // Package checkbox toggles its classes
  pkgBlocks.forEach((pkgEl) => {
    const headCb = $(".pkg-checkbox", pkgEl);
    headCb?.addEventListener("change", () => {
      const classes = $$(".cls-checkbox", pkgEl);
      classes.forEach((c) => setChecked(c, headCb.checked));
      updatePkgState(pkgEl);
      updateSelectAllState();
      updateContinueState();
    });
  });

  // Class checkbox updates package and global states
  $$(".cls-checkbox").forEach((cb) => {
    cb.addEventListener("change", () => {
      const pkgEl = cb.closest(".pkg");
      if (pkgEl) updatePkgState(pkgEl);
      updateSelectAllState();
      updateContinueState();
    });
  });

  // Select all toggles everything
  if (selectAll) {
    selectAll.addEventListener("change", () => {
      const allClassCbs = $$(".cls-checkbox");
      const allPkgCbs = $$(".pkg-checkbox");
      allClassCbs.forEach((c) => setChecked(c, selectAll.checked));
      allPkgCbs.forEach((p) => setChecked(p, selectAll.checked));
      selectAll.indeterminate = false;
      // After bulk toggle, ensure states are refreshed
      pkgBlocks.forEach(updatePkgState);
      updateContinueState();
    });
  }

  // Prevent submit if disabled (extra safety)
  if (form && continueBtn) {
    form.addEventListener("submit", (e) => {
      if (continueBtn.disabled) {
        e.preventDefault();
        needOne?.classList.add("show");
      }
    });
  }

  // Initialize state on load
  pkgBlocks.forEach(updatePkgState);
  updateSelectAllState();
  updateContinueState();
})();
