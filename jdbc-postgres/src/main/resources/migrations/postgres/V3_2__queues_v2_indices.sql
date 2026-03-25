DROP INDEX IF EXISTS queues_type__offset ON queues ("type", "offset");
DROP INDEX IF EXISTS queues_created ON queues ("created");

CREATE INDEX IF NOT EXISTS queues_created__type ON queues ("created", "type");

