# Serves the static landing page - no build step needed here, index.html/
# styles.css/script.js are already the deployable artifact (the repo's own
# `npm run build` just mirrors them into dist/ for platforms that require a
# build output directory, e.g. Cloudflare Pages - this image copies straight
# from source instead, so it can never go stale relative to a forgotten
# `npm run build`).
FROM nginx:alpine
COPY index.html script.js styles.css /usr/share/nginx/html/
