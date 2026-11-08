-- The owner dashboard now uses nexa_owner_moderate_user, which also writes
-- an audit record. Remove the superseded status-only RPC.
drop function if exists public.nexa_owner_set_user_status(uuid, text, text);

notify pgrst, 'reload schema';
