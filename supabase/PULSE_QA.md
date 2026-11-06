# Nexa Pulse QA checklist

Run the automated suite before manual verification:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=1 --console=plain
```

## Automated coverage
- Empty entry without an image is rejected.
- Image-only entry is allowed; text length is capped at 1,000 characters.
- Blank comments are rejected; comments are capped at 500 characters.
- A refused local HTTP connection is returned as a network failure rather than escaping as an uncaught exception.
- HTTP 401, 413, 429, and 5xx responses map to the expected error categories.

## Device / Supabase integration checks
- [ ] Sign in with account A; publish a text entry and verify it appears after refresh.
- [ ] Sign in with account B; verify the entry is visible and B can add a comment and reaction.
- [ ] Verify account B cannot delete account A's entry/comment by direct REST request.
- [ ] Verify an account can publish only one entry per challenge.
- [ ] Publish an image-only entry; verify upload, public image rendering, and delete cleanup.
- [ ] Publish a video entry (MP4/MOV/WebM/3GP, at most 50 MB); verify upload, playback in the feed and detail dialog, and delete cleanup.
- [ ] Verify an image and video cannot be attached to the same entry.
- [ ] With two mutual-follow accounts, send a challenge from A to B; verify B sees it and can accept or decline.
- [ ] Verify a non-mutual account cannot be challenged through direct REST and cannot read another user's challenge rows.
- [ ] Verify a challenge recipient cannot alter the sender, recipient, prompt, message, or creation time when responding.
- [ ] Verify Pulse tag and creator statistics change only when Pulse entries/reactions change, not when general posts change.
- [ ] Disable network before opening Pulse; verify an actionable error appears and the app stays responsive.
- [ ] Disable network while publishing/commenting; verify no false success is shown, then reconnect and retry.
- [ ] Attempt a 501-character comment and 1,001-character entry; verify client-side rejection.
- [ ] Earn the first-entry and first-comment badges; verify they appear after refresh and survive sign-out/sign-in.
- [ ] Verify five-entry, ten-comment, and ten-received-reaction badges only appear after the server-side thresholds are met.
- [ ] Verify the scheduled challenge advances at its cron time and does not create duplicate active prompts.

Manual integration items require two real authenticated accounts and a device; they are not represented as passed by unit tests.
