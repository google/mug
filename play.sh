#!/bin/bash

# 1. Define the submodules you want to include
MODULES="mug dot-parse mug-guava"

echo "Configuring environment for: $MODULES..."

# 2. Build modules and collect classpaths
# -pl: project list, -am: also make dependencies
mvn install -DskipTests -Dmaven.javadoc.skip=true -pl $(echo $MODULES | sed 's/ /,/g') -am -q

FULL_CP=""
for MOD in $MODULES; do
    # Generate classpath for each module and append its own classes
    mvn -f $MOD/pom.xml dependency:build-classpath -Dmdep.outputFile=.cp.txt -q
    MOD_CP=$(cat .cp.txt):$MOD/target/classes
    FULL_CP="$FULL_CP:$MOD_CP"
done

# 3. Create a startup script for JShell to handle auto-imports
STARTUP_FILE=".jshell_startup"
cat <<EOF > $STARTUP_FILE
import com.google.mu.time.*
import com.google.mu.util.*
import com.google.mu.util.stream.*
import com.google.common.labs.parse.*
import java.util.*
import java.util.stream.*

System.out.println("-------------------------------------------------------");
System.out.println("Welcome to the Google Mug Playground!");
System.out.println("All modules (dot-parse, mug, mug-guava) are loaded.");
System.out.println("Try: Parser.word().between(\"{\", \"}\").parse(\"{foo}\")");
System.out.println("-------------------------------------------------------");
EOF

# 4. Launch JShell
jshell --class-path "$FULL_CP" --startup $STARTUP_FILE
