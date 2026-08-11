-- Earlier releases keyed rate limits by the Vercel proxy address, which could
-- temporarily block unrelated visitors. Clear only the ephemeral counters as
-- the application switches to the forwarded client address.
DELETE FROM api_rate_limit_buckets;
