-- Nexa posts, comments, likes, saves, shares, conversations and messages tables in Supabase.

create table if not exists public.posts (
    id uuid primary key default gen_random_uuid(),
    author_id uuid not null references public.profiles (id) on delete cascade,
    text text not null default '',
    title text,
    image_uri text,
    image_uris text[] not null default '{}',
    tags text[] not null default '{}',
    audience text not null default 'PUBLIC_LOCAL',
    community_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    like_count integer not null default 0 check (like_count >= 0),
    comment_count integer not null default 0 check (comment_count >= 0),
    share_count integer not null default 0 check (share_count >= 0),
    constraint posts_audience_valid check (audience in ('PUBLIC_LOCAL', 'FOLLOWERS', 'ONLY_ME'))
);

create index if not exists posts_created_at_idx on public.posts (created_at desc);
create index if not exists posts_author_id_idx on public.posts (author_id);

create table if not exists public.post_likes (
    post_id uuid not null references public.posts (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);

create table if not exists public.post_saves (
    post_id uuid not null references public.posts (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);

create table if not exists public.post_shares (
    post_id uuid not null references public.posts (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);

create table if not exists public.comments (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null references public.posts (id) on delete cascade,
    author_id uuid not null references public.profiles (id) on delete cascade,
    parent_id uuid references public.comments (id) on delete cascade,
    text text not null check (length(text) between 1 and 1000),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    like_count integer not null default 0 check (like_count >= 0)
);

create index if not exists comments_post_id_idx on public.comments (post_id, created_at asc);

create table if not exists public.comment_likes (
    comment_id uuid not null references public.comments (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (comment_id, user_id)
);

-- Messaging tables
create table if not exists public.conversations (
    id uuid primary key default gen_random_uuid(),
    user_a uuid not null references public.profiles (id) on delete cascade,
    user_b uuid not null references public.profiles (id) on delete cascade,
    last_message_preview text not null default '',
    last_message_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    constraint conversations_sorted_users check (user_a < user_b),
    constraint conversations_unique_pair unique (user_a, user_b)
);

create index if not exists conversations_user_a_idx on public.conversations (user_a, last_message_at desc);
create index if not exists conversations_user_b_idx on public.conversations (user_b, last_message_at desc);

create table if not exists public.messages (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.conversations (id) on delete cascade,
    sender_id uuid not null references public.profiles (id) on delete cascade,
    text text not null default '',
    attachment_path text,
    attachment_mime text,
    attachment_name text,
    read_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists messages_conversation_idx on public.messages (conversation_id, created_at asc);

-- Triggers for counters & conversation preview
create or replace function public.nexa_post_like_trigger()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if (TG_OP = 'INSERT') then
        update public.posts set like_count = like_count + 1 where id = new.post_id;
        return new;
    elsif (TG_OP = 'DELETE') then
        update public.posts set like_count = greatest(0, like_count - 1) where id = old.post_id;
        return old;
    end if;
    return null;
end;
$$;

drop trigger if exists nexa_post_likes_trg on public.post_likes;
create trigger nexa_post_likes_trg
after insert or delete on public.post_likes
for each row execute function public.nexa_post_like_trigger();

create or replace function public.nexa_comment_trigger()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if (TG_OP = 'INSERT') then
        update public.posts set comment_count = comment_count + 1 where id = new.post_id;
        return new;
    elsif (TG_OP = 'DELETE') then
        update public.posts set comment_count = greatest(0, comment_count - 1) where id = old.post_id;
        return old;
    end if;
    return null;
end;
$$;

drop trigger if exists nexa_comments_trg on public.comments;
create trigger nexa_comments_trg
after insert or delete on public.comments
for each row execute function public.nexa_comment_trigger();

create or replace function public.nexa_comment_like_trigger()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if (TG_OP = 'INSERT') then
        update public.comments set like_count = like_count + 1 where id = new.comment_id;
        return new;
    elsif (TG_OP = 'DELETE') then
        update public.comments set like_count = greatest(0, like_count - 1) where id = old.comment_id;
        return old;
    end if;
    return null;
end;
$$;

drop trigger if exists nexa_comment_likes_trg on public.comment_likes;
create trigger nexa_comment_likes_trg
after insert or delete on public.comment_likes
for each row execute function public.nexa_comment_like_trigger();

create or replace function public.nexa_post_share_trigger()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if (TG_OP = 'INSERT') then
        update public.posts set share_count = share_count + 1 where id = new.post_id;
        return new;
    end if;
    return null;
end;
$$;

drop trigger if exists nexa_post_shares_trg on public.post_shares;
create trigger nexa_post_shares_trg
after insert on public.post_shares
for each row execute function public.nexa_post_share_trigger();

create or replace function public.nexa_message_trigger()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if (TG_OP = 'INSERT') then
        update public.conversations
        set last_message_preview = left(coalesce(nullif(new.text, ''), new.attachment_name, ''), 120),
            last_message_at = new.created_at
        where id = new.conversation_id;
        return new;
    end if;
    return null;
end;
$$;

drop trigger if exists nexa_messages_trg on public.messages;
create trigger nexa_messages_trg
after insert on public.messages
for each row execute function public.nexa_message_trigger();

create or replace function public.nexa_can_view_post(target_post_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.posts p
        where p.id = target_post_id
        and (
            p.author_id = auth.uid()
            or p.audience = 'PUBLIC_LOCAL'
            or (
                p.audience = 'FOLLOWERS'
                and exists (
                    select 1
                    from public.follows f
                    where f.follower_id = auth.uid() and f.following_id = p.author_id
                )
            )
        )
    );
$$;

grant execute on function public.nexa_can_view_post(uuid) to authenticated;

-- Row Level Security
alter table public.posts enable row level security;
alter table public.post_likes enable row level security;
alter table public.post_saves enable row level security;
alter table public.post_shares enable row level security;
alter table public.comments enable row level security;
alter table public.comment_likes enable row level security;
alter table public.conversations enable row level security;
alter table public.messages enable row level security;

do $$
declare policy_row record;
begin
    for policy_row in
        select policyname, tablename
        from pg_policies
        where schemaname = 'public'
          and tablename in (
              'posts', 'post_likes', 'post_saves', 'post_shares',
              'comments', 'comment_likes', 'conversations', 'messages'
          )
          and policyname like 'nexa_%'
    loop
        execute format(
            'drop policy if exists %I on public.%I',
            policy_row.policyname,
            policy_row.tablename
        );
    end loop;
end $$;

create policy nexa_posts_select on public.posts for select to authenticated using (
    author_id = auth.uid()
    or audience = 'PUBLIC_LOCAL'
    or (
        audience = 'FOLLOWERS'
        and exists (
            select 1 from public.follows f
            where f.follower_id = auth.uid() and f.following_id = posts.author_id
        )
    )
);
create policy nexa_posts_insert on public.posts for insert to authenticated with check (author_id = auth.uid());
create policy nexa_posts_update on public.posts for update to authenticated using (author_id = auth.uid()) with check (author_id = auth.uid());
create policy nexa_posts_delete on public.posts for delete to authenticated using (author_id = auth.uid());

create policy nexa_post_likes_select on public.post_likes for select to authenticated using (true);
create policy nexa_post_likes_insert on public.post_likes for insert to authenticated with check (user_id = auth.uid() and public.nexa_can_view_post(post_id));
create policy nexa_post_likes_delete on public.post_likes for delete to authenticated using (user_id = auth.uid());

create policy nexa_post_saves_select on public.post_saves for select to authenticated using (user_id = auth.uid());
create policy nexa_post_saves_insert on public.post_saves for insert to authenticated with check (user_id = auth.uid() and public.nexa_can_view_post(post_id));
create policy nexa_post_saves_delete on public.post_saves for delete to authenticated using (user_id = auth.uid());

create policy nexa_post_shares_select on public.post_shares for select to authenticated using (true);
create policy nexa_post_shares_insert on public.post_shares for insert to authenticated with check (user_id = auth.uid() and public.nexa_can_view_post(post_id));

create policy nexa_comments_select on public.comments for select to authenticated using (
    public.nexa_can_view_post(post_id)
);
create policy nexa_comments_insert on public.comments for insert to authenticated with check (
    author_id = auth.uid()
    and public.nexa_can_view_post(post_id)
);
create policy nexa_comments_update on public.comments for update to authenticated using (author_id = auth.uid()) with check (author_id = auth.uid());
create policy nexa_comments_delete on public.comments for delete to authenticated using (author_id = auth.uid() or exists (select 1 from public.posts p where p.id = comments.post_id and p.author_id = auth.uid()));

create policy nexa_comment_likes_select on public.comment_likes for select to authenticated using (true);
create policy nexa_comment_likes_insert on public.comment_likes for insert to authenticated with check (
    user_id = auth.uid()
    and exists (
        select 1 from public.comments c
        where c.id = comment_id and public.nexa_can_view_post(c.post_id)
    )
);
create policy nexa_comment_likes_delete on public.comment_likes for delete to authenticated using (user_id = auth.uid());

create policy nexa_conversations_select on public.conversations for select to authenticated using (user_a = auth.uid() or user_b = auth.uid());
create policy nexa_conversations_insert on public.conversations for insert to authenticated with check (user_a = auth.uid() or user_b = auth.uid());

create policy nexa_messages_select on public.messages for select to authenticated using (exists (select 1 from public.conversations c where c.id = messages.conversation_id and (c.user_a = auth.uid() or c.user_b = auth.uid())));
create policy nexa_messages_insert on public.messages for insert to authenticated with check (sender_id = auth.uid() and exists (select 1 from public.conversations c where c.id = messages.conversation_id and (c.user_a = auth.uid() or c.user_b = auth.uid())));
create policy nexa_messages_update on public.messages for update to authenticated using (exists (select 1 from public.conversations c where c.id = messages.conversation_id and (c.user_a = auth.uid() or c.user_b = auth.uid())));
create policy nexa_messages_delete on public.messages for delete to authenticated using (sender_id = auth.uid());

grant select, insert, delete on public.posts to authenticated;
grant update (text, title, image_uri, image_uris, tags, audience, community_id, updated_at)
    on public.posts to authenticated;
grant select, insert, delete on public.post_likes to authenticated;
grant select, insert, delete on public.post_saves to authenticated;
grant select, insert on public.post_shares to authenticated;
grant select, insert, delete on public.comments to authenticated;
grant update (text, updated_at) on public.comments to authenticated;
grant select, insert, delete on public.comment_likes to authenticated;
grant select, insert on public.conversations to authenticated;
grant select, insert, delete on public.messages to authenticated;
grant update (read_at) on public.messages to authenticated;

notify pgrst, 'reload schema';
