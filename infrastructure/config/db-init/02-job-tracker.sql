CREATE DATABASE  IF NOT EXISTS `job_tracker`;
USE `job_tracker`;

DROP TABLE IF EXISTS `job_application`;
DROP TABLE IF EXISTS `contact`;

--
-- Table structure for table `contact` (recruiters, referrals, hiring managers)
--

CREATE TABLE `contact` (
  `id` int NOT NULL AUTO_INCREMENT,
  `first_name` varchar(45) DEFAULT NULL,
  `last_name` varchar(45) DEFAULT NULL,
  `email` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

--
-- Data for table `contact`
--

INSERT INTO `contact` VALUES
	(1,'Dana','Reyes','dana.reyes@acme.example'),
	(2,'Marcus','Lee','marcus.lee@initech.example');

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
  PRIMARY KEY (`id`),
  KEY `FK_CONTACT_idx` (`contact_id`),
  CONSTRAINT `FK_CONTACT` FOREIGN KEY (`contact_id`)
  REFERENCES `contact` (`id`) ON DELETE SET NULL ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

--
-- Data for table `job_application`
--

INSERT INTO `job_application` VALUES
	(1,'Acme Corp','Backend Engineer','APPLIED',1),
	(2,'Globex','Data Engineer','SCREENING',NULL),
	(3,'Initech','Java Developer','INTERVIEW',2),
	(4,'Umbrella Labs','Platform Engineer','OFFER',NULL),
	(5,'Stark Industries','Software Engineer','REJECTED',NULL);
