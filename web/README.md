# SMS → ntfy Web Dashboard

Static RTL/Persian dashboard using the Sahel font and native `EventSource` for live ntfy events.

Serve (do not open directly from disk):

```sh
python3 -m http.server 8080
```

Open `http://localhost:8080`, enter server/topic, then connect. Public topics work directly when the ntfy server permits CORS. Browser EventSource cannot attach Authorization headers; use a same-origin authenticated proxy for private topics.
