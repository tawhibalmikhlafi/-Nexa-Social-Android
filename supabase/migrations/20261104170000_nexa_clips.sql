-- Remote short-form video feed for Nexa.
create table if not exists public.clips (
  id uuid primary key default gen_random_uuid(), author_id uuid not null references public.profiles(id) on delete cascade,
  video_path text not null, video_url text not null, caption text not null default '', created_at timestamptz not null default now(),
  like_count integer not null default 0 check (like_count >= 0), comment_count integer not null default 0 check (comment_count >= 0),
  view_count bigint not null default 0 check (view_count >= 0)
);
create index if not exists clips_created_at_idx on public.clips(created_at desc);
create index if not exists clips_author_id_idx on public.clips(author_id);
create table if not exists public.clip_likes (clip_id uuid not null references public.clips(id) on delete cascade,user_id uuid not null references public.profiles(id) on delete cascade,created_at timestamptz not null default now(),primary key(clip_id,user_id));
create table if not exists public.clip_saves (clip_id uuid not null references public.clips(id) on delete cascade,user_id uuid not null references public.profiles(id) on delete cascade,created_at timestamptz not null default now(),primary key(clip_id,user_id));
alter table public.clips enable row level security; alter table public.clip_likes enable row level security; alter table public.clip_saves enable row level security;
drop policy if exists nexa_clips_read on public.clips; create policy nexa_clips_read on public.clips for select to authenticated using (true);
drop policy if exists nexa_clips_insert on public.clips; create policy nexa_clips_insert on public.clips for insert to authenticated with check (author_id=auth.uid());
drop policy if exists nexa_clips_delete on public.clips; create policy nexa_clips_delete on public.clips for delete to authenticated using (author_id=auth.uid());
drop policy if exists nexa_clip_likes_read on public.clip_likes; create policy nexa_clip_likes_read on public.clip_likes for select to authenticated using (true);
drop policy if exists nexa_clip_likes_insert on public.clip_likes; create policy nexa_clip_likes_insert on public.clip_likes for insert to authenticated with check (user_id=auth.uid());
drop policy if exists nexa_clip_likes_delete on public.clip_likes; create policy nexa_clip_likes_delete on public.clip_likes for delete to authenticated using (user_id=auth.uid());
drop policy if exists nexa_clip_saves_read on public.clip_saves; create policy nexa_clip_saves_read on public.clip_saves for select to authenticated using (user_id=auth.uid());
drop policy if exists nexa_clip_saves_insert on public.clip_saves; create policy nexa_clip_saves_insert on public.clip_saves for insert to authenticated with check (user_id=auth.uid());
drop policy if exists nexa_clip_saves_delete on public.clip_saves; create policy nexa_clip_saves_delete on public.clip_saves for delete to authenticated using (user_id=auth.uid());
create or replace function public.nexa_clip_like_counter() returns trigger language plpgsql security definer set search_path=public as $$
begin if TG_OP='INSERT' then update public.clips set like_count=like_count+1 where id=new.clip_id; return new; else update public.clips set like_count=greatest(0,like_count-1) where id=old.clip_id; return old; end if; end $$;
drop trigger if exists nexa_clip_like_counter_trg on public.clip_likes;
create trigger nexa_clip_like_counter_trg after insert or delete on public.clip_likes for each row execute function public.nexa_clip_like_counter();
grant select,insert,delete on public.clips to authenticated; grant select,insert,delete on public.clip_likes,public.clip_saves to authenticated;
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values ('clips-media','clips-media',true,524288000,array['video/mp4','video/webm','video/quicktime']) on conflict(id) do update set public=true,file_size_limit=524288000,allowed_mime_types=excluded.allowed_mime_types;
drop policy if exists nexa_clip_storage_read on storage.objects; create policy nexa_clip_storage_read on storage.objects for select to authenticated using(bucket_id='clips-media');
drop policy if exists nexa_clip_storage_insert on storage.objects; create policy nexa_clip_storage_insert on storage.objects for insert to authenticated with check(bucket_id='clips-media' and (storage.foldername(name))[1]=auth.uid()::text);
drop policy if exists nexa_clip_storage_delete on storage.objects; create policy nexa_clip_storage_delete on storage.objects for delete to authenticated using(bucket_id='clips-media' and (storage.foldername(name))[1]=auth.uid()::text);
notify pgrst,'reload schema';