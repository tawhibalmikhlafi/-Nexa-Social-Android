# Nexa Pulse daily challenge

The Android MVP reads the currently active, published challenge from Supabase and supports:
- one text entry per authenticated user per challenge (1000 characters maximum);
- public entry feed for authenticated users;
- one reaction per user per entry, with removal;
- deletion of the current user's own entry.

## Database

Migration: `migrations/20261105090000_nexa_pulse_daily_challenge.sql`.

The migration has been applied to the Nexa Supabase project. Row Level Security is enabled on all three tables. The Android client uses the publishable/anon key and the signed-in user's access token; no service-role key is shipped in the app.

## Publishing the next daily challenge

Challenge creation is intentionally admin-only; there is no admin screen in this MVP. In Supabase SQL Editor, insert the next challenge before its start time. Example (replace the title and description):

```sql
insert into public.pulse_prompts
  (title, description, starts_at, ends_at, is_published)
values
  ('ما المهارة التي تريد تعلمها؟',
   'شاركنا مهارة تتمنى إتقانها ولماذا.',
   date_trunc('day', now()) + interval '1 day',
   date_trunc('day', now()) + interval '2 days',
   true);
```

Use UTC timestamps consistently and ensure challenge windows do not overlap. Only a published challenge whose time window contains the current database time is displayed.

## Current MVP limits

Entries are text-only. Image/video attachments, comments, challenge scheduling automation, and an in-app admin editor are not part of this first implementation. The existing Pulse analytics (trending tags, creators, and worlds) remains available below the challenge.
