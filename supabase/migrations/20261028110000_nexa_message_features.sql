-- Nexa messaging: edit, delete for me / for everyone, reactions, story replies.

alter table public.messages add column if not exists edited_at timestamptz;
alter table public.messages add column if not exists is_deleted boolean not null default false;
alter table public.messages
    add column if not exists reply_to_story_id uuid
    references public.stories (id) on delete set null;

create index if not exists messages_reply_story_idx
    on public.messages (reply_to_story_id);

-- Per-user deletion ("delete for me"). The message stays for the other participant.
create table if not exists public.message_deletions (
    message_id uuid not null references public.messages (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    deleted_at timestamptz not null default now(),
    primary key (message_id, user_id)
);

alter table public.message_deletions enable row level security;

drop policy if exists nexa_message_deletions_select on public.message_deletions;
create policy nexa_message_deletions_select on public.message_deletions
for select to authenticated using (user_id = auth.uid());

drop policy if exists nexa_message_deletions_insert on public.message_deletions;
create policy nexa_message_deletions_insert on public.message_deletions
for insert to authenticated with check (
    user_id = auth.uid()
    and exists (
        select 1 from public.messages m
        join public.conversations c on c.id = m.conversation_id
        where m.id = message_id
          and (c.user_a = auth.uid() or c.user_b = auth.uid())
    )
);

drop policy if exists nexa_message_deletions_delete on public.message_deletions;
create policy nexa_message_deletions_delete on public.message_deletions
for delete to authenticated using (user_id = auth.uid());

grant select, insert, delete on public.message_deletions to authenticated;

-- One reaction per user per message.
create table if not exists public.message_reactions (
    id uuid primary key default gen_random_uuid(),
    message_id uuid not null references public.messages (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    emoji text not null check (length(emoji) between 1 and 16),
    created_at timestamptz not null default now(),
    unique (message_id, user_id)
);

create index if not exists message_reactions_message_idx
    on public.message_reactions (message_id);

alter table public.message_reactions enable row level security;

drop policy if exists nexa_message_reactions_select on public.message_reactions;
create policy nexa_message_reactions_select on public.message_reactions
for select to authenticated using (
    exists (
        select 1 from public.messages m
        join public.conversations c on c.id = m.conversation_id
        where m.id = message_reactions.message_id
          and (c.user_a = auth.uid() or c.user_b = auth.uid())
    )
);

drop policy if exists nexa_message_reactions_insert on public.message_reactions;
create policy nexa_message_reactions_insert on public.message_reactions
for insert to authenticated with check (
    user_id = auth.uid()
    and exists (
        select 1 from public.messages m
        join public.conversations c on c.id = m.conversation_id
        where m.id = message_reactions.message_id
          and (c.user_a = auth.uid() or c.user_b = auth.uid())
    )
);

drop policy if exists nexa_message_reactions_update on public.message_reactions;
create policy nexa_message_reactions_update on public.message_reactions
for update to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists nexa_message_reactions_delete on public.message_reactions;
create policy nexa_message_reactions_delete on public.message_reactions
for delete to authenticated using (user_id = auth.uid());

grant select, insert, update, delete on public.message_reactions to authenticated;

-- ── Update guard ───────────────────────────────────────────────────────
create or replace function public.nexa_guard_message_update()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if (new.text is distinct from old.text or new.is_deleted is distinct from old.is_deleted) then
        if old.sender_id <> auth.uid() then
            raise exception 'not the message owner' using errcode = '42501';
        end if;
        if old.is_deleted then
            raise exception 'message already deleted' using errcode = '42501';
        end if;
    end if;
    if new.text is distinct from old.text then
        new.edited_at := now();
    end if;
    return new;
end;
$$;

drop trigger if exists nexa_guard_message_update on public.messages;
create trigger nexa_guard_message_update
before update on public.messages
for each row execute function public.nexa_guard_message_update();

-- ── Read policy: hide deleted messages and per-user deletions ──────────
drop policy if exists nexa_messages_select on public.messages;
create policy nexa_messages_select on public.messages for select to authenticated using (
    exists (
        select 1 from public.conversations c
        where c.id = messages.conversation_id
          and (c.user_a = auth.uid() or c.user_b = auth.uid())
    )
    and not messages.is_deleted
    and not exists (
        select 1 from public.message_deletions d
        where d.message_id = messages.id and d.user_id = auth.uid()
    )
);

grant update (text, edited_at, is_deleted) on public.messages to authenticated;

-- ── Notifications for new messages and story replies ───────────────────
create or replace function public.nexa_notify_message()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    recipient uuid;
begin
    if new.reply_to_story_id is not null then
        return new;
    end if;
    select case when c.user_a = new.sender_id then c.user_b else c.user_a end
        into recipient
    from public.conversations c
    where c.id = new.conversation_id;
    if recipient is null or recipient = new.sender_id then
        return new;
    end if;
    insert into public.notifications (user_id, actor_id, type, preview)
    values (recipient, new.sender_id, 'message', left(coalesce(new.text, ''), 140));
    return new;
end;
$$;

drop trigger if exists nexa_notify_message on public.messages;
create trigger nexa_notify_message
after insert on public.messages
for each row execute function public.nexa_notify_message();

create or replace function public.nexa_notify_story_reply()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    target uuid;
begin
    if new.reply_to_story_id is null then
        return new;
    end if;
    select s.author_id into target
    from public.stories s
    where s.id = new.reply_to_story_id;
    if target is null or target = new.sender_id then
        return new;
    end if;
    insert into public.notifications (user_id, actor_id, type, preview)
    values (target, new.sender_id, 'story_reply', left(coalesce(new.text, ''), 140));
    return new;
end;
$$;

drop trigger if exists nexa_notify_story_reply on public.messages;
create trigger nexa_notify_story_reply
after insert on public.messages
for each row execute function public.nexa_notify_story_reply();

notify pgrst, 'reload schema';
