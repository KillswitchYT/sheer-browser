"""Prints the centre of the first node in a uiautomator dump whose text or content description is exactly the given text."""
import re, sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[2]).getroot()
except Exception:
    sys.exit(1)
for n in root.iter('node'):
    if n.get('text') == sys.argv[1] or n.get('content-desc') == sys.argv[1]:
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.get('bounds'))
        print((int(m[1]) + int(m[3])) // 2, (int(m[2]) + int(m[4])) // 2)
        sys.exit(0)
sys.exit(1)
