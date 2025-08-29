// select.js — cascade checkboxes: select-all ⇄ packages ⇄ classes

(() => {
  const $ = (sel, ctx) => (ctx || document).querySelector(sel);
  const $$ = (sel, ctx) => Array.from((ctx || document).querySelectorAll(sel));

  const selectAll = $("#select-all");
  const pkgRows = $$(".pkg-row");

  function setChecked(cb, v) {
    if (!cb) return;
    cb.indeterminate = false;
    cb.checked = v;
  }

  function updatePkgStateForRow(row) {
    const pkgCb = $(".pkg-checkbox", row);
    const classCbs = $$(".cls-checkbox", row);
    if (!pkgCb || classCbs.length === 0) return;

    const checked = classCbs.filter((c) => c.checked).length;
    if (checked === 0) {
      pkgCb.indeterminate = false;
      pkgCb.checked = false;
    } else if (checked === classCbs.length) {
      pkgCb.indeterminate = false;
      pkgCb.checked = true;
    } else {
      pkgCb.indeterminate = true;
      pkgCb.checked = false;
    }
  }

  function updateSelectAllState() {
    const allClassCbs = $$(".cls-checkbox");
    if (!selectAll || allClassCbs.length === 0) return;

    const checked = allClassCbs.filter((c) => c.checked).length;
    if (checked === 0) setChecked(selectAll, false);
    else if (checked === allClassCbs.length) setChecked(selectAll, true);
    else {
      selectAll.indeterminate = true;
      selectAll.checked = false;
    }
  }

  // Package checkbox toggles all its classes
  $$(".pkg-row").forEach((row) => {
    const pkgCb = $(".pkg-checkbox", row);
    const classCbs = $$(".cls-checkbox", row);

    if (pkgCb) {
      pkgCb.addEventListener("change", () => {
        classCbs.forEach((c) => setChecked(c, pkgCb.checked));
        updatePkgStateForRow(row);
        updateSelectAllState();
      });
    }

    classCbs.forEach((c) => {
      c.addEventListener("change", () => {
        updatePkgStateForRow(row);
        updateSelectAllState();
      });
    });

    updatePkgStateForRow(row);
  });

  // Select-all toggles EVERY package + EVERY class
  if (selectAll) {
    selectAll.addEventListener("change", () => {
      const allClassCbs = $$(".cls-checkbox");
      const allPkgCbs = $$(".pkg-checkbox");
      allClassCbs.forEach((c) => setChecked(c, selectAll.checked));
      allPkgCbs.forEach((p) => setChecked(p, selectAll.checked));
      selectAll.indeterminate = false;
    });
  }

  updateSelectAllState();
})();
