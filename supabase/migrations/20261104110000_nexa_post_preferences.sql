-- Persist hide and pin choices for signed-in Nexa users.
create table if not exists public.post_hides (
    post_id uuid not null references public.posts (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);

create table if not exists public.post_pins (
    post_id uuid not null references public.posts (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);

alter table public.post_hides enable row level security;
alter table public.post_pins enable row level security;

drop policy if exists nexa_post_hides_select on public.post_hides;
drop policy if exists nexa_post_hides_insert on public.post_hides;
drop policy if exists nexa_post_hides_delete on public.post_hides;
create policy nexa_post_hides_select on public.post_hides
    for select to authenticated using (user_id = auth.uid());
create policy nexa_post_hides_insert on public.post_hides
    for insert to authenticated with check (
        user_id = auth.uid() and public.nexa_can_view_post(post_id)
    );
create policy nexa_post_hides_delete on public.post_hides
    for delete to authenticated using (user_id = auth.uid());

drop policy if exists nexa_post_pins_select on public.post_pins;
drop policy if exists nexa_post_pins_insert on public.post_pins
    for insert to authenticated with check (
        user_id = auth.uid()
        and exists (
            select 1 from public.posts p
            where p.id = post_pins.post_id and p.author_id = auth.uid()
        )
    );
create policy nexa_post_pins_delete on public.post_pins
    for delete to authenticated using (
        user_id = auth.uid()
        and exists (
            select 1 from public.posts p
            where p.id = post_pins.post_id and p.author_id = auth.uid()
        )
    );

grant select, insert, delete on public.post_hides to authenticated;
grant select, insert, delete on public.post_pins to authenticated;
revoke all on public.post_hides, public.post_pins from anon, public;

notify pgrst, 'reload schema';
