# Nexa Pulse daily challenge

The Android screen uses Supabase for live challenge and community data:
- one entry per authenticated user per challenge (up to 1000 characters, text and/or one image);
- image uploads to the `nexa-pulse` Storage bucket (JPEG, PNG, WebP; 5 MB maximum);
- public entry feed for authenticated users;
- one reaction per user per entry, with removal;
- deletion of the current user's own entry and its uploaded image.

## Database and security

- `migrations/20261105090000_nexa_pulse_daily_challenge.sql` creates the challenge, entry, and reaction tables with RLS.
- `migrations/20261105100000_nexa_pulse_images.sql` creates the Storage bucket/policies and image path.
- `migrations/20261105110000_nexa_pulse_daily_scheduler.sql` seeds the challenge bank and schedules automatic publication through `pg_cron`.

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

## Current limits

- Each entry supports one image plus text; image/video attachments on comments and an in-app admin editor are not included.
- The feed is currently limited to the latest 100 entries for the active challenge.
- Android build/test status must be verified separately after pulling this branch.
