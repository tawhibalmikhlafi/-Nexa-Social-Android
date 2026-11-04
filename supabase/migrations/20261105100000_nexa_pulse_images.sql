-- Pulse image uploads use a per-user folder: <auth.uid>/<random-file-name>.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'nexa-pulse',
  'nexa-pulse',
  true,
  5242880,
  array['image/jpeg','image/png','image/webp']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Pulse users upload own images" on storage.objects;
create policy "Pulse users upload own images"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'nexa-pulse'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

drop policy if exists "Pulse users read challenge images" on storage.objects;
create policy "Pulse users read challenge images"
on storage.objects for select to authenticated
using (bucket_id = 'nexa-pulse');

drop policy if exists "Pulse users delete own images" on storage.objects;
create policy "Pulse users delete own images"
on storage.objects for delete to authenticated
using (
  bucket_id = 'nexa-pulse'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

alter table public.pulse_entries
  add column if not exists image_path text;

-- Keep body optional only when an image is present.
alter table public.pulse_entries drop constraint if exists pulse_entry_body_length;
alter table public.pulse_entries
  add constraint pulse_entry_has_content
  check (
    (char_length(trim(coalesce(body, ''))) between 1 and 1000)
    or image_path is not null
  );

alter table public.pulse_entries alter column body set default '';
