(function () {
  'use strict';

  var prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  var revealTargets = document.querySelectorAll('.reveal');

  if (!('IntersectionObserver' in window) || prefersReducedMotion) {
    revealTargets.forEach(function (el) { el.classList.add('is-visible'); });
    return;
  }

  var observer = new IntersectionObserver(
    function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        }
      });
    },
    { root: null, rootMargin: '0px 0px -8% 0px', threshold: 0.12 }
  );

  revealTargets.forEach(function (el) { observer.observe(el); });
})();
