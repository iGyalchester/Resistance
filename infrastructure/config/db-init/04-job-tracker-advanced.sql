DROP SCHEMA IF EXISTS `job_tracker_advanced`;

CREATE SCHEMA `job_tracker_advanced`;

use `job_tracker_advanced`;

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `recruiter_detail` (
  `id` int NOT NULL AUTO_INCREMENT,
  `linkedin_url` varchar(128) DEFAULT NULL,
  `agency` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;


CREATE TABLE `recruiter` (
  `id` int NOT NULL AUTO_INCREMENT,
  `first_name` varchar(45) DEFAULT NULL,
  `last_name` varchar(45) DEFAULT NULL,
  `email` varchar(45) DEFAULT NULL,
  `recruiter_detail_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_DETAIL_idx` (`recruiter_detail_id`),
  CONSTRAINT `FK_DETAIL` FOREIGN KEY (`recruiter_detail_id`)
  REFERENCES `recruiter_detail` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;


CREATE TABLE `job_posting` (
  `id` int NOT NULL AUTO_INCREMENT,
  `title` varchar(128) DEFAULT NULL,
  `recruiter_id` int DEFAULT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `TITLE_UNIQUE` (`title`),

  KEY `FK_RECRUITER_idx` (`recruiter_id`),

  CONSTRAINT `FK_RECRUITER`
  FOREIGN KEY (`recruiter_id`)
  REFERENCES `recruiter` (`id`)

  ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=latin1;


CREATE TABLE `note` (
  `id` int NOT NULL AUTO_INCREMENT,
  `comment` varchar(256) DEFAULT NULL,
  `job_posting_id` int DEFAULT NULL,

  PRIMARY KEY (`id`),

  KEY `FK_JOB_POSTING_ID_idx` (`job_posting_id`),

  CONSTRAINT `FK_JOB_POSTING`
  FOREIGN KEY (`job_posting_id`)
  REFERENCES `job_posting` (`id`)

  ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;


CREATE TABLE `candidate` (
  `id` int NOT NULL AUTO_INCREMENT,
  `first_name` varchar(45) DEFAULT NULL,
  `last_name` varchar(45) DEFAULT NULL,
  `email` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;


CREATE TABLE `job_posting_candidate` (
  `job_posting_id` int NOT NULL,
  `candidate_id` int NOT NULL,

  PRIMARY KEY (`job_posting_id`,`candidate_id`),

  KEY `FK_CANDIDATE_idx` (`candidate_id`),

  CONSTRAINT `FK_JOB_POSTING_05` FOREIGN KEY (`job_posting_id`)
  REFERENCES `job_posting` (`id`)
  ON DELETE NO ACTION ON UPDATE NO ACTION,

  CONSTRAINT `FK_CANDIDATE` FOREIGN KEY (`candidate_id`)
  REFERENCES `candidate` (`id`)
  ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

SET FOREIGN_KEY_CHECKS = 1;

--
-- Seed data: the advanced-data-service demo runner expects candidate 2 to
-- exist with at least one linked posting (its JOIN FETCH query returns no
-- row otherwise). Titles are UNIQUE - keep these distinct from the titles
-- the demo runner inserts.
--

INSERT INTO `recruiter_detail` VALUES
	(1,'https://linkedin.com/in/susan-public','TalentWorks');

INSERT INTO `recruiter` VALUES
	(1,'Susan','Public','susan@talentworks.example',1);

INSERT INTO `job_posting` VALUES
	(10,'Java Developer - Distributed Systems',1),
	(11,'QA Engineer - Automation',1);

INSERT INTO `note` VALUES
	(1,'Strong pipeline, moving fast on this one',10);

INSERT INTO `candidate` VALUES
	(1,'John','Doe','john@resistance.com'),
	(2,'Mary','Public','mary@resistance.com');

INSERT INTO `job_posting_candidate` VALUES
	(10,1),
	(10,2),
	(11,2);
