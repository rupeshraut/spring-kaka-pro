#!/bin/bash

# Create a quick fix script for the Kafka project
cd /Users/rpraut/Documents/workspace/spring-kafka-pro

# Fix imports and annotations for all files that need @Slf4j
for file in $(find src/main/java -name "*.java" -exec grep -l "log\." {} \;); do
    echo "Processing $file"
    # Add @Slf4j import and annotation if not present
    if ! grep -q "lombok.extern.slf4j.Slf4j" "$file"; then
        # Add import after package declaration
        sed -i '' '/^package/a\
import lombok.extern.slf4j.Slf4j;' "$file"
        
        # Add @Slf4j annotation before class declaration
        sed -i '' '/^@.*$/,/^public class/ { /^public class/i\
@Slf4j
}' "$file"
    fi
done

echo "Logging fixes applied"
