-- Post like reactions: keep the emoji the user picked alongside the like row.
alter table public.post_likes
    add column if not exists emoji text not null default '❤️';

drop policy if exists nexa_post_likes_update on public.post_likes;
create policy nexa_post_likes_update on public.post_likes for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid() and public.nexa_can_view_post(post_id));

grant update on public.post_likes to authenticated;
