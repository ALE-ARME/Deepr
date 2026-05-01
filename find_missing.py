import xml.etree.ElementTree as ET
import os

def get_strings(path):
    try:
        tree = ET.parse(path)
        root = tree.getroot()
        return {child.attrib['name']: child.text for child in root if child.tag == 'string'}
    except:
        return {}

base_path = "app/src/main/res/values/strings.xml"
base_strings = get_strings(base_path)

langs = ["it", "fr", "es", "de"]
for lang in langs:
    lang_path = f"app/src/main/res/values-{lang}/strings.xml"
    lang_strings = get_strings(lang_path)
    missing = [k for k in base_strings if k not in lang_strings]
    print(f"--- {lang.upper()} missing: {len(missing)} ---")
    for k in missing[:10]: # Print first 10 missing keys
        print(f"  - {k}: {base_strings[k]}")
    if len(missing) > 10:
        print(f"  ... and {len(missing)-10} more")
