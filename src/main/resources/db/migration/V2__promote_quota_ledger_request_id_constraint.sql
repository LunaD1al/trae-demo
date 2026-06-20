drop index if exists uq_ai_quota_ledger_request_id;

alter table ai_quota_ledger
  add constraint uq_ai_quota_ledger_request_id unique (request_id);
