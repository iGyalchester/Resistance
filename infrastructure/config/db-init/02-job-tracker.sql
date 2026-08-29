CREATE DATABASE  IF NOT EXISTS `job_tracker`;
USE `job_tracker`;

--
-- Table structure for table `job_application`
--

DROP TABLE IF EXISTS `job_application`;

CREATE TABLE `job_application` (
  `id` int NOT NULL AUTO_INCREMENT,
  `company_name` varchar(90) DEFAULT NULL,
  `position_title` varchar(90) DEFAULT NULL,
  `status` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

--
-- Data for table `job_application`
--

INSERT INTO `job_application` VALUES
	(1,'Acme Corp','Backend Engineer','applied'),
	(2,'Globex','Data Engineer','screening'),
	(3,'Initech','Java Developer','interview'),
	(4,'Umbrella Labs','Platform Engineer','offer'),
	(5,'Stark Industries','Software Engineer','rejected');

--
-- Table structure for table `contact` (recruiters, referrals, hiring managers)
--

DROP TABLE IF EXISTS `contact`;

CREATE TABLE `contact` (
  `id` int NOT NULL AUTO_INCREMENT,
  `first_name` varchar(45) DEFAULT NULL,
  `last_name` varchar(45) DEFAULT NULL,
  `email` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;
