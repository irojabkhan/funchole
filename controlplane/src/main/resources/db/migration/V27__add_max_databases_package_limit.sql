-- Adds the MAX_DATABASES limit to the seeded 'free' package (see
-- PackageLimitKey/PackageLimitService) - one auto-provisioned default
-- database per free-tier user, matching MAX_GATEWAYS's 1.

INSERT INTO package_limits (id, package_id, limit_key, limit_value)
VALUES ('33333333-3333-3333-3333-333333333338', '33333333-3333-3333-3333-333333333333', 'MAX_DATABASES', 1)
ON CONFLICT (package_id, limit_key) DO NOTHING;
