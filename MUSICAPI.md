# MusicAPI (musicapi.ai) – API key and usage

## How to get your API key

1. **Sign up / Sign in**  
   Go to **[musicapi.ai/signin](https://musicapi.ai/signin)** and create an account or log in.

2. **Get your Bearer token**  
   - Open **[musicapi.ai/dashboard/apikey](https://musicapi.ai/dashboard/apikey)** (or: Dashboard → API Key in the site menu).  
   - Copy your **Bearer token**. This is your API key.

3. **Use it in SongMaker**  
   - In the app, find the **“Create full song (MusicAPI)”** section.  
   - Paste the key into **“MusicAPI key (Bearer token)”**.  
   - The app will remember it for next time.

You can paste the key with or without the word `Bearer`; the app adds `Bearer` if it’s missing.

## Requirements

- **Account / credits**  
  MusicAPI is a paid API. You need a subscription and credits.  
  Details: [musicapi.ai](https://musicapi.ai) and [docs.musicapi.ai](https://docs.musicapi.ai).

- **Rate limit**  
  One request every 3 seconds. The app waits for the song to be ready (about 30 seconds) and then plays it.

## If you get "Your MusicAPI API key is incorrect" (401)

- **Regenerate the key:** In [musicapi.ai/dashboard/apikey](https://musicapi.ai/dashboard/apikey), create a **new** API key and use that in the app.
- **Confirm plan:** Your subscription must include access to the **Producer API** (FUZZ model). Some plans only include other endpoints.
- **Contact MusicAPI support:** If the key is active in the dashboard but still rejected, ask them to verify your key works for `POST /api/v1/producer/create`. Their FAQ links to support, e.g. **Telegram:** [t.me/+CeedesRm0T9lODU9](https://t.me/+CeedesRm0T9lODU9).

## What the app does

- Uses the **Producer API** with model **FUZZ-2.0** to generate a full song (vocals + music) from the lyrics in the **Text Input** box.
- Sends those lyrics to MusicAPI, polls until the task completes, then streams the returned audio URL in the app.
