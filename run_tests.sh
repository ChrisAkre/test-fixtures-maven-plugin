#!/bin/bash
set -e

# Define directories
SRC_DIR="src/main/java"
TEST_DIR="src/test/java"
OUT_DIR="target/test-classes"

mkdir -p $OUT_DIR

# Define Classpath
# Include Maven jars
CP=$(find /usr/share/maven/lib -name "*.jar" | tr '\n' ':')
# Include JUnit jars from Gradle
CP=$CP:$(find /usr/share/gradle-8.8/lib -name "junit-*.jar" | tr '\n' ':')
CP=$CP:$(find /usr/share/gradle-8.8/lib -name "hamcrest-core-*.jar" | tr '\n' ':')
# Include output directory
CP=$CP:$OUT_DIR

echo "Compiling..."
javac -cp "$CP" -d $OUT_DIR \
    $SRC_DIR/dev/akre/maven/plugins/fixtures/TestFixturesWorkspaceReader.java \
    $TEST_DIR/dev/akre/maven/plugins/fixtures/TestFixturesWorkspaceReaderTest.java

echo "Running tests..."
java -cp "$CP" org.junit.runner.JUnitCore dev.akre.maven.plugins.fixtures.TestFixturesWorkspaceReaderTest
