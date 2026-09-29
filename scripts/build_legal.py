"""Renders the documents in docs/legal/*.md (legal pages and the Android Auto setup guide) into
standalone HTML pages.

    python scripts/build_legal.py [--site DIR]

The Markdown files are the source of truth (GitHub renders them as-is). The HTML copies are
written to app/src/main/assets/legal/, where the app opens them offline, and, with --site, to
DIR/legal/ for the download page. Re-run after editing any of the Markdown files and commit the
result. Needs the `markdown` package (pip install markdown).
"""
import argparse
import html
import pathlib
import re

import markdown

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE_DIR = ROOT / "docs" / "legal"
ASSET_DIR = ROOT / "app" / "src" / "main" / "assets" / "legal"

# Order and labels of the shared navigation strip.
PAGES = [
    ("android-auto-setup", "Android Auto setup"),
    ("privacy", "Privacy"),
    ("terms", "Terms"),
    ("driving-safety", "Driving safety"),
    ("notices", "Notices"),
]

TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>{title} · RoadBrowser</title>
<style>
  /* First source serves the download site, second the copy bundled in the app. */
  @font-face {{
    font-family: "Overpass";
    src: url("../assets/overpass.ttf") format("truetype-variations"),
         url("file:///android_res/font/overpass.ttf") format("truetype-variations");
    font-weight: 300 700;
    font-display: swap;
  }}
  :root {{
    --paper: #ede3cf;
    --card: #faf6ec;
    --ink: #1b1714;
    --ink-muted: #5d5445;
    --rule: #c9b894;
    --amber: #8a5a0e;
  }}
  @media (prefers-color-scheme: dark) {{
    :root {{
      --paper: #14120f;
      --card: #1b1815;
      --ink: #f2ead9;
      --ink-muted: #d0c7b5;
      --rule: #4e483e;
      --amber: #e3b25c;
    }}
  }}
  * {{ box-sizing: border-box; }}
  body {{
    margin: 0;
    background: var(--paper);
    color: var(--ink);
    font-family: "Overpass", system-ui, sans-serif;
    font-size: 1.05rem;
    line-height: 1.6;
    -webkit-font-smoothing: antialiased;
  }}
  .wrap {{ max-width: 46rem; margin: 0 auto; padding: 0 16px 56px; }}
  header {{ padding: 28px 0 0; }}
  .brand {{ font-weight: 700; letter-spacing: -0.01em; color: var(--ink-muted); }}
  nav {{ display: flex; flex-wrap: wrap; gap: 8px; margin: 14px 0 0; }}
  nav a {{
    color: var(--ink); text-decoration: none; font-weight: 600; font-size: .95rem;
    padding: 9px 16px; border: 1px solid var(--rule); border-radius: 999px; background: var(--card);
  }}
  nav a[aria-current="page"] {{ background: var(--ink); color: var(--paper); border-color: var(--ink); }}
  .roadrule {{
    height: 5px; margin: 22px 0 0; border-radius: 3px; opacity: .85;
    background: repeating-linear-gradient(90deg, var(--amber) 0 30px, transparent 30px 52px);
  }}
  h1 {{ font-size: clamp(2rem, 6vw, 2.8rem); letter-spacing: -0.02em; line-height: 1.1; margin: 28px 0 12px; }}
  h2 {{ font-size: 1.25rem; margin: 34px 0 8px; padding-top: 18px; border-top: 1px solid var(--rule); }}
  p, li {{ max-width: 68ch; }}
  li {{ margin-bottom: 6px; }}
  a {{ color: var(--amber); overflow-wrap: anywhere; }}
  code {{ background: var(--card); border: 1px solid var(--rule); border-radius: 6px; padding: 1px 6px; font-size: .9em; }}
  table {{ width: 100%; border-collapse: collapse; margin: 12px 0; background: var(--card); border: 1px solid var(--rule); border-radius: 12px; overflow: hidden; }}
  th, td {{ text-align: left; vertical-align: top; padding: 10px 14px; border-bottom: 1px solid var(--rule); }}
  tr:last-child td {{ border-bottom: 0; }}
  th {{ font-weight: 600; }}
  footer {{ margin-top: 40px; padding-top: 18px; border-top: 1px solid var(--rule); color: var(--ink-muted); font-size: .9rem; }}
</style>
</head>
<body>
<div class="wrap">
  <header>
    <div class="brand">RoadBrowser</div>
    <nav>{nav}</nav>
    <div class="roadrule"></div>
  </header>
  <main>
{body}
  </main>
  <footer>Made by Yash V Maurya · <a href="https://github.com/Yash-v-maurya/RoadBrowser">github.com/Yash-v-maurya/RoadBrowser</a></footer>
</div>
</body>
</html>
"""


def render(slug: str) -> str:
    source = (SOURCE_DIR / f"{slug}.md").read_text(encoding="utf-8")
    title = re.search(r"^# (.+)$", source, re.MULTILINE).group(1).strip()
    # The Markdown ends with a "See also" line for GitHub readers; the nav strip replaces it.
    source = re.sub(r"\n+See also:.*\s*$", "\n", source)
    body = markdown.markdown(source, extensions=["tables"])
    body = re.sub(r'href="([a-z-]+)\.md"', r'href="\1.html"', body)
    current = ' aria-current="page"'
    nav = "".join(
        f'<a href="{page}.html"{current if page == slug else ""}>{html.escape(label)}</a>'
        for page, label in PAGES
    )
    return TEMPLATE.format(title=html.escape(title), nav=nav, body=body)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--site", type=pathlib.Path, help="also write the pages to SITE/legal/")
    args = parser.parse_args()

    targets = [ASSET_DIR] + ([args.site / "legal"] if args.site else [])
    for target in targets:
        target.mkdir(parents=True, exist_ok=True)
    for slug, _ in PAGES:
        page = render(slug)
        for target in targets:
            (target / f"{slug}.html").write_text(page, encoding="utf-8", newline="\n")
            print(f"wrote {target / (slug + '.html')}")


if __name__ == "__main__":
    main()
