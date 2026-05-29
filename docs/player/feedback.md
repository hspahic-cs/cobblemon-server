# /feedback — report bugs and suggestions in-game

When something breaks or you want to suggest an improvement, run:

```
/feedback bug <description>
/feedback suggest <description>
```

This files a GitHub issue automatically. The issue includes server-side
context (your coords, dimension, party, recent log lines) so the dev
team has enough to investigate without asking follow-up questions.

## Attaching a screenshot

You can optionally attach a screenshot of what you're looking at:

1. **Press F2** while the bug is on screen. You'll see two messages:
   - Vanilla MC: `Saved screenshot as ...png`
   - Our mod: `📸 Captured. If you /feedback in the next 120s, you'll be asked whether to upload this screenshot to a public URL on the bug report.`
2. **Run `/feedback bug ...`** within 120 seconds. Chat shows two clickable
   buttons:
   ```
   You have a screenshot from Ns ago. Upload it publicly with this issue?
    [ Attach screenshot ]   [ Submit without ]
   (auto-cancels in 30s — defaults to text-only)
   ```
3. **Click [Attach screenshot]** to embed the image in the GitHub issue, or
   **[Submit without]** to skip. If you walk away, after 30s it defaults
   to text-only.

## Privacy notes

- Your username and Minecraft UUID are **not** in public issues — they're
  replaced with a random `anon-XXXXXXXX` ID. Server admins can recover
  the mapping if needed; the public issue does not expose it.
- Screenshots are only uploaded after you click the button. The image
  goes to a public URL, so the rule of thumb is: if you're embarrassed
  by what's in the frame, click "Submit without."
- Vanilla F2 still saves a screenshot to your local screenshots folder
  regardless of what you do with `/feedback`.
