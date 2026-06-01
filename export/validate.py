"""
validate.py
-----------
Validates summarization quality using the HuggingFace pipeline.
Produces reference output to compare against Android ExecuTorch inference.

Usage:
    python export/validate.py
    python export/validate.py --input "your custom text here"
    python export/validate.py --article health

Requirements:
    pip install transformers torch

Output:
    docs/benchmark_{label}.json   — saved reference result
"""

import argparse
import json
import os
import time
from datetime import datetime

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

# ── Config ────────────────────────────────────────────────────────────────────

MODEL_ID   = "meta-llama/Llama-3.2-1B-Instruct"
MAX_TOKENS = 150
OUTPUT_DIR = "docs"

# ── Test articles ─────────────────────────────────────────────────────────────

TEST_ARTICLES = {
    "tech": """
    Artificial intelligence is transforming how businesses operate across every
    industry. Companies are investing heavily in machine learning systems that
    can automate repetitive tasks, analyze large datasets, and make predictions
    with increasing accuracy. However, most AI systems today require powerful
    cloud servers to run, which raises concerns about privacy, latency, and cost.
    Every query sent to a cloud AI system means user data leaving the device and
    traveling to a remote server. On-device AI aims to solve this by running
    models directly on phones and embedded systems, eliminating the need for a
    constant internet connection and keeping user data local. Recent advances in
    model quantization have made it possible to compress large language models
    from several gigabytes down to under one gigabyte while retaining most of
    their capability.
    """,

    "health": """
    Regular physical exercise has been shown to have significant benefits for
    both physical and mental health. Studies indicate that even moderate amounts
    of activity, such as 30 minutes of walking per day, can reduce the risk of
    heart disease, diabetes, and certain cancers. Exercise also releases
    endorphins, which are natural mood elevators that help combat depression and
    anxiety. Despite these well-documented benefits, surveys consistently show
    that a majority of adults in developed countries do not meet recommended
    activity guidelines. Researchers believe that the rise of sedentary work,
    increased screen time, and urban environments that discourage walking are
    key contributors to this trend. Public health experts are calling for
    structural changes in workplaces and cities to make physical activity easier
    to incorporate into daily life.
    """,
}

# ── Prompt ────────────────────────────────────────────────────────────────────

PROMPT_TEMPLATE = """Summarize the following text in 3 sentences. Be concise and capture the key points.

Text:
{text}

Summary:"""

# ── Save ──────────────────────────────────────────────────────────────────────

def save_results(label, input_text, summary, elapsed, tok_per_sec, input_token_count, output_token_count):
    """Save results to docs/benchmark_{label}.json"""

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    result = {
        "timestamp":          datetime.now().isoformat(),
        "model":              MODEL_ID,
        "article":            label,
        "input_words":        len(input_text.split()),
        "input_tokens":       input_token_count,
        "output_tokens":      output_token_count,
        "summary":            summary,
        "time_seconds":       round(elapsed, 2),
        "tokens_per_sec":     round(tok_per_sec, 1),
        "note":               "Reference output — compare against Android ExecuTorch inference"
    }

    output_path = os.path.join(OUTPUT_DIR, f"benchmark_{label}.json")
    with open(output_path, "w") as f:
        json.dump(result, f, indent=2)

    print(f"\n✅ Saved to {output_path}")
    return output_path

# ── Validate ──────────────────────────────────────────────────────────────────

def run_validation(text: str, label: str = "custom"):
    """Run summarization and save results."""

    print(f"\n{'='*50}")
    print(f" Article: {label}")
    print(f"{'='*50}")
    print(f"\nInput ({len(text.split())} words):")
    print(text.strip())

    prompt = PROMPT_TEMPLATE.format(text=text.strip())

    print(f"\nLoading {MODEL_ID}...")
    print("(First run downloads ~2.5 GB — subsequent runs use cache)\n")

    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
    model = AutoModelForCausalLM.from_pretrained(
        MODEL_ID,
        torch_dtype=torch.float32,
        device_map="cpu",
    )

    inputs = tokenizer(prompt, return_tensors="pt")
    input_token_count = inputs["input_ids"].shape[1]
    print(f"Input tokens: {input_token_count}")

    # Run inference
    start = time.time()
    with torch.no_grad():
        outputs = model.generate(
            **inputs,
            max_new_tokens=MAX_TOKENS,
            do_sample=False,
            temperature=None,
            top_p=None,
        )
    elapsed = time.time() - start

    # Decode only new tokens
    new_tokens = outputs[0][input_token_count:]
    summary = tokenizer.decode(new_tokens, skip_special_tokens=True).strip()
    output_token_count = len(new_tokens)
    tok_per_sec = output_token_count / elapsed

    print(f"\nReference summary:")
    print(f"{'─'*40}")
    print(summary)
    print(f"{'─'*40}")
    print(f"\nStats:")
    print(f"  Input tokens  : {input_token_count}")
    print(f"  Output tokens : {output_token_count}")
    print(f"  Time          : {elapsed:.1f}s")
    print(f"  Speed         : {tok_per_sec:.1f} tok/s (CPU, reference)")

    # Save
    output_path = save_results(
        label, text, summary, elapsed,
        tok_per_sec, input_token_count, output_token_count
    )

    print(f"\n⚠️  Compare this summary against Android inference to verify")
    print(f"   ExecuTorch PTE produces equivalent results.")

    return summary, output_path

# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Validate summarization quality — saves reference output to docs/"
    )
    parser.add_argument(
        "--input",
        type=str,
        default=None,
        help="Custom text to summarize"
    )
    parser.add_argument(
        "--article",
        type=str,
        choices=["tech", "health"],
        default="tech",
        help="Built-in test article to use (default: tech)"
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Run all built-in articles"
    )
    args = parser.parse_args()

    print("=" * 50)
    print(" edgelm — validate.py")
    print(" HuggingFace reference output")
    print("=" * 50)

    if args.input:
        run_validation(args.input, label="custom")
    elif args.all:
        for label, text in TEST_ARTICLES.items():
            run_validation(text, label=label)
    else:
        run_validation(TEST_ARTICLES[args.article], label=args.article)

    print("\n" + "=" * 50)
    print(" Validation complete")
    print(" Reference saved to docs/")
    print(" Next: run Android app and compare output")
    print("=" * 50)


if __name__ == "__main__":
    main()