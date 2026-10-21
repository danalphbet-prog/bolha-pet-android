#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Bootstrap only on a workstation with the Android SDK/NDK available.
if [ ! -f lib/src/main/cpp/CMakeLists.txt ]; then
  mkdir -p .cache
  if [ ! -d .cache/eduagent ]; then
    git clone https://github.com/danalphbet-prog/eduagent-android.git .cache/eduagent
    git -C .cache/eduagent checkout 8bcc07e8c9670daa8f055afbddadb773d98ea8a1
  fi
  cp -a .cache/eduagent/lib lib
  cp -a .cache/eduagent/gradle/wrapper gradle/wrapper
  cp .cache/eduagent/gradlew gradlew
  chmod +x gradlew
fi
if [ ! -f third_party/llama.cpp/CMakeLists.txt ]; then
  mkdir -p third_party
  git clone https://github.com/ggml-org/llama.cpp.git third_party/llama.cpp
  git -C third_party/llama.cpp checkout f174553e1621c6a22a64e7bfcbb58d3a5c49e6fb
  git -C third_party/llama.cpp submodule update --init --recursive
fi
echo 'Runtime nativo pronto. Execute ./gradlew :app:assembleDebug'
