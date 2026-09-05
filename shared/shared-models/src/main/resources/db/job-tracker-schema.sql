-- Idempotent schema for the tracker tables, applied by mvc-service and
-- intake-service on start when the qa profile is active (RDS has no init
-- directory the way the local MySQL container does).
--
-- Rules: CREATE TABLE IF NOT EXISTS only. No DROP, no USE, no seed rows -
-- this runs against live data on every deploy. Column definitions must
-- match infrastructure/config/db-init/02-job-tracker.sql exactly;
-- SchemaFilesInSyncTests in mvc-service fails the build when they drift.

CREATE TABLE IF NOT EXISTS `user_account` (
  `id` int NOT NULL AUTO_INCREMENT,
  `full_name` varchar(90) DEFAULT NULL,
  `email` varchar(120) NOT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `intake_alias` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `EMAIL_UNIQUE` (`email`),
  UNIQUE KEY `INTAKE_ALIAS_UNIQUE` (`intake_alias`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

CREATE TABLE IF NOT EXISTS `login_code` (
  `id` int NOT NULL AUTO_INCREMENT,
  `account_id` int NOT NULL,
  `code_hash` varchar(64) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `consumed` bit(1) NOT NULL DEFAULT 0,
  `attempts` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `FK_LOGIN_ACCOUNT_idx` (`account_id`),
  CONSTRAINT `FK_LOGIN_ACCOUNT` FOREIGN KEY (`account_id`)
  REFERENCES `user_account` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

CREATE TABLE IF NOT EXISTS `contact` (
  `id` int NOT NULL AUTO_INCREMENT,
  `first_name` varchar(45) DEFAULT NULL,
  `last_name` varchar(45) DEFAULT NULL,
  `email` varchar(45) DEFAULT NULL,
  `owner_account_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_CONTACT_OWNER_idx` (`owner_account_id`),
  CONSTRAINT `FK_CONTACT_OWNER` FOREIGN KEY (`owner_account_id`)
  REFERENCES `user_account` (`id`) ON DELETE SET NULL ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

CREATE TABLE IF NOT EXISTS `job_application` (
  `id` int NOT NULL AUTO_INCREMENT,
  `company_name` varchar(90) DEFAULT NULL,
  `position_title` varchar(90) DEFAULT NULL,
  `status` varchar(20) DEFAULT NULL,
  `contact_id` int DEFAULT NULL,
  `owner_account_id` int DEFAULT NULL,
  `applied_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_CONTACT_idx` (`contact_id`),
  CONSTRAINT `FK_CONTACT` FOREIGN KEY (`contact_id`)
  REFERENCES `contact` (`id`) ON DELETE SET NULL ON UPDATE NO ACTION,
  KEY `FK_OWNER_idx` (`owner_account_id`),
  CONSTRAINT `FK_OWNER` FOREIGN KEY (`owner_account_id`)
  REFERENCES `user_account` (`id`) ON DELETE SET NULL ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

CREATE TABLE IF NOT EXISTS `status_history` (
  `id` int NOT NULL AUTO_INCREMENT,
  `application_id` int NOT NULL,
  `from_status` varchar(20) DEFAULT NULL,
  `to_status` varchar(20) NOT NULL,
  `changed_at` datetime(6) NOT NULL,
  `source` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_HISTORY_APP_idx` (`application_id`),
  CONSTRAINT `FK_HISTORY_APP` FOREIGN KEY (`application_id`)
  REFERENCES `job_application` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;
