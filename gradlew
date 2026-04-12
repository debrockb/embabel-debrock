#!/bin/sh

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Gradle wrapper script for POSIX systems

APP_NAME="MATOE"
APP_BASE_NAME=$(basename "$0")

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/bin/java" ] ; then
        JAVACMD="$JAVA_HOME/bin/java"
    else
        echo "ERROR: JAVA_HOME is set but java executable not found at $JAVA_HOME/bin/java"
        exit 1
    fi
else
    JAVACMD="java"
    command -v java >/dev/null 2>&1 || { echo "ERROR: JAVA_HOME is not set and no 'java' command found"; exit 1; }
fi

# Resolve application home
APP_HOME=$(dirname "$0")

# Execute Gradle wrapper
exec "$JAVACMD" -cp "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
