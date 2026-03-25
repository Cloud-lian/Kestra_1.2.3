DROP INDEX `ix_type__offset` ON queues;
DROP INDEX `ix_created` ON queues;

CREATE INDEX IF NOT EXISTS queues_created__type ON queues ("created", "type");