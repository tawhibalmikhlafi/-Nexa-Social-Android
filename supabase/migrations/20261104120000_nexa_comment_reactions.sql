-- Store the selected emoji for each comment reaction.
alter table public.comment_likes
    add column if not exists emoji text not null default '❤️';

alter table public.comment_likes
    drop constraint if exists comment_likes_emoji_check;
alter table public.comment_likes
    add constraint comment_likes_emoji_check
    check (char_length(emoji) between 1 and 16);

drop policy if exists nexa_comment_likes_update on public.comment_likes;
create policy nexa_comment_likes_update on public.comment_likes
    for update to authenticated using (user_id = auth.uid())
    with check (user_id = auth.uid());
grant select, insert, delete on public.comment_likes to authenticated;
grant update (emoji) on public.comment_likes to authenticated;
revoke all on public.comment_likes from anon, public;

notify pgrst, 'reload schema';
