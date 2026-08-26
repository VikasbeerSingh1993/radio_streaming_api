-- Divine Bliss web SaaS + CMS (separate from bani_search Gurbani DB)
CREATE TABLE IF NOT EXISTS saas_plans (
  id CHAR(36) PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description TEXT NULL,
  price_cents INT NOT NULL DEFAULT 0,
  price_currency VARCHAR(8) NOT NULL DEFAULT 'INR',
  credits_included BIGINT NOT NULL DEFAULT 0,
  credit_cost_ocr INT NOT NULL DEFAULT 5,
  credit_cost_ai_image INT NOT NULL DEFAULT 10,
  credit_cost_sikh_history INT NOT NULL DEFAULT 2,
  credit_cost_gurbani_ai INT NOT NULL DEFAULT 3,
  daily_limit_sikh_history INT NOT NULL DEFAULT 5,
  daily_limit_gurbani_ai INT NOT NULL DEFAULT 5,
  features_json JSON NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uq_saas_plans_code (code),
  KEY idx_saas_plans_active_sort (active, sort_order)
);

-- Additive migrations for existing divine_bliss_web installs (continueOnError ignores duplicates)
ALTER TABLE saas_plans ADD COLUMN price_currency VARCHAR(8) NOT NULL DEFAULT 'INR';
ALTER TABLE saas_plans ADD COLUMN daily_limit_sikh_history INT NOT NULL DEFAULT 5;
ALTER TABLE saas_plans ADD COLUMN daily_limit_gurbani_ai INT NOT NULL DEFAULT 5;

CREATE TABLE IF NOT EXISTS saas_users (
  id CHAR(36) PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  display_name VARCHAR(255) NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'USER',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  plan_id CHAR(36) NULL,
  plan_name VARCHAR(128) NULL,
  credits_remaining BIGINT NOT NULL DEFAULT 0,
  credits_used BIGINT NOT NULL DEFAULT 0,
  credits_pending BIGINT NOT NULL DEFAULT 0,
  allow_ocr_overage TINYINT(1) NOT NULL DEFAULT 0,
  allow_ai_image_overage TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uq_saas_users_email (email),
  KEY idx_saas_users_plan (plan_id)
);

CREATE TABLE IF NOT EXISTS saas_api_keys (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  name VARCHAR(128) NOT NULL,
  key_prefix VARCHAR(32) NOT NULL,
  key_hash CHAR(64) NOT NULL,
  revoked TINYINT(1) NOT NULL DEFAULT 0,
  last_used_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  hit_count BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uq_saas_api_keys_hash (key_hash),
  KEY idx_saas_api_keys_user (user_id)
);

CREATE TABLE IF NOT EXISTS saas_usage_events (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  api_key_id CHAR(36) NULL,
  operation VARCHAR(64) NOT NULL,
  credits_charged INT NOT NULL,
  overage TINYINT(1) NOT NULL DEFAULT 0,
  status VARCHAR(32) NULL,
  metadata_json JSON NULL,
  created_at DATETIME(3) NOT NULL,
  KEY idx_usage_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS saas_billing_events (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  plan_id CHAR(36) NULL,
  plan_name VARCHAR(128) NULL,
  type VARCHAR(32) NOT NULL,
  amount_cents INT NOT NULL DEFAULT 0,
  credits_added BIGINT NOT NULL DEFAULT 0,
  note TEXT NULL,
  status VARCHAR(32) NULL,
  created_at DATETIME(3) NOT NULL,
  KEY idx_billing_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS saas_pending_registrations (
  id CHAR(36) PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  first_name VARCHAR(128) NULL,
  last_name VARCHAR(128) NULL,
  password_hash VARCHAR(255) NOT NULL,
  otp_hash VARCHAR(255) NOT NULL,
  otp_expires_at DATETIME(3) NOT NULL,
  send_count INT NOT NULL DEFAULT 0,
  verify_attempts INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uq_pending_email (email)
);

CREATE TABLE IF NOT EXISTS site_settings (
  setting_key VARCHAR(64) PRIMARY KEY,
  setting_value MEDIUMTEXT NULL,
  updated_at DATETIME(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS site_pages (
  page_key VARCHAR(64) PRIMARY KEY,
  title VARCHAR(255) NULL,
  subtitle VARCHAR(512) NULL,
  body_html MEDIUMTEXT NULL,
  hero_image_url VARCHAR(1024) NULL,
  meta_json JSON NULL,
  updated_at DATETIME(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS site_media (
  id CHAR(36) PRIMARY KEY,
  slot_key VARCHAR(64) NOT NULL,
  label VARCHAR(255) NULL,
  image_url VARCHAR(1024) NOT NULL,
  link_url VARCHAR(1024) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  active TINYINT(1) NOT NULL DEFAULT 1,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uq_site_media_slot (slot_key)
);
