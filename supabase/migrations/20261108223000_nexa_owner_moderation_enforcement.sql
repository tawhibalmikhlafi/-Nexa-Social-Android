create or replace function public.nexa_can_write()
returns boolean language sql stable security definer set search_path=public
as $$
 select public.nexa_is_owner()
   or (
     exists(select 1 from public.profiles p where p.id=auth.uid() and coalesce(p.account_status,'ACTIVE')='ACTIVE')
     and coalesce((select setting_value::text='true' from public.nexa_app_settings where setting_key='maintenance_mode'),false)=false
   );
$$;
revoke all on function public.nexa_can_write() from public,anon;
grant execute on function public.nexa_can_write() to authenticated;

create or replace function public.nexa_protect_profile_admin_fields()
returns trigger language plpgsql security definer set search_path=public
as $$
begin
 if auth.uid()=old.id and not public.nexa_is_owner() then
   new.account_status := old.account_status;
   new.is_verified := old.is_verified;
   new.verified_label := old.verified_label;
 end if;
 return new;
end $$;
drop trigger if exists nexa_profile_admin_fields_guard on public.profiles;
create trigger nexa_profile_admin_fields_guard before update on public.profiles
for each row execute function public.nexa_protect_profile_admin_fields();

drop policy if exists nexa_posts_insert on public.posts;
create policy nexa_posts_insert on public.posts for insert to authenticated
with check (author_id=auth.uid() and public.nexa_can_write());
drop policy if exists nexa_posts_update on public.posts;
create policy nexa_posts_update on public.posts for update to authenticated
using (author_id=auth.uid() and public.nexa_can_write())
with check (author_id=auth.uid() and public.nexa_can_write());
drop policy if exists nexa_posts_delete on public.posts;
create policy nexa_posts_delete on public.posts for delete to authenticated
using (author_id=auth.uid() and public.nexa_can_write());

drop policy if exists nexa_comments_insert on public.comments;
create policy nexa_comments_insert on public.comments for insert to authenticated
with check (author_id=auth.uid() and public.nexa_can_write() and nexa_can_view_post(post_id) and nexa_can_comment_on_post(post_id));
drop policy if exists nexa_comments_update on public.comments;
create policy nexa_comments_update on public.comments for update to authenticated
using (author_id=auth.uid() and public.nexa_can_write())
with check (author_id=auth.uid() and public.nexa_can_write());
drop policy if exists nexa_comments_delete on public.comments;
create policy nexa_comments_delete on public.comments for delete to authenticated
using (public.nexa_can_write() and ((author_id=auth.uid()) or exists(select 1 from public.posts p where p.id=comments.post_id and p.author_id=auth.uid())));

drop policy if exists nexa_stories_insert on public.stories;
create policy nexa_stories_insert on public.stories for insert to authenticated
with check (author_id=auth.uid() and public.nexa_can_write());
drop policy if exists nexa_stories_update on public.stories;
create policy nexa_stories_update on public.stories for update to authenticated
using (author_id=auth.uid() and public.nexa_can_write())
with check (author_id=auth.uid() and public.nexa_can_write());
drop policy if exists nexa_stories_delete on public.stories;
create policy nexa_stories_delete on public.stories for delete to authenticated
using (author_id=auth.uid() and public.nexa_can_write());

drop policy if exists nexa_clips_insert on public.clips;
create policy nexa_clips_insert on public.clips for insert to authenticated
with check (author_id=auth.uid() and public.nexa_can_write());
drop policy if exists nexa_clips_update on public.clips;
create policy nexa_clips_update on public.clips for update to authenticated
using (author_id=auth.uid() and public.nexa_can_write())
with check (author_id=auth.uid() and public.nexa_can_write());
drop policy if exists nexa_clips_delete on public.clips;
create policy nexa_clips_delete on public.clips for delete to authenticated
using (author_id=auth.uid() and public.nexa_can_write());

notify pgrst,'reload schema';