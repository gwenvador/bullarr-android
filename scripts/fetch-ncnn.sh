#!/bin/sh
# Télécharge ncnn 20220420 (Android, Vulkan) dans app/src/main/cpp/ncnn, requis pour compiler l'amélioration d'image.
# Version figée : Real-ESRGAN-ncnn-vulkan a été écrit pour elle ; les versions récentes de ncnn produisent des images corrompues.
set -e
cd "$(dirname "$0")/../app/src/main/cpp"
[ -d ncnn/arm64-v8a ] && { echo "ncnn déjà présent"; exit 0; }
curl -fsSL -o ncnn.zip https://github.com/Tencent/ncnn/releases/download/20220420/ncnn-20220420-android-vulkan.zip
echo "98ef9a75d829ced6b767d169a23006ef51250e3704946821c6448d0235a20c47  ncnn.zip" | shasum -a 256 -c -
unzip -q ncnn.zip && rm ncnn.zip && mv ncnn-20220420-android-vulkan ncnn
echo "ncnn installé dans app/src/main/cpp/ncnn"
