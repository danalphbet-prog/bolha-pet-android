#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
pkg install -y git gh unzip
termux-setup-storage || true
SOURCE="/sdcard/Download/bolha-pet-qwen3b-android.zip"
[ -f "$SOURCE" ] || { echo "Baixe o ZIP e salve em Download primeiro: $SOURCE"; exit 1; }
DIR="$HOME/bolha-pet-android"
if [ -e "$DIR/.git" ]; then
  echo "O diretório $DIR já contém Git. Não será sobrescrito automaticamente."
  exit 1
fi
mkdir -p "$DIR"
unzip -oq "$SOURCE" -d "$DIR"
cd "$DIR"
git init -b main
git add -A
git commit -m "Bolha Pet 0.1 procedural + Qwen2.5 3B Q4_K_M"
gh repo create bolha-pet-android --public --source=. --remote=origin --push
gh run list -R "$(gh api user --jq .login)/bolha-pet-android" --limit 3
