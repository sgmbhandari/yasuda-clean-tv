#!/bin/sh
set -eu
if [ -z "${JAVA_HOME:-}" ]; then
    if [ -d /opt/homebrew/opt/openjdk@17 ]; then JAVA_HOME=/opt/homebrew/opt/openjdk@17
    elif [ -x /usr/libexec/java_home ]; then JAVA_HOME=$(/usr/libexec/java_home -v 17)
    else JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")"); fi
fi
export JAVA_HOME
if [ -z "${ANDROID_HOME:-}" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then ANDROID_HOME="$HOME/Library/Android/sdk"; else ANDROID_HOME="$HOME/Android/Sdk"; fi
fi
export ANDROID_HOME
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
