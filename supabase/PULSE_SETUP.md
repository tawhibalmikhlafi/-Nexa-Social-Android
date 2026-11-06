# Nexa Pulse daily challenge

The Android screen uses Supabase for live challenge and community data:
- one entry per authenticated user per challenge (up to 1000 characters, text-only, image, or video; image and video cannot be attached together);
- image uploads to `nexa-pulse` (JPEG, PNG, WebP; 5 MB maximum) and video uploads to `nexa-pulse-video` (MP4, MOV, WebM, 3GP; 50 MB maximum);
- public entry feed for authenticated users;
- one reaction per user per entry, with removal;
- comments up to 500 characters with optional image or video attachments, ownership and active-challenge RLS;
- server-awarded badges: first entry, five entries, first comment, ten comments, and ten received reactions;
- deletion of the current user's own entry and its uploaded image or video;
- challenges sent to mutual-follow friends, with incoming accept/decline actions and participant-only RLS;
- tag, creator, and world metrics derived from actual Pulse entries/reactions for the active challenge. Each entry stores an optional world_id/world_name attribution; the selectable world catalog is still supplied by the existing app world repository.

## Database and security

- `migrations/20261105090000_nexa_pulse_daily_challenge.sql` creates the challenge, entry, and reaction tables with RLS.
- `migrations/20261105100000_nexa_pulse_images.sql` creates the Storage bucket/policies and image path.
- `migrations/20261105110000_nexa_pulse_daily_scheduler.sql` seeds the challenge bank and schedules automatic publication through `pg_cron`.
- `migrations/20261105130000_nexa_pulse_comments_badges.sql` adds authenticated comments, RLS, and server-side badge awards.
- `migrations/20261105140000_nexa_pulse_image_only_entries.sql` aligns the entry constraint with image-only submissions;
- `migrations/20261106150522_nexa_pulse_video_and_friend_challenges.sql` adds video paths and mutual-follow challenge invitations;
- `migrations/20261106150709_nexa_pulse_mutual_friends_view.sql`, `20261106150726_nexa_pulse_invite_policy_use_mutual_view.sql`, `20261106150813_nexa_pulse_friend_challenge_update_guard.sql`, and `20261106150919_nexa_pulse_mutual_friends_private_rpc.sql` secure mutual-friend lookup and harden challenge responses;
- `migrations/20261106151029_nexa_pulse_user_activity_indexes.sql`, `20261106151046_nexa_pulse_comment_media.sql`, and `20261106151142_nexa_pulse_media_path_ownership.sql` add activity indexes and secure comment media;
- `migrations/20261106151336_nexa_pulse_separate_video_storage_bucket.sql` isolates video size limits from images;
- `migrations/20261106151713_nexa_pulse_world_metrics.sql` adds Pulse-native world attribution for entry analytics.
The migrations above have been applied to the Nexa Supabase project. The Android client uses the publishable/anon key and the signed-in user's access token; no service-role key is shipped in the app.

## Automatic scheduling

The `nexa-pulse-publish-daily` cron job runs at 00:05 UTC daily. It publishes the next challenge from `pulse_prompt_bank`, avoids repeating a title used in the last 30 days when possible, and keeps a single active challenge window. To inspect the schedule in Supabase SQL Editor:

```sql
select jobname, schedule, command, active
from cron.job
where jobname = 'nexa-pulse-publish-daily';
```

To add a challenge, insert it into `pulse_prompt_bank`:

```sql
insert into public.pulse_prompt_bank (title, description)
values ('سؤال جديد', 'وصف قصير للتحدي');
```

Only database administrators should edit the challenge bank. The bank has no grants for anonymous or authenticated app users.

## Release scope

- Each entry supports text, one image, or one video plus optional text. Friend challenges use mutual follows; the invitation itself does not automatically create a second entry.
- Comments support text, one image, or one video and are limited to the latest 100 comments per entry.
- The feed is limited to the latest 100 entries for the active challenge.
- The Android workflow runs unit tests, including input-boundary and connection-refused handling, before building the debug APK. CI results must pass before release.
