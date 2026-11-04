drop policy if exists nexa_clip_storage_update on storage.objects;
create policy nexa_clip_storage_update on storage.objects
for update to authenticated
using (bucket_id = 'clips-media' and (storage.foldername(name))[1] = auth.uid()::text)
with check (bucket_id = 'clips-media' and (storage.foldername(name))[1] = auth.uid()::text);
