#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
command -v whisper-cli >/dev/null 2>&1 || HOMEBREW_NO_AUTO_UPDATE=1 brew install whisper-cpp
mkdir -p .local
if [ ! -f .local/ggml-base.en.bin ]; then
  curl -fL --retry 2 https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin -o .local/ggml-base.en.bin.download
  printf '%s\n' 'a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002  .local/ggml-base.en.bin.download' | shasum -a 256 -c -
  mv .local/ggml-base.en.bin.download .local/ggml-base.en.bin
fi
printf '%s\n' 'a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002  .local/ggml-base.en.bin' | shasum -a 256 -c -
printf '%s\n' 'Ready: ENABLE_SIMULATION=1 ENABLE_LOCAL_SPEECH=1 npm run dev'
