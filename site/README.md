# RailCast landing site

A single self-contained static page (`index.html`) — no build step, no JavaScript,
no external requests (fonts and styles are inline). Styled with the app's own
design tokens (`android/.../core/design/Colors.kt`), light + dark theme aware.

## Hosting + wiring your domain

Any static host works; the whole site is one file. Three easy options:

### Cloudflare Pages (recommended — free, fast in India)
1. Pages → Create project → **Direct upload** → drop the `site/` folder
   (or connect this repo and set the build output directory to `site`, no build command).
2. Project → **Custom domains** → add your domain.
3. If the domain's DNS is on Cloudflare it wires itself; otherwise add the
   `CNAME` record it shows you at your registrar.

### Netlify
1. Drag the `site/` folder onto app.netlify.com/drop.
2. Site settings → **Domain management** → add your domain → follow its
   `CNAME`/`ALIAS` instructions at your registrar.

### GitHub Pages
1. Settings → Pages → deploy from branch, folder `site/` (or move the file to a
   `gh-pages` branch root).
2. Enter your domain under "Custom domain" — this commits a `CNAME` file.
3. At your registrar: apex `A` records to GitHub Pages IPs
   (185.199.108.153 / .109. / .110. / .111.) and `www` `CNAME` → `<user>.github.io`.
4. Tick **Enforce HTTPS** once the cert is issued.

## Notes
- **Apex vs www:** pick one as canonical and redirect the other (every host above
  has a one-click redirect setting).
- The **BFF privacy page** (`GET /privacy`) is served by the API server, not this
  site. If you host the API on `api.<your-domain>`, consider linking
  `https://api.<your-domain>/privacy` from this page before launch.
- Keep the **non-affiliation disclaimer** in the footer — it is an FR-11.1
  requirement, not decoration.
