alter table public.posts
    add column if not exists comment_permission text not null default 'EVERYONE';

alter table public.posts
    drop constraint if exists posts_comment_permission_valid;

alter table public.posts
    add constraint posts_comment_permission_valid
    check (comment_permission in ('FOLLOWING', 'FOLLOWERS', 'EVERYONE', 'NO_ONE'));

grant update (comment_permission) on public.posts to authenticated;

create or replace function public.nexa_can_comment_on_post(target_post_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select exists (
        select 1
        from public.posts p
        where p.id = target_post_id
          and auth.uid() is not null
          and (
              p.comment_permission = 'EVERYONE'
              or (
                  p.comment_permission = 'FOLLOWING'
                  and exists (
                      select 1 from public.follows f
                      where f.follower_id = p.author_id
                        and f.following_id = auth.uid()
                  )
              )
              or (
                  p.comment_permission = 'FOLLOWERS'
                  and exists (
                      select 1 from public.follows f
                      where f.follower_id = auth.uid()
                        and f.following_id = p.author_id
                  )
              )
          )
    );
$$;

revoke all on function public.nexa_can_comment_on_post(uuid) from public;
grant execute on function public.nexa_can_comment_on_post(uuid) to authenticated;

drop policy if exists nexa_comments_insert on public.comments;
create policy nexa_comments_insert on public.comments
for insert to authenticated
with check (
    author_id = auth.uid()
    and public.nexa_can_view_post(post_id)
    and public.nexa_can_comment_on_post(post_id)
);

notify pgrst, 'reload schema';
