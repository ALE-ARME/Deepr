DEFAULT_KEYS="default_keys.txt"
for file in $(find app/src/main/res -name "strings.xml" | grep "values-"); do
    echo "Checking $file..."
    grep "<string name=" "$file" | sed -E 's/.*name="([^"]+)".*/\1/' | sort > current_keys.txt
    
    # Check for ExtraTranslation
    EXTRA=$(comm -23 current_keys.txt "$DEFAULT_KEYS")
    if [ ! -z "$EXTRA" ]; then
        echo "  [!] EXTRA KEYS (will break build):"
        echo "$EXTRA" | sed 's/^/    - /'
    fi
    
    # Check for unescaped apostrophes
    # Regex: find ' that is NOT preceded by \ AND NOT enclosed in ""
    # Simplified check: just count lines with ' that don't have \"
    APOSTROPHES=$(grep -v '"' "$file" | grep "[^\\]'")
    if [ ! -z "$APOSTROPHES" ]; then
        echo "  [?] Potential unescaped apostrophes (might need quotes):"
        # echo "$APOSTROPHES"
    fi
done
rm current_keys.txt
