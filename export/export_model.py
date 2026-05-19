"""
export_model.py
---------------
Exports Llama 3.2 1B from HuggingFace to ExecuTorch .pte format
using Optimum ExecuTorch with SpinQuant INT4 quantization.

Usage:
    python export/export_model.py

Requirements:
    pip install executorch==0.4.0 optimum-executorch torchao transformers
    torch==2.5.0 (exact version required by ExecuTorch 0.4.0)

Environment:
    Tested on: Linux, Python 3.12
    Known issue: ExecuTorch 0.4.0 requires torch==2.5.0 exactly.
    Colab ships torch==2.5.0+cu124 which breaks the .so library load.
    Workaround: Use Docker image or wait for ExecuTorch 0.5.0.
    Alternative: Download pre-exported PTE via download_model.sh

Output:
    ./model/Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8.pte  (~1.14 GB)
    ./model/tokenizer.model                                 (~2.18 MB)
"""

import os
import sys

# ── Config ────────────────────────────────────────────────────────────────────

MODEL_ID    = "meta-llama/Llama-3.2-1B-Instruct"
OUTPUT_DIR  = "./model"
BACKEND     = "xnnpack"
RECIPE      = "spinquant"   # SpinQuant INT4: best quality/size tradeoff
                             # Benchmarks: 50.2 tok/s decode, 260.5 tok/s prefill
                             # Model size: 1.14 GB vs 2.47 GB (BF16 baseline)

# ── Checks ────────────────────────────────────────────────────────────────────

def check_environment():
    """Verify environment before attempting export."""

    print("Checking environment...")

    # Check torch version
    import torch
    torch_version = torch.__version__
    print(f"  torch: {torch_version}")

    if not torch_version.startswith("2.5.0"):
        print(f"""
⚠️  Warning: ExecuTorch 0.4.0 requires torch==2.5.0 exactly.
   You have: {torch_version}
   This may cause .so library load errors.
   See: https://github.com/pytorch/executorch/issues
        """)

    # Check RAM
    import psutil
    available_gb = psutil.virtual_memory().available / 1024**3
    print(f"  Available RAM: {available_gb:.1f} GB")

    if available_gb < 4.0:
        print("""
❌ Not enough RAM. Export requires at least 4 GB free.
   Close other applications and try again.
   Alternative: Use download_model.sh to get pre-exported PTE.
        """)
        sys.exit(1)

    # Check disk space
    import shutil
    _, _, free = shutil.disk_usage(".")
    free_gb = free / 1024**3
    print(f"  Free disk: {free_gb:.1f} GB")

    if free_gb < 5.0:
        print("❌ Not enough disk space. Need at least 5 GB free.")
        sys.exit(1)

    # Check HuggingFace login
    try:
        from huggingface_hub import whoami
        user = whoami()
        print(f"  HuggingFace: logged in as {user['name']}")
    except Exception:
        print("""
❌ Not logged into HuggingFace.
   Run: huggingface-cli login
   Token: https://huggingface.co/settings/tokens
        """)
        sys.exit(1)

    print("✅ Environment checks passed\n")


# ── Export ────────────────────────────────────────────────────────────────────

def export_model():
    """Export Llama 3.2 1B to ExecuTorch PTE format."""

    from optimum.executorch import ExecuTorchModelForCausalLM
    from transformers import AutoTokenizer

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    print(f"Model:   {MODEL_ID}")
    print(f"Backend: {BACKEND}")
    print(f"Recipe:  {RECIPE}")
    print(f"Output:  {OUTPUT_DIR}")
    print("")
    print("Exporting... this takes 10-20 minutes.")
    print("")

    # Export
    model = ExecuTorchModelForCausalLM.from_pretrained(
        MODEL_ID,
        recipe=BACKEND,
    )

    # Tokenizer
    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)

    # Save
    print("Saving model.pte...")
    model.save_pretrained(OUTPUT_DIR)
    tokenizer.save_pretrained(OUTPUT_DIR)

    print("✅ Export complete")


# ── Verify ────────────────────────────────────────────────────────────────────

def verify_output():
    """Check output files exist and are the right size."""

    print("\nVerifying output...")

    pte_path = os.path.join(OUTPUT_DIR, "model.pte")
    tok_path = os.path.join(OUTPUT_DIR, "tokenizer.model")

    if os.path.exists(pte_path):
        size_mb = os.path.getsize(pte_path) / 1024**2
        print(f"✅ model.pte: {size_mb:.0f} MB")
        if size_mb < 900:
            print("⚠️  File seems small — export may have been incomplete")
    else:
        print("❌ model.pte not found")

    if os.path.exists(tok_path):
        size_mb = os.path.getsize(tok_path) / 1024**2
        print(f"✅ tokenizer.model: {size_mb:.1f} MB")
    else:
        print("❌ tokenizer.model not found")


# ── Main ──────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    print("=" * 50)
    print(" edgelm — export_model.py")
    print("=" * 50)
    print("")

    check_environment()
    export_model()
    verify_output()

    print("")
    print("=" * 50)
    print(" Next: run android/summarizer")
    print("=" * 50)
