# On-Device LLM Model for Lyric Generation

SongMaker can generate song lyrics from a text prompt using an on-device LLM (no internet after the model is saved).

## You need a .task file (not .tflite)

MediaPipe LLM Inference requires a **.task** bundle (model + tokenizer + metadata). A standalone **.tflite** file will show “Model file found but load failed.”

## Copy the model to the device (required)

The large Gemma model is **not** bundled in the app. Copy a **.task** file to the device after installing the app.

**Paths the app checks (in order):**
1. `.../files/models/gemma2/model.task`
2. `.../files/models/model.task`

**Full path on device:**  
`/data/data/com.songmaker.app/files/models/gemma2/model.task`  
or  
`/data/data/com.songmaker.app/files/models/model.task`

**Ways to copy:**
- **Android Studio Device File Explorer**: **View → Tool Windows → Device File Explorer** → `data/data/com.songmaker.app/files/` → create `models`, then inside it create `gemma2`, and copy your **model.task** into `gemma2` (or copy **model.task** directly into `models`).

### Where to get the .task file

- **Hugging Face – LiteRT Community**: [Gemma2-2B-IT](https://huggingface.co/litert-community/Gemma2-2B-IT) — download a file that **ends in .task** (e.g. a task bundle from the “Files and versions” tab). Accept the Gemma license if prompted. Rename it to **model.task** and copy it to the paths above.

## After the model is ready

1. Open the **Generate lyrics** section.
2. Enter a short prompt (e.g. “a song about summer love” or “freedom on the road”).
3. Tap **Generate lyrics**.
4. Generated lyrics appear in the **Text Input** field; tap **Convert to Song** to hear them with the selected voice.

Runs fully on-device; no lyrics are sent to the internet.
