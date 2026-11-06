alter table public.pulse_comments
  add column if not exists image_path text,
  add column if not exists video_path text;

alter table public.pulse_comments
  drop constraint if exists pulse_comment_body_length;
alter table public.pulse_comments
  add constraint pulse_comment_has_content check (
    (char_length(trim(coalesce(body, ''))) between 1 and 500)
    or (char_length(trim(coalesce(body, ''))) = 0 and (image_path is not null or video_path is not null))
  ),
  add constraint pulse_comment_single_media check (not (image_path is not null and video_path is not null));

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
  and exists (
    select 1 from public.pulse_entries e
    join public.pulse_prompts p on p.id = e.prompt_id
    where e.id = entry_id and p.is_published = true
      and now() >= p.starts_at and now() < p.ends_at
  )
);
