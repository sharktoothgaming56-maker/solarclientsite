# solarclientsite

Static site for **figgysmp.shop**, served by GitHub Pages from the repository root.

## Structure

```
index.html            Home / download page
guides/               Seven long-form Minecraft performance guides + hub page
faq.html              Full FAQ (emits FAQPage structured data)
about.html            About the project, funding, and what it will not do
support.html          Installation / login / crash troubleshooting
changelog.html        Release history, read live from the GitHub Releases API
privacy.html          Privacy policy
terms.html            Terms of service
cookies.html          Cookie policy
404.html              Not-found page (noindex, no ad code)

assets/css/site.css   Single shared stylesheet for every page
assets/js/site.js     Starfield, FAQ accordion, clipboard, live release data
assets/js/changelog.js Changelog page: renders GitHub releases
assets/img/logo.svg   Logo (also copied to /favicon.svg)

ads.txt               AdSense authorised sellers file
robots.txt            Crawl rules + sitemap pointer
sitemap.xml           16 indexable URLs
CNAME                 figgysmp.shop

ad-slot.html          IN-LAUNCHER ad frame - not part of the website
ads.html              IN-LAUNCHER ad frame - not part of the website
ads-config.json       Ad slot config consumed by the two files above
backend.json          Launcher backend config
```

## Editing

Every page shares one stylesheet and one nav/footer structure. If you change the
navigation or footer, change it in **all** HTML files, or the site drifts out of
sync. The pages were generated from a shared template; keeping them consistent by
hand is the trade-off for a dependency-free static site.

Content that updates itself and should **not** be hard-coded:

- Version number, download URL, file size and SHA-256 on the home page
- The VirusTotal link
- The release list on `changelog.html`

All of these read from the GitHub Releases API at page load, with static fallbacks
in the HTML for when the API is unavailable or rate-limited.

## Local preview

Root-relative paths (`/assets/...`) need a real server - opening `index.html`
from disk will not load the stylesheet.

```bash
python -m http.server 8777
```

Then open <http://localhost:8777>.

## AdSense notes

- `ads.txt` must stay at the site root and must keep the publisher ID
  `pub-7931973085541540`. AdSense reports "Ads.txt status: Not found" without it.
- Every page carries a `canonical` pointing at its own `https://figgysmp.shop/`
  URL. Do **not** reintroduce a canonical pointing at the `github.io` address -
  that tells Google the whole domain is a duplicate of another site.
- Maximum two ad units per page, each with a distinct `data-ad-slot`. Pages with
  little static text (`404.html`, `cookies.html`, `privacy.html`, `terms.html`)
  deliberately omit the AdSense script entirely.
- `ad-slot.html` and `ads.html` serve Adsterra ads **inside the desktop launcher**.
  They are `noindex`, disallowed in `robots.txt`, excluded from the sitemap, and
  nothing on the website links to them. They are still hosted on the same domain
  as AdSense, which carries policy risk - moving them to a subdomain would remove
  it entirely.
