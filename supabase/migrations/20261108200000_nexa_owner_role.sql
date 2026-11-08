-- Secure owner identity for Nexa administration.
-- Owner access is tied to the immutable Supabase Auth user UUID, never a client-supplied email.

create table if not exists public.nexa_admin_roles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    role text not null check (role in ('OWNER', 'MODERATOR')),
    created_at timestamptz not null default now()
);

alter table public.nexa_admin_roles enable row level security;

revoke all on table public.nexa_admin_roles from anon, authenticated;
grant all on table public.nexa_admin_roles to service_role;

create or replace function public.nexa_is_owner()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.nexa_admin_roles r
        where r.user_id = auth.uid()
          and r.role = 'OWNER'
    );
$$;

revoke all on function public.nexa_is_owner() from public, anon;
grant execute on function public.nexa_is_owner() to authenticated;

insert into public.nexa_admin_roles (user_id, role)
values ('b81639ff-c669-416e-8a58-452710658bd2'::uuid, 'OWNER')
on conflict (user_id) do update set role = excluded.role;

notify pgrst, 'reload schema';
