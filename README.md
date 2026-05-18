# edgelm

On-device language model deployments for Android using ExecuTorch and Llama 3.2 1B.

ExecuTorch (MLSys 2026) is the first framework to solve *experimentation parity* — validate quantization, debug numerics, and profile performance entirely in PyTorch before the model ever touches a device. No conversion step. No numerical surprises after deployment. This repo is a working implementation of that pipeline across real Android use cases.

---

## Use cases

### 1. Summarizer app
A standalone Android app. Paste or share any long-form text and get a concise summary generated entirely on-device.

### 2. System-level text selection
Register as a system action on Android. When a user selects text anywhere — browser, email, notes, any app — "Summarize" appears directly in the text selection toolbar. Tap it, get a local summary instantly. No internet. No API. Works system-wide.

This is the more compelling demo: it runs inside apps you don't own, the inference is invisible to the user, and the privacy story is immediately obvious.

---

## The pipeline

```
Python (one-time export)               Android (every inference)
────────────────────────               ─────────────────────────
HuggingFace model                      Text input
      ↓                                      ↓
int4/int8 quantization                 Prompt template
      ↓                                      ↓
torch.export → Edge Dialect            C++ tokenizer
      ↓                                      ↓
XNNPACK delegation                     ExecuTorch runtime (~26 KiB)
      ↓                                      ↓
model.pte (~821 MB) ─────────────────▶ Streamed summary output
```

---

## Why this matters

Most on-device ML pipelines today use ONNX, TFLite, or llama.cpp — each requiring model conversion or complete reimplementation outside PyTorch. ExecuTorch eliminates that gap. The same quantized model you debug in Python runs on the phone, with behavior that matches almost exactly.

This repo validates that claim across two deployment surfaces: a standalone app and a system-level Android integration.

---

## Repo structure

```
edgelm/
├── export/
│   ├── export_model.py        # Export Llama 3.2 1B → model.pte
│   ├── validate.py            # Validate summary quality in Python
│   └── prompt_template.py     # Shared prompt used at runtime
│
├── android/
│   ├── summarizer/            # Standalone summarizer app
│   │   ├── MainActivity.kt
│   │   ├── SummarizerViewModel.kt
│   │   └── LlamaRunner.kt
│   │
│   └── process-text/          # System-level text selection integration
│       ├── SummarizeActivity.kt
│       └── AndroidManifest.xml
│
├── docs/
│   └── benchmarks.md
│
├── requirements.txt
└── README.md
```

---

## Roadmap

### Phase 1 — Export & validate (Week 1)
- [ ] Export Llama 3.2 1B with int4/int8 quantization → `model.pte`
- [ ] Validate summary quality against HuggingFace pipeline in Python
- [ ] Confirm XNNPACK output matches eager mode

### Phase 2 — Summarizer app (Week 2)
- [ ] Load `model.pte` on Android via `LlamaRunner.kt`
- [ ] Stream tokens to UI as they generate
- [ ] Basic input / output screen

### Phase 3 — Text selection integration (Week 3)
- [ ] Register `ACTION_PROCESS_TEXT` intent
- [ ] Receive selected text from any app system-wide
- [ ] Show streamed summary in a bottom sheet overlay

### Phase 4 — Polish & benchmark (Week 4)
- [ ] Benchmark prefill and decode on multiple devices
- [ ] On-demand model download (avoid bundling 821 MB in APK)
- [ ] Demo video

---

## Performance

Benchmarked figures from the ExecuTorch paper (Samsung Galaxy S25 Ultra, Llama 3.2 1B, group size 32):

| Backend | Prefill | Decode |
|---|---|---|
| XNNPACK (CPU) | ~529 tok/s | ~67 tok/s |
| Vulkan (GPU) | ~928–1208 tok/s | ~59–66 tok/s |

For a 500-word input and 100-word summary: ~1s to first token, ~2s total on CPU.

---

## Stack

- Model: `meta-llama/Llama-3.2-1B`
- Framework: ExecuTorch + Optimum ExecuTorch
- Quantization: 8da4w (int8 activations × int4 weights, group size 32)
- Backend: XNNPACK
- Platform: Android (Kotlin)

---

## Setup

```bash
pip install executorch optimum-executorch torchao transformers

python export/export_model.py --model_id meta-llama/Llama-3.2-1B --output model.pte

python export/validate.py --model_path model.pte --input "your article text"
```

Copy `model.pte` to `android/summarizer/src/main/assets/` and open in Android Studio.

---

## Reference

Nachin et al., *ExecuTorch: A Unified PyTorch Solution to Run AI Models On-Device*, MLSys 2026. [arXiv:2605.08195](https://arxiv.org/abs/2605.08195)
