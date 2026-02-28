---
license: gemma
base_model: google/Gemma-2-2B-IT
pipeline_tag: text-generation
tags:
- chat
extra_gated_heading: Access Gemma2-2B-IT on Hugging Face
extra_gated_prompt: >-
  To access Gemma2-2B-IT on Hugging Face, you are required to review and agree
  to the gemma license. To do this, please ensure you are logged in to
  Hugging Face and click below. Requests are processed immediately.
extra_gated_button_content: Acknowledge licensed
---

# litert-community/Gemma2-2B-IT

This model provides a few variants of
[google/Gemma-2-2B-IT](https://huggingface.co/google/Gemma-2-2B-IT) that are ready for
deployment on Android using the
[LiteRT (fka TFLite) stack](https://ai.google.dev/edge/litert) and
[MediaPipe LLM Inference API](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference).

## Use the models

### Colab

*Disclaimer: The target deployment surface for the LiteRT models is
Android/iOS/Web and the stack has been optimized for performance on these
targets. Trying out the system in Colab is an easier way to familiarize yourself
with the LiteRT stack, with the caveat that the performance (memory and latency)
on Colab could be much worse than on a local device.*

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/#fileId=https://huggingface.co/litert-community/Gemma2-2B-IT/blob/main/notebook.ipynb)

### Android

*   Download and install
    [the apk](https://github.com/google-ai-edge/mediapipe-samples/releases/latest/download/llm_inference-debug.apk).
*   Follow the instructions in the app.

To build the demo app from source, please follow the
[instructions](https://github.com/google-ai-edge/mediapipe-samples/blob/main/examples/llm_inference/android/README.md)
from the GitHub repository.

## Performance

### Android

Note that all benchmark stats are from a Samsung S24 Ultra with 
1280 KV cache size with multiple prefill signatures enabled.

<table border="1">
  <tr>
   <th></th>
   <th>Backend</th>
   <th>Prefill (tokens/sec)</th>
   <th>Decode (tokens/sec)</th>
   <th>Time-to-first-token (sec)</th>
   <th>Memory (RSS in MB)</th>
   <th>Model size (MB)</th>
  </tr>
  <tr>
<td>dynamic_int8</td>
<td>cpu</td>
<td><p style="text-align: right">134.03 tk/s</p></td>
<td><p style="text-align: right">10.03 tk/s</p></td>
<td><p style="text-align: right">5.54 s</p></td>
<td><p style="text-align: right">6,216 MB</p></td>
<td><p style="text-align: right">2,587 MB</p></td>
</tr>

</table>

*   Model Size: measured by the size of the .tflite flatbuffer (serialization
    format for LiteRT models)
*   Memory: indicator of peak RAM usage
*   The inference on CPU is accelerated via the LiteRT
    [XNNPACK](https://github.com/google/XNNPACK) delegate with 4 threads
*   Benchmark is done assuming XNNPACK cache is enabled
*   dynamic_int8: quantized model with int8 weights and float activations.
