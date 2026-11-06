-- Keep image uploads at 5 MB and isolate larger video uploads in their own bucket.
update storage.buckets
set file_size_limit = 5242880,
    allowed_mime_types = array['image/jpeg','image/png','image/webp']
where id = 'nexa-pulse';

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'nexa-pulse-video',
  'nexa-pulse-video',
  true,
  52428800,
  array['video/mp4','video/quicktime','video/webm','video/3gpp']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Pulse users upload own videos" on storage.objects;
create policy "Pulse users upload own videos"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'nexa-pulse-video'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

drop policy if exists "Pulse users read challenge videos" on storage.objects;
create policy "Pulse users read challenge videos"
on storage.objects for select to authenticated
using (bucket_id = 'nexa-pulse-video');

drop policy if exists "Pulse users delete own videos" on storage.objects;
create policy "Pulse users delete own videos"
on storage.objects for delete to authenticated
using (
  bucket_id = 'nexa-pulse-video'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);
