#!/usr/bin/env python3
"""Generate the iOS string catalog from shared/i18n/translations.json.

    python3 tools/i18n.py          # write Tamdid/Resources/Localizable.xcstrings and InfoPlist.xcstrings
    python3 tools/i18n.py --check  # fail if a string used in the app lacks a translation or the catalog is out of date

Scans the Swift sources for user-facing literals. translations.json is the same file the Android
app uses (github.com/Ali-Asadi-Dev/tamdid-android); keep the two copies in sync.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LANGS = ["fa", "ar", "tr", "de"]
IOS_APP = ROOT / "Tamdid"

data = json.loads((ROOT / "shared/i18n/translations.json").read_text())
STRINGS, PLURALS = data["strings"], data["plurals"]
problems = []


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists() or path.read_text() != text:
        if "--check" in sys.argv:
            problems.append(f"out of date: {path.relative_to(ROOT)}")
        else:
            path.write_text(text)


# ---------------------------------------------------------------- iOS
# Interpolations that are integers become %lld; everything else is a string (%@).
INT_EXPRESSIONS = re.compile(r"^[\w.]*(count|interval|days|month)$")
CONTEXTS = re.compile(
    r'(?:\bText|\bLabel|\bButton|\bSection|\bLabeledContent|\bPicker|\bToggle|\bTextField|\bnavigationTitle|'
    r'\bconfirmationDialog|\balert|String\(localized:|StatusBadge\(text:|\btitle:|\bmessage:|\bactionTitle:|'
    r'\baccessibilityLabel|\btabItem \{ Label|\bLink)\(?\s*"((?:[^"\\]|\\.)*)"'
)
NOT_TEXT = re.compile(r"^(#|[a-z]+(\.[a-z]+)+$|[a-z]+[A-Z]\w*$|-|tamdid|https?:)")


def swift_key(literal):
    """Turn a Swift literal like `\\(count) active subscriptions` into its catalog key."""
    out, i = [], 0
    while i < len(literal):
        if literal.startswith("\\(", i):
            depth, j = 1, i + 2
            while depth:  # find the matching parenthesis
                depth += {"(": 1, ")": -1}.get(literal[j], 0)
                j += 1
            expression = literal[i + 2:j - 1].strip()
            out.append("%lld" if INT_EXPRESSIONS.match(expression) else "%@")
            i = j
        else:
            out.append(literal[i])
            i += 1
    return "".join(out)


def to_template(key):
    """iOS key -> the Android-style template used in translations.json."""
    index = 0
    def numbered(match):
        nonlocal index
        index += 1
        return f"%{index}$s" if match.group(0) == "%@" else f"%{index}$d"
    return re.sub(r"%@|%lld", numbered, key)


def to_ios_value(text):
    return re.sub(r"%(\d)\$s", r"%\1$@", re.sub(r"%(\d)\$d", r"%\1$lld", text))


keys = set()
for path in sorted(IOS_APP.rglob("*.swift")):
    for literal in CONTEXTS.findall(path.read_text()):
        if literal and not NOT_TEXT.match(literal) and re.search(r"[A-Za-z]", literal):
            keys.add(swift_key(literal))

catalog = {"sourceLanguage": "en", "strings": {}, "version": "1.0"}
for key in sorted(keys):
    template = to_template(key)
    localizations = {}
    plural = PLURALS.get(template)
    if plural and key.count("%") == 1:
        for lang in ["en"] + LANGS:
            forms = {q: {"stringUnit": {"state": "translated", "value": to_ios_value(v).replace("%1$lld", "%lld")}}
                     for q, v in plural[lang].items()}
            localizations[lang] = {"variations": {"plural": forms}}
    else:
        entry = STRINGS.get(template) or (plural and {lang: plural[lang]["other"] for lang in LANGS})
        if not entry:
            problems.append(f"ios: no translation for {key!r}")
            continue
        for lang in LANGS:
            localizations[lang] = {"stringUnit": {"state": "translated", "value": to_ios_value(entry[lang])}}
    catalog["strings"][key] = {"extractionState": "manual", "localizations": localizations}

write(IOS_APP / "Resources/Localizable.xcstrings", json.dumps(catalog, ensure_ascii=False, indent=2, sort_keys=True) + "\n")

names = {"en": "Tamdid", "fa": "تمدید", "ar": "تمديد", "tr": "Tamdid", "de": "Tamdid"}
info = {"sourceLanguage": "en", "version": "1.0", "strings": {"CFBundleDisplayName": {"extractionState": "manual", "localizations": {
    lang: {"stringUnit": {"state": "translated", "value": name}} for lang, name in names.items()}}}}
write(IOS_APP / "Resources/InfoPlist.xcstrings", json.dumps(info, ensure_ascii=False, indent=2, sort_keys=True) + "\n")

if problems:
    print("\n".join(problems))
    sys.exit(1)
print(f"ok: {len(keys)} keys, {len(PLURALS)} plural entries, languages en+{'+'.join(LANGS)}")
