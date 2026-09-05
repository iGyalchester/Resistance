CREATE DATABASE  IF NOT EXISTS `job_tracker`;
USE `job_tracker`;

DROP TABLE IF EXISTS `status_history`;
DROP TABLE IF EXISTS `login_code`;
DROP TABLE IF EXISTS `job_application`;
DROP TABLE IF EXISTS `contact`;
DROP TABLE IF EXISTS `user_account`;

--
-- Table structure for table `user_account` (tracker users, auto-provisioned
-- by intake-service; passwordless, so no password column)
--

CREATE TABLE `user_account` (
  `id` int NOT NULL AUTO_INCREMENT,
  `full_name` varchar(90) DEFAULT NULL,
  `email` varchar(120) NOT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `intake_alias` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `EMAIL_UNIQUE` (`email`),
  UNIQUE KEY `INTAKE_ALIAS_UNIQUE` (`intake_alias`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

--
-- Data for table `user_account`
--

INSERT INTO `user_account` VALUES
	(1,'Demo User','demo@resistance.com',NULL,'demo2f7kq3');

--
-- Table structure for table `login_code` (one-time login codes; only the
-- SHA-256 hash of a code is ever stored)
--

CREATE TABLE `login_code` (
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

--
-- Table structure for table `contact` (recruiters, referrals, hiring managers)
--

CREATE TABLE `contact` (
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

--
-- Data for table `contact`
--

INSERT INTO `contact` VALUES
	(1,'Dana','Reyes','dana.reyes@acme.example',1),
	(2,'Marcus','Lee','marcus.lee@initech.example',1);

--
-- Table structure for table `job_application`
--
-- `status` holds ApplicationStatus enum names:
-- APPLIED, SCREENING, INTERVIEW, OFFER, REJECTED, ACCEPTED, WITHDRAWN
--

CREATE TABLE `job_application` (
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

--
-- Data for table `job_application`
--

INSERT INTO `job_application` VALUES
	(1,'Acme Corp','Backend Engineer','APPLIED',1,1,'2026-08-20 09:00:00','2026-08-20 09:00:00'),
	(2,'Globex','Data Engineer','SCREENING',NULL,1,'2026-08-18 10:30:00','2026-08-24 15:00:00'),
	(3,'Initech','Java Developer','INTERVIEW',2,1,'2026-08-12 08:15:00','2026-08-26 11:00:00'),
	(4,'Umbrella Labs','Platform Engineer','OFFER',NULL,1,'2026-08-01 12:00:00','2026-08-27 17:45:00'),
	(5,'Stark Industries','Software Engineer','REJECTED',NULL,1,'2026-08-05 14:20:00','2026-08-22 09:30:00');

--
-- Table structure for table `status_history` (one row per status
-- transition; from_status NULL marks the creation event). Cascades on
-- application delete so history never blocks a deletion.
--

CREATE TABLE `status_history` (
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

INSERT INTO `status_history` VALUES
	(1,1,NULL,'APPLIED','2026-08-20 09:00:00','INTAKE'),
	(2,2,NULL,'APPLIED','2026-08-18 10:30:00','INTAKE'),
	(3,2,'APPLIED','SCREENING','2026-08-24 15:00:00','INTAKE'),
	(4,3,NULL,'APPLIED','2026-08-12 08:15:00','INTAKE'),
	(5,3,'APPLIED','INTERVIEW','2026-08-26 11:00:00','INTAKE'),
	(6,4,NULL,'APPLIED','2026-08-01 12:00:00','INTAKE'),
	(7,4,'APPLIED','OFFER','2026-08-27 17:45:00','INTAKE'),
	(8,5,NULL,'APPLIED','2026-08-05 14:20:00','INTAKE'),
	(9,5,'APPLIED','REJECTED','2026-08-22 09:30:00','INTAKE');
