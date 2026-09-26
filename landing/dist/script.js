(() => {
  "use strict";

  // ---------- footer year ----------
  const yearEl = document.getElementById("year");
  if (yearEl) yearEl.textContent = new Date().getFullYear();

  // ---------- mobile nav ----------
  const menuToggle = document.getElementById("menuToggle");
  const mobileNav = document.getElementById("mobileNav");
  if (menuToggle && mobileNav) {
    menuToggle.addEventListener("click", () => {
      const isOpen = mobileNav.classList.toggle("is-open");
      menuToggle.setAttribute("aria-expanded", String(isOpen));
    });
    mobileNav.querySelectorAll("a").forEach((link) => {
      link.addEventListener("click", () => {
        mobileNav.classList.remove("is-open");
        menuToggle.setAttribute("aria-expanded", "false");
      });
    });
  }

  // ---------- scroll reveal ----------
  const revealEls = document.querySelectorAll(".reveal");
  if ("IntersectionObserver" in window && revealEls.length) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: "0px 0px -40px 0px" }
    );
    revealEls.forEach((el) => observer.observe(el));
  } else {
    revealEls.forEach((el) => el.classList.add("is-visible"));
  }

  // ---------- copy-to-clipboard helper ----------
  const copyToClipboard = async (text, triggerEl, copiedLabel) => {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      return;
    }
    if (!triggerEl) return;
    const original = triggerEl.dataset.originalHtml || triggerEl.innerHTML;
    triggerEl.dataset.originalHtml = original;
    triggerEl.classList.add("is-copied");
    if (copiedLabel) triggerEl.innerHTML = copiedLabel;
    window.clearTimeout(triggerEl._copyTimeout);
    triggerEl._copyTimeout = window.setTimeout(() => {
      triggerEl.classList.remove("is-copied");
      triggerEl.innerHTML = original;
    }, 1800);
  };

  // ---------- API key card copy ----------
  const KEY_COPIED_LABEL =
    '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>Copied';
  const keyCopyBtn = document.querySelector(".key-card-copy");
  if (keyCopyBtn) {
    keyCopyBtn.addEventListener("click", () => {
      copyToClipboard(keyCopyBtn.dataset.copy || "", keyCopyBtn, KEY_COPIED_LABEL);
    });
  }

  // ---------- agent connect tabs ----------
  const agentTabs = document.querySelectorAll(".agent-tab");
  const agentCommandPre = document.getElementById("agentCommand");
  const agentCommandCode = document.getElementById("agentCommandCode");
  const agentCodeCopy = document.getElementById("agentCodeCopy");

  if (agentTabs.length && agentCommandPre && agentCommandCode) {
    agentTabs.forEach((tab) => {
      tab.addEventListener("click", () => {
        agentTabs.forEach((t) => {
          t.classList.remove("is-active");
          t.setAttribute("aria-selected", "false");
        });
        tab.classList.add("is-active");
        tab.setAttribute("aria-selected", "true");
        const agent = tab.dataset.agent;
        const command = agentCommandPre.dataset[agent];
        if (command) agentCommandCode.textContent = command;
      });
    });
  }

  const CHECK_ICON = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>';

  if (agentCodeCopy && agentCommandCode) {
    agentCodeCopy.addEventListener("click", () => {
      copyToClipboard(agentCommandCode.textContent || "", agentCodeCopy, CHECK_ICON);
    });
  }

  // ---------- live GitHub star count ----------
  const formatStars = (n) => {
    if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, "") + "k";
    return String(n);
  };

  fetch("https://api.github.com/repos/stoopid-computers/funchole")
    .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
    .then((data) => {
      const count = typeof data.stargazers_count === "number" ? data.stargazers_count : null;
      if (count === null) return;

      const navEl = document.getElementById("nav-star-count");
      if (navEl) navEl.textContent = formatStars(count);

      const heroEl = document.getElementById("hero-star-count");
      if (heroEl) heroEl.textContent = formatStars(count);

      const osEl = document.getElementById("os-star-count");
      if (osEl) osEl.textContent = formatStars(count);
    })
    .catch(() => {
      // Offline or rate-limited: hide just the count badge, not the whole
      // "Star on GitHub" link/button - that's still a valid CTA without a
      // live number, never show a fabricated one instead.
      document.querySelectorAll(".star-count").forEach((el) => el.remove());
    });
})();
