-- Nexa: Supabase Storage buckets and Row Level Security policies.
-- Run this once in the Supabase SQL editor (or with `supabase db execute`).
-- It is safe to re-run: buckets are upserted and every policy is dropped/recreated.
--
-- Media ownership is enforced by the first path segment of the object name. The Android app
-- always uploads to "<auth.uid()>/<random-id>", so a user can never write outside that folder.
-- post-media, story-media and profile-media contain public social media. message-media is
-- private and is only accessed through short-lived signed URLs.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('post-media', 'post-media', true, 10485760,
    array['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/heic', 'image/heif']),
  ('story-media', 'story-media', true, 10485760,
    array['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/heic', 'image/heif']),
  ('profile-media', 'profile-media', true, 10485760,
    array['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/heic', 'image/heif']),
  ('group-media', 'group-media', true, 10485760,
    array['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/heic', 'image/heif']),
  ('message-media', 'message-media', false, 26214400,
    array['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/heic',
          'image/heif', 'video/mp4', 'video/webm', 'audio/mp4', 'audio/mpeg',
          'application/pdf'])
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

-- Public media: anyone may read; only the authenticated owner may write in their uid folder.
drop policy if exists "nexa_post_media_write" on storage.objects;
drop policy if exists "nexa_story_media_write" on storage.objects;
drop policy if exists "nexa_profile_media_write" on storage.objects;
drop policy if exists "nexa_message_media_write" on storage.objects;

drop policy if exists "nexa_post_media_read" on storage.objects;
create policy "nexa_post_media_read"
on storage.objects for select
using (bucket_id = 'post-media');

drop policy if exists "nexa_post_media_insert" on storage.objects;
create policy "nexa_post_media_insert"
on storage.objects for insert
to authenticated
with check (
  bucket_id = 'post-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "nexa_post_media_update" on storage.objects;
create policy "nexa_post_media_update"
on storage.objects for update
to authenticated
using (
  bucket_id = 'post-media'
  and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
  bucket_id = 'post-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "nexa_post_media_delete" on storage.objects;
create policy "nexa_post_media_delete"
on storage.objects for delete
to authenticated
using (
  bucket_id = 'post-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "nexa_story_media_read" on storage.objects;
create policy "nexa_story_media_read"
on storage.objects for select
using (bucket_id = 'story-media');

drop policy if exists "nexa_story_media_insert" on storage.objects;
create policy "nexa_story_media_insert"
on storage.objects for insert
to authenticated
with check (
  bucket_id = 'story-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "nexa_story_media_update" on storage.objects;
create policy "nexa_story_media_update"
on storage.objects for update
to authenticated
using (
  bucket_id = 'story-media'
  and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
  bucket_id = 'story-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "nexa_story_media_delete" on storage.objects;
create policy "nexa_story_media_delete"
on storage.objects for delete
to authenticated
using (
  bucket_id = 'story-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "nexa_profile_media_read" on storage.objects;
create policy "nexa_profile_media_read"
on storage.objects for select
using (bucket_id = 'profile-media');

drop policy if exists "nexa_profile_media_insert" on storage.objects;
create policy "nexa_profile_media_insert"
on storage.objects for insert
to authenticated
with check (
  bucket_id = 'profile-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "nexa_profile_media_update" on storage.objects;
create policy "nexa_profile_media_update"
on storage.objects for update
to authenticated
using (
  bucket_id = 'profile-media'
  and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
  bucket_id = 'profile-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "nexa_profile_media_delete" on storage.objects;
create policy "nexa_profile_media_delete"
on storage.objects for delete
to authenticated
using (
  bucket_id = 'profile-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

-- Group media: public read, owner-scoped writes under "<auth.uid()>/groups/<group-id>/".
drop policy if exists "nexa_group_media_read" on storage.objects;
create policy "nexa_group_media_read"
on storage.objects for select
using (bucket_id = 'group-media');

drop policy if exists "nexa_group_media_insert" on storage.objects;
create policy "nexa_group_media_insert"
on storage.objects for insert
to authenticated
with check (
  bucket_id = 'group-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "nexa_group_media_update" on storage.objects;
create policy "nexa_group_media_update"
on storage.objects for update
to authenticated
using (
  bucket_id = 'group-media'
  and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
  bucket_id = 'group-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "nexa_group_media_delete" on storage.objects;
create policy "nexa_group_media_delete"
on storage.objects for delete
to authenticated
using (
  bucket_id = 'group-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

-- Private message media: only the owner can read, sign or delete an object.
drop policy if exists "nexa_message_media_read" on storage.objects;
create policy "nexa_message_media_read"
on storage.objects for select
to authenticated
using (
  bucket_id = 'message-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "nexa_message_media_insert" on storage.objects;
create policy "nexa_message_media_insert"
on storage.objects for insert
to authenticated
with check (
  bucket_id = 'message-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "nexa_message_media_update" on storage.objects;
create policy "nexa_message_media_update"
on storage.objects for update
to authenticated
using (
  bucket_id = 'message-media'
  and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
  bucket_id = 'message-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "nexa_message_media_delete" on storage.objects;
create policy "nexa_message_media_delete"
on storage.objects for delete
to authenticated
using (
  bucket_id = 'message-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);
