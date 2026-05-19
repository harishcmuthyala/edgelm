#!/bin/bash
# setup.sh
# Run this once to set up the edgelm environment

set -e  # stop on any error

echo "=============================="
echo " edgelm setup"
echo "=============================="

# Check Python
echo ""
echo "Checking Python version..."
python --version

# Check pip
echo "Checking pip..."
pip --version

# Install dependencies
echo ""
echo "Installing dependencies..."
pip install huggingface_hub

# HuggingFace login
echo ""
echo "Logging into HuggingFace..."
echo "You will be prompted to paste your token from:"
echo "https://huggingface.co/settings/tokens"
echo ""
huggingface-cli login

echo ""
echo "=============================="
echo " Setup complete"
echo " Run: bash download_model.sh"
echo "=============================="
