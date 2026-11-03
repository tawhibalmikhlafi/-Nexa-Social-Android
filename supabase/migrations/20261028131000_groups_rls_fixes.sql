-- Groups expansion follow-up: keep post permissions aligned with group settings.

drop policy if exists "group_posts_insert" on public.group_posts;
create policy "group_posts_insert" on public.group_posts for insert with check (
    auth.uid() = author_id
    and public.nexa_is_group_member(group_id, auth.uid())
    and exists (
        select 1 from public.groups g
        where g.id = group_posts.group_id
          and not g.is_archived
          and (g.allow_member_posts or public.nexa_is_group_admin(g.id, auth.uid()))
    )
);

drop policy if exists "group_posts_update_admin" on public.group_posts;
create policy "group_posts_update_admin" on public.group_posts for update using (
    public.nexa_is_group_admin(group_id, auth.uid())
) with check (
    public.nexa_is_group_admin(group_id, auth.uid())
);

notify pgrst, 'reload schema';

drop policy if exists "gpc_update_admin" on public.group_post_comments;
create policy "gpc_update_admin" on public.group_post_comments for update using (
    exists (
        select 1 from public.group_posts gp
        where gp.id = group_post_comments.post_id
          and public.nexa_is_group_admin(gp.group_id, auth.uid())
    )
) with check (
    exists (
        select 1 from public.group_posts gp
        where gp.id = group_post_comments.post_id
          and public.nexa_is_group_admin(gp.group_id, auth.uid())
    )
);
