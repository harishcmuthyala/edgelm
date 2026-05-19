#!/bin/bash
# download_model.sh
# Downloads the pre-exported Llama 3.2 1B SpinQuant PTE file
# Model: executorch-community/Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8-ET
# Size: ~1.14 GB

set -e  # stop on any error

echo "=============================="
echo " edgelm model download"
echo "=============================="

# Check huggingface_hub is installed
python -c "import huggingface_hub" 2>/dev/null || {
    echo "huggingface_hub not found. Run: bash setup.sh first"
    exit 1
}

# Create model directory
mkdir -p ./model

echo ""
echo "Downloading model files..."
echo "This will take 5-10 minutes depending on your connection."
echo ""

huggingface-cli download \
  executorch-community/Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8-ET \
  Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8.pte \
  tokenizer.model \
  --local-dir ./model

echo ""
echo "Verifying downloads..."

# Check .pte file
if [ -f "./model/Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8.pte" ]; then
    size=$(du -h "./model/Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8.pte" | cut -f1)
    echo "✅ model.pte found: $size"
else
    echo "❌ model.pte not found — check errors above"
    exit 1
fi

# Check tokenizer
if [ -f "./model/tokenizer.model" ]; then
    size=$(du -h "./model/tokenizer.model" | cut -f1)
    echo "✅ tokenizer.model found: $size"
else
    echo "❌ tokenizer.model not found — check errors above"
    exit 1
fi

echo ""
echo "=============================="
echo " Download complete"
echo " Files are in ./model/"
echo " Next: open Android Studio"
echo "=============================="
