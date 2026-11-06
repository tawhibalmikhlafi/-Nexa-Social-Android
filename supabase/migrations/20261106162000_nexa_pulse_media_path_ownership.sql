drop policy if exists "pulse users create own active entry" on public.pulse_entries;
create policy "pulse users create own active entry"
on public.pulse_entries for insert to authenticated
with check (
  user_id = (select auth.uid())
  and (image_path is null or split_part(image_path, '/', 1) = (select auth.uid())::text)
  and (video_path is null or split_part(video_path, '/', 1) = (select auth.uid())::text)
  and exists (
    select 1 from public.pulse_prompts p
    where p.id = prompt_id and p.is_published = true
      and now() >= p.starts_at and now() < p.ends_at
  )
);

drop policy if exists "pulse users comment as themselves" on public.pulse_comments;
create policy "pulse users comment as themselves"
on public.pulse_comments for insert to authenticated
with check (
  user_id = (select auth.uid())
  and (
    char_length(trim(coalesce(body, ''))) between 1 and 500
    or (char_length(trim(coalesce(body, ''))) = 0 and (image_path is not null or video_path is not null))
  )
  and not (image_path is not null and video_path is not null)
  and (image_path is null or split_part(image_path, '/', 1) = (select auth.uid())::text)
  and (video_path is null or split_part(video_path, '/', 1) = (select auth.uid())::text)
  and exists (
    select 1 from public.pulse_entries e
    join public.pulse_prompts p on p.id = e.prompt_id
    where e.id = entry_id and p.is_published = true
      and now() >= p.starts_at and now() < p.ends_at
  )
);
