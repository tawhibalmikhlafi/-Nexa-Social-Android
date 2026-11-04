-- Comments and deduplicated per-account view tracking for Nexa clips.
create table if not exists public.clip_comments (
  id uuid primary key default gen_random_uuid(),
  clip_id uuid not null references public.clips(id) on delete cascade,
  author_id uuid not null references public.profiles(id) on delete cascade,
  content text not null check (length(trim(content)) between 1 and 2000),
  created_at timestamptz not null default now(),
  edited_at timestamptz,
  is_deleted boolean not null default false
);
create index if not exists clip_comments_clip_created_idx on public.clip_comments(clip_id, created_at);
alter table public.clip_comments enable row level security;
drop policy if exists nexa_clip_comments_read on public.clip_comments;
create policy nexa_clip_comments_read on public.clip_comments for select to authenticated using (not is_deleted);
drop policy if exists nexa_clip_comments_insert on public.clip_comments;
create policy nexa_clip_comments_insert on public.clip_comments for insert to authenticated with check (author_id = auth.uid());
drop policy if exists nexa_clip_comments_update on public.clip_comments;
create policy nexa_clip_comments_update on public.clip_comments for update to authenticated using (author_id = auth.uid()) with check (author_id = auth.uid());
drop policy if exists nexa_clip_comments_delete on public.clip_comments;
create policy nexa_clip_comments_delete on public.clip_comments for delete to authenticated using (author_id = auth.uid());
grant select, insert, update, delete on public.clip_comments to authenticated;

create or replace function public.nexa_clip_comment_counter() returns trigger
language plpgsql security definer set search_path=public as $$
begin
  if TG_OP='INSERT' then
    update public.clips set comment_count=comment_count+1 where id=new.clip_id;
    return new;
  elsif TG_OP='DELETE' then
    if not old.is_deleted then update public.clips set comment_count=greatest(0,comment_count-1) where id=old.clip_id; end if;
    return old;
  elsif TG_OP='UPDATE' and old.is_deleted is distinct from new.is_deleted then
    update public.clips set comment_count=greatest(0,comment_count + case when new.is_deleted then -1 else 1 end) where id=new.clip_id;
    return new;
  end if;
  return new;
end $$;
drop trigger if exists nexa_clip_comment_counter_trg on public.clip_comments;
create trigger nexa_clip_comment_counter_trg after insert or update or delete on public.clip_comments
for each row execute function public.nexa_clip_comment_counter();

create table if not exists public.clip_views (
  clip_id uuid not null references public.clips(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  viewed_at timestamptz not null default now(),
  primary key(clip_id,user_id)
);
alter table public.clip_views enable row level security;
drop policy if exists nexa_clip_views_select on public.clip_views;
create policy nexa_clip_views_select on public.clip_views for select to authenticated using (user_id=auth.uid());
drop policy if exists nexa_clip_views_insert on public.clip_views;
create policy nexa_clip_views_insert on public.clip_views for insert to authenticated with check (user_id=auth.uid());
grant select, insert on public.clip_views to authenticated;

create or replace function public.nexa_clip_view_counter() returns trigger
language plpgsql security definer set search_path=public as $$
begin
  update public.clips set view_count=view_count+1 where id=new.clip_id;
  return new;
end $$;
drop trigger if exists nexa_clip_view_counter_trg on public.clip_views;
create trigger nexa_clip_view_counter_trg after insert on public.clip_views
for each row execute function public.nexa_clip_view_counter();

notify pgrst, 'reload schema';
