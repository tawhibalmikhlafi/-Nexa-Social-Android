-- Allow clip owners to edit captions without changing media ownership.
drop policy if exists nexa_clips_update on public.clips;
create policy nexa_clips_update on public.clips for update to authenticated
using (author_id=auth.uid()) with check (author_id=auth.uid());
grant update (caption) on public.clips to authenticated;
notify pgrst,'reload schema';